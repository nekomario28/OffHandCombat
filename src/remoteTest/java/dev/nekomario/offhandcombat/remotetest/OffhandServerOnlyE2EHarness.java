package dev.nekomario.offhandcombat.remotetest;

import dev.nekomario.offhandcombat.OffHandCombat;
import dev.nekomario.offhandcombat.attachment.OffhandCombatAttachments;
import dev.nekomario.offhandcombat.attachment.OffhandCombatState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = OffHandCombat.MOD_ID, value = Dist.DEDICATED_SERVER)
public final class OffhandServerOnlyE2EHarness {
    public static final String PLAYER_NAME = "OHCNoModPeer";

    private static final String ENABLE_PROPERTY = "offhandcombat.serverOnlyE2E";
    private static final int STABILITY_TICKS = 100;
    private static final int TIMEOUT_TICKS = 3600;

    private static Phase phase = Phase.WAITING_FOR_PLAYER;
    private static int elapsedTicks;
    private static int joinedAtTick;
    private static ServerPlayer joinedPlayer;
    private static OffhandCombatState joinedState;

    private OffhandServerOnlyE2EHarness() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!Boolean.getBoolean(ENABLE_PROPERTY) || phase == Phase.PASSED || phase == Phase.FAILED) {
            return;
        }

        try {
            elapsedTicks++;
            if (elapsedTicks > TIMEOUT_TICKS) {
                fail("timed out in phase " + phase);
                return;
            }

            MinecraftServer server = event.getServer();
            if (!server.isDedicatedServer()) {
                fail("server-only mismatch harness was not running on a dedicated server");
                return;
            }

            ServerPlayer player = server.getPlayerList().getPlayerByName(PLAYER_NAME);
            if (phase == Phase.WAITING_FOR_PLAYER) {
                observeJoin(player);
            } else if (phase == Phase.WAITING_FOR_STABILITY) {
                verifyStableIdle(player);
            }
        } catch (Throwable throwable) {
            fail("server-only mismatch harness exception", throwable);
        }
    }

    private static void observeJoin(ServerPlayer player) {
        if (player == null) {
            return;
        }

        joinedPlayer = player;
        joinedState = player.getData(OffhandCombatAttachments.COMBAT_STATE);
        if (!isFresh(joinedState)) {
            fail("peer without Off Hand Combat joined with non-fresh combat state");
            return;
        }
        if (!player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty()) {
            fail("peer inventory was unexpectedly changed on join");
            return;
        }

        joinedAtTick = elapsedTicks;
        phase = Phase.WAITING_FOR_STABILITY;
        OffHandCombat.LOGGER.info(
                "Off Hand Combat server-only mismatch peer connected with fresh idle state");
    }

    private static void verifyStableIdle(ServerPlayer player) {
        if (player == null) {
            fail("peer without Off Hand Combat disconnected during the stability window");
            return;
        }
        if (player != joinedPlayer) {
            fail("peer ServerPlayer object changed during the stability window");
            return;
        }

        OffhandCombatState state = player.getData(OffhandCombatAttachments.COMBAT_STATE);
        if (state != joinedState) {
            fail("peer combat-state object changed during the stability window");
            return;
        }
        if (!isFresh(state)) {
            fail("server-only installation processed unexpected off-hand network state");
            return;
        }
        if (!player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty()) {
            fail("server-only installation changed peer inventory while idle");
            return;
        }

        if (elapsedTicks - joinedAtTick < STABILITY_TICKS) {
            return;
        }

        phase = Phase.PASSED;
        OffHandCombat.LOGGER.info(
                "Off Hand Combat server-only no-mod NeoForge peer E2E passed: joined, fresh state, no request/result, stable connection");
    }

    private static boolean isFresh(OffhandCombatState state) {
        return state.lastNetworkSequence() == 0L
                && state.lastNetworkResult() == null
                && state.lastClientResult() == null;
    }

    private static void fail(String reason) {
        if (phase != Phase.FAILED) {
            phase = Phase.FAILED;
            OffHandCombat.LOGGER.error(
                    "Off Hand Combat server-only no-mod peer E2E failed: {}", reason);
        }
    }

    private static void fail(String reason, Throwable throwable) {
        if (phase != Phase.FAILED) {
            phase = Phase.FAILED;
            OffHandCombat.LOGGER.error(
                    "Off Hand Combat server-only no-mod peer E2E failed: {}", reason, throwable);
        }
    }

    private enum Phase {
        WAITING_FOR_PLAYER,
        WAITING_FOR_STABILITY,
        PASSED,
        FAILED
    }
}
