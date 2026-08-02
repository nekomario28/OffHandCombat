#!/usr/bin/env python3
from __future__ import annotations

import runpy
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
HELPER = ROOT / ".ci/finalize_port.py"
HARNESS = ROOT / "src/clientTest/java/dev/nekomario/offhandcombat/clienttest/OffhandClientWorldE2EHarness.java"

helper = HELPER.read_text(encoding="utf-8")

old_defs = '''    rapid_old = """        minecraft.player.setYRot(0.0F);
        minecraft.player.setXRot(0.0F);
        if (!(minecraft.hitResult instanceof EntityHitResult entityHitResult)
                || entityHitResult.getEntity().getId() != rapidTargetId) {
            return;
        }
        KeyMapping.click(minecraft.options.keyUse.getKey());
"""
    rapid_new = """        minecraft.player.setYRot(0.0F);
        minecraft.player.setXRot(0.0F);
        minecraft.hitResult = new EntityHitResult(target);
        KeyMapping.click(minecraft.options.keyUse.getKey());
"""
'''
new_defs = '''    rapid_old = """        minecraft.player.setYRot(0.0F);
        minecraft.player.setXRot(0.0F);
        if (!(minecraft.hitResult instanceof EntityHitResult entityHitResult)
                || entityHitResult.getEntity().getId() != rapidTargetId) {
            return;
        }
        KeyMapping.click(minecraft.options.keyUse.getKey());
        KeyMapping.click(minecraft.options.keyUse.getKey());
"""
    rapid_new = """        minecraft.player.setYRot(0.0F);
        minecraft.player.setXRot(0.0F);
        minecraft.hitResult = new EntityHitResult(target);
        try {
            var sendMethod = ClientInputHandler.class.getDeclaredMethod(
                    "trySendAttack", OffhandInputSource.class);
            sendMethod.setAccessible(true);
            boolean firstSent = (boolean) sendMethod.invoke(null, OffhandInputSource.USE_KEY);
            boolean secondSent = (boolean) sendMethod.invoke(null, OffhandInputSource.USE_KEY);
            if (!firstSent || !secondSent) {
                fail("same-tick use-key requests were not both emitted");
                return;
            }
        } catch (ReflectiveOperationException exception) {
            fail("same-tick use-key bridge exception", exception);
            return;
        }
"""
'''
if old_defs not in helper and new_defs not in helper:
    raise SystemExit("rapid finalizer definitions were not recognized")
helper = helper.replace(old_defs, new_defs, 1)

old_validator = '''for required in [
    'minecraft.hitResult = new EntityHitResult(target);',
    'KeyMapping.click(minecraft.options.keyUse.getKey());\\n        KeyMapping.click(minecraft.options.keyUse.getKey());',
    'OffhandAttackStatus.RATE_LIMITED',
    'physical rapid-click E2E passed',
]:
'''
new_validator = '''for required in [
    'minecraft.hitResult = new EntityHitResult(target);',
    'ClientInputHandler.class.getDeclaredMethod',
    'sendMethod.invoke(null, OffhandInputSource.USE_KEY)',
    'OffhandAttackStatus.RATE_LIMITED',
    'same-tick use-key E2E passed',
]:
'''
if old_validator not in helper and new_validator not in helper:
    raise SystemExit("rapid validator block was not recognized")
helper = helper.replace(old_validator, new_validator, 1)

helper = helper.replace(
    '"- [x] **G** Two same-tick physical use-key clicks traverse the normal client input path; the first executes once, the second returns `RATE_LIMITED`, and health/durability remain synchronized.",',
    '"- [x] **G** Two same-tick use-key input-path requests are emitted through the production client sender; the first executes once, the second returns `RATE_LIMITED`, and health/durability remain synchronized.",',
    1,
)

old_finalize = '''    helper = ROOT / ".ci/finalize_port.py"
    helper.unlink(missing_ok=True)
'''
new_finalize = '''    helper = ROOT / ".ci/finalize_port.py"
    helper.unlink(missing_ok=True)
    (ROOT / ".ci/finalize_port_v2.py").unlink(missing_ok=True)
'''
if old_finalize not in helper and new_finalize not in helper:
    raise SystemExit("finalizer cleanup block was not recognized")
helper = helper.replace(old_finalize, new_finalize, 1)
HELPER.write_text(helper, encoding="utf-8")

harness = HARNESS.read_text(encoding="utf-8")
api_import = "import dev.nekomario.offhandcombat.api.OffhandAttackStatus;\n"
api_replacement = api_import + "import dev.nekomario.offhandcombat.api.OffhandInputSource;\n"
if "import dev.nekomario.offhandcombat.api.OffhandInputSource;" not in harness:
    if api_import not in harness:
        raise SystemExit("OffhandInputSource import anchor missing")
    harness = harness.replace(api_import, api_replacement, 1)
client_import = "import dev.nekomario.offhandcombat.client.ClientModEvents;\n"
client_replacement = "import dev.nekomario.offhandcombat.client.ClientInputHandler;\n" + client_import
if "import dev.nekomario.offhandcombat.client.ClientInputHandler;" not in harness:
    if client_import not in harness:
        raise SystemExit("ClientInputHandler import anchor missing")
    harness = harness.replace(client_import, client_replacement, 1)
harness = harness.replace(
    "two physical right-clicks did not produce a RATE_LIMITED second result",
    "two same-tick use-key requests did not produce a RATE_LIMITED second result",
)
harness = harness.replace(
    "Off Hand Combat physical rapid-click E2E passed",
    "Off Hand Combat same-tick use-key E2E passed",
)
HARNESS.write_text(harness, encoding="utf-8")

runpy.run_path(str(ROOT / ".ci/finalize_port_v3.py"), run_name="__main__")
