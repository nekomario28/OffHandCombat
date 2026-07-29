package dev.nekomario.offhandcombat.remotetest;

import dev.nekomario.offhandcombat.OffHandCombat;
import dev.nekomario.offhandcombat.api.OffhandAttackResult;
import dev.nekomario.offhandcombat.api.OffhandAttackStatus;
import dev.nekomario.offhandcombat.attachment.OffhandCombatAttachments;
import dev.nekomario.offhandcombat.client.ClientModEvents;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = OffHandCombat.MOD_ID, value = Dist.CLIENT)
public final class OffhandLifecycleClientE2EHarness {
    private static final String ENABLE_PROPERTY = "offhandcombat.lifecycleClientE2E";
    private static final String SERVER_ADDRESS = "127.0.0.1:25566";
    private static final int CONNECT_AFTER_TICKS = 20;
    private static final int INITIAL_RESULT_SETTLE_TICKS = 20;
    private static final int RECONNECT_DELAY_TICKS = 20;
    private static final int RESPAWN_DELAY_TICKS = 20;
    private static final int TIMEOUT_TICKS = 7200;

    private static Phase phase = Phase.WAITING_TO_CONNECT;
    private static int elapsedTicks;
    private static int phaseStartedAtTick;
    private static int targetId = -1;
    private static OffhandAttackResult respawnResult;

    private OffhandLifecycleClientE2EHarness() {
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
                case WAITING_TO_CONNECT -> connectWhenClientIsReady(minecraft, false);
                case WAITING_FOR_INITIAL_CONNECTION -> waitForConnection(minecraft, false);
                case WAITING_FOR_INITIAL_TARGET -> attackNamedTarget(
                        minecraft, OffhandLifecycleServerE2EHarness.INITIAL_TARGET_NAME,
                        Phase.WAITING_FOR_INITIAL_RESULT);
                case WAITING_FOR_INITIAL_RESULT -> waitForInitialResult(minecraft);
                case WAITING_TO_DISCONNECT_FOR_RECONNECT -> waitToDisconnectForReconnect(minecraft);
                case WAITING_FOR_DISCONNECT_CLEANUP -> waitForDisconnectCleanup(minecraft);
                case WAITING_FOR_RECONNECT_CONNECTION -> waitForConnection(minecraft, true);
                case WAITING_FOR_RECONNECT_TARGET -> attackNamedTarget(
                        minecraft, OffhandLifecycleServerE2EHarness.RECONNECT_TARGET_NAME,
                        Phase.WAITING_FOR_RECONNECT_RESULT);
                case WAITING_FOR_RECONNECT_RESULT -> waitForReconnectResult(minecraft);
                case WAITING_FOR_DEATH -> waitForDeathScreen(minecraft);
                case WAITING_TO_RESPAWN -> waitToRespawn(minecraft);
                case WAITING_FOR_RESPAWN_PLAYER -> waitForRespawnPlayer(minecraft);
                case WAITING_FOR_RESPAWN_TARGET -> attackNamedTarget(
                        minecraft, OffhandLifecycleServerE2EHarness.RESPAWN_TARGET_NAME,
                        Phase.WAITING_FOR_RESPAWN_RESULT);
                case WAITING_FOR_RESPAWN_RESULT -> waitForRespawnResult(minecraft);
                case WAITING_FOR_DIMENSION -> waitForDimension(minecraft);
                case WAITING_FOR_DIMENSION_TARGET -> attackNamedTarget(
                        minecraft, OffhandLifecycleServerE2EHarness.DIMENSION_TARGET_NAME,
                        Phase.WAITING_FOR_DIMENSION_RESULT);
                case WAITING_FOR_DIMENSION_RESULT -> waitForDimensionResult(minecraft);
                default -> {
                }
            }
        } catch (Throwable throwable) {
            fail("lifecycle client harness exception", throwable);
        }
    }

    private static void connectWhenClientIsReady(Minecraft minecraft, boolean reconnect) {
        int delay = reconnect ? RECONNECT_DELAY_TICKS : CONNECT_AFTER_TICKS;
        if (elapsedTicks - phaseStartedAtTick < delay || minecraft.screen == null) {
            return;
        }
        if (minecraft.level != null || minecraft.player != null || minecraft.getConnection() != null) {
            if (reconnect) {
                return;
            }
            fail("lifecycle client had an unexpected active world before initial connection");
            return;
        }

        ServerData serverData = new ServerData(
                "Off Hand Combat lifecycle E2E", SERVER_ADDRESS, ServerData.Type.OTHER);
        ConnectScreen.startConnecting(
                minecraft.screen,
                minecraft,
                ServerAddress.parseString(SERVER_ADDRESS),
                serverData,
                false,
                null);
        beginPhase(reconnect
                ? Phase.WAITING_FOR_RECONNECT_CONNECTION
                : Phase.WAITING_FOR_INITIAL_CONNECTION);
        OffHandCombat.LOGGER.info(
                "Off Hand Combat lifecycle client connecting through vanilla ConnectScreen: {}",
                SERVER_ADDRESS);
    }

    private static void waitForConnection(Minecraft minecraft, boolean reconnect) {
        if (!isRemoteConnectionReady(minecraft)) {
            return;
        }

        var state = minecraft.player.getData(OffhandCombatAttachments.COMBAT_STATE);
        if (state.lastClientResult() != null) {
            fail((reconnect ? "reconnected" : "initial")
                    + " client inherited a cached result");
            return;
        }

        beginPhase(reconnect
                ? Phase.WAITING_FOR_RECONNECT_TARGET
                : Phase.WAITING_FOR_INITIAL_TARGET);
        OffHandCombat.LOGGER.info(
                "Off Hand Combat lifecycle client {} connection ready",
                reconnect ? "reconnect" : "initial");
    }

    private static void waitForInitialResult(Minecraft minecraft) {
        OffhandAttackResult result = currentResult(minecraft);
        if (result == null) {
            return;
        }
        if (!verifySuccessfulStage(result, 1L, 0, 1, "initial attack")) {
            return;
        }

        beginPhase(Phase.WAITING_TO_DISCONNECT_FOR_RECONNECT);
        OffHandCombat.LOGGER.info(
                "Off Hand Combat lifecycle client initial result observed; allowing server phase to settle");
    }

    private static void waitToDisconnectForReconnect(Minecraft minecraft) {
        if (elapsedTicks - phaseStartedAtTick < INITIAL_RESULT_SETTLE_TICKS) {
            return;
        }
        if (minecraft.getConnection() == null) {
            fail("initial connection disappeared before reconnect request");
            return;
        }

        minecraft.getConnection().getConnection().disconnect(
                Component.literal("Off Hand Combat lifecycle reconnect"));
        beginPhase(Phase.WAITING_FOR_DISCONNECT_CLEANUP);
        OffHandCombat.LOGGER.info("Off Hand Combat lifecycle client requested an actual reconnect");
    }

    private static void waitForDisconnectCleanup(Minecraft minecraft) {
        if (minecraft.level != null || minecraft.player != null || minecraft.getConnection() != null) {
            return;
        }
        if (minecraft.screen == null) {
            return;
        }

        connectWhenClientIsReady(minecraft, true);
    }

    private static void waitForReconnectResult(Minecraft minecraft) {
        OffhandAttackResult result = currentResult(minecraft);
        if (result == null) {
            return;
        }
        if (!verifySuccessfulStage(result, 1L, 0, 1, "reconnect attack")) {
            return;
        }

        beginPhase(Phase.WAITING_FOR_DEATH);
        OffHandCombat.LOGGER.info(
                "Off Hand Combat lifecycle client reconnect sequence reset passed");
    }

    private static void waitForDeathScreen(Minecraft minecraft) {
        if (!(minecraft.screen instanceof DeathScreen)
                || minecraft.player == null
                || !minecraft.player.isDeadOrDying()) {
            return;
        }

        beginPhase(Phase.WAITING_TO_RESPAWN);
        OffHandCombat.LOGGER.info("Off Hand Combat lifecycle client observed the actual death screen");
    }

    private static void waitToRespawn(Minecraft minecraft) {
        if (elapsedTicks - phaseStartedAtTick < RESPAWN_DELAY_TICKS) {
            return;
        }
        if (minecraft.getConnection() == null) {
            fail("connection disappeared before the respawn command");
            return;
        }

        minecraft.getConnection().send(new ServerboundClientCommandPacket(
                ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
        beginPhase(Phase.WAITING_FOR_RESPAWN_PLAYER);
        OffHandCombat.LOGGER.info("Off Hand Combat lifecycle client sent the vanilla respawn command");
    }

    private static void waitForRespawnPlayer(Minecraft minecraft) {
        if (minecraft.level == null
                || minecraft.player == null
                || minecraft.player.isDeadOrDying()
                || minecraft.screen != null) {
            return;
        }

        var state = minecraft.player.getData(OffhandCombatAttachments.COMBAT_STATE);
        if (state.lastClientResult() != null) {
            fail("respawned client inherited a cached result");
            return;
        }

        beginPhase(Phase.WAITING_FOR_RESPAWN_TARGET);
        OffHandCombat.LOGGER.info("Off Hand Combat lifecycle client death/respawn state reset passed");
    }

    private static void waitForRespawnResult(Minecraft minecraft) {
        OffhandAttackResult result = currentResult(minecraft);
        if (result == null) {
            return;
        }
        if (!verifySuccessfulStage(result, 1L, 0, 1, "respawn attack")) {
            return;
        }

        respawnResult = result;
        beginPhase(Phase.WAITING_FOR_DIMENSION);
        OffHandCombat.LOGGER.info(
                "Off Hand Combat lifecycle client respawn sequence reset passed");
    }

    private static void waitForDimension(Minecraft minecraft) {
        if (minecraft.level == null
                || minecraft.player == null
                || !minecraft.level.dimension().equals(Level.NETHER)
                || minecraft.screen != null) {
            return;
        }

        OffhandAttackResult preserved = minecraft.player
                .getData(OffhandCombatAttachments.COMBAT_STATE)
                .lastClientResult();
        if (!respawnResult.equals(preserved)) {
            fail("dimension transition lost the client's active result/sequence anchor");
            return;
        }
        if (minecraft.player.getOffhandItem().getDamageValue() != 1) {
            fail("dimension transition changed client-visible off-hand durability");
            return;
        }

        beginPhase(Phase.WAITING_FOR_DIMENSION_TARGET);
        OffHandCombat.LOGGER.info(
                "Off Hand Combat lifecycle client dimension state preservation passed");
    }

    private static void waitForDimensionResult(Minecraft minecraft) {
        OffhandAttackResult result = currentResult(minecraft);
        if (result == null || result.sequence() == 1L) {
            return;
        }
        if (!verifySuccessfulStage(result, 2L, 1, 2, "dimension attack")) {
            return;
        }

        phase = Phase.PASSED;
        OffHandCombat.LOGGER.info(
                "Off Hand Combat lifecycle client E2E passed: reconnect sequence=1, "
                        + "respawn sequence=1, dimension sequence=2");
    }

    private static void attackNamedTarget(
            Minecraft minecraft, String targetName, Phase resultPhase) {
        if (!isRemoteConnectionReady(minecraft)
                || minecraft.screen != null
                || !minecraft.player.getOffhandItem().is(Items.IRON_SWORD)) {
            return;
        }

        Mob target = findNamedTarget(minecraft, targetName);
        if (target == null) {
            return;
        }

        targetId = target.getId();
        minecraft.hitResult = new EntityHitResult(target);
        KeyMapping.click(ClientModEvents.OFFHAND_ATTACK.get().getKey());
        beginPhase(resultPhase);
    }

    private static OffhandAttackResult currentResult(Minecraft minecraft) {
        if (minecraft.player == null) {
            return null;
        }
        OffhandAttackResult result = minecraft.player
                .getData(OffhandCombatAttachments.COMBAT_STATE)
                .lastClientResult();
        return result != null && result.targetId() == targetId ? result : null;
    }

    private static boolean verifySuccessfulStage(
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
            fail(label + " used sequence " + result.sequence()
                    + " instead of " + expectedSequence);
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
            fail("lifecycle client unexpectedly created an integrated server");
            return false;
        }
        if (!OffhandLifecycleServerE2EHarness.PLAYER_NAME.equals(
                minecraft.player.getGameProfile().getName())) {
            fail("lifecycle client joined with the wrong username");
            return false;
        }
        return true;
    }

    private static Mob findNamedTarget(Minecraft minecraft, String targetName) {
        if (minecraft.level == null || minecraft.player == null) {
            return null;
        }
        List<Mob> targets = minecraft.level.getEntitiesOfClass(
                Mob.class,
                minecraft.player.getBoundingBox().inflate(32.0D),
                target -> target.hasCustomName()
                        && targetName.equals(target.getCustomName().getString()));
        return targets.size() == 1 ? targets.getFirst() : null;
    }

    private static void beginPhase(Phase next) {
        phase = next;
        phaseStartedAtTick = elapsedTicks;
    }

    private static void fail(String reason) {
        if (phase != Phase.FAILED) {
            phase = Phase.FAILED;
            OffHandCombat.LOGGER.error("Off Hand Combat lifecycle client E2E failed: {}", reason);
        }
    }

    private static void fail(String reason, Throwable throwable) {
        if (phase != Phase.FAILED) {
            phase = Phase.FAILED;
            OffHandCombat.LOGGER.error(
                    "Off Hand Combat lifecycle client E2E failed: {}", reason, throwable);
        }
    }

    private enum Phase {
        WAITING_TO_CONNECT,
        WAITING_FOR_INITIAL_CONNECTION,
        WAITING_FOR_INITIAL_TARGET,
        WAITING_FOR_INITIAL_RESULT,
        WAITING_TO_DISCONNECT_FOR_RECONNECT,
        WAITING_FOR_DISCONNECT_CLEANUP,
        WAITING_FOR_RECONNECT_CONNECTION,
        WAITING_FOR_RECONNECT_TARGET,
        WAITING_FOR_RECONNECT_RESULT,
        WAITING_FOR_DEATH,
        WAITING_TO_RESPAWN,
        WAITING_FOR_RESPAWN_PLAYER,
        WAITING_FOR_RESPAWN_TARGET,
        WAITING_FOR_RESPAWN_RESULT,
        WAITING_FOR_DIMENSION,
        WAITING_FOR_DIMENSION_TARGET,
        WAITING_FOR_DIMENSION_RESULT,
        PASSED,
        FAILED
    }
}
