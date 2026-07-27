#!/usr/bin/env bash
set -euo pipefail

TIMEOUT_SECONDS="${1:-300}"
SERVER_ADDRESS="127.0.0.1:25565"
SERVER_DIR="run/remote-server"
CLIENT_DIR="run/remote-client"
SERVER_LOG="remote-server-e2e.log"
CLIENT_LOG="remote-client-e2e.log"
SERVER_MARKER="Off Hand Combat remote server E2E passed"
CLIENT_MARKER="Off Hand Combat remote client E2E passed"

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
rm -f "$SERVER_LOG" "$CLIENT_LOG" remote-server-latest.log remote-client-latest.log
mkdir -p "$SERVER_DIR" "$CLIENT_DIR"
printf 'eula=true\n' > "$SERVER_DIR/eula.txt"
cat > "$SERVER_DIR/server.properties" <<'EOF'
online-mode=false
server-port=25565
level-name=remote-world
spawn-protection=0
view-distance=6
simulation-distance=4
motd=Off Hand Combat remote E2E
EOF

# Compile and generate run metadata before concurrent Gradle invocations begin.
gradle --no-daemon compileRemoteTestJava generateModMetadata --stacktrace > remote-prepare.log 2>&1

setsid gradle --no-daemon --offline runRemoteServerE2E --stacktrace > "$SERVER_LOG" 2>&1 &
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

# The Gradle run configuration uses the same loopback address pinned above.
test "$SERVER_ADDRESS" = "127.0.0.1:25565"
setsid xvfb-run -a gradle --no-daemon --offline runRemoteClientE2E --stacktrace > "$CLIENT_LOG" 2>&1 &
client_process=$!

passed=0
for _ in $(seq 1 "$TIMEOUT_SECONDS"); do
  latest_server_log="$SERVER_DIR/logs/latest.log"
  latest_client_log="$CLIENT_DIR/logs/latest.log"

  if [[ -f "$latest_server_log" ]] && [[ -f "$latest_client_log" ]] \
      && grep -Fq "$SERVER_MARKER" "$latest_server_log" \
      && grep -Fq "$CLIENT_MARKER" "$latest_client_log"; then
    passed=1
    break
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

cp "$SERVER_DIR/logs/latest.log" remote-server-latest.log
cp "$CLIENT_DIR/logs/latest.log" remote-client-latest.log

for log in remote-server-latest.log remote-client-latest.log; do
  ! grep -Eiq 'mixin apply failed|exception in thread|fatal error|failed to load mod' "$log"
done

grep -F "$SERVER_MARKER" remote-server-latest.log
grep -F "$CLIENT_MARKER" remote-client-latest.log
