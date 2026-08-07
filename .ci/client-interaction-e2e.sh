#!/usr/bin/env bash
set -euo pipefail

TIMEOUT_SECONDS="${1:-240}"
WORLD_SOURCE="run/world"
RUN_PID=""

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
  process_alive && kill -KILL -- "-$RUN_PID" 2>/dev/null || true
  wait "$RUN_PID" 2>/dev/null || true
  RUN_PID=""
}

cleanup() {
  local status=$?
  process_alive && stop_process_group
  exit "$status"
}
trap cleanup EXIT INT TERM

run_client_e2e() {
  local world_name="$1"
  local gradle_task="$2"
  local log_file="$3"
  local success_marker="$4"
  local failure_marker="$5"
  local world_destination="run/saves/$world_name"
  local result="failed"

  rm -rf "$world_destination"
  mkdir -p "$(dirname "$world_destination")"
  cp -a "$WORLD_SOURCE" "$world_destination"
  rm -f "$world_destination/session.lock"
  : > "$log_file"

  set +e
  setsid stdbuf -oL -eL xvfb-run -a -s "-screen 0 1280x720x24" \
    gradle --no-daemon "$gradle_task" --stacktrace \
    > >(tee -a "$log_file") 2> >(tee -a "$log_file" >&2) &
  RUN_PID=$!
  set -e

  local deadline=$((SECONDS + TIMEOUT_SECONDS))
  while true; do
    if grep -Fq "$success_marker" "$log_file"; then
      result="pass"
      break
    fi
    if grep -Fq "$failure_marker" "$log_file"; then
      result="failed"
      break
    fi
    process_alive || break
    if (( SECONDS >= deadline )); then
      result="timeout"
      break
    fi
    sleep 1
  done

  stop_process_group
  if [[ "$result" != "pass" ]]; then
    cat "$log_file"
    echo "$gradle_task did not pass: $result" >&2
    return 1
  fi
  if grep -Eiq 'mixin apply failed|exception in thread|fatal error|failed to load mod|crash report' "$log_file"; then
    cat "$log_file"
    echo "$gradle_task found a fatal signature" >&2
    return 1
  fi
  grep -F "$success_marker" "$log_file"
}

command -v xvfb-run >/dev/null 2>&1 || { echo "xvfb-run is unavailable" >&2; exit 1; }
[[ -f "$WORLD_SOURCE/level.dat" ]] || { echo "Smoke world is unavailable" >&2; exit 1; }
export LIBGL_ALWAYS_SOFTWARE=1
export ALSOFT_DRIVERS=null

run_client_e2e \
  AirSwingWorld \
  runClientAirSwingE2E \
  client-air-swing-e2e.log \
  "Off Hand Combat upstream air swing E2E passed" \
  "Off Hand Combat off-hand air swing E2E failed"

grep -F "animation=OFF_HAND, sequence unchanged, durability unchanged, cooldown reset and recharging" client-air-swing-e2e.log

run_client_e2e \
  InteractionWorld \
  runClientInteractionE2E \
  client-interaction-e2e.log \
  "Off Hand Combat interaction priority E2E passed: button, door and chest" \
  "Off Hand Combat interaction priority E2E failed"

grep -F "Off Hand Combat interaction priority E2E passed for button" client-interaction-e2e.log
grep -F "Off Hand Combat interaction priority E2E passed for door" client-interaction-e2e.log
grep -F "Off Hand Combat interaction priority E2E passed for chest" client-interaction-e2e.log

run_client_e2e \
  VillagerWorld \
  runClientVillagerE2E \
  client-villager-e2e.log \
  "Off Hand Combat villager trading priority E2E passed" \
  "Off Hand Combat villager trading priority E2E failed"
