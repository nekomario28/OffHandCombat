#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
FINALIZER = ROOT / ".ci/finalize_port.py"

text = FINALIZER.read_text(encoding="utf-8")

workflow_start = text.find('    master_workflow = subprocess.run(\n')
workflow_end_marker = '    (ROOT / ".ci/.interaction-retrigger").unlink(missing_ok=True)\n'
workflow_end = text.find(workflow_end_marker, workflow_start)
if workflow_start < 0 or workflow_end < 0:
    if 'workflow was not restored before finalization' not in text:
        raise SystemExit('workflow finalization block was not recognized')
else:
    workflow_end += len(workflow_end_marker)
    replacement = '''    workflow = read(".github/workflows/build.yml")
    for required in (
        "runs-on: ubuntu-latest",
        "client-interaction-e2e.log",
        "client-villager-e2e.log",
        "vanilla-client-modded-server.log",
        "vanilla-client-peer.log",
    ):
        if required not in workflow:
            raise RuntimeError(
                f"workflow was not restored before finalization: missing {required}"
            )
    placeholder = ROOT / ".github/workflows/self-hosted-release-gate.yml"
    if placeholder.exists():
        raise RuntimeError("temporary self-hosted workflow still exists")
    (ROOT / ".ci/.interaction-retrigger").unlink(missing_ok=True)
'''
    text = text[:workflow_start] + replacement + text[workflow_end:]

text = text.replace(
    '    run("git", "add", "-A")\n',
    '    run("git", "add", "-u")\n',
    1,
)
if 'run("git", "add", "-u")' not in text:
    raise SystemExit('tracked-only staging patch was not applied')

old_cleanup = '    (ROOT / ".ci/finalize_port_v4.py").unlink(missing_ok=True)\n'
new_cleanup = (
    '    (ROOT / ".ci/finalize_port_v4.py").unlink(missing_ok=True)\n'
    '    (ROOT / ".ci/finalize_port_v5.py").unlink(missing_ok=True)\n'
)
if new_cleanup not in text:
    if old_cleanup not in text:
        raise SystemExit('finalizer v5 cleanup anchor missing')
    text = text.replace(old_cleanup, new_cleanup, 1)

FINALIZER.write_text(text, encoding="utf-8")
