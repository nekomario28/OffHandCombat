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
metadata = (ROOT / 'src/main/templates/META-INF/neoforge.mods.toml').read_text(encoding='utf-8')
for key, value in props.items():
    metadata = metadata.replace('${' + key + '}', value)
try:
    tomllib.loads(metadata)
except Exception as exc:
    errors.append(f'neoforge.mods.toml template: {exc}')

workflow = (ROOT / '.github/workflows/build.yml').read_text(encoding='utf-8')
for required in ['actions/setup-java@v4', "java-version: '21'", "gradle-version: '9.2.1'", 'python3 validate_port.py', 'gradle --no-daemon clean test build']:
    if required not in workflow:
        errors.append(f'workflow missing required fragment: {required}')

for source_root in [ROOT / 'src/main/java', ROOT / 'src/test/java']:
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
    'LICENSE', 'THIRD_PARTY_NOTICES.md', 'AUDIT_1.21.1.md', 'TEST_MATRIX.md',
    'docs/PROTOCOL.md', 'docs/PUBLIC_API.md'
]:
    if not (ROOT / required).is_file():
        errors.append(f'missing {required}')

license_text = (ROOT / 'LICENSE').read_text(encoding='utf-8')
if 'Copyright (c) 2017 Arekkuusu' not in license_text or 'MIT License' not in license_text:
    errors.append('original MIT attribution is missing')

source_paths = list((ROOT / 'src/main/java').rglob('*.java'))
source_text = '\n'.join(path.read_text(encoding='utf-8') for path in source_paths)
forbidden = {
    'ServerboundInteractPacket': 'vanilla packet mutation',
    'invulnerableTime = 0': 'invulnerability-frame reset',
    'lastHurt = 0': 'damage-state reset',
    'getInventory().items.set': 'live main-hand inventory swap',
    'getInventory().offhand.set': 'live off-hand inventory swap',
    'Map<UUID': 'static UUID combat state',
    'static final Map<UUID': 'static UUID combat state',
}
for pattern, description in forbidden.items():
    if pattern in source_text:
        errors.append(f'forbidden pattern remains ({description}): {pattern}')

required_patterns = {
    'AttachmentType.builder': 'Data Attachment state',
    '.optional()': 'optional protocol negotiation',
    'classifyNetworkSequence': 'request replay classification',
    'OffhandAttackEvent.Before': 'before attack event',
    'OffhandAttackEvent.After': 'after attack event',
    'OffhandInputArbitrationRegistry': 'input arbitration API',
}
for pattern, description in required_patterns.items():
    if pattern not in source_text:
        errors.append(f'missing required design ({description}): {pattern}')

if errors:
    print('VALIDATION FAILED')
    for error in errors:
        print(f'- {error}')
    sys.exit(1)

print(f'VALIDATION PASSED: {len(source_paths)} main Java files; metadata, resources, workflow, legal and architecture checks OK')
