#!/usr/bin/env python3
from __future__ import annotations

import json
import re
import sys
import tomllib
from pathlib import Path

ROOT = Path(__file__).resolve().parent
errors: list[str] = []

for path in sorted(ROOT.rglob('*.json')):
    try:
        json.loads(path.read_text(encoding='utf-8'))
    except Exception as exc:
        errors.append(f'JSON {path.relative_to(ROOT)}: {exc}')

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
    'gradle --no-daemon clean test build',
    '10 tests are now running',
    '10 GAME TESTS COMPLETE',
    'All 10 required tests passed :)',
    'bash .ci/client-world-e2e.sh 240',
    "grep -Fq 'dev/nekomario/offhandcombat/gametest/'",
    "grep -Fq 'dev/nekomario/offhandcombat/clienttest/'",
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

build_script = (ROOT / 'build.gradle').read_text(encoding='utf-8')
if "gameDirectory = project.file('run')" not in build_script:
    errors.append('client E2E run is not pinned to the shared run directory')

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

source_roots = [
    ROOT / 'src/main/java',
    ROOT / 'src/gameTest/java',
    ROOT / 'src/clientTest/java',
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
    'canInteractWithEntity(target, 0.0D)': 'vanilla entity reach validation',
    'target.level() != player.level()': 'public API foreign-Level target rejection',
    'player.level().getEntity(targetId) != target': 'public API exact Entity identity validation',
    'preserving the executed result': 'truthful result preservation after After-event failure',
}.items():
    if pattern not in source_text:
        errors.append(f'missing required design ({description}): {pattern}')

if (ROOT / 'src/main/java/dev/nekomario/offhandcombat/gametest').exists():
    errors.append('GameTest Java sources must not be in the production source set')
if (ROOT / 'src/main/resources/data/offhandcombat/structure/gametest').exists():
    errors.append('GameTest structures must not be in production resources')
if (ROOT / 'src/main/java/dev/nekomario/offhandcombat/clienttest').exists():
    errors.append('client E2E Java sources must not be in the production source set')

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

if errors:
    print('VALIDATION FAILED')
    for error in errors:
        print(f'- {error}')
    sys.exit(1)

game_test_paths = list((ROOT / 'src/gameTest/java').rglob('*.java'))
client_test_paths = list((ROOT / 'src/clientTest/java').rglob('*.java'))
print(
    f'VALIDATION PASSED: {len(source_paths)} main Java files, '
    f'{len(game_test_paths)} isolated GameTest Java files and '
    f'{len(client_test_paths)} isolated client E2E Java files; '
    'metadata, resources, workflow, legal, compatibility and architecture checks OK'
)
