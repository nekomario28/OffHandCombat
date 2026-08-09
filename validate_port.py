#!/usr/bin/env python3
from __future__ import annotations

import json
import re
import sys
import tomllib
from pathlib import Path

ROOT = Path(__file__).resolve().parent
errors: list[str] = []


def read(path: str) -> str:
    file = ROOT / path
    if not file.is_file():
        errors.append(f'missing {path}')
        return ''
    return file.read_text(encoding='utf-8')


def require(path: str, *fragments: str) -> str:
    text = read(path)
    for fragment in fragments:
        if fragment not in text:
            errors.append(f'{path} missing required fragment: {fragment}')
    return text


ignored_json_roots = {'.git', '.gradle', 'build', 'run'}
for path in sorted(ROOT.rglob('*.json')):
    relative = path.relative_to(ROOT)
    if relative.parts and relative.parts[0] in ignored_json_roots:
        continue
    try:
        json.loads(path.read_text(encoding='utf-8'))
    except Exception as exc:
        errors.append(f'JSON {relative}: {exc}')

props: dict[str, str] = {}
for line in read('gradle.properties').splitlines():
    line = line.strip()
    if line and not line.startswith('#') and '=' in line:
        key, value = line.split('=', 1)
        props[key.strip()] = value.strip()
metadata_template = read('src/main/templates/META-INF/neoforge.mods.toml')
metadata = metadata_template
for key, value in props.items():
    metadata = metadata.replace('${' + key + '}', value)
try:
    tomllib.loads(metadata)
except Exception as exc:
    errors.append(f'neoforge.mods.toml template: {exc}')
for mod_id in ('bettercombat', 'combatify'):
    if f'modId="{mod_id}"' not in metadata_template:
        errors.append(f'neoforge.mods.toml missing compatibility policy for {mod_id}')
if metadata_template.count('type="discouraged"') < 2 or metadata_template.count('reason=') < 2:
    errors.append('combat-authority conflicts require discouraged metadata with user-facing reasons')

require(
    '.github/workflows/build.yml',
    'actions/setup-java@v4',
    "java-version: '21'",
    "gradle-version: '9.2.1'",
    'python3 validate_port.py',
    'sha256sum --check PORT_MANIFEST.sha256',
    'gradle --no-daemon clean test compileClientTestJava build',
    '10 tests are now running',
    'All 10 required tests passed :)',
    'bash .ci/client-world-e2e.sh 300',
    'bash .ci/vanilla-server-client-e2e.sh 420',
    'bash .ci/remote-multiplayer-e2e.sh 420',
    'bash .ci/remote-lifecycle-e2e.sh 480',
    'bash .ci/remote-network-stress-e2e.sh 600',
    'OffhandActiveUseAlternationE2EHarness.java',
    'ClientActiveUseAlternation.class',
    "! jar tf \"$jar_file\" | grep -Fq 'dev/nekomario/offhandcombat/util/ClientCooldownResetWindow.class'",
    'client-active-use-alternation-e2e.log',
)

build_script = require(
    'build.gradle',
    "gameDirectory = project.file('run')",
    "gameDirectory = project.file('run/remote-server')",
    "gameDirectory = project.file('run/remote-client-a')",
    "gameDirectory = project.file('run/remote-client-b')",
    'sourceSet = sourceSets.remoteTest',
    'clientAirSwingE2E {',
    'clientActiveUseAlternationE2E {',
    "systemProperty 'offhandcombat.airSwingE2E', 'true'",
    "systemProperty 'offhandcombat.activeUseAlternationE2E', 'true'",
)
if '--quickPlayMultiplayer' in build_script:
    errors.append('remote clients must not depend on flaky Quick Play auto-connect')

require(
    '.ci/client-world-e2e.sh',
    'runClientWorldE2E',
    'Off Hand Combat client world E2E passed',
    'Off Hand Combat client GUI suppression E2E passed',
    'bash .ci/client-interaction-e2e.sh "$TIMEOUT_SECONDS"',
)
require(
    '.ci/client-interaction-e2e.sh',
    'runClientAirSwingE2E',
    'Off Hand Combat upstream air swing E2E passed',
    'cooldown reset and recharging',
    'runClientActiveUseAlternationE2E',
    'Off Hand Combat active-hand alternation E2E passed: shield, bow, crossbow and trident',
    'runClientInteractionE2E',
    'runClientVillagerE2E',
)
require('.ci/remote-multiplayer-e2e.sh', 'runRemoteServerE2E', 'runRemoteClientAE2E', 'runRemoteClientBE2E')
require('.ci/remote-lifecycle-e2e.sh', 'runLifecycleServerE2E', 'runLifecycleClientE2E')
require('.ci/remote-network-stress-e2e.sh', 'runNetworkStressServerE2E', 'runNetworkStressClientE2E')
require('.ci/vanilla-server-client-e2e.sh', 'piston-meta.mojang.com', 'vanilla')
require('.ci/vanilla-client-server-e2e.sh', 'piston-meta.mojang.com', 'vanilla')

source_roots = [
    ROOT / 'src/main/java',
    ROOT / 'src/gameTest/java',
    ROOT / 'src/clientTest/java',
    ROOT / 'src/remoteTest/java',
    ROOT / 'src/test/java',
]
for source_root in source_roots:
    if not source_root.exists():
        continue
    for path in sorted(source_root.rglob('*.java')):
        text = path.read_text(encoding='utf-8')
        package_match = re.search(r'^package\s+([\w.]+);', text, re.MULTILINE)
        if not package_match:
            errors.append(f'{path.relative_to(ROOT)}: missing package declaration')
            continue
        expected = Path(*package_match.group(1).split('.')) / path.name
        actual = path.relative_to(source_root)
        if expected != actual:
            errors.append(f'{actual}: package path mismatch; expected {expected}')
        scrubbed = re.sub(r'/\*.*?\*/', '', text, flags=re.DOTALL)
        scrubbed = re.sub(r'//.*', '', scrubbed)
        scrubbed = re.sub(r'"(?:\\.|[^"\\])*"', '""', scrubbed)
        scrubbed = re.sub(r"'(?:\\.|[^'\\])*'", "''", scrubbed)
        for opening, closing in (('(', ')'), ('{', '}'), ('[', ']')):
            if scrubbed.count(opening) != scrubbed.count(closing):
                errors.append(f'{actual}: unbalanced {opening}{closing}')

for required_file in (
    'LICENSE', 'THIRD_PARTY_NOTICES.md', 'AUDIT_1.21.1.md', 'TEST_MATRIX.md',
    'docs/PROTOCOL.md', 'docs/PUBLIC_API.md', 'docs/COMPATIBILITY.md',
):
    if not (ROOT / required_file).is_file():
        errors.append(f'missing {required_file}')
if 'Copyright (c) 2017 Arekkuusu' not in read('LICENSE') or 'MIT License' not in read('LICENSE'):
    errors.append('original MIT attribution is missing')

production_paths = list((ROOT / 'src/main/java').rglob('*.java'))
source_text = '\n'.join(path.read_text(encoding='utf-8') for path in production_paths)
for pattern, description in {
    'ServerboundInteractPacket': 'vanilla packet mutation',
    'invulnerableTime = 0': 'invulnerability-frame reset',
    'lastHurt = 0': 'damage-state reset',
    'getInventory().items.set': 'live main-hand inventory swap',
    'getInventory().offhand.set': 'live off-hand inventory swap',
    'Map<UUID': 'static UUID combat state',
    'static final Map<UUID': 'static UUID combat state',
    'getEyePosition().distanceToSqr(target.getBoundingBox().getCenter())': 'custom entity-reach approximation',
    'markClientCooldownReset': 'obsolete delayed-result cooldown reset',
}.items():
    if pattern in source_text:
        errors.append(f'forbidden pattern remains ({description}): {pattern}')

for pattern, description in {
    'AttachmentType.builder': 'Data Attachment state',
    '.optional()': 'optional protocol negotiation',
    'classifyNetworkSequence': 'request replay classification',
    'OffhandAttackEvent.Before': 'before attack event',
    'OffhandAttackEvent.After': 'after attack event',
    'OffhandInputArbitrationRegistry': 'input arbitration API',
    'minecraft.screen != null': 'explicit GUI input suppression',
    'minecraft.hitResult.getType() != HitResult.Type.MISS': 'true-MISS-only air swing input',
    'player.swing(InteractionHand.OFF_HAND)': 'vanilla off-hand swing path',
    'registerAboveAll': 'off-hand cooldown GUI layer registration',
    'offhand_attack_indicator': 'stable off-hand cooldown GUI layer ID',
    'ofc$applySwingCooldown': 'swing-driven cross-hand cooldown semantics',
    'setAirSwingMissTicks': 'survival air-swing miss throttle',
    'recordActiveUseStopped': 'recent active-hand capture',
    'shouldDeferRecentlyUsedHand': 'bounded active-use alternation window',
    'tickActiveUseWindow': 'active-use alternation window expiry',
    'UPSTREAM_ALTERNATION_WINDOW_TICKS = 3': 'bounded upstream alternation window',
    'minecraft.gameMode.useItem(player, InteractionHand.OFF_HAND)': 'off-hand use replay through vanilla game mode',
    'canInteractWithEntity(target, 0.0D)': 'vanilla entity reach validation',
    'target.level() != player.level()': 'public API foreign-Level target rejection',
    'player.level().getEntity(targetId) != target': 'public API exact Entity identity validation',
    'preserving the executed result': 'truthful result preservation after After-event failure',
}.items():
    if pattern not in source_text:
        errors.append(f'missing required design ({description}): {pattern}')

for obsolete in (
    'src/main/java/dev/nekomario/offhandcombat/util/ClientCooldownResetWindow.java',
    'src/test/java/dev/nekomario/offhandcombat/util/ClientCooldownResetWindowTest.java',
):
    if (ROOT / obsolete).exists():
        errors.append(f'obsolete cooldown-reset helper returned: {obsolete}')

require(
    'src/main/java/dev/nekomario/offhandcombat/mixin/PlayerMixin.java',
    'state.tickCooldown();', 'state.tickActiveUseWindow();',
    'public void ofc$applySwingCooldown(InteractionHand hand)',
)
require(
    'src/main/java/dev/nekomario/offhandcombat/mixin/LivingEntityMixin.java',
    'method = "swing(Lnet/minecraft/world/InteractionHand;Z)V"',
    'ofc$applySwingCooldown(hand)',
    '@Inject(method = "releaseUsingItem", at = @At("HEAD"))',
    '@Inject(method = "stopUsingItem", at = @At("HEAD"))',
    'recordActiveUseStopped(self.getUsedItemHand())',
)
require(
    'src/main/java/dev/nekomario/offhandcombat/client/ClientInputHandler.java',
    'inputMode == OffhandInputMode.USE_KEY_ALWAYS && player.isCrouching()',
    'state.airSwingMissTicks() > 0',
    'player.swing(InteractionHand.OFF_HAND)',
    'state.setAirSwingMissTicks(UPSTREAM_MISS_COOLDOWN_TICKS)',
)
require(
    'src/main/java/dev/nekomario/offhandcombat/client/ClientActiveUseAlternation.java',
    'UPSTREAM_ALTERNATION_WINDOW_TICKS = 3',
    'event.getHand() != InteractionHand.MAIN_HAND',
    'minecraft.getConnection().hasChannel(OffhandAttackRequestPayload.TYPE)',
    'minecraft.gameMode.useItem(player, InteractionHand.OFF_HAND)',
    'offhandResult.consumesAction()',
    'event.setCanceled(true)',
)
require(
    'src/main/java/dev/nekomario/offhandcombat/attachment/OffhandCombatState.java',
    'ticksSinceLastActiveUse = Integer.MAX_VALUE',
    'public void tickActiveUseWindow()',
    'public void recordActiveUseStopped(InteractionHand hand)',
    'ticksSinceLastActiveUse < Math.max(0, windowTicks)',
)
require(
    'src/clientTest/java/dev/nekomario/offhandcombat/clienttest/OffhandAirSwingE2EHarness.java',
    'empty-air swing did not reset the off-hand cooldown',
    'empty-air swing did not arm the upstream miss throttle',
    'miss throttle consumed an immediate repeat instead of leaving it to vanilla',
    'Off Hand Combat upstream air swing E2E passed',
    'cooldown reset and recharging',
)
require(
    'src/clientTest/java/dev/nekomario/offhandcombat/clienttest/OffhandActiveUseAlternationE2EHarness.java',
    'Items.SHIELD, Items.BOW, Items.CROSSBOW, Items.TRIDENT',
    'first use was not MAIN_HAND',
    'recorded MAIN_HAND release was not deferred to OFF_HAND',
    'alternation emitted an Off Hand Combat attack request',
    'Off Hand Combat active-hand alternation E2E passed: shield, bow, crossbow and trident',
)
require(
    'src/test/java/dev/nekomario/offhandcombat/attachment/OffhandCombatStateActiveUseTest.java',
    'recentlyUsedHandExpiresAfterConfiguredWindow',
    'state.tickActiveUseWindow();',
    'assertFalse(state.shouldDeferRecentlyUsedHand(InteractionHand.MAIN_HAND, 3));',
    'zeroLengthWindowNeverDefers',
)
require(
    'src/gameTest/java/dev/nekomario/offhandcombat/gametest/OffhandCombatGameTests.java',
    'deadAndOccludedTargetsAreRejected',
    'offhandStackChangeResetsReadiness',
    'offhandAttackDoesNotMutateLiveAttributeMap',
)
require(
    'src/gameTest/java/dev/nekomario/offhandcombat/gametest/OffhandCombatPublicApiGameTests.java',
    'publicApiRejectsNullAndForeignLevelEntities', 'Level.NETHER', 'OffhandAttackStatus.INVALID_TARGET',
)

for forbidden_dir in (
    'src/main/java/dev/nekomario/offhandcombat/gametest',
    'src/main/java/dev/nekomario/offhandcombat/clienttest',
    'src/main/java/dev/nekomario/offhandcombat/remotetest',
):
    if (ROOT / forbidden_dir).exists():
        errors.append(f'test Java sources must not be in production: {forbidden_dir}')
if (ROOT / 'src/main/resources/data/offhandcombat/structure/gametest').exists():
    errors.append('GameTest structures must not be in production resources')

if errors:
    for error in errors:
        print(f'ERROR: {error}', file=sys.stderr)
    print(f'VALIDATION FAILED: {len(errors)} issue(s)', file=sys.stderr)
    raise SystemExit(1)

print('VALIDATION PASSED: NeoForge 1.21.1 safety, release-gate, and restored dual-hand semantics checks are present')
