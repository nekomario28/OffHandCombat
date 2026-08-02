#!/usr/bin/env bash
set -euo pipefail
set -o pipefail

LOG_FILE="client-world-e2e.log"
exec > >(tee -a "$LOG_FILE") 2>&1

echo '=== Off Hand Combat full self-hosted release gate start ==='

echo '[1/7] Dedicated server smoke evidence'
test -f run/world/level.dat
if [[ -f client-world-prepare.log ]]; then
  ! grep -Eiq 'mixin apply failed|exception in thread|fatal error|failed to load mod' client-world-prepare.log
fi
echo 'Dedicated server smoke evidence passed'

echo '[2/7] GameTest server'
timeout 300s gradle --no-daemon runGameTestServer --stacktrace 2>&1 | tee full-gametest.log
grep -Fq '10 tests are now running' full-gametest.log
grep -Fq '10 GAME TESTS COMPLETE' full-gametest.log
grep -Fq 'All 10 required tests passed :)' full-gametest.log
echo 'GameTest server passed: all 10 required tests'

echo '[3/7] Physical client smoke'
bash .ci/client-smoke.sh 180
echo 'Physical client smoke passed'

echo '[4/7] Bidirectional vanilla compatibility'
bash .ci/vanilla-server-client-e2e.sh 420
echo 'Bidirectional vanilla compatibility passed'

echo '[5/7] Separate dedicated server with two clients'
bash .ci/remote-multiplayer-e2e.sh 420
echo 'Two-client dedicated-server E2E passed'

echo '[6/7] Reconnect, death/respawn and dimension lifecycle'
bash .ci/remote-lifecycle-e2e.sh 480
echo 'Lifecycle E2E passed'

echo '[7/7] Delayed, reordered and burst network stress'
bash .ci/remote-network-stress-e2e.sh 600
echo 'Network stress E2E passed'

echo 'Verifying distributable JAR'
jar_file="$(find build/libs -maxdepth 1 -type f -name '*.jar' ! -name '*-sources.jar' -print -quit)"
test -n "$jar_file"
for entry in \
  LICENSE \
  META-INF/neoforge.mods.toml \
  offhandcombat.mixins.json \
  dev/nekomario/offhandcombat/OffHandCombat.class \
  assets/offhandcombat/lang/en_us.json \
  assets/offhandcombat/lang/ja_jp.json \
  offhandcombat.png
do
  jar tf "$jar_file" | grep -Fqx "$entry"
done
! jar tf "$jar_file" | grep -Fq 'dev/nekomario/offhandcombat/gametest/'
! jar tf "$jar_file" | grep -Fq 'data/offhandcombat/structure/gametest/'
! jar tf "$jar_file" | grep -Fq 'dev/nekomario/offhandcombat/clienttest/'
! jar tf "$jar_file" | grep -Fq 'dev/nekomario/offhandcombat/remotetest/'
unzip -p "$jar_file" META-INF/neoforge.mods.toml | grep -Fq 'modId="bettercombat"'
unzip -p "$jar_file" META-INF/neoforge.mods.toml | grep -Fq 'modId="combatify"'
sha256sum "$jar_file"

echo '=== Off Hand Combat full self-hosted release gate passed ==='
