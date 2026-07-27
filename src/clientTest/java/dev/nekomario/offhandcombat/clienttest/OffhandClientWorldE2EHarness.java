package dev.nekomario.offhandcombat.clienttest;

import dev.nekomario.offhandcombat.OffHandCombat;
import dev.nekomario.offhandcombat.api.OffhandAttackAccess;
import dev.nekomario.offhandcombat.api.OffhandAttackResult;
import dev.nekomario.offhandcombat.api.OffhandAttackStatus;
import dev.nekomario.offhandcombat.attachment.OffhandCombatAttachments;
import dev.nekomario.offhandcombat.client.ClientModEvents;
import dev.nekomario.offhandcombat.network.OffhandAttackRequestPayload;
import java.util.UUID;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = OffHandCombat.MOD_ID, value = Dist.CLIENT)
public final class OffhandClientWorldE2EHarness {
    private static final String ENABLE_PROPERTY = "offhandcombat.clientWorldE2E";
    private static final String WORLD_NAME = "SmokeWorld";
    private static final int TIMEOUT_CLIENT_TICKS = 2400;
    private static final int GUI_SUPPRESSION_TICKS = 20;

    private static volatile Phase phase = Phase.WAITING_FOR_WORLD;
    private static volatile int targetId = -1;
    private static volatile OffhandAttackResult firstResult;
    private static int clientTicks;
    private static int guiSuppressionDeadline;

    private OffhandClientWorldE2EHarness() {
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
            if (phase == Phase.WAITING_FOR_WORLD) {
                openWorldWhenClientIsReady(minecraft);
            } else if (phase == Phase.OPENING_WORLD) {
                beginServerSetupWhenWorldIsReady(minecraft);
            } else if (phase == Phase.WAITING_FOR_CLIENT_SYNC) {
                beginGuiSuppressionCheckWhenClientIsSynchronized(minecraft);
            } else if (phase == Phase.WAITING_FOR_GUI_SUPPRESSION) {
                verifyGuiSuppressionAndArmServer(minecraft);
            } else if (phase == Phase.ARMED) {
                triggerDedicatedKeyAttack(minecraft);
            } else if (phase == Phase.WAITING_FOR_FIRST_RESULT) {
                acceptFirstResultAndReplay(minecraft);
            } else if (phase == Phase.WAITING_FOR_REPLAY_RESULT) {
                verifyReplayResult(minecraft);
            }
        } catch (Throwable throwable) {
            fail("client harness exception", throwable);
        }
    }

    private static void openWorldWhenClientIsReady(Minecraft minecraft) {
        if (minecraft.level != null) {
            phase = Phase.OPENING_WORLD;
            return;
        }
        if (clientTicks < 20 || minecraft.screen == null) {
            return;
        }
        if (!minecraft.getLevelSource().levelExists(WORLD_NAME)) {
            fail("copied E2E world was not visible in the configured game directory: " + WORLD_NAME);
            return;
        }

        phase = Phase.OPENING_WORLD;
        OffHandCombat.LOGGER.info("Opening copied Off Hand Combat E2E world: {}", WORLD_NAME);
        minecraft.createWorldOpenFlows().openWorld(WORLD_NAME,
                () -> fail("opening copied E2E world was aborted"));
    }

    private static void beginServerSetupWhenWorldIsReady(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null || minecraft.getConnection() == null
                || minecraft.getSingleplayerServer() == null
                || !minecraft.getConnection().hasChannel(OffhandAttackRequestPayload.TYPE)) {
            return;
        }

        phase = Phase.SETTING_UP_SERVER;
        OffHandCombat.LOGGER.info("Off Hand Combat client world loaded for E2E");
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
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WOODEN_SWORD));
                player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.IRON_SWORD));

                Mob target = EntityType.COW.create(player.serverLevel());
                if (target == null) {
                    fail("failed to create E2E target");
                    return;
                }
                target.setNoAi(true);
                target.setNoGravity(true);
                target.setInvulnerable(true);
                target.setPersistenceRequired();
                target.setHealth(target.getMaxHealth());
                target.moveTo(player.getX(), player.getY() + 2.5D, player.getZ(), 0.0F, 0.0F);
                if (!player.serverLevel().addFreshEntity(target)) {
                    fail("failed to add E2E target to the integrated server");
                    return;
                }

                targetId = target.getId();
                phase = Phase.WAITING_FOR_CLIENT_SYNC;
            } catch (Throwable throwable) {
                fail("integrated server setup exception", throwable);
            }
        });
    }

    private static void beginGuiSuppressionCheckWhenClientIsSynchronized(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null || minecraft.getSingleplayerServer() == null) {
            return;
        }
        Entity target = minecraft.level.getEntity(targetId);
        if (target == null || !minecraft.player.getOffhandItem().is(Items.IRON_SWORD)) {
            return;
        }

        minecraft.hitResult = new EntityHitResult(target);
        minecraft.setScreen(new Screen(Component.literal("Off Hand Combat E2E GUI suppression")) {
        });
        KeyMapping.click(ClientModEvents.OFFHAND_ATTACK.get().getKey());
        guiSuppressionDeadline = clientTicks + GUI_SUPPRESSION_TICKS;
        phase = Phase.WAITING_FOR_GUI_SUPPRESSION;
    }

    private static void verifyGuiSuppressionAndArmServer(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.getSingleplayerServer() == null
                || clientTicks < guiSuppressionDeadline) {
            return;
        }
        if (minecraft.player.getData(OffhandCombatAttachments.COMBAT_STATE).lastClientResult() != null) {
            fail("dedicated key produced a network result while a GUI was open");
            return;
        }

        minecraft.setScreen(null);
        phase = Phase.VERIFYING_GUI_SUPPRESSION;
        UUID playerId = minecraft.player.getUUID();
        var server = minecraft.getSingleplayerServer();
        server.execute(() -> {
            try {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                Entity target = player == null ? null : player.serverLevel().getEntity(targetId);
                if (player == null || !(target instanceof LivingEntity living)) {
                    fail("server state was unavailable after GUI suppression check");
                    return;
                }

                var state = player.getData(OffhandCombatAttachments.COMBAT_STATE);
                if (state.lastNetworkSequence() != 0L || state.lastNetworkResult() != null) {
                    fail("dedicated key sent a network request while a GUI was open");
                    return;
                }
                if (Float.compare(living.getHealth(), living.getMaxHealth()) != 0) {
                    fail("GUI-suppressed key changed target health");
                    return;
                }
                if (player.getOffhandItem().getDamageValue() != 0) {
                    fail("GUI-suppressed key consumed off-hand durability");
                    return;
                }

                living.setInvulnerable(false);
                OffHandCombat.LOGGER.info("Off Hand Combat client GUI suppression E2E passed");
                ((OffhandAttackAccess) player).ofc$setOffhandAttackStrengthTicker(100);
                phase = Phase.ARMED;
            } catch (Throwable throwable) {
                fail("GUI suppression verification exception", throwable);
            }
        });
    }

    private static void triggerDedicatedKeyAttack(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null || minecraft.screen != null) {
            return;
        }
        Entity target = minecraft.level.getEntity(targetId);
        if (target == null) {
            return;
        }

        minecraft.hitResult = new EntityHitResult(target);
        KeyMapping.click(ClientModEvents.OFFHAND_ATTACK.get().getKey());
        phase = Phase.WAITING_FOR_FIRST_RESULT;
    }

    private static void acceptFirstResultAndReplay(Minecraft minecraft) {
        if (minecraft.player == null) {
            return;
        }
        var state = minecraft.player.getData(OffhandCombatAttachments.COMBAT_STATE);
        OffhandAttackResult result = state.lastClientResult();
        if (result == null || result.targetId() != targetId) {
            return;
        }
        if (result.status() != OffhandAttackStatus.SUCCESS) {
            fail("first network result was " + result.status());
            return;
        }
        if (!(result.targetHealthAfter() < result.targetHealthBefore())) {
            fail("first network attack did not reduce target health");
            return;
        }
        if (result.durabilityAfter() - result.durabilityBefore() != 1) {
            fail("first network attack did not consume exactly one off-hand durability");
            return;
        }

        firstResult = result;
        state.setLastClientResult(OffhandAttackResult.rejected(
                Long.MAX_VALUE, -1, OffhandAttackStatus.INTERNAL_ERROR, result.gameTime()));
        PacketDistributor.sendToServer(new OffhandAttackRequestPayload(result.sequence(), result.targetId()));
        phase = Phase.WAITING_FOR_REPLAY_RESULT;
    }

    private static void verifyReplayResult(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.getSingleplayerServer() == null || firstResult == null) {
            return;
        }
        OffhandAttackResult replay = minecraft.player
                .getData(OffhandCombatAttachments.COMBAT_STATE)
                .lastClientResult();
        if (!firstResult.equals(replay)) {
            return;
        }

        phase = Phase.VERIFYING_SERVER_STATE;
        UUID playerId = minecraft.player.getUUID();
        var server = minecraft.getSingleplayerServer();
        server.execute(() -> {
            try {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                Entity target = player == null ? null : player.serverLevel().getEntity(targetId);
                if (player == null || !(target instanceof LivingEntity living)) {
                    fail("server state was unavailable after duplicate replay");
                    return;
                }
                if (Float.compare(living.getHealth(), firstResult.targetHealthAfter()) != 0) {
                    fail("duplicate payload changed target health a second time");
                    return;
                }
                if (player.getOffhandItem().getDamageValue() != firstResult.durabilityAfter()) {
                    fail("duplicate payload consumed off-hand durability a second time");
                    return;
                }

                phase = Phase.PASSED;
                OffHandCombat.LOGGER.info(
                        "Off Hand Combat client world E2E passed: sequence={}, target={}, health={} -> {}, durability={} -> {}",
                        firstResult.sequence(), firstResult.targetId(),
                        firstResult.targetHealthBefore(), firstResult.targetHealthAfter(),
                        firstResult.durabilityBefore(), firstResult.durabilityAfter());
            } catch (Throwable throwable) {
                fail("server verification exception", throwable);
            }
        });
    }

    private static void fail(String reason) {
        if (phase != Phase.FAILED) {
            phase = Phase.FAILED;
            OffHandCombat.LOGGER.error("Off Hand Combat client world E2E failed: {}", reason);
        }
    }

    private static void fail(String reason, Throwable throwable) {
        if (phase != Phase.FAILED) {
            phase = Phase.FAILED;
            OffHandCombat.LOGGER.error("Off Hand Combat client world E2E failed: {}", reason, throwable);
        }
    }

    private enum Phase {
        WAITING_FOR_WORLD,
        OPENING_WORLD,
        SETTING_UP_SERVER,
        WAITING_FOR_CLIENT_SYNC,
        WAITING_FOR_GUI_SUPPRESSION,
        VERIFYING_GUI_SUPPRESSION,
        ARMED,
        WAITING_FOR_FIRST_RESULT,
        WAITING_FOR_REPLAY_RESULT,
        VERIFYING_SERVER_STATE,
        PASSED,
        FAILED
    }
}
