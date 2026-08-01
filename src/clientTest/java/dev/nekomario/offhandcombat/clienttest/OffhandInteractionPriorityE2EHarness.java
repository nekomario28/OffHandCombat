package dev.nekomario.offhandcombat.clienttest;

import dev.nekomario.offhandcombat.OffHandCombat;
import dev.nekomario.offhandcombat.attachment.OffhandCombatAttachments;
import java.util.UUID;
import net.minecraft.client.KeyMapping;
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
    private static final int TARGET_SYNC_TIMEOUT_TICKS = 200;
    private static final int INTERACTION_TIMEOUT_TICKS = 80;

    private static volatile Phase phase = Phase.WAITING_FOR_WORLD;
    private static volatile BlockPos interactionPos;
    private static volatile Vec3 aimPoint;
    private static volatile long baselineSequence;
    private static int clientTicks;
    private static int interactionDeadline;

    private OffhandInteractionPriorityE2EHarness() {
    }

    @SubscribeEvent
    public static void onClientTickPre(ClientTickEvent.Pre event) {
        if (!Boolean.getBoolean(ENABLE_PROPERTY) || phase == Phase.PASSED || phase == Phase.FAILED) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        try {
            switch (phase) {
                case WAITING_FOR_BUTTON_SYNC -> triggerButtonWhenTargeted(minecraft);
                case WAITING_FOR_DOOR_SYNC -> triggerDoorWhenTargeted(minecraft);
                case WAITING_FOR_CHEST_SYNC -> triggerChestWhenTargeted(minecraft);
                default -> {
                }
            }
        } catch (Throwable throwable) {
            fail("interaction pre-tick exception", throwable);
        }
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
                case WAITING_FOR_WORLD -> openWorldWhenClientIsReady(minecraft);
                case OPENING_WORLD -> beginServerSetupWhenWorldIsReady(minecraft);
                case WAITING_FOR_BUTTON_SYNC -> {
                }
                case WAITING_FOR_BUTTON_RESULT -> verifyButtonResult(minecraft);
                case WAITING_FOR_DOOR_SYNC -> {
                }
                case WAITING_FOR_DOOR_RESULT -> verifyDoorResult(minecraft);
                case WAITING_FOR_CHEST_SYNC -> {
                }
                case WAITING_FOR_CHEST_RESULT -> verifyChestResult(minecraft);
                default -> {
                }
            }
        } catch (Throwable throwable) {
            fail("interaction harness exception", throwable);
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
            fail("copied interaction E2E world was unavailable: " + WORLD_NAME);
            return;
        }

        phase = Phase.OPENING_WORLD;
        OffHandCombat.LOGGER.info("Opening copied Off Hand Combat interaction E2E world: {}", WORLD_NAME);
        minecraft.createWorldOpenFlows().openWorld(
                WORLD_NAME,
                () -> fail("opening copied interaction E2E world was aborted"));
    }

    private static void beginServerSetupWhenWorldIsReady(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null || minecraft.getSingleplayerServer() == null) {
            return;
        }

        phase = Phase.SETTING_UP_BUTTON;
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
                interactionPos = player.blockPosition().offset(0, 2, 2);
                setupButton(player);
                phase = Phase.WAITING_FOR_BUTTON_SYNC;
            } catch (Throwable throwable) {
                fail("button setup exception", throwable);
            }
        });
    }

    private static void setupButton(ServerPlayer player) {
        clearInteractionArea(player);
        player.serverLevel().setBlockAndUpdate(
                interactionPos.relative(Direction.SOUTH),
                Blocks.STONE.defaultBlockState());
        BlockState button = Blocks.STONE_BUTTON.defaultBlockState()
                .setValue(BlockStateProperties.ATTACH_FACE, AttachFace.WALL)
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
                .setValue(BlockStateProperties.POWERED, false);
        player.serverLevel().setBlockAndUpdate(interactionPos, button);
        aimPoint = Vec3.atCenterOf(interactionPos).add(0.0D, 0.0D, -0.43D);
        orientServerPlayer(player);
        interactionDeadline = clientTicks + TARGET_SYNC_TIMEOUT_TICKS;
    }

    private static void triggerButtonWhenTargeted(Minecraft minecraft) {
        if (!targetInteractionBlock(minecraft, Blocks.STONE_BUTTON, "button")) {
            return;
        }
        KeyMapping.click(minecraft.options.keyUse.getKey());
        interactionDeadline = clientTicks + INTERACTION_TIMEOUT_TICKS;
        phase = Phase.WAITING_FOR_BUTTON_RESULT;
    }

    private static void verifyButtonResult(Minecraft minecraft) {
        orientClientPlayer(minecraft);
        if (minecraft.level != null
                && minecraft.level.getBlockState(interactionPos).is(Blocks.STONE_BUTTON)
                && minecraft.level.getBlockState(interactionPos).getValue(BlockStateProperties.POWERED)) {
            phase = Phase.VERIFYING_BUTTON_SERVER;
            verifyServerStateAndAdvance(minecraft, "button", OffhandInteractionPriorityE2EHarness::setupDoor);
            return;
        }
        if (clientTicks >= interactionDeadline) {
            fail("physical right-click did not power the button");
        }
    }

    private static void setupDoor(ServerPlayer player) {
        clearInteractionArea(player);
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
        aimPoint = Vec3.atCenterOf(interactionPos).add(0.0D, 0.0D, -0.35D);
        orientServerPlayer(player);
        interactionDeadline = clientTicks + TARGET_SYNC_TIMEOUT_TICKS;
        phase = Phase.WAITING_FOR_DOOR_SYNC;
    }

    private static void triggerDoorWhenTargeted(Minecraft minecraft) {
        if (!targetInteractionBlock(minecraft, Blocks.OAK_DOOR, "door")) {
            return;
        }
        KeyMapping.click(minecraft.options.keyUse.getKey());
        interactionDeadline = clientTicks + INTERACTION_TIMEOUT_TICKS;
        phase = Phase.WAITING_FOR_DOOR_RESULT;
    }

    private static void verifyDoorResult(Minecraft minecraft) {
        orientClientPlayer(minecraft);
        if (minecraft.level != null
                && minecraft.level.getBlockState(interactionPos).is(Blocks.OAK_DOOR)
                && minecraft.level.getBlockState(interactionPos).getValue(BlockStateProperties.OPEN)) {
            phase = Phase.VERIFYING_DOOR_SERVER;
            verifyServerStateAndAdvance(minecraft, "door", OffhandInteractionPriorityE2EHarness::setupChest);
            return;
        }
        if (clientTicks >= interactionDeadline) {
            fail("physical right-click did not open the door");
        }
    }

    private static void setupChest(ServerPlayer player) {
        clearInteractionArea(player);
        player.serverLevel().setBlockAndUpdate(interactionPos, Blocks.CHEST.defaultBlockState());
        aimPoint = Vec3.atCenterOf(interactionPos).add(0.0D, 0.1D, -0.35D);
        orientServerPlayer(player);
        interactionDeadline = clientTicks + TARGET_SYNC_TIMEOUT_TICKS;
        phase = Phase.WAITING_FOR_CHEST_SYNC;
    }

    private static void triggerChestWhenTargeted(Minecraft minecraft) {
        if (!targetInteractionBlock(minecraft, Blocks.CHEST, "chest")) {
            return;
        }
        KeyMapping.click(minecraft.options.keyUse.getKey());
        interactionDeadline = clientTicks + INTERACTION_TIMEOUT_TICKS;
        phase = Phase.WAITING_FOR_CHEST_RESULT;
    }

    private static void verifyChestResult(Minecraft minecraft) {
        orientClientPlayer(minecraft);
        if (minecraft.screen instanceof AbstractContainerScreen<?>) {
            phase = Phase.VERIFYING_CHEST_SERVER;
            verifyServerStateAndAdvance(minecraft, "chest", player -> {
                player.closeContainer();
                phase = Phase.PASSED;
                OffHandCombat.LOGGER.info(
                        "Off Hand Combat interaction priority E2E passed: button, door and chest");
            });
            return;
        }
        if (clientTicks >= interactionDeadline) {
            fail("physical right-click did not open the chest container");
        }
    }

    private static boolean targetInteractionBlock(Minecraft minecraft, Block block, String interaction) {
        if (minecraft.level == null || minecraft.player == null || interactionPos == null
                || aimPoint == null || minecraft.screen != null) {
            return false;
        }
        orientClientPlayer(minecraft);
        if (!minecraft.level.getBlockState(interactionPos).is(block)) {
            if (clientTicks >= interactionDeadline) {
                fail(interaction + " block did not synchronize to the client");
            }
            return false;
        }
        minecraft.hitResult = new BlockHitResult(
                aimPoint,
                Direction.NORTH,
                interactionPos,
                false);
        return true;
    }

    private static void verifyServerStateAndAdvance(
            Minecraft minecraft,
            String interaction,
            ServerAdvance advance) {
        if (minecraft.player == null || minecraft.getSingleplayerServer() == null) {
            fail("integrated server was unavailable while verifying " + interaction);
            return;
        }

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
                if (state.lastNetworkSequence() != baselineSequence || state.lastNetworkResult() != null) {
                    fail(interaction + " interaction emitted an Off Hand Combat attack request");
                    return;
                }
                if (player.getOffhandItem().getDamageValue() != 0) {
                    fail(interaction + " interaction consumed off-hand durability");
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

    private static void clearInteractionArea(ServerPlayer player) {
        player.serverLevel().setBlockAndUpdate(interactionPos, Blocks.AIR.defaultBlockState());
        player.serverLevel().setBlockAndUpdate(interactionPos.above(), Blocks.AIR.defaultBlockState());
        player.serverLevel().setBlockAndUpdate(interactionPos.below(), Blocks.AIR.defaultBlockState());
        player.serverLevel().setBlockAndUpdate(
                interactionPos.relative(Direction.SOUTH), Blocks.AIR.defaultBlockState());
    }

    private static void orientServerPlayer(ServerPlayer player) {
        applyLookRotation(player, aimPoint);
        player.setYHeadRot(player.getYRot());
        player.setYBodyRot(player.getYRot());
    }

    private static void orientClientPlayer(Minecraft minecraft) {
        if (minecraft.player == null || aimPoint == null) {
            return;
        }
        applyLookRotation(minecraft.player, aimPoint);
        minecraft.player.setYHeadRot(minecraft.player.getYRot());
        minecraft.player.setYBodyRot(minecraft.player.getYRot());
    }

    private static void applyLookRotation(net.minecraft.world.entity.Entity player, Vec3 target) {
        double dx = target.x - player.getX();
        double dy = target.y - player.getEyeY();
        double dz = target.z - player.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));
        player.setYRot(yaw);
        player.setXRot(pitch);
    }

    private static void fail(String reason) {
        if (phase != Phase.FAILED) {
            phase = Phase.FAILED;
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.options != null) {
                minecraft.options.keyUse.setDown(false);
            }
            OffHandCombat.LOGGER.error("Off Hand Combat interaction priority E2E failed: {}", reason);
        }
    }

    private static void fail(String reason, Throwable throwable) {
        if (phase != Phase.FAILED) {
            phase = Phase.FAILED;
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.options != null) {
                minecraft.options.keyUse.setDown(false);
            }
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
        SETTING_UP_BUTTON,
        WAITING_FOR_BUTTON_SYNC,
        WAITING_FOR_BUTTON_RESULT,
        VERIFYING_BUTTON_SERVER,
        WAITING_FOR_DOOR_SYNC,
        WAITING_FOR_DOOR_RESULT,
        VERIFYING_DOOR_SERVER,
        WAITING_FOR_CHEST_SYNC,
        WAITING_FOR_CHEST_RESULT,
        VERIFYING_CHEST_SERVER,
        PASSED,
        FAILED
    }
}
