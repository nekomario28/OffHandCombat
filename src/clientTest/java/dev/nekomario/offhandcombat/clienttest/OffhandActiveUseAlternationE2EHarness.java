package dev.nekomario.offhandcombat.clienttest;

import dev.nekomario.offhandcombat.OffHandCombat;
import dev.nekomario.offhandcombat.attachment.OffhandCombatAttachments;
import dev.nekomario.offhandcombat.network.OffhandAttackRequestPayload;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = OffHandCombat.MOD_ID, value = Dist.CLIENT)
public final class OffhandActiveUseAlternationE2EHarness {
    private static final String ENABLE_PROPERTY = "offhandcombat.activeUseAlternationE2E";
    private static final String WORLD_NAME = "AlternationWorld";
    private static final int TIMEOUT_CLIENT_TICKS = 1800;
    private static final int USE_TIMEOUT_TICKS = 100;
    private static final Item[] ITEMS = {
            Items.SHIELD,
            Items.BOW,
            Items.CROSSBOW,
            Items.TRIDENT
    };

    private static volatile Phase phase = Phase.WAITING_FOR_WORLD;
    private static volatile boolean serverObservedOffhandUse;
    private static volatile long baselineServerSequence;
    private static int itemIndex;
    private static int clientTicks;
    private static int phaseDeadline;

    private OffhandActiveUseAlternationE2EHarness() {
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
                case OPENING_WORLD -> setupWorld(minecraft);
                case PREPARING_ITEM -> prepareItem(minecraft);
                case WAITING_FOR_ITEM_SYNC -> startMainHandUse(minecraft);
                case WAITING_FOR_MAIN_USE -> observeMainHandUse(minecraft);
                case WAITING_FOR_MAIN_STOP -> startAlternatedUse(minecraft);
                case WAITING_FOR_OFFHAND_USE -> observeOffhandUse(minecraft);
                case WAITING_FOR_SERVER_OFFHAND -> finishItemAfterServerObservation(minecraft);
                default -> {
                }
            }
        } catch (Throwable throwable) {
            fail("alternation harness exception", throwable);
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
            fail("copied alternation E2E world was unavailable: " + WORLD_NAME);
            return;
        }

        phase = Phase.OPENING_WORLD;
        minecraft.createWorldOpenFlows().openWorld(
                WORLD_NAME,
                () -> fail("opening copied alternation E2E world was aborted"));
    }

    private static void setupWorld(Minecraft minecraft) {
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
                player.getInventory().setItem(0, new ItemStack(Items.ARROW, 64));
                baselineServerSequence = player.getData(OffhandCombatAttachments.COMBAT_STATE).lastNetworkSequence();
                itemIndex = 0;
                phase = Phase.PREPARING_ITEM;
            } catch (Throwable throwable) {
                fail("alternation server setup exception", throwable);
            }
        });
    }

    private static void prepareItem(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.getSingleplayerServer() == null || itemIndex >= ITEMS.length) {
            return;
        }

        phase = Phase.SETTING_ITEM;
        Item item = ITEMS[itemIndex];
        UUID playerId = minecraft.player.getUUID();
        var server = minecraft.getSingleplayerServer();
        server.execute(() -> {
            try {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player == null) {
                    fail("server player unavailable while preparing " + item);
                    return;
                }
                player.stopUsingItem();
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
                player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(item));
                player.getInventory().setItem(0, new ItemStack(Items.ARROW, 64));
                serverObservedOffhandUse = false;
                phaseDeadline = clientTicks + USE_TIMEOUT_TICKS;
                phase = Phase.WAITING_FOR_ITEM_SYNC;
            } catch (Throwable throwable) {
                fail("item setup exception for " + item, throwable);
            }
        });
    }

    private static void startMainHandUse(Minecraft minecraft) {
        Item item = ITEMS[itemIndex];
        if (!clientReadyForItem(minecraft, item) || minecraft.player.isUsingItem()) {
            if (clientTicks >= phaseDeadline) {
                fail("client did not synchronize both hands for " + item);
            }
            return;
        }

        pointAtMiss(minecraft);
        minecraft.options.keyUse.setDown(true);
        phaseDeadline = clientTicks + USE_TIMEOUT_TICKS;
        phase = Phase.WAITING_FOR_MAIN_USE;
    }

    private static void observeMainHandUse(Minecraft minecraft) {
        if (minecraft.player == null) {
            return;
        }
        if (minecraft.player.isUsingItem()) {
            if (minecraft.player.getUsedItemHand() != InteractionHand.MAIN_HAND) {
                minecraft.options.keyUse.setDown(false);
                fail("first use did not start in MAIN_HAND for " + ITEMS[itemIndex]);
                return;
            }
            minecraft.options.keyUse.setDown(false);
            phaseDeadline = clientTicks + USE_TIMEOUT_TICKS;
            phase = Phase.WAITING_FOR_MAIN_STOP;
            return;
        }
        if (clientTicks >= phaseDeadline) {
            minecraft.options.keyUse.setDown(false);
            fail("first right-click did not start MAIN_HAND use for " + ITEMS[itemIndex]);
        }
    }

    private static void startAlternatedUse(Minecraft minecraft) {
        if (minecraft.player == null) {
            return;
        }
        if (minecraft.player.isUsingItem()) {
            if (clientTicks >= phaseDeadline) {
                fail("MAIN_HAND use did not stop for " + ITEMS[itemIndex]);
            }
            return;
        }

        pointAtMiss(minecraft);
        minecraft.options.keyUse.setDown(true);
        phaseDeadline = clientTicks + USE_TIMEOUT_TICKS;
        phase = Phase.WAITING_FOR_OFFHAND_USE;
    }

    private static void observeOffhandUse(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.getSingleplayerServer() == null) {
            return;
        }
        if (minecraft.player.isUsingItem()) {
            if (minecraft.player.getUsedItemHand() != InteractionHand.OFF_HAND) {
                minecraft.options.keyUse.setDown(false);
                fail("recent MAIN_HAND use was not deferred to OFF_HAND for " + ITEMS[itemIndex]);
                return;
            }

            phase = Phase.WAITING_FOR_SERVER_OFFHAND;
            UUID playerId = minecraft.player.getUUID();
            Item item = ITEMS[itemIndex];
            var server = minecraft.getSingleplayerServer();
            server.execute(() -> {
                try {
                    ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                    if (player == null) {
                        fail("server player unavailable while observing OFF_HAND use");
                        return;
                    }
                    if (!player.isUsingItem()
                            || player.getUsedItemHand() != InteractionHand.OFF_HAND
                            || !player.getUseItem().is(item)) {
                        fail("server did not observe OFF_HAND active use for " + item);
                        return;
                    }
                    if (player.getData(OffhandCombatAttachments.COMBAT_STATE).lastNetworkSequence()
                            != baselineServerSequence) {
                        fail("active-use alternation emitted an Off Hand Combat attack request");
                        return;
                    }
                    serverObservedOffhandUse = true;
                } catch (Throwable throwable) {
                    fail("server OFF_HAND observation exception", throwable);
                }
            });
            return;
        }
        if (clientTicks >= phaseDeadline) {
            minecraft.options.keyUse.setDown(false);
            fail("second right-click did not start OFF_HAND use for " + ITEMS[itemIndex]);
        }
    }

    private static void finishItemAfterServerObservation(Minecraft minecraft) {
        if (!serverObservedOffhandUse || minecraft.player == null) {
            return;
        }
        minecraft.options.keyUse.setDown(false);
        OffHandCombat.LOGGER.info(
                "Off Hand Combat active-hand alternation E2E passed for {}: MAIN_HAND -> OFF_HAND",
                ITEMS[itemIndex]);
        itemIndex++;
        if (itemIndex >= ITEMS.length) {
            phase = Phase.PASSED;
            OffHandCombat.LOGGER.info(
                    "Off Hand Combat active-hand alternation E2E passed: shield, bow, crossbow and trident");
        } else {
            phase = Phase.PREPARING_ITEM;
        }
    }

    private static boolean clientReadyForItem(Minecraft minecraft, Item item) {
        return minecraft.level != null
                && minecraft.player != null
                && minecraft.screen == null
                && minecraft.getConnection() != null
                && minecraft.player.getMainHandItem().is(item)
                && minecraft.player.getOffhandItem().is(item);
    }

    private static void pointAtMiss(Minecraft minecraft) {
        Vec3 view = minecraft.player.getViewVector(1.0F);
        Vec3 missLocation = minecraft.player.getEyePosition().add(view.scale(5.0D));
        minecraft.hitResult = BlockHitResult.miss(
                missLocation,
                Direction.getNearest(view),
                minecraft.player.blockPosition());
    }

    private static void fail(String reason) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options != null) {
            minecraft.options.keyUse.setDown(false);
        }
        if (phase != Phase.FAILED) {
            phase = Phase.FAILED;
            OffHandCombat.LOGGER.error("Off Hand Combat active-hand alternation E2E failed: {}", reason);
        }
    }

    private static void fail(String reason, Throwable throwable) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options != null) {
            minecraft.options.keyUse.setDown(false);
        }
        if (phase != Phase.FAILED) {
            phase = Phase.FAILED;
            OffHandCombat.LOGGER.error("Off Hand Combat active-hand alternation E2E failed: {}", reason, throwable);
        }
    }

    private enum Phase {
        WAITING_FOR_WORLD,
        OPENING_WORLD,
        SETTING_UP,
        PREPARING_ITEM,
        SETTING_ITEM,
        WAITING_FOR_ITEM_SYNC,
        WAITING_FOR_MAIN_USE,
        WAITING_FOR_MAIN_STOP,
        WAITING_FOR_OFFHAND_USE,
        WAITING_FOR_SERVER_OFFHAND,
        PASSED,
        FAILED
    }
}
