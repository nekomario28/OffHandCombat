package dev.nekomario.offhandcombat.clienttest;

import dev.nekomario.offhandcombat.OffHandCombat;
import dev.nekomario.offhandcombat.attachment.OffhandCombatAttachments;
import dev.nekomario.offhandcombat.attachment.OffhandCombatState;
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
    private static final int TIMEOUT_TICKS = 1800;
    private static final int PHASE_TIMEOUT_TICKS = 100;
    private static final int SETTLE_TICKS = 5;
    private static final Item[] ITEMS = {Items.SHIELD, Items.BOW, Items.CROSSBOW, Items.TRIDENT};

    private static volatile Phase phase = Phase.WAITING_FOR_WORLD;
    private static volatile long baselineSequence;
    private static volatile boolean serverObservedOffhandUse;
    private static volatile boolean serverCheckPending;
    private static int clientTicks;
    private static int deadline;
    private static int itemIndex;

    private OffhandActiveUseAlternationE2EHarness() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean(ENABLE_PROPERTY) || phase == Phase.PASSED || phase == Phase.FAILED) {
            return;
        }
        try {
            clientTicks++;
            if (clientTicks > TIMEOUT_TICKS) {
                fail("global timeout in " + phase);
                return;
            }
            Minecraft minecraft = Minecraft.getInstance();
            switch (phase) {
                case WAITING_FOR_WORLD -> openWorld(minecraft);
                case OPENING_WORLD -> setup(minecraft);
                case PREPARE -> prepare(minecraft);
                case WAIT_SYNC -> waitSyncAndStartMain(minecraft);
                case WAIT_MAIN -> observeMainAndRelease(minecraft);
                case START_SECOND -> startSecond(minecraft);
                case WAIT_OFF -> observeOffhand(minecraft);
                case WAIT_SERVER -> pollServer(minecraft);
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
            fail("copied alternation world unavailable");
            return;
        }
        phase = Phase.OPENING_WORLD;
        minecraft.createWorldOpenFlows().openWorld(WORLD_NAME, () -> fail("opening alternation world aborted"));
    }

    private static void setup(Minecraft minecraft) {
        if (!clientReady(minecraft) || minecraft.getSingleplayerServer() == null) {
            return;
        }
        phase = Phase.SETUP_SERVER;
        UUID id = minecraft.player.getUUID();
        var server = minecraft.getSingleplayerServer();
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null) {
                fail("server player unavailable during setup");
                return;
            }
            player.setGameMode(GameType.SURVIVAL);
            player.getInventory().setItem(1, new ItemStack(Items.ARROW, 64));
            baselineSequence = player.getData(OffhandCombatAttachments.COMBAT_STATE).lastNetworkSequence();
            itemIndex = 0;
            phase = Phase.PREPARE;
        });
    }

    private static void prepare(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.getSingleplayerServer() == null) {
            return;
        }
        phase = Phase.PREPARE_SERVER;
        Item item = ITEMS[itemIndex];
        UUID id = minecraft.player.getUUID();
        var server = minecraft.getSingleplayerServer();
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null) {
                fail("server player unavailable preparing " + item);
                return;
            }
            player.stopUsingItem();
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
            player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(item));
            player.getInventory().setItem(1, new ItemStack(Items.ARROW, 64));
            serverObservedOffhandUse = false;
            serverCheckPending = false;
            deadline = clientTicks + PHASE_TIMEOUT_TICKS;
            phase = Phase.WAIT_SYNC;
        });
    }

    private static void waitSyncAndStartMain(Minecraft minecraft) {
        Item item = ITEMS[itemIndex];
        if (minecraft.player == null
                || minecraft.screen != null
                || !minecraft.player.getMainHandItem().is(item)
                || !minecraft.player.getOffhandItem().is(item)
                || minecraft.player.isUsingItem()) {
            timeout("hand sync for " + item);
            return;
        }
        if (clientTicks + SETTLE_TICKS >= deadline) {
            fail("not enough settle window for " + item);
            return;
        }
        OffhandCombatState state = minecraft.player.getData(OffhandCombatAttachments.COMBAT_STATE);
        if (state.shouldDeferRecentlyUsedHand(InteractionHand.MAIN_HAND, 3)) {
            return;
        }
        pointAtMiss(minecraft);
        minecraft.options.keyUse.setDown(true);
        deadline = clientTicks + PHASE_TIMEOUT_TICKS;
        phase = Phase.WAIT_MAIN;
    }

    private static void observeMainAndRelease(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.gameMode == null) {
            return;
        }
        if (!minecraft.player.isUsingItem()) {
            timeout("MAIN_HAND use for " + ITEMS[itemIndex]);
            return;
        }
        if (minecraft.player.getUsedItemHand() != InteractionHand.MAIN_HAND) {
            fail("first use was not MAIN_HAND for " + ITEMS[itemIndex]);
            return;
        }

        minecraft.options.keyUse.setDown(false);
        minecraft.gameMode.releaseUsingItem(minecraft.player);
        OffhandCombatState state = minecraft.player.getData(OffhandCombatAttachments.COMBAT_STATE);
        if (!state.shouldDeferRecentlyUsedHand(InteractionHand.MAIN_HAND, 3)) {
            fail("client did not record MAIN_HAND release for " + ITEMS[itemIndex]);
            return;
        }
        if (state.shouldDeferRecentlyUsedHand(InteractionHand.OFF_HAND, 3)) {
            fail("client incorrectly marked OFF_HAND as recently used for " + ITEMS[itemIndex]);
            return;
        }
        deadline = clientTicks + PHASE_TIMEOUT_TICKS;
        phase = Phase.START_SECOND;
    }

    private static void startSecond(Minecraft minecraft) {
        if (minecraft.player == null) {
            return;
        }
        if (minecraft.player.isUsingItem()) {
            timeout("MAIN_HAND release for " + ITEMS[itemIndex]);
            return;
        }
        OffhandCombatState state = minecraft.player.getData(OffhandCombatAttachments.COMBAT_STATE);
        if (!state.shouldDeferRecentlyUsedHand(InteractionHand.MAIN_HAND, 3)) {
            fail("MAIN_HAND alternation window expired before second click for " + ITEMS[itemIndex]);
            return;
        }
        pointAtMiss(minecraft);
        minecraft.options.keyUse.setDown(true);
        deadline = clientTicks + PHASE_TIMEOUT_TICKS;
        phase = Phase.WAIT_OFF;
    }

    private static void observeOffhand(Minecraft minecraft) {
        if (minecraft.player == null) {
            return;
        }
        if (!minecraft.player.isUsingItem()) {
            timeout("OFF_HAND use for " + ITEMS[itemIndex]);
            return;
        }
        if (minecraft.player.getUsedItemHand() != InteractionHand.OFF_HAND) {
            fail("recorded MAIN_HAND release was not deferred to OFF_HAND for " + ITEMS[itemIndex]);
            return;
        }
        deadline = clientTicks + PHASE_TIMEOUT_TICKS;
        phase = Phase.WAIT_SERVER;
    }

    private static void pollServer(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.getSingleplayerServer() == null) {
            return;
        }
        if (serverObservedOffhandUse) {
            minecraft.options.keyUse.setDown(false);
            minecraft.gameMode.releaseUsingItem(minecraft.player);
            OffHandCombat.LOGGER.info(
                    "Off Hand Combat active-hand alternation E2E passed for {}: MAIN_HAND -> OFF_HAND",
                    ITEMS[itemIndex]);
            itemIndex++;
            if (itemIndex == ITEMS.length) {
                phase = Phase.PASSED;
                OffHandCombat.LOGGER.info(
                        "Off Hand Combat active-hand alternation E2E passed: shield, bow, crossbow and trident");
            } else {
                phase = Phase.PREPARE;
            }
            return;
        }
        if (clientTicks >= deadline) {
            fail("server did not observe OFF_HAND use for " + ITEMS[itemIndex]);
            return;
        }
        if (serverCheckPending) {
            return;
        }

        serverCheckPending = true;
        UUID id = minecraft.player.getUUID();
        Item item = ITEMS[itemIndex];
        var server = minecraft.getSingleplayerServer();
        server.execute(() -> {
            try {
                ServerPlayer player = server.getPlayerList().getPlayer(id);
                if (player == null) {
                    fail("server player unavailable while polling OFF_HAND");
                    return;
                }
                if (player.getData(OffhandCombatAttachments.COMBAT_STATE).lastNetworkSequence() != baselineSequence) {
                    fail("alternation emitted an Off Hand Combat attack request");
                    return;
                }
                serverObservedOffhandUse = player.isUsingItem()
                        && player.getUsedItemHand() == InteractionHand.OFF_HAND
                        && player.getUseItem().is(item);
            } finally {
                serverCheckPending = false;
            }
        });
    }

    private static boolean clientReady(Minecraft minecraft) {
        return minecraft.level != null
                && minecraft.player != null
                && minecraft.getConnection() != null
                && minecraft.getConnection().hasChannel(OffhandAttackRequestPayload.TYPE);
    }

    private static void pointAtMiss(Minecraft minecraft) {
        minecraft.player.setXRot(-90.0F);
        Vec3 view = minecraft.player.getViewVector(1.0F);
        Vec3 point = minecraft.player.getEyePosition().add(view.scale(5.0D));
        minecraft.hitResult = BlockHitResult.miss(point, Direction.getNearest(view), minecraft.player.blockPosition());
    }

    private static void timeout(String context) {
        if (clientTicks >= deadline) {
            fail("timed out waiting for " + context);
        }
    }

    private static void fail(String reason) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.options.keyUse.setDown(false);
        if (phase != Phase.FAILED) {
            phase = Phase.FAILED;
            OffHandCombat.LOGGER.error("Off Hand Combat active-hand alternation E2E failed: {}", reason);
        }
    }

    private static void fail(String reason, Throwable throwable) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.options.keyUse.setDown(false);
        if (phase != Phase.FAILED) {
            phase = Phase.FAILED;
            OffHandCombat.LOGGER.error("Off Hand Combat active-hand alternation E2E failed: {}", reason, throwable);
        }
    }

    private enum Phase {
        WAITING_FOR_WORLD,
        OPENING_WORLD,
        SETUP_SERVER,
        PREPARE,
        PREPARE_SERVER,
        WAIT_SYNC,
        WAIT_MAIN,
        START_SECOND,
        WAIT_OFF,
        WAIT_SERVER,
        PASSED,
        FAILED
    }
}
