#!/usr/bin/env bash
set -euo pipefail

TIMEOUT_SECONDS="${1:-600}"
SERVER_ADDRESS="127.0.0.1:25567"
SERVER_DIR="run/stress-server"
CLIENT_DIR="run/stress-client"
SERVER_LOG="stress-server-e2e.log"
CLIENT_LOG="stress-client-e2e.log"
SERVER_MARKER="Off Hand Combat network stress server E2E passed"
CLIENT_MARKER="Off Hand Combat network stress client E2E passed"
SERVER_FAILURE="Off Hand Combat network stress server E2E failed"
CLIENT_FAILURE="Off Hand Combat network stress client E2E failed"

server_process=''
client_process=''
netem_enabled=0

cleanup() {
  local process
  if [[ "$netem_enabled" -eq 1 ]]; then
    sudo tc qdisc del dev lo root 2>/dev/null || true
  fi
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
  stress-prepare.log stress-server-latest.log stress-client-latest.log
mkdir -p "$SERVER_DIR/config" "$CLIENT_DIR"
printf 'eula=true\n' > "$SERVER_DIR/eula.txt"
cat > "$SERVER_DIR/server.properties" <<'EOF'
online-mode=false
server-port=25567
level-name=stress-world
spawn-protection=0
view-distance=6
simulation-distance=4
max-players=1
motd=Off Hand Combat network stress E2E
EOF
cat > "$SERVER_DIR/config/offhandcombat-common.toml" <<'EOF'
[combat]
oppositeHandCooldown = 0.5
minimumOffhandAttackStrength = 0.0
requireLineOfSight = true
requestCooldownTicks = 20
EOF

gradle --no-daemon --max-workers=1 compileRemoteTestJava generateModMetadata --stacktrace \
  > stress-prepare.log 2>&1

GRADLE_JVM_LIMIT='-Dorg.gradle.jvmargs=-Xmx512m'
setsid gradle "$GRADLE_JVM_LIMIT" --no-daemon --offline --max-workers=1 \
  runNetworkStressServerE2E --stacktrace > "$SERVER_LOG" 2>&1 &
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

command -v tc >/dev/null
sudo tc qdisc replace dev lo root netem delay 120ms 20ms distribution normal
netem_enabled=1
tc qdisc show dev lo | grep -Fq 'netem'
test "$SERVER_ADDRESS" = "127.0.0.1:25567"

setsid xvfb-run -a gradle "$GRADLE_JVM_LIMIT" --no-daemon --offline --max-workers=1 \
  runNetworkStressClientE2E --stacktrace > "$CLIENT_LOG" 2>&1 &
client_process=$!

passed=0
for _ in $(seq 1 "$TIMEOUT_SECONDS"); do
  latest_server_log="$SERVER_DIR/logs/latest.log"
  latest_client_log="$CLIENT_DIR/logs/latest.log"

  if [[ -f "$latest_server_log" ]] && grep -Fq "$SERVER_FAILURE" "$latest_server_log"; then
    cat "$SERVER_LOG"
    cat "$latest_server_log"
    [[ -f "$latest_client_log" ]] && cat "$latest_client_log"
    exit 1
  fi
  if [[ -f "$latest_client_log" ]] && grep -Fq "$CLIENT_FAILURE" "$latest_client_log"; then
    cat "$CLIENT_LOG"
    cat "$latest_client_log"
    [[ -f "$latest_server_log" ]] && cat "$latest_server_log"
    exit 1
  fi

  if [[ -f "$latest_server_log" ]] \
      && [[ -f "$latest_client_log" ]] \
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

cp "$SERVER_DIR/logs/latest.log" stress-server-latest.log
cp "$CLIENT_DIR/logs/latest.log" stress-client-latest.log

for log in stress-server-latest.log stress-client-latest.log; do
  ! grep -Eiq 'mixin apply failed|exception in thread|fatal error|failed to load mod|outofmemoryerror' "$log"
done

grep -F 'duplicate flood remained exactly-once' stress-server-latest.log
grep -F 'sequence 3 accepted before delayed sequence 2' stress-server-latest.log
grep -F '64-request burst caused no extra effect' stress-server-latest.log
grep -F "$SERVER_MARKER" stress-server-latest.log
grep -F "$CLIENT_MARKER" stress-client-latest.log
