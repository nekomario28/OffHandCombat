package dev.nekomario.offhandcombat.remotetest;

import dev.nekomario.offhandcombat.OffHandCombat;
import dev.nekomario.offhandcombat.api.OffhandAttackAccess;
import dev.nekomario.offhandcombat.api.OffhandAttackResult;
import dev.nekomario.offhandcombat.api.OffhandAttackStatus;
import dev.nekomario.offhandcombat.attachment.OffhandCombatAttachments;
import dev.nekomario.offhandcombat.attachment.OffhandCombatState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = OffHandCombat.MOD_ID, value = Dist.DEDICATED_SERVER)
public final class OffhandNetworkStressServerE2EHarness {
    public static final String PLAYER_NAME = "OffhandStress";
    public static final String INITIAL_TARGET_NAME = "OffhandStressInitialTarget";
    public static final String REORDER_TARGET_NAME = "OffhandStressReorderTarget";
    public static final String FINAL_TARGET_NAME = "OffhandStressFinalTarget";

    private static final String ENABLE_PROPERTY = "offhandcombat.networkStressServerE2E";
    private static final int TIMEOUT_TICKS = 9000;
    private static final int SETTLE_TICKS = 20;
    private static final int STABILITY_TICKS = 40;

    private static Phase phase = Phase.WAITING_FOR_PLAYER;
    private static int elapsedTicks;
    private static int phaseStartedAtTick;
    private static int targetId = -1;
    private static float stableHealth;
    private static int stableDurability;
    private static OffhandAttackResult acceptedResult;

    private OffhandNetworkStressServerE2EHarness() {
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
                fail("network stress harness was not running on a dedicated server");
                return;
            }

            ServerPlayer player = server.getPlayerList().getPlayerByName(PLAYER_NAME);
            switch (phase) {
                case WAITING_FOR_PLAYER -> waitForPlayer(player);
                case WAITING_FOR_INITIAL_SETTLE -> waitForInitialSettle(player);
                case WAITING_FOR_INITIAL_SUCCESS -> waitForInitialSuccess(player);
                case WAITING_FOR_DUPLICATE_STABILITY -> waitForDuplicateStability(player);
                case WAITING_FOR_REORDER_SUCCESS -> waitForReorderSuccess(player);
                case WAITING_FOR_BURST_RESULT -> waitForBurstResult(player);
                case WAITING_FOR_FINAL_SETTLE -> waitForFinalSettle(player);
                case WAITING_FOR_FINAL_SUCCESS -> waitForFinalSuccess(player);
                default -> {
                }
            }
        } catch (Throwable throwable) {
            fail("network stress server harness exception", throwable);
        }
    }

    private static void waitForPlayer(ServerPlayer player) {
        if (player == null) {
            return;
        }

        preparePlayer(player);
        beginPhase(Phase.WAITING_FOR_INITIAL_SETTLE);
        OffHandCombat.LOGGER.info("Off Hand Combat network stress server player connected and equipped");
    }

    private static void waitForInitialSettle(ServerPlayer player) {
        if (!requirePlayer(player, "initial setup") || !settled()) {
            return;
        }

        targetId = armAndSpawnTarget(player, INITIAL_TARGET_NAME);
        if (targetId >= 0) {
            beginPhase(Phase.WAITING_FOR_INITIAL_SUCCESS);
        }
    }

    private static void waitForInitialSuccess(ServerPlayer player) {
        if (!requirePlayer(player, "initial attack")) {
            return;
        }

        OffhandCombatState state = player.getData(OffhandCombatAttachments.COMBAT_STATE);
        OffhandAttackResult result = state.lastNetworkResult();
        if (result == null || result.sequence() != 1L || result.targetId() != targetId) {
            return;
        }
        if (!verifySuccess(player, state, result, 1L, 0, 1, "initial attack")) {
            return;
        }

        acceptedResult = result;
        stableHealth = result.targetHealthAfter();
        stableDurability = result.durabilityAfter();
        beginPhase(Phase.WAITING_FOR_DUPLICATE_STABILITY);
        OffHandCombat.LOGGER.info(
                "Off Hand Combat network stress initial request accepted; awaiting duplicate flood stability");
    }

    private static void waitForDuplicateStability(ServerPlayer player) {
        if (!requirePlayer(player, "duplicate flood") || !stableFor(STABILITY_TICKS)) {
            return;
        }
        if (!verifyStable(player, 1L, acceptedResult, stableHealth, stableDurability, "duplicate flood")) {
            return;
        }

        discardTarget(player);
        targetId = armAndSpawnTarget(player, REORDER_TARGET_NAME);
        if (targetId >= 0) {
            beginPhase(Phase.WAITING_FOR_REORDER_SUCCESS);
            OffHandCombat.LOGGER.info(
                    "Off Hand Combat network stress duplicate flood remained exactly-once; armed reorder target");
        }
    }

    private static void waitForReorderSuccess(ServerPlayer player) {
        if (!requirePlayer(player, "reordered request")) {
            return;
        }

        OffhandCombatState state = player.getData(OffhandCombatAttachments.COMBAT_STATE);
        OffhandAttackResult result = state.lastNetworkResult();
        if (result == null || result.sequence() != 3L || result.targetId() != targetId) {
            return;
        }
        if (!verifySuccess(player, state, result, 3L, 1, 2, "reordered sequence 3 attack")) {
            return;
        }

        acceptedResult = result;
        stableHealth = result.targetHealthAfter();
        stableDurability = result.durabilityAfter();
        beginPhase(Phase.WAITING_FOR_BURST_RESULT);
        OffHandCombat.LOGGER.info(
                "Off Hand Combat network stress sequence 3 accepted before delayed sequence 2");
    }

    private static void waitForBurstResult(ServerPlayer player) {
        if (!requirePlayer(player, "unique-sequence burst")) {
            return;
        }

        OffhandCombatState state = player.getData(OffhandCombatAttachments.COMBAT_STATE);
        OffhandAttackResult result = state.lastNetworkResult();
        if (state.lastNetworkSequence() < 68L || result == null || result.sequence() != 68L) {
            return;
        }
        if (result.status() != OffhandAttackStatus.RATE_LIMITED) {
            fail("unique-sequence burst ended with " + result.status() + " instead of RATE_LIMITED");
            return;
        }
        if (!verifyTargetAndDurability(player, stableHealth, stableDurability, "unique-sequence burst")) {
            return;
        }

        beginPhase(Phase.WAITING_FOR_FINAL_SETTLE);
        OffHandCombat.LOGGER.info(
                "Off Hand Combat network stress delayed stale request and 64-request burst caused no extra effect");
    }

    private static void waitForFinalSettle(ServerPlayer player) {
        if (!requirePlayer(player, "final setup") || !stableFor(STABILITY_TICKS)) {
            return;
        }

        discardTarget(player);
        targetId = armAndSpawnTarget(player, FINAL_TARGET_NAME);
        if (targetId >= 0) {
            beginPhase(Phase.WAITING_FOR_FINAL_SUCCESS);
        }
    }

    private static void waitForFinalSuccess(ServerPlayer player) {
        if (!requirePlayer(player, "final accepted request")) {
            return;
        }

        OffhandCombatState state = player.getData(OffhandCombatAttachments.COMBAT_STATE);
        OffhandAttackResult result = state.lastNetworkResult();
        if (result == null || result.sequence() != 69L || result.targetId() != targetId) {
            return;
        }
        if (!verifySuccess(player, state, result, 69L, 2, 3, "final sequence 69 attack")) {
            return;
        }

        phase = Phase.PASSED;
        OffHandCombat.LOGGER.info(
                "Off Hand Combat network stress server E2E passed: duplicateFlood=64, "
                        + "reordered=3-before-2, burst=5-68, finalSequence=69, durability=3");
    }

    private static void preparePlayer(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        int x = 64;
        int z = 0;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) + 1;
        preparePlatform(level, x, y, z);
        player.setGameMode(GameType.SURVIVAL);
        player.teleportTo(x + 0.5D, y, z + 0.5D);
        player.setYRot(0.0F);
        player.setXRot(0.0F);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WOODEN_SWORD));
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.IRON_SWORD));
    }

    private static void preparePlatform(ServerLevel level, int centerX, int y, int centerZ) {
        for (int x = centerX - 4; x <= centerX + 4; x++) {
            for (int z = centerZ - 2; z <= centerZ + 4; z++) {
                BlockPos floor = new BlockPos(x, y - 1, z);
                level.setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
                level.setBlockAndUpdate(floor.above(), Blocks.AIR.defaultBlockState());
                level.setBlockAndUpdate(floor.above(2), Blocks.AIR.defaultBlockState());
            }
        }
    }

    private static int armAndSpawnTarget(ServerPlayer player, String name) {
        ((OffhandAttackAccess) player).ofc$setOffhandAttackStrengthTicker(100);
        Mob target = EntityType.COW.create(player.serverLevel());
        if (target == null) {
            fail("failed to create target " + name);
            return -1;
        }

        target.setNoAi(true);
        target.setPersistenceRequired();
        target.setCustomName(Component.literal(name));
        target.setCustomNameVisible(true);
        target.setHealth(target.getMaxHealth());
        target.moveTo(player.getX(), player.getY(), player.getZ() + 2.0D, 0.0F, 0.0F);
        if (!player.serverLevel().addFreshEntity(target)) {
            fail("failed to add target " + name);
            return -1;
        }
        return target.getId();
    }

    private static void discardTarget(ServerPlayer player) {
        Entity target = player.serverLevel().getEntity(targetId);
        if (target != null) {
            target.discard();
        }
    }

    private static boolean verifySuccess(
            ServerPlayer player,
            OffhandCombatState state,
            OffhandAttackResult result,
            long expectedSequence,
            int expectedDurabilityBefore,
            int expectedDurabilityAfter,
            String label) {
        if (result.status() != OffhandAttackStatus.SUCCESS) {
            fail(label + " result was " + result.status());
            return false;
        }
        if (state.lastNetworkSequence() != expectedSequence) {
            fail(label + " server sequence was " + state.lastNetworkSequence());
            return false;
        }
        if (!(result.targetHealthAfter() < result.targetHealthBefore())) {
            fail(label + " did not reduce target health");
            return false;
        }
        if (result.durabilityBefore() != expectedDurabilityBefore
                || result.durabilityAfter() != expectedDurabilityAfter) {
            fail(label + " used unexpected durability transition "
                    + result.durabilityBefore() + " -> " + result.durabilityAfter());
            return false;
        }
        return verifyTargetAndDurability(player, result.targetHealthAfter(), expectedDurabilityAfter, label);
    }

    private static boolean verifyStable(
            ServerPlayer player,
            long expectedSequence,
            OffhandAttackResult expectedResult,
            float expectedHealth,
            int expectedDurability,
            String label) {
        OffhandCombatState state = player.getData(OffhandCombatAttachments.COMBAT_STATE);
        if (state.lastNetworkSequence() != expectedSequence || !expectedResult.equals(state.lastNetworkResult())) {
            fail(label + " changed the accepted sequence or cached result");
            return false;
        }
        return verifyTargetAndDurability(player, expectedHealth, expectedDurability, label);
    }

    private static boolean verifyTargetAndDurability(
            ServerPlayer player, float expectedHealth, int expectedDurability, String label) {
        Entity target = player.serverLevel().getEntity(targetId);
        if (!(target instanceof Mob mob)) {
            fail(label + " target disappeared");
            return false;
        }
        if (Float.compare(mob.getHealth(), expectedHealth) != 0) {
            fail(label + " changed target health to " + mob.getHealth()
                    + " instead of " + expectedHealth);
            return false;
        }
        if (player.getOffhandItem().getDamageValue() != expectedDurability) {
            fail(label + " changed durability to " + player.getOffhandItem().getDamageValue()
                    + " instead of " + expectedDurability);
            return false;
        }
        return true;
    }

    private static boolean requirePlayer(ServerPlayer player, String stage) {
        if (player == null) {
            fail("network stress player disconnected during " + stage);
            return false;
        }
        return true;
    }

    private static boolean settled() {
        return elapsedTicks - phaseStartedAtTick >= SETTLE_TICKS;
    }

    private static boolean stableFor(int ticks) {
        return elapsedTicks - phaseStartedAtTick >= ticks;
    }

    private static void beginPhase(Phase next) {
        phase = next;
        phaseStartedAtTick = elapsedTicks;
    }

    private static void fail(String reason) {
        if (phase != Phase.FAILED) {
            phase = Phase.FAILED;
            OffHandCombat.LOGGER.error("Off Hand Combat network stress server E2E failed: {}", reason);
        }
    }

    private static void fail(String reason, Throwable throwable) {
        if (phase != Phase.FAILED) {
            phase = Phase.FAILED;
            OffHandCombat.LOGGER.error(
                    "Off Hand Combat network stress server E2E failed: {}", reason, throwable);
        }
    }

    private enum Phase {
        WAITING_FOR_PLAYER,
        WAITING_FOR_INITIAL_SETTLE,
        WAITING_FOR_INITIAL_SUCCESS,
        WAITING_FOR_DUPLICATE_STABILITY,
        WAITING_FOR_REORDER_SUCCESS,
        WAITING_FOR_BURST_RESULT,
        WAITING_FOR_FINAL_SETTLE,
        WAITING_FOR_FINAL_SUCCESS,
        PASSED,
        FAILED
    }
}
