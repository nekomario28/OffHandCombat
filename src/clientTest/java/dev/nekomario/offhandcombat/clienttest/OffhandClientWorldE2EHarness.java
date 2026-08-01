package dev.nekomario.offhandcombat.clienttest;

import dev.nekomario.offhandcombat.OffHandCombat;
import dev.nekomario.offhandcombat.api.OffhandAttackAccess;
import dev.nekomario.offhandcombat.api.OffhandAttackResult;
import dev.nekomario.offhandcombat.api.OffhandAttackStatus;
import dev.nekomario.offhandcombat.api.OffhandInputArbitrationRegistry;
import dev.nekomario.offhandcombat.api.OffhandInputArbitrationRule;
import dev.nekomario.offhandcombat.attachment.OffhandCombatAttachments;
import dev.nekomario.offhandcombat.client.ClientModEvents;
import dev.nekomario.offhandcombat.network.OffhandAttackRequestPayload;
import java.util.UUID;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
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
    private static final int TIMEOUT_CLIENT_TICKS = 3000;
    private static final int GUI_SUPPRESSION_TICKS = 20;
    private static final int ARBITRATION_SUPPRESSION_TICKS = 20;
    private static final int ACTIVE_USE_TIMEOUT_TICKS = 80;
    private static final int ACTIVE_USE_SETTLE_TICKS = 5;
    private static final int RAPID_CLICK_TIMEOUT_TICKS = 80;
    private static final Item[] ACTIVE_USE_ITEMS = {
            Items.SHIELD,
            Items.BOW,
            Items.COOKED_BEEF,
            Items.POTION
    };
    private static final ResourceLocation ARBITRATION_RULE_ID = ResourceLocation.fromNamespaceAndPath(
            OffHandCombat.MOD_ID, "client_e2e_deny");

    private static volatile Phase phase = Phase.WAITING_FOR_WORLD;
    private static volatile int targetId = -1;
    private static volatile int rapidTargetId = -1;
    private static volatile OffhandAttackResult firstResult;
    private static volatile int activeUseIndex;
    private static volatile float rapidHealthBefore;
    private static int clientTicks;
    private static int guiSuppressionDeadline;
    private static int arbitrationSuppressionDeadline;
    private static int activeUseDeadline;
    private static int rapidClickDeadline;

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
                verifyGuiSuppression(minecraft);
            } else if (phase == Phase.WAITING_TO_TRIGGER_ARBITRATION_DENIAL) {
                beginArbitrationDenialCheck(minecraft);
            } else if (phase == Phase.WAITING_FOR_ARBITRATION_DENIAL) {
                verifyArbitrationDenialAndBeginUsePriority(minecraft);
            } else if (phase == Phase.PREPARING_ACTIVE_USE) {
                prepareActiveUseItem(minecraft);
            } else if (phase == Phase.WAITING_FOR_ACTIVE_USE_SYNC) {
                triggerActiveUseItem(minecraft);
            } else if (phase == Phase.WAITING_FOR_ACTIVE_USE) {
                observeActiveUseItem(minecraft);
            } else if (phase == Phase.WAITING_FOR_ACTIVE_USE_SETTLE) {
                verifyActiveUseItem(minecraft);
            } else if (phase == Phase.ARMED) {
                triggerDedicatedKeyAttack(minecraft);
            } else if (phase == Phase.WAITING_FOR_FIRST_RESULT) {
                acceptFirstResultAndReplay(minecraft);
            } else if (phase == Phase.WAITING_FOR_REPLAY_RESULT) {
                verifyReplayResultAndPrepareRapidClick(minecraft);
            } else if (phase == Phase.PREPARING_RAPID_CLICK) {
                prepareRapidClick(minecraft);
            } else if (phase == Phase.WAITING_FOR_RAPID_CLICK_SYNC) {
                triggerRapidClick(minecraft);
            } else if (phase == Phase.WAITING_FOR_RAPID_CLICK_RESULT) {
                verifyRapidClick(minecraft);
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
                player.getFoodData().setFoodLevel(10);
                player.getInventory().setItem(0, new ItemStack(Items.ARROW, 64));

                Mob target = createTarget(player);
                if (target == null) {
                    return;
                }
                target.setInvulnerable(true);
                targetId = target.getId();
                phase = Phase.WAITING_FOR_CLIENT_SYNC;
            } catch (Throwable throwable) {
                fail("integrated server setup exception", throwable);
            }
        });
    }

    private static Mob createTarget(ServerPlayer player) {
        Mob target = EntityType.COW.create(player.serverLevel());
        if (target == null) {
            fail("failed to create E2E target");
            return null;
        }
        target.setNoAi(true);
        target.setNoGravity(true);
        target.setPersistenceRequired();
        target.setHealth(target.getMaxHealth());
        target.moveTo(player.getX(), player.getY() + 2.5D, player.getZ(), 0.0F, 0.0F);
        if (!player.serverLevel().addFreshEntity(target)) {
            fail("failed to add E2E target to the integrated server");
            return null;
        }
        return target;
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

    private static void verifyGuiSuppression(Minecraft minecraft) {
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

                verifyNoNetworkAttack(player, living, "GUI-suppressed key");
                OffHandCombat.LOGGER.info("Off Hand Combat client GUI suppression E2E passed");
                phase = Phase.WAITING_TO_TRIGGER_ARBITRATION_DENIAL;
            } catch (Throwable throwable) {
                fail("GUI suppression verification exception", throwable);
            }
        });
    }

    private static void beginArbitrationDenialCheck(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null || minecraft.screen != null
                || minecraft.getSingleplayerServer() == null) {
            return;
        }
        Entity target = minecraft.level.getEntity(targetId);
        if (target == null) {
            return;
        }

        OffhandInputArbitrationRegistry.register(
                ARBITRATION_RULE_ID,
                Integer.MAX_VALUE,
                context -> OffhandInputArbitrationRule.Decision.DENY);
        minecraft.hitResult = new EntityHitResult(target);
        KeyMapping.click(ClientModEvents.OFFHAND_ATTACK.get().getKey());
        arbitrationSuppressionDeadline = clientTicks + ARBITRATION_SUPPRESSION_TICKS;
        phase = Phase.WAITING_FOR_ARBITRATION_DENIAL;
    }

    private static void verifyArbitrationDenialAndBeginUsePriority(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.getSingleplayerServer() == null
                || clientTicks < arbitrationSuppressionDeadline) {
            return;
        }
        if (minecraft.player.getData(OffhandCombatAttachments.COMBAT_STATE).lastClientResult() != null) {
            fail("input-arbitration DENY produced a client network result");
            return;
        }

        OffhandInputArbitrationRegistry.register(
                ARBITRATION_RULE_ID,
                Integer.MAX_VALUE,
                context -> OffhandInputArbitrationRule.Decision.PASS);
        phase = Phase.VERIFYING_ARBITRATION_DENIAL;
        UUID playerId = minecraft.player.getUUID();
        var server = minecraft.getSingleplayerServer();
        server.execute(() -> {
            try {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                Entity target = player == null ? null : player.serverLevel().getEntity(targetId);
                if (player == null || !(target instanceof LivingEntity living)) {
                    fail("server state was unavailable after input-arbitration denial check");
                    return;
                }

                verifyNoNetworkAttack(player, living, "input-arbitration DENY");
                OffHandCombat.LOGGER.info("Off Hand Combat client arbitration denial E2E passed");
                activeUseIndex = 0;
                phase = Phase.PREPARING_ACTIVE_USE;
            } catch (Throwable throwable) {
                fail("input-arbitration denial verification exception", throwable);
            }
        });
    }

    private static void prepareActiveUseItem(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.getSingleplayerServer() == null
                || activeUseIndex < 0 || activeUseIndex >= ACTIVE_USE_ITEMS.length) {
            return;
        }

        phase = Phase.SETTING_ACTIVE_USE;
        UUID playerId = minecraft.player.getUUID();
        Item item = ACTIVE_USE_ITEMS[activeUseIndex];
        var server = minecraft.getSingleplayerServer();
        server.execute(() -> {
            try {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                Entity target = player == null ? null : player.serverLevel().getEntity(targetId);
                if (player == null || !(target instanceof LivingEntity living)) {
                    fail("server state was unavailable while preparing active-use item " + item);
                    return;
                }
                player.stopUsingItem();
                player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(item));
                if (item == Items.COOKED_BEEF) {
                    player.getFoodData().setFoodLevel(10);
                }
                verifyNoNetworkAttack(player, living, "active-use setup");
                phase = Phase.WAITING_FOR_ACTIVE_USE_SYNC;
            } catch (Throwable throwable) {
                fail("active-use setup exception for " + item, throwable);
            }
        });
    }

    private static void triggerActiveUseItem(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null || minecraft.screen != null) {
            return;
        }
        Item item = ACTIVE_USE_ITEMS[activeUseIndex];
        Entity target = minecraft.level.getEntity(targetId);
        if (target == null || !minecraft.player.getOffhandItem().is(item) || minecraft.player.isUsingItem()) {
            return;
        }
        if (item == Items.BOW && minecraft.player.getProjectile(minecraft.player.getOffhandItem()).isEmpty()) {
            return;
        }
        if (item == Items.COOKED_BEEF && !minecraft.player.canEat(false)) {
            return;
        }

        minecraft.hitResult = new EntityHitResult(target);
        KeyMapping.click(minecraft.options.keyUse.getKey());
        activeUseDeadline = clientTicks + ACTIVE_USE_TIMEOUT_TICKS;
        phase = Phase.WAITING_FOR_ACTIVE_USE;
    }

    private static void observeActiveUseItem(Minecraft minecraft) {
        if (minecraft.player == null) {
            return;
        }
        if (minecraft.player.isUsingItem()
                && minecraft.player.getUsedItemHand() == InteractionHand.OFF_HAND) {
            activeUseDeadline = clientTicks + ACTIVE_USE_SETTLE_TICKS;
            phase = Phase.WAITING_FOR_ACTIVE_USE_SETTLE;
            return;
        }
        if (clientTicks >= activeUseDeadline) {
            fail("right-click did not start normal off-hand use for " + ACTIVE_USE_ITEMS[activeUseIndex]);
        }
    }

    private static void verifyActiveUseItem(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.getSingleplayerServer() == null
                || clientTicks < activeUseDeadline) {
            return;
        }

        phase = Phase.VERIFYING_ACTIVE_USE;
        UUID playerId = minecraft.player.getUUID();
        Item item = ACTIVE_USE_ITEMS[activeUseIndex];
        var server = minecraft.getSingleplayerServer();
        server.execute(() -> {
            try {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                Entity target = player == null ? null : player.serverLevel().getEntity(targetId);
                if (player == null || !(target instanceof LivingEntity living)) {
                    fail("server state was unavailable while verifying active-use item " + item);
                    return;
                }
                if (!player.isUsingItem() || player.getUsedItemHand() != InteractionHand.OFF_HAND) {
                    fail("server did not observe normal off-hand use for " + item);
                    return;
                }
                verifyNoNetworkAttack(player, living, "normal use of " + item);
                player.stopUsingItem();
                OffHandCombat.LOGGER.info(
                        "Off Hand Combat right-click priority E2E passed for active-use item {}", item);

                activeUseIndex++;
                if (activeUseIndex < ACTIVE_USE_ITEMS.length) {
                    phase = Phase.PREPARING_ACTIVE_USE;
                } else {
                    player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.IRON_SWORD));
                    ((OffhandAttackAccess) player).ofc$setOffhandAttackStrengthTicker(100);
                    OffHandCombat.LOGGER.info(
                            "Off Hand Combat active-use priority E2E passed: shield, bow, food and potion");
                    phase = Phase.ARMED;
                }
            } catch (Throwable throwable) {
                fail("active-use verification exception for " + item, throwable);
            }
        });
    }

    private static void triggerDedicatedKeyAttack(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null || minecraft.screen != null
                || !minecraft.player.getOffhandItem().is(Items.IRON_SWORD)) {
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

    private static void verifyReplayResultAndPrepareRapidClick(Minecraft minecraft) {
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

                OffHandCombat.LOGGER.info(
                        "Off Hand Combat client attack and replay E2E passed: sequence={}, target={}, health={} -> {}, durability={} -> {}",
                        firstResult.sequence(), firstResult.targetId(),
                        firstResult.targetHealthBefore(), firstResult.targetHealthAfter(),
                        firstResult.durabilityBefore(), firstResult.durabilityAfter());
                phase = Phase.PREPARING_RAPID_CLICK;
            } catch (Throwable throwable) {
                fail("server verification exception", throwable);
            }
        });
    }

    private static void prepareRapidClick(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.getSingleplayerServer() == null) {
            return;
        }

        phase = Phase.SETTING_UP_RAPID_CLICK;
        UUID playerId = minecraft.player.getUUID();
        var server = minecraft.getSingleplayerServer();
        server.execute(() -> {
            try {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player == null) {
                    fail("server player was unavailable while preparing rapid-click E2E");
                    return;
                }
                Mob target = createTarget(player);
                if (target == null) {
                    return;
                }
                target.setInvulnerable(false);
                rapidTargetId = target.getId();
                rapidHealthBefore = target.getHealth();
                player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.IRON_SWORD));
                ((OffhandAttackAccess) player).ofc$setOffhandAttackStrengthTicker(100);
                player.getData(OffhandCombatAttachments.COMBAT_STATE).setLastClientResult(
                        OffhandAttackResult.rejected(
                                Long.MAX_VALUE, -1, OffhandAttackStatus.INTERNAL_ERROR,
                                player.level().getGameTime()));
                phase = Phase.WAITING_FOR_RAPID_CLICK_SYNC;
            } catch (Throwable throwable) {
                fail("rapid-click setup exception", throwable);
            }
        });
    }

    private static void triggerRapidClick(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null || minecraft.screen != null
                || !minecraft.player.getOffhandItem().is(Items.IRON_SWORD)) {
            return;
        }
        Entity target = minecraft.level.getEntity(rapidTargetId);
        if (target == null) {
            return;
        }

        minecraft.hitResult = new EntityHitResult(target);
        KeyMapping.click(minecraft.options.keyUse.getKey());
        KeyMapping.click(minecraft.options.keyUse.getKey());
        rapidClickDeadline = clientTicks + RAPID_CLICK_TIMEOUT_TICKS;
        phase = Phase.WAITING_FOR_RAPID_CLICK_RESULT;
    }

    private static void verifyRapidClick(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.getSingleplayerServer() == null) {
            return;
        }
        OffhandAttackResult result = minecraft.player
                .getData(OffhandCombatAttachments.COMBAT_STATE)
                .lastClientResult();
        if (result == null || result.targetId() != rapidTargetId
                || result.status() != OffhandAttackStatus.RATE_LIMITED) {
            if (clientTicks >= rapidClickDeadline) {
                fail("two physical right-clicks did not produce a RATE_LIMITED second result; last=" + result);
            }
            return;
        }

        phase = Phase.VERIFYING_RAPID_CLICK;
        UUID playerId = minecraft.player.getUUID();
        var server = minecraft.getSingleplayerServer();
        server.execute(() -> {
            try {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                Entity target = player == null ? null : player.serverLevel().getEntity(rapidTargetId);
                if (player == null || !(target instanceof LivingEntity living)) {
                    fail("server state was unavailable after rapid-click E2E");
                    return;
                }
                OffhandAttackResult serverResult = player
                        .getData(OffhandCombatAttachments.COMBAT_STATE)
                        .lastNetworkResult();
                if (serverResult == null || serverResult.status() != OffhandAttackStatus.RATE_LIMITED
                        || serverResult.sequence() != result.sequence()) {
                    fail("server did not preserve the rapid-click RATE_LIMITED result");
                    return;
                }
                if (!(living.getHealth() < rapidHealthBefore)) {
                    fail("the first rapid-click request did not execute");
                    return;
                }
                if (player.getOffhandItem().getDamageValue() != 1) {
                    fail("rapid-click burst consumed off-hand durability more or less than once");
                    return;
                }

                phase = Phase.PASSED;
                OffHandCombat.LOGGER.info(
                        "Off Hand Combat physical rapid-click E2E passed: finalSequence={}, secondStatus={}, health={} -> {}, durability=1",
                        result.sequence(), result.status(), rapidHealthBefore, living.getHealth());
                OffHandCombat.LOGGER.info(
                        "Off Hand Combat client world E2E passed: use priority, GUI/arbitration suppression, attack replay and rapid-click rate limiting");
            } catch (Throwable throwable) {
                fail("rapid-click server verification exception", throwable);
            }
        });
    }

    private static void verifyNoNetworkAttack(
            ServerPlayer player,
            LivingEntity target,
            String context) {
        var state = player.getData(OffhandCombatAttachments.COMBAT_STATE);
        if (state.lastNetworkSequence() != 0L || state.lastNetworkResult() != null) {
            throw new IllegalStateException(context + " sent a network request");
        }
        if (Float.compare(target.getHealth(), target.getMaxHealth()) != 0) {
            throw new IllegalStateException(context + " changed target health");
        }
        if (player.getOffhandItem().isDamageableItem() && player.getOffhandItem().getDamageValue() != 0) {
            throw new IllegalStateException(context + " consumed off-hand durability");
        }
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
        WAITING_TO_TRIGGER_ARBITRATION_DENIAL,
        WAITING_FOR_ARBITRATION_DENIAL,
        VERIFYING_ARBITRATION_DENIAL,
        PREPARING_ACTIVE_USE,
        SETTING_ACTIVE_USE,
        WAITING_FOR_ACTIVE_USE_SYNC,
        WAITING_FOR_ACTIVE_USE,
        WAITING_FOR_ACTIVE_USE_SETTLE,
        VERIFYING_ACTIVE_USE,
        ARMED,
        WAITING_FOR_FIRST_RESULT,
        WAITING_FOR_REPLAY_RESULT,
        VERIFYING_SERVER_STATE,
        PREPARING_RAPID_CLICK,
        SETTING_UP_RAPID_CLICK,
        WAITING_FOR_RAPID_CLICK_SYNC,
        WAITING_FOR_RAPID_CLICK_RESULT,
        VERIFYING_RAPID_CLICK,
        PASSED,
        FAILED
    }
}
