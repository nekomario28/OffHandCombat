package dev.nekomario.offhandcombat.remotetest;

import dev.nekomario.offhandcombat.OffHandCombat;
import dev.nekomario.offhandcombat.api.OffhandAttackResult;
import dev.nekomario.offhandcombat.api.OffhandAttackStatus;
import dev.nekomario.offhandcombat.attachment.OffhandCombatAttachments;
import dev.nekomario.offhandcombat.network.OffhandAttackRequestPayload;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = OffHandCombat.MOD_ID, value = Dist.CLIENT)
public final class OffhandNetworkStressClientE2EHarness {
    private static final String ENABLE_PROPERTY = "offhandcombat.networkStressClientE2E";
    private static final String SERVER_ADDRESS = "127.0.0.1:25567";
    private static final int CONNECT_AFTER_TICKS = 20;
    private static final int DELAYED_REORDER_TICKS = 20;
    private static final int TIMEOUT_TICKS = 9000;
    private static final int DUPLICATE_FLOOD_COUNT = 64;
    private static final long BURST_FIRST_SEQUENCE = 5L;
    private static final long BURST_LAST_SEQUENCE = 68L;

    private static Phase phase = Phase.WAITING_TO_CONNECT;
    private static int elapsedTicks;
    private static int phaseStartedAtTick;
    private static int targetId = -1;
    private static OffhandAttackResult initialResult;
    private static OffhandAttackResult reorderResult;
    private static OffhandAttackResult finalResult;

    private OffhandNetworkStressClientE2EHarness() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean(ENABLE_PROPERTY) || phase == Phase.PASSED || phase == Phase.FAILED) {
            return;
        }

        try {
            elapsedTicks++;
            if (elapsedTicks > TIMEOUT_TICKS) {
                fail("timed out in phase " + phase);
                return;
            }

            Minecraft minecraft = Minecraft.getInstance();
            switch (phase) {
                case WAITING_TO_CONNECT -> connectWhenReady(minecraft);
                case WAITING_FOR_CONNECTION -> waitForConnection(minecraft);
                case WAITING_FOR_INITIAL_TARGET -> findInitialTargetAndSendInvalidZero(minecraft);
                case WAITING_FOR_INVALID_ZERO -> waitForInvalidZero(minecraft);
                case WAITING_FOR_INVALID_NEGATIVE -> waitForInvalidNegative(minecraft);
                case WAITING_FOR_INITIAL_SUCCESS -> waitForInitialSuccess(minecraft);
                case WAITING_FOR_DUPLICATE_REPLAY -> waitForDuplicateReplay(minecraft);
                case WAITING_FOR_REORDER_TARGET -> findReorderTargetAndSendSequenceThree(minecraft);
                case WAITING_FOR_REORDER_SUCCESS -> waitForReorderSuccess(minecraft);
                case WAITING_TO_SEND_DELAYED_TWO -> waitToSendDelayedTwo(minecraft);
                case WAITING_FOR_STALE_TWO -> waitForStaleTwo(minecraft);
                case WAITING_FOR_DUPLICATE_THREE -> waitForDuplicateThree(minecraft);
                case WAITING_FOR_INVALID_TARGET_FOUR -> waitForInvalidTargetFour(minecraft);
                case WAITING_FOR_BURST_RESULT -> waitForBurstResult(minecraft);
                case WAITING_FOR_FINAL_TARGET -> findFinalTargetAndSendSequenceSixtyNine(minecraft);
                case WAITING_FOR_FINAL_SUCCESS -> waitForFinalSuccess(minecraft);
                case WAITING_FOR_FINAL_STALE_FOUR -> waitForFinalStaleFour(minecraft);
                case WAITING_FOR_FINAL_DUPLICATE -> waitForFinalDuplicate(minecraft);
                case WAITING_FOR_FINAL_RATE_LIMIT -> waitForFinalRateLimit(minecraft);
                default -> {
                }
            }
        } catch (Throwable throwable) {
            fail("network stress client harness exception", throwable);
        }
    }

    private static void connectWhenReady(Minecraft minecraft) {
        if (elapsedTicks < CONNECT_AFTER_TICKS || minecraft.screen == null) {
            return;
        }
        if (minecraft.level != null || minecraft.player != null || minecraft.getConnection() != null) {
            fail("network stress client had an unexpected active world before connection");
            return;
        }

        ServerData serverData = new ServerData(
                "Off Hand Combat network stress E2E", SERVER_ADDRESS, ServerData.Type.OTHER);
        ConnectScreen.startConnecting(
                minecraft.screen,
                minecraft,
                ServerAddress.parseString(SERVER_ADDRESS),
                serverData,
                false,
                null);
        beginPhase(Phase.WAITING_FOR_CONNECTION);
        OffHandCombat.LOGGER.info(
                "Off Hand Combat network stress client connecting through vanilla ConnectScreen: {}",
                SERVER_ADDRESS);
    }

    private static void waitForConnection(Minecraft minecraft) {
        if (!isRemoteConnectionReady(minecraft)) {
            return;
        }

        var state = minecraft.player.getData(OffhandCombatAttachments.COMBAT_STATE);
        if (state.lastClientResult() != null) {
            fail("network stress client inherited a cached result");
            return;
        }

        beginPhase(Phase.WAITING_FOR_INITIAL_TARGET);
        OffHandCombat.LOGGER.info("Off Hand Combat network stress client connection ready under netem delay");
    }

    private static void findInitialTargetAndSendInvalidZero(Minecraft minecraft) {
        Mob target = findNamedTarget(minecraft, OffhandNetworkStressServerE2EHarness.INITIAL_TARGET_NAME);
        if (target == null) {
            return;
        }

        targetId = target.getId();
        minecraft.hitResult = new EntityHitResult(target);
        clearClientResult(minecraft);
        send(0L, targetId);
        beginPhase(Phase.WAITING_FOR_INVALID_ZERO);
    }

    private static void waitForInvalidZero(Minecraft minecraft) {
        OffhandAttackResult result = currentResult(minecraft);
        if (!matches(result, 0L, targetId, OffhandAttackStatus.INVALID_SEQUENCE)) {
            return;
        }

        clearClientResult(minecraft);
        send(-1L, targetId);
        beginPhase(Phase.WAITING_FOR_INVALID_NEGATIVE);
    }

    private static void waitForInvalidNegative(Minecraft minecraft) {
        OffhandAttackResult result = currentResult(minecraft);
        if (!matches(result, -1L, targetId, OffhandAttackStatus.INVALID_SEQUENCE)) {
            return;
        }

        clearClientResult(minecraft);
        send(1L, targetId);
        beginPhase(Phase.WAITING_FOR_INITIAL_SUCCESS);
    }

    private static void waitForInitialSuccess(Minecraft minecraft) {
        OffhandAttackResult result = currentResult(minecraft);
        if (result == null) {
            return;
        }
        if (!verifySuccess(result, 1L, 0, 1, "initial sequence 1")) {
            return;
        }

        initialResult = result;
        clearClientResult(minecraft);
        for (int index = 0; index < DUPLICATE_FLOOD_COUNT; index++) {
            send(1L, targetId);
        }
        beginPhase(Phase.WAITING_FOR_DUPLICATE_REPLAY);
        OffHandCombat.LOGGER.info(
                "Off Hand Combat network stress client sent {} duplicate sequence-1 requests",
                DUPLICATE_FLOOD_COUNT);
    }

    private static void waitForDuplicateReplay(Minecraft minecraft) {
        OffhandAttackResult result = currentResult(minecraft);
        if (result == null || !initialResult.equals(result)) {
            return;
        }
        if (minecraft.player.getOffhandItem().getDamageValue() != 1) {
            fail("duplicate flood changed client-visible durability");
            return;
        }

        beginPhase(Phase.WAITING_FOR_REORDER_TARGET);
        OffHandCombat.LOGGER.info("Off Hand Combat network stress duplicate flood replayed cached success");
    }

    private static void findReorderTargetAndSendSequenceThree(Minecraft minecraft) {
        Mob target = findNamedTarget(minecraft, OffhandNetworkStressServerE2EHarness.REORDER_TARGET_NAME);
        if (target == null) {
            return;
        }

        targetId = target.getId();
        minecraft.hitResult = new EntityHitResult(target);
        clearClientResult(minecraft);
        send(3L, targetId);
        beginPhase(Phase.WAITING_FOR_REORDER_SUCCESS);
    }

    private static void waitForReorderSuccess(Minecraft minecraft) {
        OffhandAttackResult result = currentResult(minecraft);
        if (result == null) {
            return;
        }
        if (!verifySuccess(result, 3L, 1, 2, "reordered sequence 3")) {
            return;
        }

        reorderResult = result;
        beginPhase(Phase.WAITING_TO_SEND_DELAYED_TWO);
        OffHandCombat.LOGGER.info(
                "Off Hand Combat network stress client accepted sequence 3 before delayed sequence 2");
    }

    private static void waitToSendDelayedTwo(Minecraft minecraft) {
        if (elapsedTicks - phaseStartedAtTick < DELAYED_REORDER_TICKS) {
            return;
        }

        clearClientResult(minecraft);
        send(2L, targetId);
        beginPhase(Phase.WAITING_FOR_STALE_TWO);
    }

    private static void waitForStaleTwo(Minecraft minecraft) {
        OffhandAttackResult result = currentResult(minecraft);
        if (!matches(result, 2L, targetId, OffhandAttackStatus.STALE_SEQUENCE)) {
            return;
        }
        if (minecraft.player.getOffhandItem().getDamageValue() != 2) {
            fail("delayed stale sequence 2 changed durability");
            return;
        }

        clearClientResult(minecraft);
        send(3L, targetId);
        beginPhase(Phase.WAITING_FOR_DUPLICATE_THREE);
    }

    private static void waitForDuplicateThree(Minecraft minecraft) {
        OffhandAttackResult result = currentResult(minecraft);
        if (result == null || !reorderResult.equals(result)) {
            return;
        }
        if (minecraft.player.getOffhandItem().getDamageValue() != 2) {
            fail("duplicate sequence 3 changed durability");
            return;
        }

        clearClientResult(minecraft);
        send(4L, -1);
        beginPhase(Phase.WAITING_FOR_INVALID_TARGET_FOUR);
    }

    private static void waitForInvalidTargetFour(Minecraft minecraft) {
        OffhandAttackResult result = currentResult(minecraft);
        if (!matches(result, 4L, -1, OffhandAttackStatus.INVALID_TARGET)) {
            return;
        }

        clearClientResult(minecraft);
        for (long sequence = BURST_FIRST_SEQUENCE; sequence <= BURST_LAST_SEQUENCE; sequence++) {
            send(sequence, targetId);
        }
        beginPhase(Phase.WAITING_FOR_BURST_RESULT);
        OffHandCombat.LOGGER.info(
                "Off Hand Combat network stress client sent unique-sequence burst {}-{}",
                BURST_FIRST_SEQUENCE, BURST_LAST_SEQUENCE);
    }

    private static void waitForBurstResult(Minecraft minecraft) {
        OffhandAttackResult result = currentResult(minecraft);
        if (!matches(result, BURST_LAST_SEQUENCE, targetId, OffhandAttackStatus.RATE_LIMITED)) {
            return;
        }
        if (minecraft.player.getOffhandItem().getDamageValue() != 2) {
            fail("unique-sequence burst changed durability");
            return;
        }

        beginPhase(Phase.WAITING_FOR_FINAL_TARGET);
        OffHandCombat.LOGGER.info(
                "Off Hand Combat network stress client observed burst rate limiting without execution");
    }

    private static void findFinalTargetAndSendSequenceSixtyNine(Minecraft minecraft) {
        Mob target = findNamedTarget(minecraft, OffhandNetworkStressServerE2EHarness.FINAL_TARGET_NAME);
        if (target == null) {
            return;
        }

        targetId = target.getId();
        minecraft.hitResult = new EntityHitResult(target);
        clearClientResult(minecraft);
        send(69L, targetId);
        beginPhase(Phase.WAITING_FOR_FINAL_SUCCESS);
    }

    private static void waitForFinalSuccess(Minecraft minecraft) {
        OffhandAttackResult result = currentResult(minecraft);
        if (result == null) {
            return;
        }
        if (!verifySuccess(result, 69L, 2, 3, "final sequence 69")) {
            return;
        }

        finalResult = result;
        clearClientResult(minecraft);
        send(4L, targetId);
        beginPhase(Phase.WAITING_FOR_FINAL_STALE_FOUR);
    }

    private static void waitForFinalStaleFour(Minecraft minecraft) {
        OffhandAttackResult result = currentResult(minecraft);
        if (!matches(result, 4L, targetId, OffhandAttackStatus.STALE_SEQUENCE)) {
            return;
        }
        if (minecraft.player.getOffhandItem().getDamageValue() != 3) {
            fail("final stale sequence 4 changed durability");
            return;
        }

        clearClientResult(minecraft);
        send(69L, targetId);
        beginPhase(Phase.WAITING_FOR_FINAL_DUPLICATE);
    }

    private static void waitForFinalDuplicate(Minecraft minecraft) {
        OffhandAttackResult result = currentResult(minecraft);
        if (result == null || !finalResult.equals(result)) {
            return;
        }
        if (minecraft.player.getOffhandItem().getDamageValue() != 3) {
            fail("final duplicate sequence 69 changed durability");
            return;
        }

        clearClientResult(minecraft);
        send(70L, targetId);
        beginPhase(Phase.WAITING_FOR_FINAL_RATE_LIMIT);
    }

    private static void waitForFinalRateLimit(Minecraft minecraft) {
        OffhandAttackResult result = currentResult(minecraft);
        if (!matches(result, 70L, targetId, OffhandAttackStatus.RATE_LIMITED)) {
            return;
        }
        if (minecraft.player.getOffhandItem().getDamageValue() != 3) {
            fail("final immediate sequence 70 changed durability");
            return;
        }

        phase = Phase.PASSED;
        OffHandCombat.LOGGER.info(
                "Off Hand Combat network stress client E2E passed: invalid=0/-1, duplicateFlood=64, "
                        + "reordered=3-before-2, burst=5-68, finalSequence=69");
    }

    private static void send(long sequence, int target) {
        PacketDistributor.sendToServer(new OffhandAttackRequestPayload(sequence, target));
    }

    private static void clearClientResult(Minecraft minecraft) {
        minecraft.player.getData(OffhandCombatAttachments.COMBAT_STATE).setLastClientResult(
                OffhandAttackResult.rejected(
                        Long.MAX_VALUE, Integer.MIN_VALUE,
                        OffhandAttackStatus.INTERNAL_ERROR,
                        minecraft.level.getGameTime()));
    }

    private static OffhandAttackResult currentResult(Minecraft minecraft) {
        if (minecraft.player == null) {
            return null;
        }
        return minecraft.player.getData(OffhandCombatAttachments.COMBAT_STATE).lastClientResult();
    }

    private static boolean matches(
            OffhandAttackResult result,
            long expectedSequence,
            int expectedTarget,
            OffhandAttackStatus expectedStatus) {
        if (result == null
                || result.sequence() != expectedSequence
                || result.targetId() != expectedTarget) {
            return false;
        }
        if (result.status() != expectedStatus) {
            fail("sequence " + expectedSequence + " result was " + result.status()
                    + " instead of " + expectedStatus);
            return false;
        }
        return true;
    }

    private static boolean verifySuccess(
            OffhandAttackResult result,
            long expectedSequence,
            int expectedDurabilityBefore,
            int expectedDurabilityAfter,
            String label) {
        if (result.status() != OffhandAttackStatus.SUCCESS) {
            fail(label + " result was " + result.status());
            return false;
        }
        if (result.sequence() != expectedSequence) {
            fail(label + " used sequence " + result.sequence());
            return false;
        }
        if (!(result.targetHealthAfter() < result.targetHealthBefore())) {
            fail(label + " did not reduce target health");
            return false;
        }
        if (result.durabilityBefore() != expectedDurabilityBefore
                || result.durabilityAfter() != expectedDurabilityAfter) {
            fail(label + " used unexpected durability transition "
                    + result.durabilityBefore() + " -> " + result.durabilityAfter());
            return false;
        }
        return true;
    }

    private static boolean isRemoteConnectionReady(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null || minecraft.getConnection() == null) {
            return false;
        }
        if (minecraft.getSingleplayerServer() != null) {
            fail("network stress client unexpectedly created an integrated server");
            return false;
        }
        if (!OffhandNetworkStressServerE2EHarness.PLAYER_NAME.equals(
                minecraft.player.getGameProfile().getName())) {
            fail("network stress client joined with the wrong username");
            return false;
        }
        if (!minecraft.getConnection().hasChannel(OffhandAttackRequestPayload.TYPE)) {
            return false;
        }
        return minecraft.screen == null;
    }

    private static Mob findNamedTarget(Minecraft minecraft, String targetName) {
        if (!isRemoteConnectionReady(minecraft)) {
            return null;
        }

        List<Mob> targets = minecraft.level.getEntitiesOfClass(
                Mob.class,
                minecraft.player.getBoundingBox().inflate(32.0D),
                target -> target.hasCustomName() && targetName.equals(target.getCustomName().getString()));
        return targets.size() == 1 ? targets.getFirst() : null;
    }

    private static void beginPhase(Phase next) {
        phase = next;
        phaseStartedAtTick = elapsedTicks;
    }

    private static void fail(String reason) {
        if (phase != Phase.FAILED) {
            phase = Phase.FAILED;
            OffHandCombat.LOGGER.error("Off Hand Combat network stress client E2E failed: {}", reason);
        }
    }

    private static void fail(String reason, Throwable throwable) {
        if (phase != Phase.FAILED) {
            phase = Phase.FAILED;
            OffHandCombat.LOGGER.error(
                    "Off Hand Combat network stress client E2E failed: {}", reason, throwable);
        }
    }

    private enum Phase {
        WAITING_TO_CONNECT,
        WAITING_FOR_CONNECTION,
        WAITING_FOR_INITIAL_TARGET,
        WAITING_FOR_INVALID_ZERO,
        WAITING_FOR_INVALID_NEGATIVE,
        WAITING_FOR_INITIAL_SUCCESS,
        WAITING_FOR_DUPLICATE_REPLAY,
        WAITING_FOR_REORDER_TARGET,
        WAITING_FOR_REORDER_SUCCESS,
        WAITING_TO_SEND_DELAYED_TWO,
        WAITING_FOR_STALE_TWO,
        WAITING_FOR_DUPLICATE_THREE,
        WAITING_FOR_INVALID_TARGET_FOUR,
        WAITING_FOR_BURST_RESULT,
        WAITING_FOR_FINAL_TARGET,
        WAITING_FOR_FINAL_SUCCESS,
        WAITING_FOR_FINAL_STALE_FOUR,
        WAITING_FOR_FINAL_DUPLICATE,
        WAITING_FOR_FINAL_RATE_LIMIT,
        PASSED,
        FAILED
    }
}
