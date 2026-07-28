#!/usr/bin/env bash
set -euo pipefail

TIMEOUT_SECONDS="${1:-420}"
SERVER_DIR="run/server-only-server"
CLIENT_DIR="run/server-only-peer"
SERVER_LOG="server-only-server-e2e.log"
CLIENT_LOG="server-only-peer-e2e.log"
SERVER_MARKER="Off Hand Combat server-only no-mod NeoForge peer E2E passed"
SERVER_FAILURE_MARKER="Off Hand Combat server-only no-mod peer E2E failed"

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
  "$SERVER_LOG" "$CLIENT_LOG" server-only-prepare.log \
  server-only-server-latest.log server-only-peer-latest.log
mkdir -p "$SERVER_DIR" "$CLIENT_DIR"
printf 'eula=true\n' > "$SERVER_DIR/eula.txt"
cat > "$SERVER_DIR/server.properties" <<'EOF'
online-mode=false
server-port=25569
level-name=server-only-world
spawn-protection=0
view-distance=6
simulation-distance=4
max-players=1
gamemode=survival
force-gamemode=true
difficulty=peaceful
motd=Off Hand Combat server-only no-mod peer E2E
EOF

gradle --no-daemon --max-workers=1 compileRemoteTestJava generateModMetadata --stacktrace \
  > server-only-prepare.log 2>&1

GRADLE_JVM_LIMIT='-Dorg.gradle.jvmargs=-Xmx512m'
setsid gradle "$GRADLE_JVM_LIMIT" --no-daemon --offline --max-workers=1 \
  runServerOnlyServerE2E --stacktrace > "$SERVER_LOG" 2>&1 &
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

setsid xvfb-run -a gradle "$GRADLE_JVM_LIMIT" --no-daemon --offline --max-workers=1 \
  runServerOnlyPeerClientE2E --stacktrace > "$CLIENT_LOG" 2>&1 &
client_process=$!

passed=0
for _ in $(seq 1 "$TIMEOUT_SECONDS"); do
  latest_server_log="$SERVER_DIR/logs/latest.log"
  latest_client_log="$CLIENT_DIR/logs/latest.log"

  if [[ -f "$latest_server_log" ]] && grep -Fq "$SERVER_MARKER" "$latest_server_log"; then
    passed=1
    break
  fi

  if [[ -f "$latest_server_log" ]] && grep -Fq "$SERVER_FAILURE_MARKER" "$latest_server_log"; then
    cat "$SERVER_LOG"
    cat "$latest_server_log"
    [[ -f "$latest_client_log" ]] && cat "$latest_client_log"
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

cp "$SERVER_DIR/logs/latest.log" server-only-server-latest.log
cp "$CLIENT_DIR/logs/latest.log" server-only-peer-latest.log

for log in server-only-server-latest.log server-only-peer-latest.log; do
  ! grep -Eiq 'mixin apply failed|exception in thread|fatal error|failed to load mod|outofmemoryerror' "$log"
done

! grep -Fq 'dev.nekomario.offhandcombat' server-only-peer-latest.log
! grep -Fq 'Off Hand Combat' server-only-peer-latest.log
grep -F 'OHCNoModPeer joined the game' server-only-server-latest.log
grep -F 'server-only mismatch peer connected with fresh idle state' server-only-server-latest.log
grep -F "$SERVER_MARKER" server-only-server-latest.log
