#!/usr/bin/env bash
set -euo pipefail

TIMEOUT_SECONDS="${1:-420}"
MC_VERSION="1.21.1"
PORT="25569"
PLAYER_NAME="OHCPlainVanilla"
SERVER_DIR="run/vanilla-client-peer-server"
CLIENT_DIR="run/vanilla-client-peer"
CACHE_DIR="build/vanilla-client-${MC_VERSION}"
SERVER_CONSOLE_LOG="vanilla-client-modded-server.log"
CLIENT_LOG="vanilla-client-peer.log"
SERVER_PID=""
CLIENT_PID=""

process_alive() {
  local pid="$1"
  [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null
}

stop_group() {
  local pid="$1"
  [[ -n "$pid" ]] || return 0
  kill -TERM -- "-$pid" 2>/dev/null || true
  for _ in $(seq 1 15); do
    process_alive "$pid" || break
    sleep 1
  done
  if process_alive "$pid"; then
    kill -KILL -- "-$pid" 2>/dev/null || true
  fi
  wait "$pid" 2>/dev/null || true
}

cleanup() {
  local status=$?
  stop_group "$CLIENT_PID"
  stop_group "$SERVER_PID"
  exit "$status"
}
trap cleanup EXIT INT TERM

if ! command -v xvfb-run >/dev/null 2>&1; then
  echo "xvfb-run is unavailable" >&2
  exit 1
fi

rm -rf "$SERVER_DIR" "$CLIENT_DIR"
mkdir -p "$SERVER_DIR" "$CLIENT_DIR" "$CACHE_DIR"
printf 'eula=true\n' > "$SERVER_DIR/eula.txt"
cat > "$SERVER_DIR/server.properties" <<EOF
online-mode=false
server-port=${PORT}
level-name=world
max-players=1
view-distance=4
simulation-distance=4
spawn-protection=0
EOF
: > "$SERVER_CONSOLE_LOG"
: > "$CLIENT_LOG"

setsid stdbuf -oL -eL gradle --no-daemon runVanillaClientServerE2E --stacktrace \
  > "$SERVER_CONSOLE_LOG" 2>&1 &
SERVER_PID=$!

server_latest="$SERVER_DIR/logs/latest.log"
deadline=$((SECONDS + TIMEOUT_SECONDS))
while true; do
  if [[ -f "$server_latest" ]] && grep -Fq 'Done (' "$server_latest"; then
    break
  fi
  if ! process_alive "$SERVER_PID"; then
    cat "$SERVER_CONSOLE_LOG"
    [[ -f "$server_latest" ]] && cat "$server_latest"
    echo "Modded server exited before becoming ready" >&2
    exit 1
  fi
  if (( SECONDS >= deadline )); then
    cat "$SERVER_CONSOLE_LOG"
    [[ -f "$server_latest" ]] && cat "$server_latest"
    echo "Timed out waiting for modded server" >&2
    exit 1
  fi
  sleep 1
done

python3 - "$MC_VERSION" "$CACHE_DIR" <<'PY'
from __future__ import annotations

import hashlib
import json
import os
import platform
import re
import sys
import urllib.request
import zipfile
from pathlib import Path

version_id = sys.argv[1]
root = Path(sys.argv[2]).resolve()
libraries_dir = root / "libraries"
natives_dir = root / "natives"
libraries_dir.mkdir(parents=True, exist_ok=True)
natives_dir.mkdir(parents=True, exist_ok=True)


def fetch_json(url: str) -> dict:
    with urllib.request.urlopen(url, timeout=60) as response:
        return json.load(response)


def download(url: str, destination: Path, expected_sha1: str | None = None) -> None:
    if destination.is_file() and expected_sha1:
        digest = hashlib.sha1(destination.read_bytes()).hexdigest()
        if digest == expected_sha1:
            return
    destination.parent.mkdir(parents=True, exist_ok=True)
    temporary = destination.with_suffix(destination.suffix + ".tmp")
    with urllib.request.urlopen(url, timeout=120) as response, temporary.open("wb") as output:
        while chunk := response.read(1024 * 1024):
            output.write(chunk)
    if expected_sha1:
        digest = hashlib.sha1(temporary.read_bytes()).hexdigest()
        if digest != expected_sha1:
            temporary.unlink(missing_ok=True)
            raise RuntimeError(f"SHA-1 mismatch for {url}: {digest} != {expected_sha1}")
    temporary.replace(destination)


def rule_matches(rule: dict) -> bool:
    os_rule = rule.get("os")
    if os_rule:
        if os_rule.get("name") not in (None, "linux"):
            return False
        arch = os_rule.get("arch")
        if arch and arch not in ("x86_64", "amd64"):
            return False
        version = os_rule.get("version")
        if version and re.search(version, platform.release()) is None:
            return False
    features = rule.get("features", {})
    for key, expected in features.items():
        actual = False
        if actual != expected:
            return False
    return True


def allowed(library: dict) -> bool:
    rules = library.get("rules")
    if not rules:
        return True
    result = False
    for rule in rules:
        if rule_matches(rule):
            result = rule.get("action") == "allow"
    return result

manifest = fetch_json("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json")
entry = next(item for item in manifest["versions"] if item["id"] == version_id)
version = fetch_json(entry["url"])
(root / "version.json").write_text(json.dumps(version, indent=2), encoding="utf-8")

client = version["downloads"]["client"]
client_jar = root / f"client-{version_id}.jar"
download(client["url"], client_jar, client.get("sha1"))

classpath: list[Path] = [client_jar]
for library in version["libraries"]:
    if not allowed(library):
        continue
    downloads = library.get("downloads", {})
    artifact = downloads.get("artifact")
    if artifact:
        path = libraries_dir / artifact["path"]
        download(artifact["url"], path, artifact.get("sha1"))
        classpath.append(path)

    native_template = library.get("natives", {}).get("linux")
    if not native_template:
        continue
    classifier_name = native_template.replace("${arch}", "64")
    classifier = downloads.get("classifiers", {}).get(classifier_name)
    if not classifier:
        continue
    native_jar = libraries_dir / classifier["path"]
    download(classifier["url"], native_jar, classifier.get("sha1"))
    excluded = tuple(library.get("extract", {}).get("exclude", []))
    with zipfile.ZipFile(native_jar) as archive:
        for member in archive.infolist():
            if member.is_dir() or member.filename.startswith("META-INF/"):
                continue
            if excluded and member.filename.startswith(excluded):
                continue
            target = natives_dir / Path(member.filename).name
            with archive.open(member) as source, target.open("wb") as output:
                output.write(source.read())

(root / "classpath.txt").write_text(os.pathsep.join(str(path) for path in classpath), encoding="utf-8")
(root / "main-class.txt").write_text(version["mainClass"], encoding="utf-8")
(root / "asset-index.txt").write_text(version["assetIndex"]["id"], encoding="utf-8")
PY

assets_dir="${HOME}/.gradle/caches/neoformruntime/assets"
asset_index="$(cat "$CACHE_DIR/asset-index.txt")"
if [[ ! -f "$assets_dir/indexes/${asset_index}.json" ]]; then
  gradle --no-daemon downloadAssets
fi
if [[ ! -f "$assets_dir/indexes/${asset_index}.json" ]]; then
  echo "Minecraft assets index ${asset_index} is unavailable" >&2
  exit 1
fi

classpath="$(cat "$CACHE_DIR/classpath.txt")"
main_class="$(cat "$CACHE_DIR/main-class.txt")"
natives_dir="$(realpath "$CACHE_DIR/natives")"
client_dir="$(realpath "$CLIENT_DIR")"
assets_dir="$(realpath "$assets_dir")"

export LIBGL_ALWAYS_SOFTWARE=1
export ALSOFT_DRIVERS=null
setsid stdbuf -oL -eL xvfb-run -a -s "-screen 0 1280x720x24" \
  java -Xms256m -Xmx1536m \
  -Djava.library.path="$natives_dir" \
  -Dorg.lwjgl.librarypath="$natives_dir" \
  -cp "$classpath" "$main_class" \
  --username "$PLAYER_NAME" \
  --version "$MC_VERSION" \
  --gameDir "$client_dir" \
  --assetsDir "$assets_dir" \
  --assetIndex "$asset_index" \
  --uuid 00000000000000000000000000000042 \
  --accessToken 0 \
  --clientId 0 \
  --xuid 0 \
  --userType legacy \
  --versionType release \
  --userProperties '{}' \
  --quickPlayMultiplayer "127.0.0.1:${PORT}" \
  > "$CLIENT_LOG" 2>&1 &
CLIENT_PID=$!

joined=0
deadline=$((SECONDS + TIMEOUT_SECONDS))
while true; do
  if [[ -f "$server_latest" ]] && grep -Fq "${PLAYER_NAME} joined the game" "$server_latest"; then
    joined=1
    break
  fi
  if ! process_alive "$CLIENT_PID"; then
    cat "$CLIENT_LOG"
    cat "$SERVER_CONSOLE_LOG"
    [[ -f "$server_latest" ]] && cat "$server_latest"
    echo "Vanilla client exited before joining the modded server" >&2
    exit 1
  fi
  if ! process_alive "$SERVER_PID"; then
    cat "$SERVER_CONSOLE_LOG"
    [[ -f "$server_latest" ]] && cat "$server_latest"
    echo "Modded server exited while waiting for the vanilla client" >&2
    exit 1
  fi
  if (( SECONDS >= deadline )); then
    cat "$CLIENT_LOG"
    cat "$SERVER_CONSOLE_LOG"
    [[ -f "$server_latest" ]] && cat "$server_latest"
    echo "Timed out waiting for the vanilla client to join" >&2
    exit 1
  fi
  sleep 1
done

sleep 8
if [[ "$joined" -ne 1 ]] || ! process_alive "$CLIENT_PID" || ! process_alive "$SERVER_PID"; then
  cat "$CLIENT_LOG"
  cat "$SERVER_CONSOLE_LOG"
  [[ -f "$server_latest" ]] && cat "$server_latest"
  echo "Vanilla client connection was not stable" >&2
  exit 1
fi
if grep -Eiq 'incompatible|failed to connect|connection refused|missing required.*channel|mismatched mod' "$CLIENT_LOG"; then
  cat "$CLIENT_LOG"
  echo "Vanilla client reported an incompatibility" >&2
  exit 1
fi

printf 'Off Hand Combat vanilla-client-to-modded-server E2E passed: %s joined and remained connected without the mod.\n' "$PLAYER_NAME"
