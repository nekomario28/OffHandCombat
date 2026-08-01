package dev.nekomario.offhandcombat.clienttest;

import dev.nekomario.offhandcombat.OffHandCombat;
import dev.nekomario.offhandcombat.api.OffhandInputSource;
import dev.nekomario.offhandcombat.attachment.OffhandCombatAttachments;
import dev.nekomario.offhandcombat.client.ClientInputHandler;
import java.lang.reflect.Method;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = OffHandCombat.MOD_ID, value = Dist.CLIENT)
public final class OffhandInteractionPriorityE2EHarness {
    private static final String ENABLE_PROPERTY = "offhandcombat.interactionPriorityE2E";
    private static final String WORLD_NAME = "InteractionWorld";
    private static final int TIMEOUT_CLIENT_TICKS = 1200;
    private static final int SYNC_TIMEOUT_TICKS = 200;
    private static final int RESULT_TIMEOUT_TICKS = 80;

    private static volatile Phase phase = Phase.WAITING_FOR_WORLD;
    private static volatile BlockPos interactionPos;
    private static volatile Vec3 interactionPoint;
    private static volatile long baselineSequence;
    private static int clientTicks;
    private static int deadline;

    private OffhandInteractionPriorityE2EHarness() {
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
                case WAITING_FOR_BUTTON_SYNC -> triggerBlockUse(minecraft, Blocks.STONE_BUTTON, "button");
                case WAITING_FOR_BUTTON_RESULT -> verifyButton(minecraft);
                case WAITING_FOR_DOOR_SYNC -> triggerBlockUse(minecraft, Blocks.OAK_DOOR, "door");
                case WAITING_FOR_DOOR_RESULT -> verifyDoor(minecraft);
                case WAITING_FOR_CHEST_SYNC -> triggerBlockUse(minecraft, Blocks.CHEST, "chest");
                case WAITING_FOR_CHEST_RESULT -> verifyChest(minecraft);
                default -> {
                }
            }
        } catch (Throwable throwable) {
            fail("interaction harness exception", throwable);
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
            fail("copied interaction E2E world was unavailable: " + WORLD_NAME);
            return;
        }
        phase = Phase.OPENING_WORLD;
        minecraft.createWorldOpenFlows().openWorld(
                WORLD_NAME,
                () -> fail("opening copied interaction E2E world was aborted"));
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
                baselineSequence = player
                        .getData(OffhandCombatAttachments.COMBAT_STATE)
                        .lastNetworkSequence();
                interactionPos = player.blockPosition().offset(0, 1, 2);
                setupButton(player);
                phase = Phase.WAITING_FOR_BUTTON_SYNC;
            } catch (Throwable throwable) {
                fail("server setup exception", throwable);
            }
        });
    }

    private static void setupButton(ServerPlayer player) {
        clearArea(player);
        player.serverLevel().setBlockAndUpdate(
                interactionPos.relative(Direction.SOUTH),
                Blocks.STONE.defaultBlockState());
        BlockState button = Blocks.STONE_BUTTON.defaultBlockState()
                .setValue(BlockStateProperties.ATTACH_FACE, AttachFace.WALL)
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
                .setValue(BlockStateProperties.POWERED, false);
        player.serverLevel().setBlockAndUpdate(interactionPos, button);
        interactionPoint = Vec3.atCenterOf(interactionPos).add(0.0D, 0.0D, -0.43D);
        deadline = clientTicks + SYNC_TIMEOUT_TICKS;
    }

    private static void verifyButton(Minecraft minecraft) {
        if (minecraft.level != null
                && minecraft.level.getBlockState(interactionPos).is(Blocks.STONE_BUTTON)
                && minecraft.level.getBlockState(interactionPos).getValue(BlockStateProperties.POWERED)) {
            verifyServerAndAdvance(minecraft, "button", player -> {
                setupDoor(player);
                phase = Phase.WAITING_FOR_DOOR_SYNC;
            });
            return;
        }
        failOnDeadline("button did not become powered");
    }

    private static void setupDoor(ServerPlayer player) {
        clearArea(player);
        player.serverLevel().setBlockAndUpdate(interactionPos.below(), Blocks.STONE.defaultBlockState());
        BlockState lower = Blocks.OAK_DOOR.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
                .setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER)
                .setValue(BlockStateProperties.OPEN, false)
                .setValue(BlockStateProperties.POWERED, false);
        player.serverLevel().setBlockAndUpdate(interactionPos, lower);
        player.serverLevel().setBlockAndUpdate(
                interactionPos.above(),
                lower.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
        interactionPoint = Vec3.atCenterOf(interactionPos).add(0.0D, 0.0D, -0.35D);
        deadline = clientTicks + SYNC_TIMEOUT_TICKS;
    }

    private static void verifyDoor(Minecraft minecraft) {
        if (minecraft.level != null
                && minecraft.level.getBlockState(interactionPos).is(Blocks.OAK_DOOR)
                && minecraft.level.getBlockState(interactionPos).getValue(BlockStateProperties.OPEN)) {
            verifyServerAndAdvance(minecraft, "door", player -> {
                setupChest(player);
                phase = Phase.WAITING_FOR_CHEST_SYNC;
            });
            return;
        }
        failOnDeadline("door did not open");
    }

    private static void setupChest(ServerPlayer player) {
        clearArea(player);
        player.serverLevel().setBlockAndUpdate(interactionPos, Blocks.CHEST.defaultBlockState());
        interactionPoint = Vec3.atCenterOf(interactionPos).add(0.0D, 0.1D, -0.35D);
        deadline = clientTicks + SYNC_TIMEOUT_TICKS;
    }

    private static void verifyChest(Minecraft minecraft) {
        if (minecraft.screen instanceof AbstractContainerScreen<?>) {
            verifyServerAndAdvance(minecraft, "chest", player -> {
                player.closeContainer();
                phase = Phase.PASSED;
                OffHandCombat.LOGGER.info(
                        "Off Hand Combat interaction priority E2E passed: button, door and chest");
            });
            return;
        }
        failOnDeadline("chest container did not open");
    }

    private static void triggerBlockUse(Minecraft minecraft, Block expectedBlock, String interaction) {
        if (minecraft.level == null || minecraft.player == null || minecraft.gameMode == null
                || interactionPos == null || interactionPoint == null || minecraft.screen != null) {
            return;
        }
        if (!minecraft.level.getBlockState(interactionPos).is(expectedBlock)) {
            failOnDeadline(interaction + " did not synchronize to the client");
            return;
        }

        BlockHitResult hit = new BlockHitResult(
                interactionPoint,
                Direction.NORTH,
                interactionPos,
                false);
        minecraft.hitResult = hit;
        if (offhandHandlerWouldConsumeBlockInput()) {
            fail(interaction + " block input was incorrectly converted into an off-hand attack");
            return;
        }

        minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.OFF_HAND, hit);
        deadline = clientTicks + RESULT_TIMEOUT_TICKS;
        phase = switch (phase) {
            case WAITING_FOR_BUTTON_SYNC -> Phase.WAITING_FOR_BUTTON_RESULT;
            case WAITING_FOR_DOOR_SYNC -> Phase.WAITING_FOR_DOOR_RESULT;
            case WAITING_FOR_CHEST_SYNC -> Phase.WAITING_FOR_CHEST_RESULT;
            default -> throw new IllegalStateException("unexpected interaction phase " + phase);
        };
    }

    private static boolean offhandHandlerWouldConsumeBlockInput() {
        try {
            Method method = ClientInputHandler.class.getDeclaredMethod(
                    "trySendAttack",
                    OffhandInputSource.class);
            method.setAccessible(true);
            return (boolean) method.invoke(null, OffhandInputSource.USE_KEY);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("failed to invoke Off Hand Combat input decision", exception);
        }
    }

    private static void verifyServerAndAdvance(
            Minecraft minecraft,
            String interaction,
            ServerAdvance advance) {
        if (minecraft.player == null || minecraft.getSingleplayerServer() == null) {
            fail("integrated server was unavailable while verifying " + interaction);
            return;
        }

        phase = Phase.VERIFYING;
        UUID playerId = minecraft.player.getUUID();
        var server = minecraft.getSingleplayerServer();
        server.execute(() -> {
            try {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player == null) {
                    fail("server player was unavailable while verifying " + interaction);
                    return;
                }
                var state = player.getData(OffhandCombatAttachments.COMBAT_STATE);
                if (state.lastNetworkSequence() != baselineSequence) {
                    fail(interaction + " emitted an Off Hand Combat network request");
                    return;
                }
                if (player.getOffhandItem().getDamageValue() != 0) {
                    fail(interaction + " consumed off-hand durability");
                    return;
                }
                OffHandCombat.LOGGER.info(
                        "Off Hand Combat interaction priority E2E passed for {}", interaction);
                advance.run(player);
            } catch (Throwable throwable) {
                fail(interaction + " server verification exception", throwable);
            }
        });
    }

    private static void clearArea(ServerPlayer player) {
        player.serverLevel().setBlockAndUpdate(interactionPos, Blocks.AIR.defaultBlockState());
        player.serverLevel().setBlockAndUpdate(interactionPos.above(), Blocks.AIR.defaultBlockState());
        player.serverLevel().setBlockAndUpdate(interactionPos.below(), Blocks.AIR.defaultBlockState());
        player.serverLevel().setBlockAndUpdate(
                interactionPos.relative(Direction.SOUTH), Blocks.AIR.defaultBlockState());
    }

    private static void failOnDeadline(String reason) {
        if (clientTicks >= deadline) {
            fail(reason);
        }
    }

    private static void fail(String reason) {
        if (phase != Phase.FAILED) {
            phase = Phase.FAILED;
            OffHandCombat.LOGGER.error("Off Hand Combat interaction priority E2E failed: {}", reason);
        }
    }

    private static void fail(String reason, Throwable throwable) {
        if (phase != Phase.FAILED) {
            phase = Phase.FAILED;
            OffHandCombat.LOGGER.error(
                    "Off Hand Combat interaction priority E2E failed: {}", reason, throwable);
        }
    }

    @FunctionalInterface
    private interface ServerAdvance {
        void run(ServerPlayer player);
    }

    private enum Phase {
        WAITING_FOR_WORLD,
        OPENING_WORLD,
        SETTING_UP,
        WAITING_FOR_BUTTON_SYNC,
        WAITING_FOR_BUTTON_RESULT,
        WAITING_FOR_DOOR_SYNC,
        WAITING_FOR_DOOR_RESULT,
        WAITING_FOR_CHEST_SYNC,
        WAITING_FOR_CHEST_RESULT,
        VERIFYING,
        PASSED,
        FAILED
    }
}
