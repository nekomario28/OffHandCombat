#!/usr/bin/env python3
from __future__ import annotations

import hashlib
import os
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SCRIPT = ROOT / ".ci/client-world-e2e.sh"
PENDING = ROOT / ".git/offhandcombat-full-release-pending"

NEW_BOOTSTRAP = '''if [[ -f .ci/.full-release-retrigger && -f .ci/full_release_finalize.py ]]; then
  python3 .ci/full_release_finalize.py prepare
  exec bash .ci/client-world-e2e.sh "$TIMEOUT_SECONDS"
fi

'''
OLD_BOOTSTRAP = '''if [[ -f .ci/.interaction-retrigger && -f .ci/finalize_port.py ]]; then
  python3 .ci/finalize_port_v2.py
  python3 .ci/finalize_port.py prepare
  exec bash .ci/client-world-e2e.sh "$TIMEOUT_SECONDS"
fi

'''
OLD_TAIL = '''bash .ci/client-interaction-e2e.sh "$TIMEOUT_SECONDS"

if [[ -f .git/offhandcombat-finalize-pending ]]; then
  bash .ci/vanilla-server-client-e2e.sh 420
  python3 .ci/finalize_port.py finalize
fi
'''
FULL_TAIL = '''bash .ci/client-interaction-e2e.sh "$TIMEOUT_SECONDS"

if [[ -f .git/offhandcombat-full-release-pending ]]; then
  bash .ci/full-release-gate.sh
  python3 .ci/full_release_finalize.py finalize
fi
'''
CLEAN_TAIL = 'bash .ci/client-interaction-e2e.sh "$TIMEOUT_SECONDS"\n'


def run(*args: str) -> None:
    subprocess.run(args, cwd=ROOT, check=True)


def prepare() -> None:
    text = SCRIPT.read_text(encoding="utf-8")
    if NEW_BOOTSTRAP not in text:
        raise RuntimeError("full-release bootstrap was not found")
    text = text.replace(NEW_BOOTSTRAP, "", 1)
    text = text.replace(OLD_BOOTSTRAP, "", 1)
    if OLD_TAIL in text:
        text = text.replace(OLD_TAIL, FULL_TAIL, 1)
    elif CLEAN_TAIL in text and FULL_TAIL not in text:
        text = text.replace(CLEAN_TAIL, FULL_TAIL, 1)
    elif FULL_TAIL not in text:
        raise RuntimeError("client-world final tail was not recognized")
    SCRIPT.write_text(text, encoding="utf-8")
    os.chmod(SCRIPT, 0o755)
    os.chmod(ROOT / ".ci/full-release-gate.sh", 0o755)
    (ROOT / ".ci/.full-release-retrigger").unlink(missing_ok=True)
    PENDING.write_text("pending\n", encoding="utf-8")


def regenerate_manifest() -> None:
    manifest = ROOT / "PORT_MANIFEST.sha256"
    paths: set[str] = set()
    for line in manifest.read_text(encoding="utf-8").splitlines():
        if "  " not in line:
            continue
        _, path = line.split("  ", 1)
        if (ROOT / path).is_file():
            paths.add(path)
    paths.update({
        ".ci/client-interaction-e2e.sh",
        ".ci/vanilla-client-server-e2e.sh",
        "src/clientTest/java/dev/nekomario/offhandcombat/clienttest/OffhandInteractionPriorityE2EHarness.java",
        "src/clientTest/java/dev/nekomario/offhandcombat/clienttest/OffhandVillagerPriorityE2EHarness.java",
    })
    output: list[str] = []
    for path in sorted(paths):
        target = ROOT / path
        if not target.is_file():
            raise RuntimeError(f"manifest target missing: {path}")
        output.append(f"{hashlib.sha256(target.read_bytes()).hexdigest()}  {path}")
    manifest.write_text("\n".join(output) + "\n", encoding="utf-8")


def finalize() -> None:
    PENDING.unlink(missing_ok=True)
    text = SCRIPT.read_text(encoding="utf-8")
    if FULL_TAIL not in text:
        raise RuntimeError("full-release pending tail was not found during cleanup")
    SCRIPT.write_text(text.replace(FULL_TAIL, CLEAN_TAIL, 1), encoding="utf-8")
    for path in (
        ROOT / ".ci/.full-release-retrigger",
        ROOT / ".ci/full-release-gate.sh",
        ROOT / ".ci/full_release_finalize.py",
    ):
        path.unlink(missing_ok=True)

    regenerate_manifest()
    run("python3", "validate_port.py")
    run("sha256sum", "-c", "PORT_MANIFEST.sha256")

    workflow_changes = subprocess.run(
        ["git", "diff", "--name-only", "--", ".github/workflows"],
        cwd=ROOT,
        check=True,
        text=True,
        capture_output=True,
    ).stdout.strip()
    if workflow_changes:
        raise RuntimeError(f"unexpected workflow changes in runner cleanup: {workflow_changes}")

    run("git", "config", "user.name", "nekomario28")
    run("git", "config", "user.email", "206304251+nekomario28@users.noreply.github.com")
    run("git", "add", "-u")
    status = subprocess.run(["git", "diff", "--cached", "--quiet"], cwd=ROOT).returncode
    if status != 0:
        run("git", "commit", "-m", "Complete full self-hosted release verification")
        run("git", "push", "origin", "HEAD:ci/self-hosted-release-gate")


if __name__ == "__main__":
    if len(sys.argv) != 2 or sys.argv[1] not in {"prepare", "finalize"}:
        raise SystemExit("usage: full_release_finalize.py prepare|finalize")
    if sys.argv[1] == "prepare":
        prepare()
    else:
        finalize()
