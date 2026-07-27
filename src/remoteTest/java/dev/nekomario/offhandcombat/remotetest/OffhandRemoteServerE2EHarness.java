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
public final class OffhandRemoteServerE2EHarness {
    public static final String PLAYER_A_NAME = "OffhandRemoteA";
    public static final String PLAYER_B_NAME = "OffhandRemoteB";
    public static final String TARGET_A_NAME = "OffhandRemoteTargetA";
    public static final String TARGET_B_NAME = "OffhandRemoteTargetB";

    private static final String ENABLE_PROPERTY = "offhandcombat.remoteServerE2E";
    private static final int TIMEOUT_TICKS = 4800;
    private static final int EQUIPMENT_SETTLE_TICKS = 20;
    private static final int STABILITY_TICKS = 20;

    private static Phase phase = Phase.WAITING_FOR_PLAYERS;
    private static int elapsedTicks;
    private static int equippedAtTick;
    private static int stageSuccessAtTick;
    private static int targetAId = -1;
    private static int targetBId = -1;
    private static OffhandAttackResult resultA;
    private static OffhandAttackResult resultB;
    private static float stableHealthA;
    private static float stableHealthB;
    private static int stableDurabilityA;
    private static int stableDurabilityB;

    private OffhandRemoteServerE2EHarness() {
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
                fail("two-client remote harness was not running on a dedicated server");
                return;
            }

            ServerPlayer playerA = server.getPlayerList().getPlayerByName(PLAYER_A_NAME);
            ServerPlayer playerB = server.getPlayerList().getPlayerByName(PLAYER_B_NAME);
            if (phase == Phase.WAITING_FOR_PLAYERS) {
                if (playerA != null && playerB != null) {
                    equipAndPositionPlayers(playerA, playerB);
                }
            } else if (phase == Phase.WAITING_FOR_EQUIPMENT_SETTLE) {
                requireBothPlayers(playerA, playerB, "during setup");
                if (phase != Phase.FAILED && elapsedTicks - equippedAtTick >= EQUIPMENT_SETTLE_TICKS) {
                    armAndSpawnTargetA(playerA);
                }
            } else if (phase == Phase.WAITING_FOR_A_RESULT) {
                requireBothPlayers(playerA, playerB, "before client A result");
                if (phase != Phase.FAILED) {
                    verifyFirstResultA(playerA, playerB);
                }
            } else if (phase == Phase.WAITING_FOR_A_STABILITY) {
                requireBothPlayers(playerA, playerB, "during client A replay verification");
                if (phase != Phase.FAILED) {
                    verifyAStableAndArmB(playerA, playerB);
                }
            } else if (phase == Phase.WAITING_FOR_B_RESULT) {
                requireBothPlayers(playerA, playerB, "before client B result");
                if (phase != Phase.FAILED) {
                    verifyFirstResultB(playerA, playerB);
                }
            } else if (phase == Phase.WAITING_FOR_B_STABILITY) {
                requireBothPlayers(playerA, playerB, "during client B replay verification");
                if (phase != Phase.FAILED) {
                    verifyBothStableExactlyOnce(playerA, playerB);
                }
            }
        } catch (Throwable throwable) {
            fail("two-client remote server harness exception", throwable);
        }
    }

    private static void requireBothPlayers(ServerPlayer playerA, ServerPlayer playerB, String stage) {
        if (playerA == null || playerB == null) {
            fail("one of the two remote players disconnected " + stage);
        }
    }

    private static void equipAndPositionPlayers(ServerPlayer playerA, ServerPlayer playerB) {
        if (playerA.serverLevel() != playerB.serverLevel()) {
            fail("remote players joined different levels");
            return;
        }

        ServerLevel level = playerA.serverLevel();
        int baseX = 0;
        int baseZ = 0;
        int baseY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, baseX, baseZ) + 1;

        for (int x = -7; x <= 7; x++) {
            for (int z = -1; z <= 4; z++) {
                BlockPos floor = new BlockPos(baseX + x, baseY - 1, baseZ + z);
                level.setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
                level.setBlockAndUpdate(floor.above(), Blocks.AIR.defaultBlockState());
                level.setBlockAndUpdate(floor.above(2), Blocks.AIR.defaultBlockState());
            }
        }

        preparePlayer(playerA, baseX - 4.5D, baseY, baseZ + 0.5D);
        preparePlayer(playerB, baseX + 4.5D, baseY, baseZ + 0.5D);

        equippedAtTick = elapsedTicks;
        phase = Phase.WAITING_FOR_EQUIPMENT_SETTLE;
        OffHandCombat.LOGGER.info("Off Hand Combat two-client remote server players connected and equipped");
    }

    private static void preparePlayer(ServerPlayer player, double x, double y, double z) {
        player.setGameMode(GameType.SURVIVAL);
        player.teleportTo(x, y, z);
        player.setYRot(0.0F);
        player.setXRot(0.0F);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WOODEN_SWORD));
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.IRON_SWORD));
    }

    private static void armAndSpawnTargetA(ServerPlayer playerA) {
        ((OffhandAttackAccess) playerA).ofc$setOffhandAttackStrengthTicker(100);
        targetAId = spawnTarget(playerA, TARGET_A_NAME);
        if (targetAId < 0) {
            return;
        }

        phase = Phase.WAITING_FOR_A_RESULT;
        OffHandCombat.LOGGER.info("Off Hand Combat two-client remote server armed client A: target={}", targetAId);
    }

    private static void verifyFirstResultA(ServerPlayer playerA, ServerPlayer playerB) {
        OffhandCombatState stateA = playerA.getData(OffhandCombatAttachments.COMBAT_STATE);
        OffhandAttackResult candidate = stateA.lastNetworkResult();
        if (candidate == null || candidate.targetId() != targetAId) {
            return;
        }

        resultA = verifySuccessfulFirstResult(playerA, stateA, candidate, targetAId, "client A");
        if (resultA == null) {
            return;
        }

        OffhandCombatState stateB = playerB.getData(OffhandCombatAttachments.COMBAT_STATE);
        if (stateB.lastNetworkSequence() != 0L || stateB.lastNetworkResult() != null) {
            fail("client A request contaminated client B replay state");
            return;
        }
        if (playerB.getOffhandItem().getDamageValue() != 0) {
            fail("client A attack consumed client B off-hand durability");
            return;
        }

        stableHealthA = resultA.targetHealthAfter();
        stableDurabilityA = resultA.durabilityAfter();
        stageSuccessAtTick = elapsedTicks;
        phase = Phase.WAITING_FOR_A_STABILITY;
    }

    private static void verifyAStableAndArmB(ServerPlayer playerA, ServerPlayer playerB) {
        if (elapsedTicks - stageSuccessAtTick < STABILITY_TICKS) {
            return;
        }

        if (!verifyStablePlayerResult(playerA, targetAId, resultA, stableHealthA, stableDurabilityA, "client A")) {
            return;
        }

        OffhandCombatState stateB = playerB.getData(OffhandCombatAttachments.COMBAT_STATE);
        if (stateB.lastNetworkSequence() != 0L || stateB.lastNetworkResult() != null) {
            fail("client A duplicate replay advanced client B sequence state");
            return;
        }
        if (playerB.getOffhandItem().getDamageValue() != 0) {
            fail("client A duplicate replay changed client B durability");
            return;
        }

        ((OffhandAttackAccess) playerB).ofc$setOffhandAttackStrengthTicker(100);
        targetBId = spawnTarget(playerB, TARGET_B_NAME);
        if (targetBId < 0) {
            return;
        }

        phase = Phase.WAITING_FOR_B_RESULT;
        OffHandCombat.LOGGER.info(
                "Off Hand Combat client A replay remained isolated; armed client B: target={}", targetBId);
    }

    private static void verifyFirstResultB(ServerPlayer playerA, ServerPlayer playerB) {
        OffhandCombatState stateB = playerB.getData(OffhandCombatAttachments.COMBAT_STATE);
        OffhandAttackResult candidate = stateB.lastNetworkResult();
        if (candidate == null || candidate.targetId() != targetBId) {
            return;
        }

        resultB = verifySuccessfulFirstResult(playerB, stateB, candidate, targetBId, "client B");
        if (resultB == null) {
            return;
        }

        if (!verifyStablePlayerResult(playerA, targetAId, resultA, stableHealthA, stableDurabilityA, "client A")) {
            return;
        }

        stableHealthB = resultB.targetHealthAfter();
        stableDurabilityB = resultB.durabilityAfter();
        stageSuccessAtTick = elapsedTicks;
        phase = Phase.WAITING_FOR_B_STABILITY;
    }

    private static void verifyBothStableExactlyOnce(ServerPlayer playerA, ServerPlayer playerB) {
        if (elapsedTicks - stageSuccessAtTick < STABILITY_TICKS) {
            return;
        }

        if (!verifyStablePlayerResult(playerA, targetAId, resultA, stableHealthA, stableDurabilityA, "client A")) {
            return;
        }
        if (!verifyStablePlayerResult(playerB, targetBId, resultB, stableHealthB, stableDurabilityB, "client B")) {
            return;
        }
        if (playerA.getData(OffhandCombatAttachments.COMBAT_STATE)
                == playerB.getData(OffhandCombatAttachments.COMBAT_STATE)) {
            fail("two remote players unexpectedly shared the same combat-state object");
            return;
        }

        phase = Phase.PASSED;
        OffHandCombat.LOGGER.info(
                "Off Hand Combat two-client remote server E2E passed: "
                        + "A(sequence=1,target={},health={},durability={}), "
                        + "B(sequence=1,target={},health={},durability={})",
                targetAId, stableHealthA, stableDurabilityA,
                targetBId, stableHealthB, stableDurabilityB);
    }

    private static int spawnTarget(ServerPlayer player, String name) {
        ServerLevel level = player.serverLevel();
        Mob target = EntityType.COW.create(level);
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
        if (!level.addFreshEntity(target)) {
            fail("failed to add target " + name);
            return -1;
        }
        return target.getId();
    }

    private static OffhandAttackResult verifySuccessfulFirstResult(
            ServerPlayer player,
            OffhandCombatState state,
            OffhandAttackResult result,
            int expectedTargetId,
            String label) {
        if (result.status() != OffhandAttackStatus.SUCCESS) {
            fail(label + " network result was " + result.status());
            return null;
        }
        if (result.sequence() != 1L || state.lastNetworkSequence() != 1L) {
            fail(label + " did not begin with independent network sequence 1");
            return null;
        }
        if (!(result.targetHealthAfter() < result.targetHealthBefore())) {
            fail(label + " attack did not reduce target health");
            return null;
        }
        if (result.durabilityAfter() - result.durabilityBefore() != 1) {
            fail(label + " attack did not consume exactly one off-hand durability");
            return null;
        }

        Entity target = player.serverLevel().getEntity(expectedTargetId);
        if (!(target instanceof Mob living)) {
            fail(label + " target disappeared before authoritative verification");
            return null;
        }
        if (Float.compare(living.getHealth(), result.targetHealthAfter()) != 0) {
            fail(label + " authoritative target health did not match its result");
            return null;
        }
        if (player.getOffhandItem().getDamageValue() != result.durabilityAfter()) {
            fail(label + " authoritative durability did not match its result");
            return null;
        }
        return result;
    }

    private static boolean verifyStablePlayerResult(
            ServerPlayer player,
            int expectedTargetId,
            OffhandAttackResult expectedResult,
            float expectedHealth,
            int expectedDurability,
            String label) {
        Entity target = player.serverLevel().getEntity(expectedTargetId);
        OffhandCombatState state = player.getData(OffhandCombatAttachments.COMBAT_STATE);
        if (!(target instanceof Mob living)) {
            fail(label + " target disappeared during stability verification");
            return false;
        }
        if (state.lastNetworkSequence() != 1L || !expectedResult.equals(state.lastNetworkResult())) {
            fail(label + " duplicate replay changed its sequence or cached result");
            return false;
        }
        if (Float.compare(living.getHealth(), expectedHealth) != 0) {
            fail(label + " duplicate replay changed target health a second time");
            return false;
        }
        if (player.getOffhandItem().getDamageValue() != expectedDurability) {
            fail(label + " duplicate replay consumed durability a second time");
            return false;
        }
        return true;
    }

    private static void fail(String reason) {
        if (phase != Phase.FAILED) {
            phase = Phase.FAILED;
            OffHandCombat.LOGGER.error("Off Hand Combat two-client remote server E2E failed: {}", reason);
        }
    }

    private static void fail(String reason, Throwable throwable) {
        if (phase != Phase.FAILED) {
            phase = Phase.FAILED;
            OffHandCombat.LOGGER.error("Off Hand Combat two-client remote server E2E failed: {}", reason, throwable);
        }
    }

    private enum Phase {
        WAITING_FOR_PLAYERS,
        WAITING_FOR_EQUIPMENT_SETTLE,
        WAITING_FOR_A_RESULT,
        WAITING_FOR_A_STABILITY,
        WAITING_FOR_B_RESULT,
        WAITING_FOR_B_STABILITY,
        PASSED,
        FAILED
    }
}
