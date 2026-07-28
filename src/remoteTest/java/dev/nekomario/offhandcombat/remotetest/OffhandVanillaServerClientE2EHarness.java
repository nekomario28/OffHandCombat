package dev.nekomario.offhandcombat.remotetest;

import dev.nekomario.offhandcombat.OffHandCombat;
import dev.nekomario.offhandcombat.attachment.OffhandCombatAttachments;
import dev.nekomario.offhandcombat.client.ClientModEvents;
import dev.nekomario.offhandcombat.network.OffhandAttackRequestPayload;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;

@EventBusSubscriber(modid = OffHandCombat.MOD_ID, value = Dist.CLIENT)
public final class OffhandVanillaServerClientE2EHarness {
    private static final String ENABLE_PROPERTY = "offhandcombat.vanillaServerClientE2E";
    private static final String SERVER_ADDRESS = "127.0.0.1:25568";
    private static final String PLAYER_NAME = "OffhandVanillaPeer";
    private static final int CONNECT_AFTER_TICKS = 20;
    private static final int USE_EVENT_TIMEOUT_TICKS = 80;
    private static final int DEDICATED_SETTLE_TICKS = 20;
    private static final int CONNECTION_STABILITY_TICKS = 100;
    private static final int TIMEOUT_TICKS = 3600;

    private static Phase phase = Phase.WAITING_TO_CONNECT;
    private static int elapsedTicks;
    private static int phaseStartedAtTick;
    private static boolean offhandUseObserved;
    private static boolean offhandUseCanceled;

    private OffhandVanillaServerClientE2EHarness() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!enabled() || phase == Phase.PASSED || phase == Phase.FAILED) {
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
                case WAITING_TO_TRIGGER_USE -> triggerVanillaUse(minecraft);
                case WAITING_FOR_USE_EVENT -> waitForUseEvent(minecraft);
                case WAITING_FOR_DEDICATED_SETTLE -> waitForDedicatedSettle(minecraft);
                case WAITING_FOR_STABILITY -> waitForStability(minecraft);
                default -> {
                }
            }
        } catch (Throwable throwable) {
            fail("vanilla-server client harness exception", throwable);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        if (!enabled() || phase != Phase.WAITING_FOR_USE_EVENT
                || !event.isUseItem() || event.getHand() != InteractionHand.OFF_HAND) {
            return;
        }
        offhandUseObserved = true;
        offhandUseCanceled |= event.isCanceled();
    }

    private static void connectWhenReady(Minecraft minecraft) {
        if (elapsedTicks - phaseStartedAtTick < CONNECT_AFTER_TICKS || minecraft.screen == null) {
            return;
        }
        if (minecraft.level != null || minecraft.player != null || minecraft.getConnection() != null) {
            fail("client had an unexpected active world before vanilla-server connection");
            return;
        }

        ServerData serverData = new ServerData(
                "Off Hand Combat vanilla-server E2E", SERVER_ADDRESS, ServerData.Type.OTHER);
        ConnectScreen.startConnecting(
                minecraft.screen,
                minecraft,
                ServerAddress.parseString(SERVER_ADDRESS),
                serverData,
                false,
                null);
        beginPhase(Phase.WAITING_FOR_CONNECTION);
        OffHandCombat.LOGGER.info(
                "Off Hand Combat vanilla-server client connecting through vanilla ConnectScreen: {}",
                SERVER_ADDRESS);
    }

    private static void waitForConnection(Minecraft minecraft) {
        if (!connectionReady(minecraft)) {
            return;
        }
        if (minecraft.getConnection().hasChannel(OffhandAttackRequestPayload.TYPE)) {
            fail("a Mojang vanilla server unexpectedly advertised the off-hand request channel");
            return;
        }
        var state = minecraft.player.getData(OffhandCombatAttachments.COMBAT_STATE);
        if (state.lastClientResult() != null) {
            fail("client inherited a result before testing the vanilla server");
            return;
        }

        beginPhase(Phase.WAITING_TO_TRIGGER_USE);
        OffHandCombat.LOGGER.info(
                "Off Hand Combat client-only vanilla-server connection ready with request channel absent");
    }

    private static void triggerVanillaUse(Minecraft minecraft) {
        if (!connectionReady(minecraft) || minecraft.screen != null) {
            return;
        }

        BlockPos block = minecraft.player.blockPosition().below();
        Vec3 location = Vec3.atCenterOf(block).add(0.0D, 0.5D, 0.0D);
        minecraft.hitResult = new BlockHitResult(location, Direction.UP, block, false);
        offhandUseObserved = false;
        offhandUseCanceled = false;
        KeyMapping.click(minecraft.options.keyUse.getKey());
        beginPhase(Phase.WAITING_FOR_USE_EVENT);
    }

    private static void waitForUseEvent(Minecraft minecraft) {
        if (!connectionReady(minecraft)) {
            return;
        }
        if (!offhandUseObserved) {
            if (elapsedTicks - phaseStartedAtTick >= USE_EVENT_TIMEOUT_TICKS) {
                fail("vanilla use input did not reach the OFF_HAND interaction pass");
            }
            return;
        }
        if (offhandUseCanceled) {
            fail("client-only Off Hand Combat canceled vanilla use input without a negotiated channel");
            return;
        }

        KeyMapping.click(ClientModEvents.OFFHAND_ATTACK.get().getKey());
        beginPhase(Phase.WAITING_FOR_DEDICATED_SETTLE);
        OffHandCombat.LOGGER.info(
                "Off Hand Combat client-only vanilla use input remained uncanceled");
    }

    private static void waitForDedicatedSettle(Minecraft minecraft) {
        if (!connectionReady(minecraft)
                || elapsedTicks - phaseStartedAtTick < DEDICATED_SETTLE_TICKS) {
            return;
        }
        if (minecraft.getConnection().hasChannel(OffhandAttackRequestPayload.TYPE)) {
            fail("off-hand request channel appeared after dedicated-key input");
            return;
        }

        var state = minecraft.player.getData(OffhandCombatAttachments.COMBAT_STATE);
        if (state.lastClientResult() != null) {
            fail("dedicated-key input against a vanilla server produced a result");
            return;
        }
        long firstAvailableSequence = state.nextClientSequence();
        if (firstAvailableSequence != 1L) {
            fail("dedicated-key input advanced sequence without a negotiated channel: "
                    + firstAvailableSequence);
            return;
        }

        beginPhase(Phase.WAITING_FOR_STABILITY);
        OffHandCombat.LOGGER.info(
                "Off Hand Combat client-only dedicated key remained idle without a negotiated channel");
    }

    private static void waitForStability(Minecraft minecraft) {
        if (!connectionReady(minecraft)) {
            return;
        }
        if (elapsedTicks - phaseStartedAtTick < CONNECTION_STABILITY_TICKS) {
            return;
        }

        phase = Phase.PASSED;
        OffHandCombat.LOGGER.info(
                "Off Hand Combat client-only vanilla-server E2E passed: connected, use uncanceled, channel absent, no request/result");
    }

    private static boolean connectionReady(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null || minecraft.getConnection() == null) {
            return false;
        }
        if (minecraft.getSingleplayerServer() != null) {
            fail("client unexpectedly created an integrated server");
            return false;
        }
        if (!PLAYER_NAME.equals(minecraft.player.getGameProfile().getName())) {
            fail("client joined the vanilla server with the wrong username");
            return false;
        }
        return true;
    }

    private static boolean enabled() {
        return Boolean.getBoolean(ENABLE_PROPERTY);
    }

    private static void beginPhase(Phase next) {
        phase = next;
        phaseStartedAtTick = elapsedTicks;
    }

    private static void fail(String reason) {
        if (phase != Phase.FAILED) {
            phase = Phase.FAILED;
            OffHandCombat.LOGGER.error(
                    "Off Hand Combat client-only vanilla-server E2E failed: {}", reason);
        }
    }

    private static void fail(String reason, Throwable throwable) {
        if (phase != Phase.FAILED) {
            phase = Phase.FAILED;
            OffHandCombat.LOGGER.error(
                    "Off Hand Combat client-only vanilla-server E2E failed: {}", reason, throwable);
        }
    }

    private enum Phase {
        WAITING_TO_CONNECT,
        WAITING_FOR_CONNECTION,
        WAITING_TO_TRIGGER_USE,
        WAITING_FOR_USE_EVENT,
        WAITING_FOR_DEDICATED_SETTLE,
        WAITING_FOR_STABILITY,
        PASSED,
        FAILED
    }
}
