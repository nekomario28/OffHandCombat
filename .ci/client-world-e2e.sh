#!/usr/bin/env bash
set -euo pipefail

TIMEOUT_SECONDS="${1:-240}"
LOG_FILE="client-world-e2e.log"
SUCCESS_MARKER="Off Hand Combat client world E2E passed"
FAILURE_MARKER="Off Hand Combat client world E2E failed"
WORLD_SOURCE="run/world"
WORLD_DESTINATION="run/saves/SmokeWorld"
RUN_PID=""
RESULT="failed"

process_alive() {
  [[ -n "$RUN_PID" ]] && kill -0 "$RUN_PID" 2>/dev/null
}

stop_process_group() {
  if [[ -z "$RUN_PID" ]]; then
    return
  fi
  kill -TERM -- "-$RUN_PID" 2>/dev/null || true
  for _ in $(seq 1 15); do
    process_alive || break
    sleep 1
  done
  if process_alive; then
    kill -KILL -- "-$RUN_PID" 2>/dev/null || true
  fi
  wait "$RUN_PID" 2>/dev/null || true
}

cleanup() {
  local status=$?
  if process_alive; then
    stop_process_group
  fi
  exit "$status"
}
trap cleanup EXIT INT TERM

if ! command -v xvfb-run >/dev/null 2>&1; then
  echo "xvfb-run is unavailable" >&2
  exit 1
fi
if [[ ! -f "$WORLD_SOURCE/level.dat" ]]; then
  echo "Dedicated-server smoke world is unavailable at $WORLD_SOURCE" >&2
  exit 1
fi

rm -rf "$WORLD_DESTINATION"
mkdir -p "$(dirname "$WORLD_DESTINATION")"
cp -a "$WORLD_SOURCE" "$WORLD_DESTINATION"
rm -f "$WORLD_DESTINATION/session.lock"

: > "$LOG_FILE"
export LIBGL_ALWAYS_SOFTWARE=1
export ALSOFT_DRIVERS=null

set +e
setsid stdbuf -oL -eL xvfb-run -a -s "-screen 0 1280x720x24" \
  gradle --no-daemon runClientWorldE2E --stacktrace \
  > >(tee -a "$LOG_FILE") 2> >(tee -a "$LOG_FILE" >&2) &
RUN_PID=$!
set -e

deadline=$((SECONDS + TIMEOUT_SECONDS))
while true; do
  if grep -Fq "$SUCCESS_MARKER" "$LOG_FILE"; then
    RESULT="pass"
    break
  fi
  if grep -Fq "$FAILURE_MARKER" "$LOG_FILE"; then
    RESULT="failed"
    break
  fi
  if ! process_alive; then
    break
  fi
  if (( SECONDS >= deadline )); then
    RESULT="timeout"
    break
  fi
  sleep 1
done

stop_process_group
trap - EXIT INT TERM

if [[ "$RESULT" != "pass" ]]; then
  cat "$LOG_FILE"
  echo "Integrated client world E2E did not pass: $RESULT" >&2
  exit 1
fi

if grep -Eiq 'mixin apply failed|exception in thread|fatal error|failed to load mod|crash report' "$LOG_FILE"; then
  cat "$LOG_FILE"
  echo "Integrated client world E2E found a fatal signature" >&2
  exit 1
fi

grep -F "Off Hand Combat client world loaded for E2E" "$LOG_FILE"
grep -F "Off Hand Combat client GUI suppression E2E passed" "$LOG_FILE"
grep -F "$SUCCESS_MARKER" "$LOG_FILE"
