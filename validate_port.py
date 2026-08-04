#!/usr/bin/env python3
from __future__ import annotations

import json
import re
import sys
import tomllib
from pathlib import Path

ROOT = Path(__file__).resolve().parent
errors: list[str] = []

IGNORED_JSON_ROOTS = {'.git', '.gradle', 'build', 'run'}
for path in sorted(ROOT.rglob('*.json')):
    relative = path.relative_to(ROOT)
    if relative.parts and relative.parts[0] in IGNORED_JSON_ROOTS:
        continue
    try:
        json.loads(path.read_text(encoding='utf-8'))
    except Exception as exc:
        errors.append(f'JSON {relative}: {exc}')

props: dict[str, str] = {}
for line in (ROOT / 'gradle.properties').read_text(encoding='utf-8').splitlines():
    line = line.strip()
    if not line or line.startswith('#') or '=' not in line:
        continue
    key, value = line.split('=', 1)
    props[key.strip()] = value.strip()

metadata_template = (ROOT / 'src/main/templates/META-INF/neoforge.mods.toml').read_text(encoding='utf-8')
metadata = metadata_template
for key, value in props.items():
    metadata = metadata.replace('${' + key + '}', value)
try:
    tomllib.loads(metadata)
except Exception as exc:
    errors.append(f'neoforge.mods.toml template: {exc}')

for mod_id in ['bettercombat', 'combatify']:
    dependency_fragment = f'modId="{mod_id}"'
    if dependency_fragment not in metadata_template:
        errors.append(f'neoforge.mods.toml missing compatibility policy for {mod_id}')
if metadata_template.count('type="discouraged"') < 2:
    errors.append('neoforge.mods.toml must warn for both researched combat-authority conflicts')
if metadata_template.count('reason=') < 2:
    errors.append('neoforge.mods.toml compatibility warnings require user-facing reasons')

workflow = (ROOT / '.github/workflows/build.yml').read_text(encoding='utf-8')
for required in [
    'actions/setup-java@v4',
    "java-version: '21'",
    "gradle-version: '9.2.1'",
    'python3 validate_port.py',
    'gradle --no-daemon clean test compileClientTestJava build',
    '10 tests are now running',
    '10 GAME TESTS COMPLETE',
    'All 10 required tests passed :)',
    'bash .ci/client-world-e2e.sh 300',
    'bash .ci/remote-multiplayer-e2e.sh 420',
    'Two remote clients against a separate dedicated server',
    'client-air-swing-e2e.log',
    'dev/nekomario/offhandcombat/client/ClientHudHandler.class',
    "grep -Fq 'dev/nekomario/offhandcombat/gametest/'",
    "grep -Fq 'dev/nekomario/offhandcombat/clienttest/'",
    "grep -Fq 'dev/nekomario/offhandcombat/remotetest/'",
    "grep -Fq 'modId=\"bettercombat\"'",
    "grep -Fq 'modId=\"combatify\"'",
]:
    if required not in workflow:
        errors.append(f'workflow missing required fragment: {required}')

client_e2e_script = (ROOT / '.ci/client-world-e2e.sh').read_text(encoding='utf-8')
for required in [
    'Off Hand Combat client world E2E passed',
    'Off Hand Combat client GUI suppression E2E passed',
    'runClientWorldE2E',
]:
    if required not in client_e2e_script:
        errors.append(f'client E2E script missing required fragment: {required}')

remote_e2e_script = (ROOT / '.ci/remote-multiplayer-e2e.sh').read_text(encoding='utf-8')
for required in [
    'runRemoteServerE2E',
    'runRemoteClientAE2E',
    'runRemoteClientBE2E',
    'Off Hand Combat two-client remote server E2E passed',
    'Off Hand Combat two-client remote client A E2E passed',
    'Off Hand Combat two-client remote client B E2E passed',
    'client A replay remained isolated; armed client B',
    'online-mode=false',
    'max-players=2',
    '127.0.0.1:25565',
    'remote-client-a',
    'remote-client-b',
    'outofmemoryerror',
]:
    if required not in remote_e2e_script:
        errors.append(f'two-client remote E2E script missing required fragment: {required}')

build_script = (ROOT / 'build.gradle').read_text(encoding='utf-8')
for required in [
    "gameDirectory = project.file('run')",
    "gameDirectory = project.file('run/remote-server')",
    "gameDirectory = project.file('run/remote-client-a')",
    "gameDirectory = project.file('run/remote-client-b')",
    'sourceSet = sourceSets.remoteTest',
    "programArguments = ['--username', 'OffhandRemoteA']",
    "programArguments = ['--username', 'OffhandRemoteB']",
    "systemProperty 'offhandcombat.remoteServerE2E', 'true'",
    "systemProperty 'offhandcombat.remoteClientE2E', 'true'",
    "systemProperty 'offhandcombat.remoteClientRole', 'A'",
    "systemProperty 'offhandcombat.remoteClientRole', 'B'",
    'clientAirSwingE2E {',
    "systemProperty 'offhandcombat.airSwingE2E', 'true'",
    "programArguments = ['--username', 'OHCAirSwing']",
    "jvmArgument '-Xmx1024m'",
    "jvmArgument '-Xmx1536m'",
]:
    if required not in build_script:
        errors.append(f'build script missing required isolated run fragment: {required}')
if '--quickPlayMultiplayer' in build_script:
    errors.append('remote clients must not depend on flaky Quick Play auto-connect')
if 'run/remote-client\'' in build_script:
    errors.append('obsolete shared remote-client game directory remains')

client_e2e_java = (
    ROOT / 'src/clientTest/java/dev/nekomario/offhandcombat/clienttest/OffhandClientWorldE2EHarness.java'
).read_text(encoding='utf-8')
for required in [
    'createWorldOpenFlows().openWorld',
    'GUI suppression E2E passed',
    'lastNetworkSequence() != 0L',
]:
    if required not in client_e2e_java:
        errors.append(f'client E2E harness missing required fragment: {required}')

remote_server_java = (
    ROOT / 'src/remoteTest/java/dev/nekomario/offhandcombat/remotetest/OffhandRemoteServerE2EHarness.java'
).read_text(encoding='utf-8')
for required in [
    'server.isDedicatedServer()',
    'PLAYER_A_NAME = "OffhandRemoteA"',
    'PLAYER_B_NAME = "OffhandRemoteB"',
    'stateB.lastNetworkSequence() != 0L',
    'client A duplicate replay advanced client B sequence state',
    'independent network sequence 1',
    'two remote players unexpectedly shared the same combat-state object',
    'Off Hand Combat two-client remote server E2E passed',
]:
    if required not in remote_server_java:
        errors.append(f'two-client remote server E2E harness missing required fragment: {required}')

remote_client_java = (
    ROOT / 'src/remoteTest/java/dev/nekomario/offhandcombat/remotetest/OffhandRemoteClientE2EHarness.java'
).read_text(encoding='utf-8')
for required in [
    'ConnectScreen.startConnecting',
    'ServerAddress.parseString(SERVER_ADDRESS)',
    'offhandcombat.remoteClientRole',
    'WAITING_FOR_PARTNER_OBSERVATION',
    'received another player\'s result or changed cached result',
    'KeyMapping.click',
    'PacketDistributor.sendToServer',
    'Off Hand Combat two-client remote client {} E2E passed',
]:
    if required not in remote_client_java:
        errors.append(f'two-client remote client E2E harness missing required fragment: {required}')

source_roots = [
    ROOT / 'src/main/java',
    ROOT / 'src/gameTest/java',
    ROOT / 'src/clientTest/java',
    ROOT / 'src/remoteTest/java',
    ROOT / 'src/test/java',
]
for source_root in source_roots:
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
        for opening, closing in [('(', ')'), ('{', '}'), ('[', ']')]:
            if scrubbed.count(opening) != scrubbed.count(closing):
                errors.append(f'{actual}: unbalanced {opening}{closing}')

for required in [
    'LICENSE',
    'THIRD_PARTY_NOTICES.md',
    'AUDIT_1.21.1.md',
    'TEST_MATRIX.md',
    'docs/PROTOCOL.md',
    'docs/PUBLIC_API.md',
    'docs/COMPATIBILITY.md',
]:
    if not (ROOT / required).is_file():
        errors.append(f'missing {required}')

license_text = (ROOT / 'LICENSE').read_text(encoding='utf-8')
if 'Copyright (c) 2017 Arekkuusu' not in license_text or 'MIT License' not in license_text:
    errors.append('original MIT attribution is missing')

source_paths = list((ROOT / 'src/main/java').rglob('*.java'))
source_text = '\n'.join(path.read_text(encoding='utf-8') for path in source_paths)
for pattern, description in {
    'ServerboundInteractPacket': 'vanilla packet mutation',
    'invulnerableTime = 0': 'invulnerability-frame reset',
    'lastHurt = 0': 'damage-state reset',
    'getInventory().items.set': 'live main-hand inventory swap',
    'getInventory().offhand.set': 'live off-hand inventory swap',
    'Map<UUID': 'static UUID combat state',
    'static final Map<UUID': 'static UUID combat state',
    'getEyePosition().distanceToSqr(target.getBoundingBox().getCenter())': 'custom entity-reach approximation',
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
    'KeyConflictContext.IN_GAME': 'in-game-only dedicated key context',
    'minecraft.screen != null': 'explicit GUI input suppression',
    'minecraft.hitResult.getType() != HitResult.Type.MISS': 'true-MISS-only air swing input',
    'player.swing(InteractionHand.OFF_HAND)': 'vanilla off-hand air swing animation',
    'registerAboveAll': 'off-hand cooldown GUI layer registration',
    'offhand_attack_indicator': 'stable off-hand cooldown GUI layer ID',
    'markClientCooldownReset': 'authoritative client cooldown reset deduplication',
    'result.status() == OffhandAttackStatus.SUCCESS': 'SUCCESS-only client cooldown reset',
    'canInteractWithEntity(target, 0.0D)': 'vanilla entity reach validation',
    'target.level() != player.level()': 'public API foreign-Level target rejection',
    'player.level().getEntity(targetId) != target': 'public API exact Entity identity validation',
    'preserving the executed result': 'truthful result preservation after After-event failure',
}.items():
    if pattern not in source_text:
        errors.append(f'missing required design ({description}): {pattern}')

for forbidden in [
    ROOT / 'src/main/java/dev/nekomario/offhandcombat/gametest',
    ROOT / 'src/main/java/dev/nekomario/offhandcombat/clienttest',
    ROOT / 'src/main/java/dev/nekomario/offhandcombat/remotetest',
]:
    if forbidden.exists():
        errors.append(f'test Java sources must not be in production: {forbidden.relative_to(ROOT)}')
if (ROOT / 'src/main/resources/data/offhandcombat/structure/gametest').exists():
    errors.append('GameTest structures must not be in production resources')

game_test_java = (
    ROOT / 'src/gameTest/java/dev/nekomario/offhandcombat/gametest/OffhandCombatGameTests.java'
).read_text(encoding='utf-8')
for required in [
    'deadAndOccludedTargetsAreRejected',
    'offhandStackChangeResetsReadiness',
    'offhandAttackDoesNotMutateLiveAttributeMap',
]:
    if required not in game_test_java:
        errors.append(f'GameTest suite missing required regression: {required}')

public_api_game_test = (
    ROOT / 'src/gameTest/java/dev/nekomario/offhandcombat/gametest/OffhandCombatPublicApiGameTests.java'
).read_text(encoding='utf-8')
for required in [
    'publicApiRejectsNullAndForeignLevelEntities',
    'Level.NETHER',
    'OffhandAttackStatus.INVALID_TARGET',
]:
    if required not in public_api_game_test:
        errors.append(f'public API GameTest missing required regression: {required}')

# Final interaction and vanilla-peer release checks
interaction_script = (ROOT / '.ci/client-interaction-e2e.sh').read_text(encoding='utf-8')
for required in [
    'runClientAirSwingE2E',
    'Off Hand Combat off-hand air swing E2E passed',
    'runClientInteractionE2E',
    'runClientVillagerE2E',
    'Off Hand Combat interaction priority E2E passed: button, door and chest',
    'Off Hand Combat villager trading priority E2E passed',
]:
    if required not in interaction_script:
        errors.append(f'interaction priority E2E script missing required fragment: {required}')

air_swing_e2e_java = (
    ROOT / 'src/clientTest/java/dev/nekomario/offhandcombat/clienttest/OffhandAirSwingE2EHarness.java'
).read_text(encoding='utf-8')
for required in [
    'BlockHitResult.miss(',
    'InteractionHand.OFF_HAND',
    'NeoForge.EVENT_BUS.post(input)',
    'minecraft.player.swingingArm != InteractionHand.OFF_HAND',
    'lastNetworkSequence() != baselineServerSequence',
    'getOffhandItem().getDamageValue() != baselineServerDurability',
    'empty-air swing lowered client off-hand readiness',
    'Off Hand Combat off-hand air swing E2E passed',
]:
    if required not in air_swing_e2e_java:
        errors.append(f'air-swing E2E harness missing required fragment: {required}')

villager_e2e_java = (
    ROOT / 'src/clientTest/java/dev/nekomario/offhandcombat/clienttest/OffhandVillagerPriorityE2EHarness.java'
).read_text(encoding='utf-8')
for required in [
    'minecraft.gameMode.interact(',
    'villager,',
    'InteractionHand.MAIN_HAND);',
    'minecraft.screen instanceof MerchantScreen',
    'lastNetworkSequence() != baselineSequence',
    'getOffhandItem().getDamageValue() != 0',
    'trade screen opened, sequence unchanged, durability unchanged',
]:
    if required not in villager_e2e_java:
        errors.append(f'villager priority E2E harness missing required fragment: {required}')

for required in [
    'minecraft.hitResult = new EntityHitResult(target);',
    'ClientInputHandler.class.getDeclaredMethod',
    'sendMethod.invoke(null, OffhandInputSource.USE_KEY)',
    'OffhandAttackStatus.RATE_LIMITED',
    'same-tick use-key E2E passed',
]:
    if required not in client_e2e_java:
        errors.append(f'deterministic rapid-click E2E missing required fragment: {required}')
if 'entityHitResult.getEntity().getId() != rapidTargetId' in client_e2e_java:
    errors.append('rapid-click E2E still depends on natural crosshair synchronization')

if 'bash .ci/client-interaction-e2e.sh "$TIMEOUT_SECONDS"' not in client_e2e_script:
    errors.append('client E2E release gate does not invoke air-swing/interaction/villager priority E2E')
if (ROOT / '.ci/.interaction-retrigger').exists():
    errors.append('temporary focused interaction marker remains')
if (ROOT / '.github/workflows/self-hosted-release-gate.yml').exists():
    errors.append('temporary self-hosted placeholder workflow remains')
if (ROOT / '.github/workflows/client-feedback-fix.yml').exists():
    errors.append('temporary client-feedback verification workflow remains')

vanilla_client_script = (ROOT / '.ci/vanilla-client-server-e2e.sh').read_text(encoding='utf-8')
for required in [
    'runVanillaClientServerE2E',
    'piston-meta.mojang.com',
    '--quickPlayMultiplayer',
    'PLAYER_NAME="OHCPlainVanilla"',
    '${PLAYER_NAME} joined the game',
    'vanilla-client-to-modded-server E2E passed',
]:
    if required not in vanilla_client_script:
        errors.append(f'vanilla-client-to-modded-server E2E missing required fragment: {required}')
if 'bash .ci/vanilla-client-server-e2e.sh "$TIMEOUT_SECONDS"' not in (
    ROOT / '.ci/vanilla-server-client-e2e.sh'
).read_text(encoding='utf-8'):
    errors.append('bidirectional vanilla compatibility gate is not chained')
if "vanillaClientServerE2E {" not in build_script:
    errors.append('build script missing isolated vanilla-client peer server run')

if errors:
    print('VALIDATION FAILED')
    for error in errors:
        print(f'- {error}')
    sys.exit(1)

game_test_paths = list((ROOT / 'src/gameTest/java').rglob('*.java'))
client_test_paths = list((ROOT / 'src/clientTest/java').rglob('*.java'))
remote_test_paths = list((ROOT / 'src/remoteTest/java').rglob('*.java'))
print(
    f'VALIDATION PASSED: {len(source_paths)} main Java files, '
    f'{len(game_test_paths)} isolated GameTest Java files, '
    f'{len(client_test_paths)} isolated client E2E Java files and '
    f'{len(remote_test_paths)} isolated two-client remote E2E Java files; '
    'metadata, resources, workflow, legal, compatibility and architecture checks OK'
)
