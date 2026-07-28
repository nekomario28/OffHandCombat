#!/usr/bin/env bash
set -euo pipefail

TIMEOUT_SECONDS="${1:-420}"
SERVER_DIR="run/vanilla-peer-server"
CLIENT_DIR="run/vanilla-peer-client"
SERVER_LOG="vanilla-peer-server-e2e.log"
CLIENT_LOG="vanilla-peer-client-e2e.log"
CLIENT_MARKER="Off Hand Combat client-only vanilla-server E2E passed"
CLIENT_FAILURE_MARKER="Off Hand Combat client-only vanilla-server E2E failed"
MINECRAFT_VERSION="1.21.1"
SERVER_PORT="25568"

server_process=''
client_process=''

cleanup() {
  local process
  for process in "$client_process" "$server_process"; do
    if [[ -n "$process" ]]; then
      kill -INT -- "-$process" 2>/dev/null || true
    fi
  done
  sleep 2
  for process in "$client_process" "$server_process"; do
    if [[ -n "$process" ]]; then
      kill -KILL -- "-$process" 2>/dev/null || true
      wait "$process" 2>/dev/null || true
    fi
  done
}
trap cleanup EXIT

rm -rf "$SERVER_DIR" "$CLIENT_DIR"
rm -f \
  "$SERVER_LOG" "$CLIENT_LOG" vanilla-peer-prepare.log \
  vanilla-peer-server-latest.log vanilla-peer-client-latest.log
mkdir -p "$SERVER_DIR" "$CLIENT_DIR"
printf 'eula=true\n' > "$SERVER_DIR/eula.txt"
cat > "$SERVER_DIR/server.properties" <<EOF
online-mode=false
server-port=$SERVER_PORT
level-name=vanilla-peer-world
spawn-protection=0
view-distance=6
simulation-distance=4
max-players=1
gamemode=creative
force-gamemode=true
difficulty=peaceful
motd=Off Hand Combat client-only vanilla server E2E
EOF

python3 - "$MINECRAFT_VERSION" "$SERVER_DIR/server.jar" <<'PY'
import hashlib
import json
import pathlib
import sys
import urllib.request

version, output = sys.argv[1], pathlib.Path(sys.argv[2])
manifest_url = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
with urllib.request.urlopen(manifest_url, timeout=60) as response:
    manifest = json.load(response)
entry = next((item for item in manifest["versions"] if item["id"] == version), None)
if entry is None:
    raise SystemExit(f"Minecraft version {version} not found in Mojang manifest")
with urllib.request.urlopen(entry["url"], timeout=60) as response:
    metadata = json.load(response)
server = metadata["downloads"]["server"]
with urllib.request.urlopen(server["url"], timeout=120) as response:
    data = response.read()
actual = hashlib.sha1(data).hexdigest()
if actual != server["sha1"]:
    raise SystemExit(f"server jar SHA-1 mismatch: {actual} != {server['sha1']}")
output.write_bytes(data)
print(f"downloaded Mojang vanilla server {version}: sha1={actual}")
PY

gradle --no-daemon --max-workers=1 compileRemoteTestJava generateModMetadata --stacktrace \
  > vanilla-peer-prepare.log 2>&1

setsid java -Xms256m -Xmx1024m -jar "$SERVER_DIR/server.jar" nogui \
  > "$SERVER_LOG" 2>&1 &
server_process=$!

server_ready=0
for _ in $(seq 1 "$TIMEOUT_SECONDS"); do
  latest_server_log="$SERVER_DIR/logs/latest.log"
  if [[ -f "$latest_server_log" ]] && grep -Fq 'Done (' "$latest_server_log"; then
    server_ready=1
    break
  fi
  if ! kill -0 "$server_process" 2>/dev/null; then
    cat "$SERVER_LOG"
    [[ -f "$latest_server_log" ]] && cat "$latest_server_log"
    exit 1
  fi
  sleep 1
done

if [[ "$server_ready" -ne 1 ]]; then
  cat "$SERVER_LOG"
  [[ -f "$SERVER_DIR/logs/latest.log" ]] && cat "$SERVER_DIR/logs/latest.log"
  exit 1
fi

GRADLE_JVM_LIMIT='-Dorg.gradle.jvmargs=-Xmx512m'
setsid xvfb-run -a gradle "$GRADLE_JVM_LIMIT" --no-daemon --offline --max-workers=1 \
  runVanillaServerClientE2E --stacktrace > "$CLIENT_LOG" 2>&1 &
client_process=$!

passed=0
for _ in $(seq 1 "$TIMEOUT_SECONDS"); do
  latest_server_log="$SERVER_DIR/logs/latest.log"
  latest_client_log="$CLIENT_DIR/logs/latest.log"

  if [[ -f "$latest_client_log" ]] && grep -Fq "$CLIENT_MARKER" "$latest_client_log"; then
    passed=1
    break
  fi

  if [[ -f "$latest_client_log" ]] && grep -Fq "$CLIENT_FAILURE_MARKER" "$latest_client_log"; then
    cat "$CLIENT_LOG"
    cat "$latest_client_log"
    [[ -f "$latest_server_log" ]] && cat "$latest_server_log"
    exit 1
  fi

  if ! kill -0 "$server_process" 2>/dev/null; then
    cat "$SERVER_LOG"
    [[ -f "$latest_server_log" ]] && cat "$latest_server_log"
    [[ -f "$latest_client_log" ]] && cat "$latest_client_log"
    exit 1
  fi
  if ! kill -0 "$client_process" 2>/dev/null; then
    cat "$CLIENT_LOG"
    [[ -f "$latest_client_log" ]] && cat "$latest_client_log"
    [[ -f "$latest_server_log" ]] && cat "$latest_server_log"
    exit 1
  fi
  sleep 1
done

if [[ "$passed" -ne 1 ]]; then
  cat "$SERVER_LOG"
  cat "$CLIENT_LOG"
  [[ -f "$SERVER_DIR/logs/latest.log" ]] && cat "$SERVER_DIR/logs/latest.log"
  [[ -f "$CLIENT_DIR/logs/latest.log" ]] && cat "$CLIENT_DIR/logs/latest.log"
  exit 1
fi

cp "$SERVER_DIR/logs/latest.log" vanilla-peer-server-latest.log
cp "$CLIENT_DIR/logs/latest.log" vanilla-peer-client-latest.log

for log in vanilla-peer-server-latest.log vanilla-peer-client-latest.log; do
  ! grep -Eiq 'mixin apply failed|exception in thread|fatal error|failed to load mod|outofmemoryerror' "$log"
done

grep -F 'OffhandVanillaPeer joined the game' vanilla-peer-server-latest.log
grep -F 'request channel absent' vanilla-peer-client-latest.log
grep -F 'vanilla use input remained uncanceled' vanilla-peer-client-latest.log
grep -F 'dedicated key remained idle without a negotiated channel' vanilla-peer-client-latest.log
grep -F "$CLIENT_MARKER" vanilla-peer-client-latest.log
