package dev.nekomario.offhandcombat.clienttest;

import dev.nekomario.offhandcombat.OffHandCombat;
import dev.nekomario.offhandcombat.attachment.OffhandCombatAttachments;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = OffHandCombat.MOD_ID, value = Dist.CLIENT)
public final class OffhandVillagerPriorityE2EHarness {
    private static final String ENABLE_PROPERTY = "offhandcombat.villagerPriorityE2E";
    private static final String WORLD_NAME = "VillagerWorld";
    private static final int TIMEOUT_CLIENT_TICKS = 1200;
    private static final int RESULT_TIMEOUT_TICKS = 100;

    private static volatile Phase phase = Phase.WAITING_FOR_WORLD;
    private static volatile int villagerId = -1;
    private static volatile long baselineSequence;
    private static int clientTicks;
    private static int deadline;

    private OffhandVillagerPriorityE2EHarness() {
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
                case WAITING_FOR_VILLAGER_SYNC -> triggerTrade(minecraft);
                case WAITING_FOR_TRADE_SCREEN -> verifyTradeScreen(minecraft);
                default -> {
                }
            }
        } catch (Throwable throwable) {
            fail("villager priority harness exception", throwable);
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
            fail("copied villager E2E world was unavailable: " + WORLD_NAME);
            return;
        }
        phase = Phase.OPENING_WORLD;
        minecraft.createWorldOpenFlows().openWorld(
                WORLD_NAME,
                () -> fail("opening copied villager E2E world was aborted"));
    }

    private static void setupWhenReady(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null || minecraft.getSingleplayerServer() == null) {
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
                baselineSequence = player.getData(OffhandCombatAttachments.COMBAT_STATE).lastNetworkSequence();

                Villager villager = EntityType.VILLAGER.create(player.serverLevel());
                if (villager == null) {
                    fail("failed to create villager");
                    return;
                }
                villager.setNoAi(true);
                villager.setInvulnerable(true);
                villager.setPersistenceRequired();
                villager.moveTo(player.getX(), player.getY(), player.getZ() + 2.0D, 180.0F, 0.0F);
                MerchantOffers offers = new MerchantOffers();
                offers.add(new MerchantOffer(
                        new ItemCost(Items.EMERALD, 1),
                        new ItemStack(Items.BREAD),
                        10,
                        1,
                        0.05F));
                villager.setOffers(offers);
                if (!player.serverLevel().addFreshEntity(villager)) {
                    fail("failed to add villager to the integrated server");
                    return;
                }
                villagerId = villager.getId();
                deadline = clientTicks + RESULT_TIMEOUT_TICKS;
                phase = Phase.WAITING_FOR_VILLAGER_SYNC;
            } catch (Throwable throwable) {
                fail("villager server setup exception", throwable);
            }
        });
    }

    private static void triggerTrade(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null || minecraft.gameMode == null
                || minecraft.screen != null) {
            return;
        }
        Entity entity = minecraft.level.getEntity(villagerId);
        if (!(entity instanceof Villager villager)) {
            if (clientTicks >= deadline) {
                fail("villager did not synchronize to the client");
            }
            return;
        }

        minecraft.hitResult = new EntityHitResult(villager);
        var result = minecraft.gameMode.interact(
                minecraft.player,
                villager,
                InteractionHand.MAIN_HAND);
        OffHandCombat.LOGGER.info(
                "Off Hand Combat villager priority vanilla result: {}", result);
        deadline = clientTicks + RESULT_TIMEOUT_TICKS;
        phase = Phase.WAITING_FOR_TRADE_SCREEN;
    }

    private static void verifyTradeScreen(Minecraft minecraft) {
        if (minecraft.screen instanceof MerchantScreen) {
            verifyServerState(minecraft);
            return;
        }
        if (clientTicks >= deadline) {
            fail("villager trade screen did not open");
        }
    }

    private static void verifyServerState(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.getSingleplayerServer() == null) {
            fail("integrated server was unavailable while verifying villager trade");
            return;
        }
        phase = Phase.VERIFYING;
        UUID playerId = minecraft.player.getUUID();
        var server = minecraft.getSingleplayerServer();
        server.execute(() -> {
            try {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player == null) {
                    fail("server player was unavailable while verifying villager trade");
                    return;
                }
                var state = player.getData(OffhandCombatAttachments.COMBAT_STATE);
                if (state.lastNetworkSequence() != baselineSequence) {
                    fail("villager interaction emitted an Off Hand Combat network request");
                    return;
                }
                if (player.getOffhandItem().getDamageValue() != 0) {
                    fail("villager interaction consumed off-hand durability");
                    return;
                }
                player.closeContainer();
                phase = Phase.PASSED;
                OffHandCombat.LOGGER.info(
                        "Off Hand Combat villager trading priority E2E passed: trade screen opened, sequence unchanged, durability unchanged");
            } catch (Throwable throwable) {
                fail("villager trade server verification exception", throwable);
            }
        });
    }

    private static void fail(String reason) {
        if (phase != Phase.FAILED) {
            phase = Phase.FAILED;
            OffHandCombat.LOGGER.error("Off Hand Combat villager trading priority E2E failed: {}", reason);
        }
    }

    private static void fail(String reason, Throwable throwable) {
        if (phase != Phase.FAILED) {
            phase = Phase.FAILED;
            OffHandCombat.LOGGER.error(
                    "Off Hand Combat villager trading priority E2E failed: {}", reason, throwable);
        }
    }

    private enum Phase {
        WAITING_FOR_WORLD,
        OPENING_WORLD,
        SETTING_UP,
        WAITING_FOR_VILLAGER_SYNC,
        WAITING_FOR_TRADE_SCREEN,
        VERIFYING,
        PASSED,
        FAILED
    }
}
