package dev.nekomario.offhandcombat.remotetest;

import dev.nekomario.offhandcombat.OffHandCombat;
import dev.nekomario.offhandcombat.api.OffhandAttackAccess;
import dev.nekomario.offhandcombat.api.OffhandAttackResult;
import dev.nekomario.offhandcombat.api.OffhandAttackStatus;
import dev.nekomario.offhandcombat.attachment.OffhandCombatAttachments;
import dev.nekomario.offhandcombat.attachment.OffhandCombatState;
import net.minecraft.core.BlockPos;
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
    public static final String PLAYER_NAME = "OffhandRemote";
    public static final String TARGET_NAME = "OffhandRemoteTarget";

    private static final String ENABLE_PROPERTY = "offhandcombat.remoteServerE2E";
    private static final int TIMEOUT_TICKS = 3600;
    private static final int EQUIPMENT_SETTLE_TICKS = 20;
    private static final int STABILITY_TICKS = 20;

    private static Phase phase = Phase.WAITING_FOR_PLAYER;
    private static int elapsedTicks;
    private static int equippedAtTick;
    private static int firstSuccessAtTick;
    private static int targetId = -1;
    private static float stableHealth;
    private static int stableDurability;

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
                fail("remote server harness was not running on a dedicated server");
                return;
            }

            ServerPlayer player = server.getPlayerList().getPlayerByName(PLAYER_NAME);
            if (phase == Phase.WAITING_FOR_PLAYER) {
                if (player != null) {
                    equipAndPosition(player);
                }
            } else if (phase == Phase.WAITING_FOR_EQUIPMENT_SETTLE) {
                if (player == null) {
                    fail("remote player disconnected during setup");
                } else if (elapsedTicks - equippedAtTick >= EQUIPMENT_SETTLE_TICKS) {
                    armAndSpawnTarget(player);
                }
            } else if (phase == Phase.WAITING_FOR_RESULT) {
                verifyFirstResult(player);
            } else if (phase == Phase.WAITING_FOR_STABILITY) {
                verifyStableExactlyOnceResult(player);
            }
        } catch (Throwable throwable) {
            fail("remote server harness exception", throwable);
        }
    }

    private static void equipAndPosition(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        int baseX = 0;
        int baseZ = 0;
        int baseY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, baseX, baseZ) + 1;

        for (int x = -2; x <= 2; x++) {
            for (int z = -1; z <= 4; z++) {
                BlockPos floor = new BlockPos(baseX + x, baseY - 1, baseZ + z);
                level.setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
                level.setBlockAndUpdate(floor.above(), Blocks.AIR.defaultBlockState());
                level.setBlockAndUpdate(floor.above(2), Blocks.AIR.defaultBlockState());
            }
        }

        player.setGameMode(GameType.SURVIVAL);
        player.teleportTo(baseX + 0.5D, baseY, baseZ + 0.5D);
        player.setYRot(0.0F);
        player.setXRot(0.0F);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WOODEN_SWORD));
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.IRON_SWORD));

        equippedAtTick = elapsedTicks;
        phase = Phase.WAITING_FOR_EQUIPMENT_SETTLE;
        OffHandCombat.LOGGER.info("Off Hand Combat remote server player connected and equipped");
    }

    private static void armAndSpawnTarget(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        ((OffhandAttackAccess) player).ofc$setOffhandAttackStrengthTicker(100);

        Mob target = EntityType.COW.create(level);
        if (target == null) {
            fail("failed to create remote E2E target");
            return;
        }
        target.setNoAi(true);
        target.setPersistenceRequired();
        target.setCustomName(net.minecraft.network.chat.Component.literal(TARGET_NAME));
        target.setCustomNameVisible(true);
        target.setHealth(target.getMaxHealth());
        target.moveTo(player.getX(), player.getY(), player.getZ() + 2.0D, 0.0F, 0.0F);
        if (!level.addFreshEntity(target)) {
            fail("failed to add remote E2E target");
            return;
        }

        targetId = target.getId();
        phase = Phase.WAITING_FOR_RESULT;
        OffHandCombat.LOGGER.info("Off Hand Combat remote server E2E armed: player={}, target={}",
                player.getGameProfile().getName(), targetId);
    }

    private static void verifyFirstResult(ServerPlayer player) {
        if (player == null) {
            fail("remote player disconnected before the result arrived");
            return;
        }

        OffhandCombatState state = player.getData(OffhandCombatAttachments.COMBAT_STATE);
        OffhandAttackResult result = state.lastNetworkResult();
        if (result == null || result.targetId() != targetId) {
            return;
        }
        if (result.status() != OffhandAttackStatus.SUCCESS) {
            fail("remote network result was " + result.status());
            return;
        }
        if (result.sequence() != 1L || state.lastNetworkSequence() != 1L) {
            fail("remote client did not begin with network sequence 1");
            return;
        }
        if (!(result.targetHealthAfter() < result.targetHealthBefore())) {
            fail("remote attack did not reduce target health");
            return;
        }
        if (result.durabilityAfter() - result.durabilityBefore() != 1) {
            fail("remote attack did not consume exactly one off-hand durability");
            return;
        }

        Entity target = player.serverLevel().getEntity(targetId);
        if (!(target instanceof Mob living)) {
            fail("remote target disappeared before authoritative verification");
            return;
        }
        if (Float.compare(living.getHealth(), result.targetHealthAfter()) != 0) {
            fail("authoritative target health did not match the remote result");
            return;
        }
        if (player.getOffhandItem().getDamageValue() != result.durabilityAfter()) {
            fail("authoritative off-hand durability did not match the remote result");
            return;
        }

        stableHealth = living.getHealth();
        stableDurability = player.getOffhandItem().getDamageValue();
        firstSuccessAtTick = elapsedTicks;
        phase = Phase.WAITING_FOR_STABILITY;
    }

    private static void verifyStableExactlyOnceResult(ServerPlayer player) {
        if (player == null) {
            fail("remote player disconnected during duplicate-replay verification");
            return;
        }
        if (elapsedTicks - firstSuccessAtTick < STABILITY_TICKS) {
            return;
        }

        Entity target = player.serverLevel().getEntity(targetId);
        OffhandCombatState state = player.getData(OffhandCombatAttachments.COMBAT_STATE);
        if (!(target instanceof Mob living)) {
            fail("remote target disappeared during stability verification");
            return;
        }
        if (state.lastNetworkSequence() != 1L) {
            fail("remote client advanced the sequence unexpectedly");
            return;
        }
        if (Float.compare(living.getHealth(), stableHealth) != 0) {
            fail("duplicate replay changed target health a second time");
            return;
        }
        if (player.getOffhandItem().getDamageValue() != stableDurability) {
            fail("duplicate replay consumed off-hand durability a second time");
            return;
        }

        phase = Phase.PASSED;
        OffHandCombat.LOGGER.info(
                "Off Hand Combat remote server E2E passed: player={}, sequence=1, target={}, health={}, durability={}",
                player.getGameProfile().getName(), targetId, stableHealth, stableDurability);
    }

    private static void fail(String reason) {
        if (phase != Phase.FAILED) {
            phase = Phase.FAILED;
            OffHandCombat.LOGGER.error("Off Hand Combat remote server E2E failed: {}", reason);
        }
    }

    private static void fail(String reason, Throwable throwable) {
        if (phase != Phase.FAILED) {
            phase = Phase.FAILED;
            OffHandCombat.LOGGER.error("Off Hand Combat remote server E2E failed: {}", reason, throwable);
        }
    }

    private enum Phase {
        WAITING_FOR_PLAYER,
        WAITING_FOR_EQUIPMENT_SETTLE,
        WAITING_FOR_RESULT,
        WAITING_FOR_STABILITY,
        PASSED,
        FAILED
    }
}
