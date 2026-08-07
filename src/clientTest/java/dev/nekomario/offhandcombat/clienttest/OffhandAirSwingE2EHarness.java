package dev.nekomario.offhandcombat.clienttest;

import dev.nekomario.offhandcombat.OffHandCombat;
import dev.nekomario.offhandcombat.api.OffhandAttackAccess;
import dev.nekomario.offhandcombat.attachment.OffhandCombatAttachments;
import dev.nekomario.offhandcombat.network.OffhandAttackRequestPayload;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.common.NeoForge;

@EventBusSubscriber(modid = OffHandCombat.MOD_ID, value = Dist.CLIENT)
public final class OffhandAirSwingE2EHarness {
    private static final String ENABLE_PROPERTY = "offhandcombat.airSwingE2E";
    private static final String WORLD_NAME = "AirSwingWorld";
    private static final int TIMEOUT_CLIENT_TICKS = 1200;
    private static final int VERIFY_DELAY_TICKS = 8;

    private static volatile Phase phase = Phase.WAITING_FOR_WORLD;
    private static volatile long baselineServerSequence;
    private static volatile int baselineServerDurability;
    private static int clientTicks;
    private static int verifyAtTick;

    private OffhandAirSwingE2EHarness() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean(ENABLE_PROPERTY) || phase == Phase.PASSED || phase == Phase.FAILED) {
            return;
        }

        try {
            clientTicks++;
            if (clientTicks > TIMEOUT_CLIENT_TICKS) {
                fail("timed out in phase " + phase);
                return;
            }

            Minecraft minecraft = Minecraft.getInstance();
            switch (phase) {
                case WAITING_FOR_WORLD -> openWorld(minecraft);
                case OPENING_WORLD -> setupWhenReady(minecraft);
                case WAITING_FOR_SYNC -> triggerAirSwingWhenSynchronized(minecraft);
                case WAITING_FOR_VERIFY -> verifyAfterDelay(minecraft);
                default -> {
                }
            }
        } catch (Throwable throwable) {
            fail("air-swing harness exception", throwable);
        }
    }

    private static void openWorld(Minecraft minecraft) {
        if (minecraft.level != null) {
            phase = Phase.OPENING_WORLD;
            return;
        }
        if (clientTicks < 20 || minecraft.screen == null) {
            return;
        }
        if (!minecraft.getLevelSource().levelExists(WORLD_NAME)) {
            fail("copied air-swing E2E world was unavailable: " + WORLD_NAME);
            return;
        }

        phase = Phase.OPENING_WORLD;
        minecraft.createWorldOpenFlows().openWorld(
                WORLD_NAME,
                () -> fail("opening copied air-swing E2E world was aborted"));
    }

    private static void setupWhenReady(Minecraft minecraft) {
        if (minecraft.level == null
                || minecraft.player == null
                || minecraft.getConnection() == null
                || minecraft.getSingleplayerServer() == null
                || !minecraft.getConnection().hasChannel(OffhandAttackRequestPayload.TYPE)) {
            return;
        }

        phase = Phase.SETTING_UP;
        UUID playerId = minecraft.player.getUUID();
        var server = minecraft.getSingleplayerServer();
        server.execute(() -> {
            try {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player == null) {
                    fail("integrated server player was unavailable");
                    return;
                }

                player.setGameMode(GameType.SURVIVAL);
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.IRON_SWORD));
                ((OffhandAttackAccess) player).ofc$setOffhandAttackStrengthTicker(100);
                var state = player.getData(OffhandCombatAttachments.COMBAT_STATE);
                baselineServerSequence = state.lastNetworkSequence();
                baselineServerDurability = player.getOffhandItem().getDamageValue();
                phase = Phase.WAITING_FOR_SYNC;
            } catch (Throwable throwable) {
                fail("air-swing server setup exception", throwable);
            }
        });
    }

    private static void triggerAirSwingWhenSynchronized(Minecraft minecraft) {
        if (minecraft.level == null
                || minecraft.player == null
                || minecraft.screen != null
                || minecraft.getConnection() == null
                || !minecraft.getConnection().hasChannel(OffhandAttackRequestPayload.TYPE)
                || !minecraft.player.getOffhandItem().is(Items.IRON_SWORD)) {
            return;
        }

        OffhandAttackAccess access = (OffhandAttackAccess) minecraft.player;
        if (access.ofc$getOffhandAttackStrengthScale(0.0F) < 0.99F) {
            return;
        }

        minecraft.player.swinging = false;
        minecraft.player.swingTime = 0;
        minecraft.player.swingingArm = InteractionHand.MAIN_HAND;

        Vec3 missLocation = minecraft.player.getEyePosition().add(
                minecraft.player.getViewVector(1.0F).scale(5.0D));
        minecraft.hitResult = BlockHitResult.miss(
                missLocation,
                Direction.getNearest(minecraft.player.getViewVector(1.0F)),
                minecraft.player.blockPosition());

        InputEvent.InteractionKeyMappingTriggered input = new InputEvent.InteractionKeyMappingTriggered(
                1,
                minecraft.options.keyUse,
                InteractionHand.OFF_HAND
        );
        NeoForge.EVENT_BUS.post(input);

        if (!input.isCanceled()) {
            fail("empty-air OFF_HAND use input was not consumed");
            return;
        }
        if (!minecraft.player.swinging || minecraft.player.swingingArm != InteractionHand.OFF_HAND) {
            fail("empty-air input did not start an off-hand swing");
            return;
        }
        if (access.ofc$getOffhandAttackStrengthTicker() != 0) {
            fail("empty-air swing did not reset the off-hand cooldown");
            return;
        }
        var state = minecraft.player.getData(OffhandCombatAttachments.COMBAT_STATE);
        if (minecraft.gameMode != null && minecraft.gameMode.hasMissTime() && state.airSwingMissTicks() <= 0) {
            fail("empty-air swing did not arm the upstream miss throttle");
            return;
        }
        if (state.lastClientResult() != null) {
            fail("empty-air swing produced an attack result");
            return;
        }

        InputEvent.InteractionKeyMappingTriggered immediateRepeat = new InputEvent.InteractionKeyMappingTriggered(
                1,
                minecraft.options.keyUse,
                InteractionHand.OFF_HAND
        );
        NeoForge.EVENT_BUS.post(immediateRepeat);
        if (minecraft.gameMode != null && minecraft.gameMode.hasMissTime() && immediateRepeat.isCanceled()) {
            fail("miss throttle consumed an immediate repeat instead of leaving it to vanilla");
            return;
        }

        verifyAtTick = clientTicks + VERIFY_DELAY_TICKS;
        phase = Phase.WAITING_FOR_VERIFY;
    }

    private static void verifyAfterDelay(Minecraft minecraft) {
        if (clientTicks < verifyAtTick
                || minecraft.player == null
                || minecraft.getSingleplayerServer() == null) {
            return;
        }

        float clientStrength = ((OffhandAttackAccess) minecraft.player).ofc$getOffhandAttackStrengthScale(0.0F);
        if (clientStrength <= 0.0F || clientStrength >= 0.99F) {
            fail("empty-air swing cooldown did not recharge from zero: " + clientStrength);
            return;
        }
        if (minecraft.player.getData(OffhandCombatAttachments.COMBAT_STATE).lastClientResult() != null) {
            fail("empty-air swing later produced an attack result");
            return;
        }

        phase = Phase.VERIFYING_SERVER;
        UUID playerId = minecraft.player.getUUID();
        var server = minecraft.getSingleplayerServer();
        server.execute(() -> {
            try {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player == null) {
                    fail("server player was unavailable during air-swing verification");
                    return;
                }
                var state = player.getData(OffhandCombatAttachments.COMBAT_STATE);
                if (state.lastNetworkSequence() != baselineServerSequence) {
                    fail("empty-air swing emitted an attack request");
                    return;
                }
                if (player.getOffhandItem().getDamageValue() != baselineServerDurability) {
                    fail("empty-air swing consumed off-hand durability");
                    return;
                }
                if (state.lastNetworkResult() != null) {
                    fail("empty-air swing created an authoritative attack result");
                    return;
                }
                float serverStrength = ((OffhandAttackAccess) player).ofc$getOffhandAttackStrengthScale(0.0F);
                if (serverStrength <= 0.0F || serverStrength >= 0.99F) {
                    fail("server did not observe the off-hand swing cooldown: " + serverStrength);
                    return;
                }

                phase = Phase.PASSED;
                OffHandCombat.LOGGER.info(
                        "Off Hand Combat upstream air swing E2E passed: animation=OFF_HAND, sequence unchanged, durability unchanged, cooldown reset and recharging");
            } catch (Throwable throwable) {
                fail("air-swing server verification exception", throwable);
            }
        });
    }

    private static void fail(String reason) {
        if (phase != Phase.FAILED) {
            phase = Phase.FAILED;
            OffHandCombat.LOGGER.error("Off Hand Combat off-hand air swing E2E failed: {}", reason);
        }
    }

    private static void fail(String reason, Throwable throwable) {
        if (phase != Phase.FAILED) {
            phase = Phase.FAILED;
            OffHandCombat.LOGGER.error("Off Hand Combat off-hand air swing E2E failed: {}", reason, throwable);
        }
    }

    private enum Phase {
        WAITING_FOR_WORLD,
        OPENING_WORLD,
        SETTING_UP,
        WAITING_FOR_SYNC,
        WAITING_FOR_VERIFY,
        VERIFYING_SERVER,
        PASSED,
        FAILED
    }
}
