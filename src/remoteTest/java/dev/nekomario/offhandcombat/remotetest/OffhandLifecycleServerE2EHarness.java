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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = OffHandCombat.MOD_ID, value = Dist.DEDICATED_SERVER)
public final class OffhandLifecycleServerE2EHarness {
    public static final String PLAYER_NAME = "OffhandLifecycle";
    public static final String INITIAL_TARGET_NAME = "OffhandLifecycleInitialTarget";
    public static final String RECONNECT_TARGET_NAME = "OffhandLifecycleReconnectTarget";
    public static final String RESPAWN_TARGET_NAME = "OffhandLifecycleRespawnTarget";
    public static final String DIMENSION_TARGET_NAME = "OffhandLifecycleDimensionTarget";

    private static final String ENABLE_PROPERTY = "offhandcombat.lifecycleServerE2E";
    private static final int TIMEOUT_TICKS = 7200;
    private static final int EQUIPMENT_SETTLE_TICKS = 20;
    private static final int CLIENT_OBSERVATION_TICKS = 40;
    private static final int OVERWORLD_X = 32;
    private static final int OVERWORLD_Z = 0;
    private static final int NETHER_X = 0;
    private static final int NETHER_Y = 80;
    private static final int NETHER_Z = 0;

    private static Phase phase = Phase.WAITING_FOR_INITIAL_PLAYER;
    private static int elapsedTicks;
    private static int phaseStartedAtTick;
    private static int targetId = -1;

    private static ServerPlayer initialPlayer;
    private static ServerPlayer reconnectPlayer;
    private static ServerPlayer respawnPlayer;
    private static OffhandCombatState initialState;
    private static OffhandCombatState reconnectState;
    private static OffhandCombatState respawnState;
    private static OffhandAttackResult respawnResult;

    private OffhandLifecycleServerE2EHarness() {
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
                fail("lifecycle harness was not running on a dedicated server");
                return;
            }

            ServerPlayer player = server.getPlayerList().getPlayerByName(PLAYER_NAME);
            switch (phase) {
                case WAITING_FOR_INITIAL_PLAYER -> waitForInitialPlayer(player);
                case WAITING_FOR_INITIAL_SETTLE -> waitForInitialSettle(player);
                case WAITING_FOR_INITIAL_RESULT -> waitForInitialResult(player);
                case WAITING_FOR_DISCONNECT -> waitForDisconnect(player);
                case WAITING_FOR_RECONNECT -> waitForReconnect(player);
                case WAITING_FOR_RECONNECT_SETTLE -> waitForReconnectSettle(player);
                case WAITING_FOR_RECONNECT_RESULT -> waitForReconnectResult(player);
                case WAITING_TO_KILL -> waitToKill(server, player);
                case WAITING_FOR_RESPAWN -> waitForRespawn(player);
                case WAITING_FOR_RESPAWN_SETTLE -> waitForRespawnSettle(player);
                case WAITING_FOR_RESPAWN_RESULT -> waitForRespawnResult(player);
                case WAITING_TO_CHANGE_DIMENSION -> waitToChangeDimension(server, player);
                case WAITING_FOR_DIMENSION_CHANGE -> waitForDimensionChange(player);
                case WAITING_FOR_DIMENSION_SETTLE -> waitForDimensionSettle(player);
                case WAITING_FOR_DIMENSION_RESULT -> waitForDimensionResult(player);
                default -> {
                }
            }
        } catch (Throwable throwable) {
            fail("lifecycle server harness exception", throwable);
        }
    }

    private static void waitForInitialPlayer(ServerPlayer player) {
        if (player == null) {
            return;
        }

        initialPlayer = player;
        initialState = player.getData(OffhandCombatAttachments.COMBAT_STATE);
        requireFreshState(initialState, "initial player");
        if (phase == Phase.FAILED) {
            return;
        }

        prepareFreshOverworldPlayer(player);
        beginPhase(Phase.WAITING_FOR_INITIAL_SETTLE);
    }

    private static void waitForInitialSettle(ServerPlayer player) {
        if (!requirePlayer(player, initialPlayer, "initial setup")) {
            return;
        }
        if (!settled()) {
            return;
        }

        targetId = armAndSpawnTarget(player, INITIAL_TARGET_NAME);
        if (targetId >= 0) {
            beginPhase(Phase.WAITING_FOR_INITIAL_RESULT);
        }
    }

    private static void waitForInitialResult(ServerPlayer player) {
        if (!requirePlayer(player, initialPlayer, "initial attack")) {
            return;
        }

        OffhandAttackResult result = currentResult(player, targetId);
        if (result == null) {
            return;
        }
        if (!verifySuccessfulStage(player, initialState, result, 1L, 0, 1, "initial attack")) {
            return;
        }

        beginPhase(Phase.WAITING_FOR_DISCONNECT);
        OffHandCombat.LOGGER.info("Off Hand Combat lifecycle initial attack passed; waiting for reconnect");
    }

    private static void waitForDisconnect(ServerPlayer player) {
        if (player != null) {
            if (player != initialPlayer) {
                fail("initial player object changed before the disconnect was observed");
            }
            return;
        }

        beginPhase(Phase.WAITING_FOR_RECONNECT);
        OffHandCombat.LOGGER.info("Off Hand Combat lifecycle disconnect observed");
    }

    private static void waitForReconnect(ServerPlayer player) {
        if (player == null) {
            return;
        }
        if (player == initialPlayer) {
            fail("reconnect reused the disconnected ServerPlayer object");
            return;
        }

        OffhandCombatState state = player.getData(OffhandCombatAttachments.COMBAT_STATE);
        if (state == initialState) {
            fail("reconnect reused the disconnected combat-state object");
            return;
        }
        requireFreshState(state, "reconnected player");
        if (phase == Phase.FAILED) {
            return;
        }

        reconnectPlayer = player;
        reconnectState = state;
        prepareFreshOverworldPlayer(player);
        beginPhase(Phase.WAITING_FOR_RECONNECT_SETTLE);
        OffHandCombat.LOGGER.info("Off Hand Combat lifecycle reconnect state reset passed");
    }

    private static void waitForReconnectSettle(ServerPlayer player) {
        if (!requirePlayer(player, reconnectPlayer, "reconnect setup")) {
            return;
        }
        if (!settled()) {
            return;
        }

        targetId = armAndSpawnTarget(player, RECONNECT_TARGET_NAME);
        if (targetId >= 0) {
            beginPhase(Phase.WAITING_FOR_RECONNECT_RESULT);
        }
    }

    private static void waitForReconnectResult(ServerPlayer player) {
        if (!requirePlayer(player, reconnectPlayer, "reconnect attack")) {
            return;
        }

        OffhandAttackResult result = currentResult(player, targetId);
        if (result == null) {
            return;
        }
        if (!verifySuccessfulStage(player, reconnectState, result, 1L, 0, 1, "reconnect attack")) {
            return;
        }

        beginPhase(Phase.WAITING_TO_KILL);
        OffHandCombat.LOGGER.info("Off Hand Combat lifecycle reconnect attack passed; waiting to kill player");
    }

    private static void waitToKill(MinecraftServer server, ServerPlayer player) {
        if (!requirePlayer(player, reconnectPlayer, "before death")) {
            return;
        }
        if (elapsedTicks - phaseStartedAtTick < CLIENT_OBSERVATION_TICKS) {
            return;
        }

        server.getCommands().performPrefixedCommand(
                server.createCommandSourceStack(), "kill " + PLAYER_NAME);

        beginPhase(Phase.WAITING_FOR_RESPAWN);
        OffHandCombat.LOGGER.info("Off Hand Combat lifecycle actual death requested");
    }

    private static void waitForRespawn(ServerPlayer player) {
        if (player == null || player == reconnectPlayer || player.isDeadOrDying()) {
            return;
        }

        OffhandCombatState state = player.getData(OffhandCombatAttachments.COMBAT_STATE);
        if (state == reconnectState) {
            fail("death/respawn reused the dead combat-state object");
            return;
        }
        requireFreshState(state, "respawned player");
        if (phase == Phase.FAILED) {
            return;
        }

        respawnPlayer = player;
        respawnState = state;
        prepareFreshOverworldPlayer(player);
        beginPhase(Phase.WAITING_FOR_RESPAWN_SETTLE);
        OffHandCombat.LOGGER.info("Off Hand Combat lifecycle death/respawn state reset passed");
    }

    private static void waitForRespawnSettle(ServerPlayer player) {
        if (!requirePlayer(player, respawnPlayer, "respawn setup")) {
            return;
        }
        if (!settled()) {
            return;
        }

        targetId = armAndSpawnTarget(player, RESPAWN_TARGET_NAME);
        if (targetId >= 0) {
            beginPhase(Phase.WAITING_FOR_RESPAWN_RESULT);
        }
    }

    private static void waitForRespawnResult(ServerPlayer player) {
        if (!requirePlayer(player, respawnPlayer, "respawn attack")) {
            return;
        }

        OffhandAttackResult result = currentResult(player, targetId);
        if (result == null) {
            return;
        }
        if (!verifySuccessfulStage(player, respawnState, result, 1L, 0, 1, "respawn attack")) {
            return;
        }

        respawnResult = result;
        beginPhase(Phase.WAITING_TO_CHANGE_DIMENSION);
        OffHandCombat.LOGGER.info(
                "Off Hand Combat lifecycle respawn attack passed; waiting to change dimension");
    }

    private static void waitToChangeDimension(MinecraftServer server, ServerPlayer player) {
        if (!requirePlayer(player, respawnPlayer, "before dimension transition")) {
            return;
        }
        if (elapsedTicks - phaseStartedAtTick < CLIENT_OBSERVATION_TICKS) {
            return;
        }

        ServerLevel nether = server.getLevel(Level.NETHER);
        if (nether == null) {
            fail("dedicated server did not expose the Nether level");
            return;
        }
        preparePlatform(nether, NETHER_X, NETHER_Y, NETHER_Z);

        server.getCommands().performPrefixedCommand(
                server.createCommandSourceStack(),
                "execute in minecraft:the_nether run tp " + PLAYER_NAME + " "
                        + NETHER_X + " " + NETHER_Y + " " + NETHER_Z);

        beginPhase(Phase.WAITING_FOR_DIMENSION_CHANGE);
        OffHandCombat.LOGGER.info("Off Hand Combat lifecycle actual dimension transition requested");
    }

    private static void waitForDimensionChange(ServerPlayer player) {
        if (player == null || !player.serverLevel().dimension().equals(Level.NETHER)) {
            return;
        }

        OffhandCombatState state = player.getData(OffhandCombatAttachments.COMBAT_STATE);
        if (state.lastNetworkSequence() != 1L || !respawnResult.equals(state.lastNetworkResult())) {
            fail("dimension transition lost or duplicated the active replay state");
            return;
        }
        if (player.getOffhandItem().getDamageValue() != 1) {
            fail("dimension transition changed off-hand durability before the next attack");
            return;
        }

        ((OffhandAttackAccess) player).ofc$setOffhandAttackStrengthTicker(100);
        beginPhase(Phase.WAITING_FOR_DIMENSION_SETTLE);
        OffHandCombat.LOGGER.info("Off Hand Combat lifecycle dimension transition state preservation passed");
    }

    private static void waitForDimensionSettle(ServerPlayer player) {
        if (player == null || !player.serverLevel().dimension().equals(Level.NETHER)) {
            fail("lifecycle player left the Nether during dimension setup");
            return;
        }
        if (!settled()) {
            return;
        }

        ((OffhandAttackAccess) player).ofc$setOffhandAttackStrengthTicker(100);
        targetId = spawnTarget(player, DIMENSION_TARGET_NAME);
        if (targetId >= 0) {
            beginPhase(Phase.WAITING_FOR_DIMENSION_RESULT);
        }
    }

    private static void waitForDimensionResult(ServerPlayer player) {
        if (player == null || !player.serverLevel().dimension().equals(Level.NETHER)) {
            fail("lifecycle player left the Nether before the dimension attack");
            return;
        }

        OffhandCombatState state = player.getData(OffhandCombatAttachments.COMBAT_STATE);
        OffhandAttackResult result = currentResult(player, targetId);
        if (result == null || result.sequence() == 1L) {
            return;
        }
        if (!verifySuccessfulStage(player, state, result, 2L, 1, 2, "dimension attack")) {
            return;
        }

        phase = Phase.PASSED;
        OffHandCombat.LOGGER.info(
                "Off Hand Combat lifecycle server E2E passed: reconnect reset, respawn reset, "
                        + "dimension sequence=2 and durability=2");
    }

    private static void prepareFreshOverworldPlayer(ServerPlayer player) {
        if (!player.serverLevel().dimension().equals(Level.OVERWORLD)) {
            fail("fresh lifecycle stage did not begin in the Overworld");
            return;
        }

        ServerLevel level = player.serverLevel();
        int y = level.getHeight(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, OVERWORLD_X, OVERWORLD_Z) + 1;
        preparePlatform(level, OVERWORLD_X, y, OVERWORLD_Z);
        player.setGameMode(GameType.SURVIVAL);
        player.setHealth(player.getMaxHealth());
        player.clearFire();
        player.teleportTo(OVERWORLD_X + 0.5D, y, OVERWORLD_Z + 0.5D);
        player.setYRot(0.0F);
        player.setXRot(0.0F);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WOODEN_SWORD));
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.IRON_SWORD));
    }

    private static void preparePlatform(ServerLevel level, int centerX, int y, int centerZ) {
        for (int x = -4; x <= 4; x++) {
            for (int z = -2; z <= 4; z++) {
                BlockPos floor = new BlockPos(centerX + x, y - 1, centerZ + z);
                level.setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
                level.setBlockAndUpdate(floor.above(), Blocks.AIR.defaultBlockState());
                level.setBlockAndUpdate(floor.above(2), Blocks.AIR.defaultBlockState());
            }
        }
    }

    private static int armAndSpawnTarget(ServerPlayer player, String name) {
        ((OffhandAttackAccess) player).ofc$setOffhandAttackStrengthTicker(100);
        return spawnTarget(player, name);
    }

    private static int spawnTarget(ServerPlayer player, String name) {
        ServerLevel level = player.serverLevel();
        Mob target = EntityType.COW.create(level);
        if (target == null) {
            fail("failed to create lifecycle target " + name);
            return -1;
        }
        target.setNoAi(true);
        target.setPersistenceRequired();
        target.setCustomName(Component.literal(name));
        target.setCustomNameVisible(true);
        target.setHealth(target.getMaxHealth());
        target.moveTo(player.getX(), player.getY(), player.getZ() + 2.0D, 0.0F, 0.0F);
        if (!level.addFreshEntity(target)) {
            fail("failed to add lifecycle target " + name);
            return -1;
        }
        return target.getId();
    }

    private static OffhandAttackResult currentResult(ServerPlayer player, int expectedTargetId) {
        OffhandAttackResult result = player
                .getData(OffhandCombatAttachments.COMBAT_STATE)
                .lastNetworkResult();
        return result != null && result.targetId() == expectedTargetId ? result : null;
    }

    private static boolean verifySuccessfulStage(
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
        if (result.sequence() != expectedSequence || state.lastNetworkSequence() != expectedSequence) {
            fail(label + " used unexpected sequence " + result.sequence());
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

        Entity target = player.serverLevel().getEntity(result.targetId());
        if (!(target instanceof Mob living)
                || Float.compare(living.getHealth(), result.targetHealthAfter()) != 0) {
            fail(label + " authoritative target health did not match its result");
            return false;
        }
        if (player.getOffhandItem().getDamageValue() != expectedDurabilityAfter) {
            fail(label + " authoritative off-hand durability did not match its result");
            return false;
        }
        return true;
    }

    private static void requireFreshState(OffhandCombatState state, String label) {
        if (state.lastNetworkSequence() != 0L || state.lastNetworkResult() != null) {
            fail(label + " inherited replay state");
        }
    }

    private static boolean requirePlayer(
            ServerPlayer current, ServerPlayer expected, String stage) {
        if (current == null) {
            fail("lifecycle player disconnected " + stage);
            return false;
        }
        if (current != expected) {
            fail("unexpected ServerPlayer replacement " + stage);
            return false;
        }
        return true;
    }

    private static boolean settled() {
        return elapsedTicks - phaseStartedAtTick >= EQUIPMENT_SETTLE_TICKS;
    }

    private static void beginPhase(Phase next) {
        phase = next;
        phaseStartedAtTick = elapsedTicks;
    }

    private static void fail(String reason) {
        if (phase != Phase.FAILED) {
            phase = Phase.FAILED;
            OffHandCombat.LOGGER.error("Off Hand Combat lifecycle server E2E failed: {}", reason);
        }
    }

    private static void fail(String reason, Throwable throwable) {
        if (phase != Phase.FAILED) {
            phase = Phase.FAILED;
            OffHandCombat.LOGGER.error(
                    "Off Hand Combat lifecycle server E2E failed: {}", reason, throwable);
        }
    }

    private enum Phase {
        WAITING_FOR_INITIAL_PLAYER,
        WAITING_FOR_INITIAL_SETTLE,
        WAITING_FOR_INITIAL_RESULT,
        WAITING_FOR_DISCONNECT,
        WAITING_FOR_RECONNECT,
        WAITING_FOR_RECONNECT_SETTLE,
        WAITING_FOR_RECONNECT_RESULT,
        WAITING_TO_KILL,
        WAITING_FOR_RESPAWN,
        WAITING_FOR_RESPAWN_SETTLE,
        WAITING_FOR_RESPAWN_RESULT,
        WAITING_TO_CHANGE_DIMENSION,
        WAITING_FOR_DIMENSION_CHANGE,
        WAITING_FOR_DIMENSION_SETTLE,
        WAITING_FOR_DIMENSION_RESULT,
        PASSED,
        FAILED
    }
}
