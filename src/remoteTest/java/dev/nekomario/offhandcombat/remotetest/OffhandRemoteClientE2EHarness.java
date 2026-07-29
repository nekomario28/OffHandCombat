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
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
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
    private static final String ROLE_PROPERTY = "offhandcombat.remoteClientRole";
    private static final String SERVER_ADDRESS = "127.0.0.1:25565";
    private static final int CONNECT_AFTER_TICKS = 20;
    private static final int TIMEOUT_TICKS = 4800;
    private static final float EXPECTED_FINAL_HEALTH = 4.0F;

    private static Phase phase = Phase.WAITING_TO_CONNECT;
    private static int elapsedTicks;
    private static int targetId = -1;
    private static OffhandAttackResult firstResult;
    private static Role role;

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

            if (role == null) {
                role = Role.parse(System.getProperty(ROLE_PROPERTY));
                if (role == null) {
                    fail("missing or invalid remote client role property");
                    return;
                }
            }

            Minecraft minecraft = Minecraft.getInstance();
            if (phase == Phase.WAITING_TO_CONNECT) {
                connectWhenClientIsReady(minecraft);
            } else if (phase == Phase.WAITING_FOR_CONNECTION) {
                waitForRemoteConnection(minecraft);
            } else if (phase == Phase.WAITING_FOR_TARGET) {
                findTargetAndTriggerAttack(minecraft);
            } else if (phase == Phase.WAITING_FOR_FIRST_RESULT) {
                acceptFirstResultAndReplay(minecraft);
            } else if (phase == Phase.WAITING_FOR_REPLAY_RESULT) {
                verifyReplayResult(minecraft);
            } else if (phase == Phase.WAITING_FOR_PARTNER_OBSERVATION) {
                verifyBothOneTimeResultsAreVisible(minecraft);
            }
        } catch (Throwable throwable) {
            fail("two-client remote client harness exception", throwable);
        }
    }

    private static void connectWhenClientIsReady(Minecraft minecraft) {
        if (elapsedTicks < CONNECT_AFTER_TICKS || minecraft.screen == null) {
            return;
        }
        if (minecraft.level != null || minecraft.player != null || minecraft.getConnection() != null) {
            fail("remote client had an unexpected active world or connection before setup");
            return;
        }

        phase = Phase.WAITING_FOR_CONNECTION;
        ServerData serverData = new ServerData(
                "Off Hand Combat two-client remote E2E", SERVER_ADDRESS, ServerData.Type.OTHER);
        OffHandCombat.LOGGER.info(
                "Off Hand Combat remote client {} connecting through vanilla ConnectScreen: {}",
                role.id, SERVER_ADDRESS);
        ConnectScreen.startConnecting(
                minecraft.screen,
                minecraft,
                ServerAddress.parseString(SERVER_ADDRESS),
                serverData,
                false,
                null);
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
        if (!role.playerName.equals(minecraft.player.getGameProfile().getName())) {
            fail("remote client joined with the wrong username for role " + role.id);
            return;
        }

        phase = Phase.WAITING_FOR_TARGET;
        OffHandCombat.LOGGER.info(
                "Off Hand Combat remote client {} connected to the separate server", role.id);
    }

    private static void findTargetAndTriggerAttack(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null || minecraft.screen != null
                || !minecraft.player.getOffhandItem().is(Items.IRON_SWORD)) {
            return;
        }

        Mob target = findNamedTarget(minecraft, role.targetName);
        if (target == null) {
            return;
        }

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
            fail("remote client " + role.id + " result was " + result.status());
            return;
        }
        if (result.sequence() != 1L) {
            fail("remote client " + role.id + " did not begin with independent sequence 1");
            return;
        }
        if (Float.compare(result.targetHealthAfter(), EXPECTED_FINAL_HEALTH) != 0
                || !(result.targetHealthAfter() < result.targetHealthBefore())) {
            fail("remote client " + role.id + " attack produced unexpected target health");
            return;
        }
        if (result.durabilityAfter() - result.durabilityBefore() != 1) {
            fail("remote client " + role.id + " attack did not consume exactly one durability");
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

        Mob target = findNamedTarget(minecraft, role.targetName);
        if (target == null || target.getId() != targetId) {
            return;
        }
        if (Float.compare(target.getHealth(), firstResult.targetHealthAfter()) != 0) {
            return;
        }
        if (minecraft.player.getOffhandItem().getDamageValue() != firstResult.durabilityAfter()) {
            return;
        }

        phase = Phase.WAITING_FOR_PARTNER_OBSERVATION;
        OffHandCombat.LOGGER.info(
                "Off Hand Combat remote client {} duplicate replay remained exactly-once", role.id);
    }

    private static void verifyBothOneTimeResultsAreVisible(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null || firstResult == null) {
            return;
        }

        OffhandAttackResult currentResult = minecraft.player
                .getData(OffhandCombatAttachments.COMBAT_STATE)
                .lastClientResult();
        if (!firstResult.equals(currentResult)) {
            fail("remote client " + role.id + " received another player's result or changed cached result");
            return;
        }

        Mob ownTarget = findNamedTarget(minecraft, role.targetName);
        Mob partnerTarget = findNamedTarget(minecraft, role.partnerTargetName);
        if (ownTarget == null || partnerTarget == null) {
            return;
        }
        if (Float.compare(ownTarget.getHealth(), EXPECTED_FINAL_HEALTH) != 0
                || Float.compare(partnerTarget.getHealth(), EXPECTED_FINAL_HEALTH) != 0) {
            return;
        }
        if (minecraft.player.getOffhandItem().getDamageValue() != 1) {
            fail("remote client " + role.id + " durability changed after partner execution");
            return;
        }

        phase = Phase.PASSED;
        OffHandCombat.LOGGER.info(
                "Off Hand Combat two-client remote client {} E2E passed: "
                        + "sequence=1, ownTarget={}, partnerTarget={}, health={}, durability=1",
                role.id, ownTarget.getId(), partnerTarget.getId(), EXPECTED_FINAL_HEALTH);
    }

    private static Mob findNamedTarget(Minecraft minecraft, String targetName) {
        if (minecraft.level == null || minecraft.player == null) {
            return null;
        }
        List<Mob> targets = minecraft.level.getEntitiesOfClass(
                Mob.class,
                minecraft.player.getBoundingBox().inflate(32.0D),
                target -> target.hasCustomName() && targetName.equals(target.getCustomName().getString()));
        return targets.size() == 1 ? targets.getFirst() : null;
    }

    private static void fail(String reason) {
        if (phase != Phase.FAILED) {
            phase = Phase.FAILED;
            OffHandCombat.LOGGER.error("Off Hand Combat two-client remote client E2E failed: {}", reason);
        }
    }

    private static void fail(String reason, Throwable throwable) {
        if (phase != Phase.FAILED) {
            phase = Phase.FAILED;
            OffHandCombat.LOGGER.error("Off Hand Combat two-client remote client E2E failed: {}", reason, throwable);
        }
    }

    private enum Role {
        A("A", OffhandRemoteServerE2EHarness.PLAYER_A_NAME,
                OffhandRemoteServerE2EHarness.TARGET_A_NAME,
                OffhandRemoteServerE2EHarness.TARGET_B_NAME),
        B("B", OffhandRemoteServerE2EHarness.PLAYER_B_NAME,
                OffhandRemoteServerE2EHarness.TARGET_B_NAME,
                OffhandRemoteServerE2EHarness.TARGET_A_NAME);

        private final String id;
        private final String playerName;
        private final String targetName;
        private final String partnerTargetName;

        Role(String id, String playerName, String targetName, String partnerTargetName) {
            this.id = id;
            this.playerName = playerName;
            this.targetName = targetName;
            this.partnerTargetName = partnerTargetName;
        }

        private static Role parse(String value) {
            if ("A".equals(value)) {
                return A;
            }
            if ("B".equals(value)) {
                return B;
            }
            return null;
        }
    }

    private enum Phase {
        WAITING_TO_CONNECT,
        WAITING_FOR_CONNECTION,
        WAITING_FOR_TARGET,
        WAITING_FOR_FIRST_RESULT,
        WAITING_FOR_REPLAY_RESULT,
        WAITING_FOR_PARTNER_OBSERVATION,
        PASSED,
        FAILED
    }
}
