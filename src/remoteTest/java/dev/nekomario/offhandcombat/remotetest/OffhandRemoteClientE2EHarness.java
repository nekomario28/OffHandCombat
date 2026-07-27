package dev.nekomario.offhandcombat.remotetest;

import dev.nekomario.offhandcombat.OffHandCombat;
import dev.nekomario.offhandcombat.api.OffhandAttackResult;
import dev.nekomario.offhandcombat.api.OffhandAttackStatus;
import dev.nekomario.offhandcombat.attachment.OffhandCombatAttachments;
import dev.nekomario.offhandcombat.client.ClientModEvents;
import dev.nekomario.offhandcombat.network.OffhandAttackRequestPayload;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = OffHandCombat.MOD_ID, value = Dist.CLIENT)
public final class OffhandRemoteClientE2EHarness {
    private static final String ENABLE_PROPERTY = "offhandcombat.remoteClientE2E";
    private static final int TIMEOUT_TICKS = 3600;

    private static Phase phase = Phase.WAITING_FOR_CONNECTION;
    private static int elapsedTicks;
    private static int targetId = -1;
    private static OffhandAttackResult firstResult;

    private OffhandRemoteClientE2EHarness() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean(ENABLE_PROPERTY) || phase == Phase.PASSED || phase == Phase.FAILED) {
            return;
        }

        try {
            elapsedTicks++;
            if (elapsedTicks > TIMEOUT_TICKS) {
                fail("timed out in phase " + phase);
                return;
            }

            Minecraft minecraft = Minecraft.getInstance();
            if (phase == Phase.WAITING_FOR_CONNECTION) {
                waitForRemoteConnection(minecraft);
            } else if (phase == Phase.WAITING_FOR_TARGET) {
                findTargetAndTriggerAttack(minecraft);
            } else if (phase == Phase.WAITING_FOR_FIRST_RESULT) {
                acceptFirstResultAndReplay(minecraft);
            } else if (phase == Phase.WAITING_FOR_REPLAY_RESULT) {
                verifyReplayResult(minecraft);
            }
        } catch (Throwable throwable) {
            fail("remote client harness exception", throwable);
        }
    }

    private static void waitForRemoteConnection(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null || minecraft.getConnection() == null) {
            return;
        }
        if (minecraft.getSingleplayerServer() != null) {
            fail("remote client unexpectedly created an integrated server");
            return;
        }
        if (!minecraft.getConnection().hasChannel(OffhandAttackRequestPayload.TYPE)) {
            return;
        }
        if (!OffhandRemoteServerE2EHarness.PLAYER_NAME.equals(minecraft.player.getGameProfile().getName())) {
            fail("remote client joined with the wrong username");
            return;
        }

        phase = Phase.WAITING_FOR_TARGET;
        OffHandCombat.LOGGER.info("Off Hand Combat remote client connected to a separate server");
    }

    private static void findTargetAndTriggerAttack(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null || minecraft.screen != null
                || !minecraft.player.getOffhandItem().is(Items.IRON_SWORD)) {
            return;
        }

        List<Mob> targets = minecraft.level.getEntitiesOfClass(
                Mob.class,
                minecraft.player.getBoundingBox().inflate(16.0D),
                target -> target.hasCustomName()
                        && OffhandRemoteServerE2EHarness.TARGET_NAME.equals(target.getCustomName().getString()));
        if (targets.size() != 1) {
            return;
        }

        Mob target = targets.getFirst();
        targetId = target.getId();
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
            fail("remote result was " + result.status());
            return;
        }
        if (result.sequence() != 1L) {
            fail("remote request did not begin with sequence 1");
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

        firstResult = result;
        state.setLastClientResult(OffhandAttackResult.rejected(
                Long.MAX_VALUE, -1, OffhandAttackStatus.INTERNAL_ERROR, result.gameTime()));
        PacketDistributor.sendToServer(new OffhandAttackRequestPayload(result.sequence(), result.targetId()));
        phase = Phase.WAITING_FOR_REPLAY_RESULT;
    }

    private static void verifyReplayResult(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null || firstResult == null) {
            return;
        }

        OffhandAttackResult replay = minecraft.player
                .getData(OffhandCombatAttachments.COMBAT_STATE)
                .lastClientResult();
        if (!firstResult.equals(replay)) {
            return;
        }

        var target = minecraft.level.getEntity(targetId);
        if (!(target instanceof Mob living)) {
            return;
        }
        if (Float.compare(living.getHealth(), firstResult.targetHealthAfter()) != 0) {
            return;
        }
        if (minecraft.player.getOffhandItem().getDamageValue() != firstResult.durabilityAfter()) {
            return;
        }

        phase = Phase.PASSED;
        OffHandCombat.LOGGER.info(
                "Off Hand Combat remote client E2E passed: sequence={}, target={}, health={} -> {}, durability={} -> {}",
                firstResult.sequence(), firstResult.targetId(),
                firstResult.targetHealthBefore(), firstResult.targetHealthAfter(),
                firstResult.durabilityBefore(), firstResult.durabilityAfter());
    }

    private static void fail(String reason) {
        if (phase != Phase.FAILED) {
            phase = Phase.FAILED;
            OffHandCombat.LOGGER.error("Off Hand Combat remote client E2E failed: {}", reason);
        }
    }

    private static void fail(String reason, Throwable throwable) {
        if (phase != Phase.FAILED) {
            phase = Phase.FAILED;
            OffHandCombat.LOGGER.error("Off Hand Combat remote client E2E failed: {}", reason, throwable);
        }
    }

    private enum Phase {
        WAITING_FOR_CONNECTION,
        WAITING_FOR_TARGET,
        WAITING_FOR_FIRST_RESULT,
        WAITING_FOR_REPLAY_RESULT,
        PASSED,
        FAILED
    }
}
