#!/usr/bin/env bash
set -euo pipefail

TIMEOUT_SECONDS="${1:-420}"
SERVER_ADDRESS="127.0.0.1:25565"
SERVER_DIR="run/remote-server"
CLIENT_A_DIR="run/remote-client-a"
CLIENT_B_DIR="run/remote-client-b"
SERVER_LOG="remote-server-e2e.log"
CLIENT_A_LOG="remote-client-a-e2e.log"
CLIENT_B_LOG="remote-client-b-e2e.log"
SERVER_MARKER="Off Hand Combat two-client remote server E2E passed"
CLIENT_A_MARKER="Off Hand Combat two-client remote client A E2E passed"
CLIENT_B_MARKER="Off Hand Combat two-client remote client B E2E passed"

server_process=''
client_a_process=''
client_b_process=''

cleanup() {
  local process
  for process in "$client_b_process" "$client_a_process" "$server_process"; do
    if [[ -n "$process" ]]; then
      kill -INT -- "-$process" 2>/dev/null || true
    fi
  done
  sleep 2
  for process in "$client_b_process" "$client_a_process" "$server_process"; do
    if [[ -n "$process" ]]; then
      kill -KILL -- "-$process" 2>/dev/null || true
      wait "$process" 2>/dev/null || true
    fi
  done
}
trap cleanup EXIT

rm -rf "$SERVER_DIR" "$CLIENT_A_DIR" "$CLIENT_B_DIR"
rm -f \
  "$SERVER_LOG" "$CLIENT_A_LOG" "$CLIENT_B_LOG" \
  remote-server-latest.log remote-client-a-latest.log remote-client-b-latest.log
mkdir -p "$SERVER_DIR" "$CLIENT_A_DIR" "$CLIENT_B_DIR"
printf 'eula=true\n' > "$SERVER_DIR/eula.txt"
cat > "$SERVER_DIR/server.properties" <<'EOF'
online-mode=false
server-port=25565
level-name=remote-world
spawn-protection=0
view-distance=6
simulation-distance=4
max-players=2
motd=Off Hand Combat two-client remote E2E
EOF

# Resolve and compile everything before starting concurrent Gradle/game JVMs.
gradle --no-daemon --max-workers=1 compileRemoteTestJava generateModMetadata --stacktrace \
  > remote-prepare.log 2>&1

GRADLE_JVM_LIMIT='-Dorg.gradle.jvmargs=-Xmx512m'
setsid gradle "$GRADLE_JVM_LIMIT" --no-daemon --offline --max-workers=1 \
  runRemoteServerE2E --stacktrace > "$SERVER_LOG" 2>&1 &
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

test "$SERVER_ADDRESS" = "127.0.0.1:25565"
setsid xvfb-run -a gradle "$GRADLE_JVM_LIMIT" --no-daemon --offline --max-workers=1 \
  runRemoteClientAE2E --stacktrace > "$CLIENT_A_LOG" 2>&1 &
client_a_process=$!
setsid xvfb-run -a gradle "$GRADLE_JVM_LIMIT" --no-daemon --offline --max-workers=1 \
  runRemoteClientBE2E --stacktrace > "$CLIENT_B_LOG" 2>&1 &
client_b_process=$!

passed=0
for _ in $(seq 1 "$TIMEOUT_SECONDS"); do
  latest_server_log="$SERVER_DIR/logs/latest.log"
  latest_client_a_log="$CLIENT_A_DIR/logs/latest.log"
  latest_client_b_log="$CLIENT_B_DIR/logs/latest.log"

  if [[ -f "$latest_server_log" ]] \
      && [[ -f "$latest_client_a_log" ]] \
      && [[ -f "$latest_client_b_log" ]] \
      && grep -Fq "$SERVER_MARKER" "$latest_server_log" \
      && grep -Fq "$CLIENT_A_MARKER" "$latest_client_a_log" \
      && grep -Fq "$CLIENT_B_MARKER" "$latest_client_b_log"; then
    passed=1
    break
  fi

  if ! kill -0 "$server_process" 2>/dev/null; then
    cat "$SERVER_LOG"
    [[ -f "$latest_server_log" ]] && cat "$latest_server_log"
    exit 1
  fi
  if ! kill -0 "$client_a_process" 2>/dev/null; then
    cat "$CLIENT_A_LOG"
    [[ -f "$latest_client_a_log" ]] && cat "$latest_client_a_log"
    exit 1
  fi
  if ! kill -0 "$client_b_process" 2>/dev/null; then
    cat "$CLIENT_B_LOG"
    [[ -f "$latest_client_b_log" ]] && cat "$latest_client_b_log"
    exit 1
  fi
  sleep 1
done

if [[ "$passed" -ne 1 ]]; then
  cat "$SERVER_LOG"
  cat "$CLIENT_A_LOG"
  cat "$CLIENT_B_LOG"
  [[ -f "$SERVER_DIR/logs/latest.log" ]] && cat "$SERVER_DIR/logs/latest.log"
  [[ -f "$CLIENT_A_DIR/logs/latest.log" ]] && cat "$CLIENT_A_DIR/logs/latest.log"
  [[ -f "$CLIENT_B_DIR/logs/latest.log" ]] && cat "$CLIENT_B_DIR/logs/latest.log"
  exit 1
fi

cp "$SERVER_DIR/logs/latest.log" remote-server-latest.log
cp "$CLIENT_A_DIR/logs/latest.log" remote-client-a-latest.log
cp "$CLIENT_B_DIR/logs/latest.log" remote-client-b-latest.log

for log in remote-server-latest.log remote-client-a-latest.log remote-client-b-latest.log; do
  ! grep -Eiq 'mixin apply failed|exception in thread|fatal error|failed to load mod|outofmemoryerror' "$log"
done

grep -F 'client A replay remained isolated; armed client B' remote-server-latest.log
grep -F "$SERVER_MARKER" remote-server-latest.log
grep -F "$CLIENT_A_MARKER" remote-client-a-latest.log
grep -F "$CLIENT_B_MARKER" remote-client-b-latest.log
