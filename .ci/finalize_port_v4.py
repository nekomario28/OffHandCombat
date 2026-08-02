#!/usr/bin/env python3
from __future__ import annotations

import runpy
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
VALIDATOR = ROOT / "validate_port.py"
FINALIZER = ROOT / ".ci/finalize_port.py"

validator = VALIDATOR.read_text(encoding="utf-8")
old_json_scan = """for path in sorted(ROOT.rglob('*.json')):
    try:
        json.loads(path.read_text(encoding='utf-8'))
    except Exception as exc:
        errors.append(f'JSON {path.relative_to(ROOT)}: {exc}')
"""
new_json_scan = """IGNORED_JSON_ROOTS = {'.git', '.gradle', 'build', 'run'}
for path in sorted(ROOT.rglob('*.json')):
    relative = path.relative_to(ROOT)
    if relative.parts and relative.parts[0] in IGNORED_JSON_ROOTS:
        continue
    try:
        json.loads(path.read_text(encoding='utf-8'))
    except Exception as exc:
        errors.append(f'JSON {relative}: {exc}')
"""
if new_json_scan not in validator:
    if old_json_scan not in validator:
        raise SystemExit("validator JSON scan anchor missing")
    validator = validator.replace(old_json_scan, new_json_scan, 1)
VALIDATOR.write_text(validator, encoding="utf-8")

finalizer = FINALIZER.read_text(encoding="utf-8")
finalizer = finalizer.replace(
    "    'minecraft.gameMode.interact(minecraft.player, InteractionHand.MAIN_HAND)',\n",
    "    'minecraft.gameMode.interact(',\n"
    "    'villager,',\n"
    "    'InteractionHand.MAIN_HAND);',\n",
    1,
)
finalizer = finalizer.replace(
    "    'OHCPlainVanilla joined the game',\n",
    "    'PLAYER_NAME=\"OHCPlainVanilla\"',\n"
    "    '${PLAYER_NAME} joined the game',\n",
    1,
)
if "'minecraft.gameMode.interact('," not in finalizer:
    raise SystemExit("villager validator patch was not applied")
if "'PLAYER_NAME=\"OHCPlainVanilla\"'," not in finalizer:
    raise SystemExit("vanilla-client validator patch was not applied")

old_cleanup = '''    (ROOT / ".ci/finalize_port_v3.py").unlink(missing_ok=True)
'''
new_cleanup = '''    (ROOT / ".ci/finalize_port_v3.py").unlink(missing_ok=True)
    (ROOT / ".ci/finalize_port_v4.py").unlink(missing_ok=True)
'''
if new_cleanup not in finalizer:
    if old_cleanup not in finalizer:
        raise SystemExit("finalizer v4 cleanup anchor missing")
    finalizer = finalizer.replace(old_cleanup, new_cleanup, 1)
FINALIZER.write_text(finalizer, encoding="utf-8")

runpy.run_path(str(ROOT / ".ci/finalize_port_v5.py"), run_name="__main__")
