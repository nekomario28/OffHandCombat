#!/usr/bin/env bash
set -euo pipefail

TIMEOUT_SECONDS="${1:-480}"
SERVER_ADDRESS="127.0.0.1:25566"
SERVER_DIR="run/lifecycle-server"
CLIENT_DIR="run/lifecycle-client"
SERVER_LOG="lifecycle-server-e2e.log"
CLIENT_LOG="lifecycle-client-e2e.log"
SERVER_MARKER="Off Hand Combat lifecycle server E2E passed"
CLIENT_MARKER="Off Hand Combat lifecycle client E2E passed"
SERVER_FAILURE_MARKER="Off Hand Combat lifecycle server E2E failed"
CLIENT_FAILURE_MARKER="Off Hand Combat lifecycle client E2E failed"

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
  "$SERVER_LOG" "$CLIENT_LOG" \
  lifecycle-server-latest.log lifecycle-client-latest.log
mkdir -p "$SERVER_DIR" "$CLIENT_DIR"
printf 'eula=true\n' > "$SERVER_DIR/eula.txt"
cat > "$SERVER_DIR/server.properties" <<'EOF'
online-mode=false
server-port=25566
level-name=lifecycle-world
spawn-protection=0
view-distance=6
simulation-distance=4
max-players=1
motd=Off Hand Combat lifecycle E2E
EOF

gradle --no-daemon --max-workers=1 compileRemoteTestJava generateModMetadata --stacktrace \
  > lifecycle-prepare.log 2>&1

GRADLE_JVM_LIMIT='-Dorg.gradle.jvmargs=-Xmx512m'
setsid gradle "$GRADLE_JVM_LIMIT" --no-daemon --offline --max-workers=1 \
  runLifecycleServerE2E --stacktrace > "$SERVER_LOG" 2>&1 &
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

test "$SERVER_ADDRESS" = "127.0.0.1:25566"
setsid xvfb-run -a gradle "$GRADLE_JVM_LIMIT" --no-daemon --offline --max-workers=1 \
  runLifecycleClientE2E --stacktrace > "$CLIENT_LOG" 2>&1 &
client_process=$!

passed=0
for _ in $(seq 1 "$TIMEOUT_SECONDS"); do
  latest_server_log="$SERVER_DIR/logs/latest.log"
  latest_client_log="$CLIENT_DIR/logs/latest.log"

  if [[ -f "$latest_server_log" ]] \
      && [[ -f "$latest_client_log" ]] \
      && grep -Fq "$SERVER_MARKER" "$latest_server_log" \
      && grep -Fq "$CLIENT_MARKER" "$latest_client_log"; then
    passed=1
    break
  fi

  if [[ -f "$latest_server_log" ]] && grep -Fq "$SERVER_FAILURE_MARKER" "$latest_server_log"; then
    cat "$SERVER_LOG"
    cat "$latest_server_log"
    [[ -f "$latest_client_log" ]] && cat "$latest_client_log"
    exit 1
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
    exit 1
  fi
  if ! kill -0 "$client_process" 2>/dev/null; then
    cat "$CLIENT_LOG"
    [[ -f "$latest_client_log" ]] && cat "$latest_client_log"
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

cp "$SERVER_DIR/logs/latest.log" lifecycle-server-latest.log
cp "$CLIENT_DIR/logs/latest.log" lifecycle-client-latest.log

for log in lifecycle-server-latest.log lifecycle-client-latest.log; do
  ! grep -Eiq 'mixin apply failed|exception in thread|fatal error|failed to load mod|outofmemoryerror' "$log"
done

grep -F 'lifecycle reconnect state reset passed' lifecycle-server-latest.log
grep -F 'lifecycle death/respawn state reset passed' lifecycle-server-latest.log
grep -F 'lifecycle dimension transition state preservation passed' lifecycle-server-latest.log
grep -F "$SERVER_MARKER" lifecycle-server-latest.log
grep -F "$CLIENT_MARKER" lifecycle-client-latest.log
