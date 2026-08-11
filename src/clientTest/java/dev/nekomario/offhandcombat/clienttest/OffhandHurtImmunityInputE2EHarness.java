package dev.nekomario.offhandcombat.clienttest;

import dev.nekomario.offhandcombat.OffHandCombat;
import dev.nekomario.offhandcombat.api.OffhandAttackAccess;
import dev.nekomario.offhandcombat.api.OffhandAttackResult;
import dev.nekomario.offhandcombat.api.OffhandAttackStatus;
import dev.nekomario.offhandcombat.attachment.OffhandCombatAttachments;
import dev.nekomario.offhandcombat.network.OffhandAttackRequestPayload;
import java.util.UUID;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = OffHandCombat.MOD_ID, value = Dist.CLIENT)
public final class OffhandHurtImmunityInputE2EHarness {
    private static final String ENABLE_PROPERTY = "offhandcombat.hurtImmunityInputE2E";
    private static final String WORLD_NAME = "HurtImmunityWorld";
    private static final int TIMEOUT_CLIENT_TICKS = 1200;
    private static final int RESULT_TIMEOUT_TICKS = 80;

    private static volatile Phase phase = Phase.WAITING_FOR_WORLD;
    private static volatile int targetId = -1;
    private static volatile float initialHealth;
    private static volatile float healthAfterMain;
    private static volatile int hurtWindowBeforeOffhand;
    private static int clientTicks;
    private static int deadline;

    private OffhandHurtImmunityInputE2EHarness() {
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
                case WAITING_FOR_CLIENT_SYNC -> triggerPhysicalMainAttack(minecraft);
                case WAITING_FOR_MAIN_DAMAGE -> observeMainAttack(minecraft);
                case WAITING_TO_TRIGGER_OFFHAND -> triggerPhysicalOffhandAttack(minecraft);
                case WAITING_FOR_OFFHAND_RESULT -> verifyOffhandResult(minecraft);
                default -> {
                }
            }
        } catch (Throwable throwable) {
            fail("hurt-immunity input harness exception", throwable);
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
            fail("copied hurt-immunity E2E world was unavailable: " + WORLD_NAME);
            return;
        }

        phase = Phase.OPENING_WORLD;
        minecraft.createWorldOpenFlows().openWorld(
                WORLD_NAME,
                () -> fail("opening copied hurt-immunity E2E world was aborted"));
    }

    private static void setupWhenReady(Minecraft minecraft) {
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
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WOODEN_SWORD));
                player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.IRON_SWORD));
                player.setYRot(0.0F);
                player.setXRot(0.0F);
                player.setYHeadRot(0.0F);
                ((OffhandAttackAccess) player).ofc$setOffhandAttackStrengthTicker(100);

                Cow target = EntityType.COW.create(player.serverLevel());
                if (target == null) {
                    fail("failed to create hurt-immunity E2E target");
                    return;
                }
                target.setNoAi(true);
                target.setNoGravity(true);
                target.setPersistenceRequired();
                target.setHealth(target.getMaxHealth());
                target.moveTo(
                        player.getX(),
                        player.getEyeY() - target.getBbHeight() * 0.5D,
                        player.getZ() + 2.0D,
                        0.0F,
                        0.0F);
                if (!player.serverLevel().addFreshEntity(target)) {
                    fail("failed to add hurt-immunity E2E target");
                    return;
                }

                targetId = target.getId();
                initialHealth = target.getHealth();
                phase = Phase.WAITING_FOR_CLIENT_SYNC;
            } catch (Throwable throwable) {
                fail("hurt-immunity server setup exception", throwable);
            }
        });
    }

    private static void triggerPhysicalMainAttack(Minecraft minecraft) {
        if (minecraft.level == null
                || minecraft.player == null
                || minecraft.screen != null
                || !minecraft.player.getMainHandItem().is(Items.WOODEN_SWORD)
                || !minecraft.player.getOffhandItem().is(Items.IRON_SWORD)
                || minecraft.player.getAttackStrengthScale(0.0F) < 0.99F) {
            return;
        }
        Entity target = minecraft.level.getEntity(targetId);
        if (!(target instanceof LivingEntity)) {
            return;
        }

        aimAtTarget(minecraft, target);
        KeyMapping.click(minecraft.options.keyAttack.getKey());
        deadline = clientTicks + RESULT_TIMEOUT_TICKS;
        phase = Phase.WAITING_FOR_MAIN_DAMAGE;
    }

    private static void observeMainAttack(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null || minecraft.getSingleplayerServer() == null) {
            return;
        }
        Entity target = minecraft.level.getEntity(targetId);
        if (!(target instanceof LivingEntity living)) {
            return;
        }
        if (!(living.getHealth() < initialHealth)) {
            if (clientTicks >= deadline) {
                fail("physical main-hand attack did not damage the target");
            }
            return;
        }

        phase = Phase.VERIFYING_MAIN_ATTACK;
        UUID playerId = minecraft.player.getUUID();
        var server = minecraft.getSingleplayerServer();
        server.execute(() -> {
            try {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                Entity serverTarget = player == null ? null : player.serverLevel().getEntity(targetId);
                if (player == null || !(serverTarget instanceof LivingEntity serverLiving)) {
                    fail("server state was unavailable after physical main-hand attack");
                    return;
                }
                if (!(serverLiving.getHealth() < initialHealth)) {
                    fail("server did not observe physical main-hand damage");
                    return;
                }
                if (serverLiving.invulnerableTime <= 0) {
                    fail("physical main-hand attack did not establish vanilla hurt immunity");
                    return;
                }
                if (player.getOffhandItem().getDamageValue() != 0) {
                    fail("physical main-hand attack consumed off-hand durability");
                    return;
                }
                var state = player.getData(OffhandCombatAttachments.COMBAT_STATE);
                if (state.lastNetworkSequence() != 0L || state.lastNetworkResult() != null) {
                    fail("physical main-hand attack emitted an Off Hand Combat request");
                    return;
                }

                healthAfterMain = serverLiving.getHealth();
                hurtWindowBeforeOffhand = serverLiving.invulnerableTime;

                // Bypass only the intentional cross-hand readiness cap so this E2E reaches the
                // authoritative vanilla hurt-immunity decision. The hurt window itself is untouched.
                ((OffhandAttackAccess) player).ofc$setOffhandAttackStrengthTicker(100);
                phase = Phase.WAITING_TO_TRIGGER_OFFHAND;
            } catch (Throwable throwable) {
                fail("main-hand hurt-window verification exception", throwable);
            }
        });
    }

    private static void triggerPhysicalOffhandAttack(Minecraft minecraft) {
        if (minecraft.level == null
                || minecraft.player == null
                || minecraft.screen != null
                || !minecraft.player.getOffhandItem().is(Items.IRON_SWORD)) {
            return;
        }
        Entity target = minecraft.level.getEntity(targetId);
        if (!(target instanceof LivingEntity)) {
            return;
        }

        aimAtTarget(minecraft, target);
        KeyMapping.click(minecraft.options.keyUse.getKey());
        deadline = clientTicks + RESULT_TIMEOUT_TICKS;
        phase = Phase.WAITING_FOR_OFFHAND_RESULT;
    }

    private static void verifyOffhandResult(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.getSingleplayerServer() == null) {
            return;
        }
        OffhandAttackResult result = minecraft.player
                .getData(OffhandCombatAttachments.COMBAT_STATE)
                .lastClientResult();
        if (result == null || result.targetId() != targetId) {
            if (clientTicks >= deadline) {
                fail("physical off-hand right-click did not produce an attack result");
            }
            return;
        }
        if (result.status() != OffhandAttackStatus.SUCCESS) {
            fail("hurt-window off-hand result was " + result.status());
            return;
        }
        if (Float.compare(result.targetHealthBefore(), result.targetHealthAfter()) != 0) {
            fail("hurt-window off-hand result reported duplicate damage");
            return;
        }

        phase = Phase.VERIFYING_OFFHAND_ATTACK;
        UUID playerId = minecraft.player.getUUID();
        var server = minecraft.getSingleplayerServer();
        server.execute(() -> {
            try {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                Entity serverTarget = player == null ? null : player.serverLevel().getEntity(targetId);
                if (player == null || !(serverTarget instanceof LivingEntity serverLiving)) {
                    fail("server state was unavailable after physical off-hand input");
                    return;
                }
                var state = player.getData(OffhandCombatAttachments.COMBAT_STATE);
                OffhandAttackResult serverResult = state.lastNetworkResult();
                if (state.lastNetworkSequence() != 1L
                        || serverResult == null
                        || serverResult.status() != OffhandAttackStatus.SUCCESS) {
                    fail("physical off-hand input did not execute one authoritative request");
                    return;
                }
                if (Float.compare(serverLiving.getHealth(), healthAfterMain) != 0) {
                    fail("physical alternating input bypassed vanilla hurt immunity");
                    return;
                }
                if (player.getOffhandItem().getDamageValue() != 0) {
                    fail("hurt-immune off-hand hit consumed durability");
                    return;
                }
                int hurtWindowAfterOffhand = serverLiving.invulnerableTime;
                if (hurtWindowAfterOffhand <= 0) {
                    fail("physical off-hand input cleared vanilla hurt immunity");
                    return;
                }
                if (hurtWindowAfterOffhand > hurtWindowBeforeOffhand) {
                    fail("physical off-hand input extended/reset vanilla hurt immunity: before="
                            + hurtWindowBeforeOffhand + ", after=" + hurtWindowAfterOffhand);
                    return;
                }

                phase = Phase.PASSED;
                OffHandCombat.LOGGER.info(
                        "Off Hand Combat rapid alternating physical-input hurt-immunity E2E passed: health={} -> {}, hurtWindowBeforeOffhand={}, hurtWindowAfterOffhand={}, offhandStatus={}, offhandDurability=0",
                        initialHealth,
                        healthAfterMain,
                        hurtWindowBeforeOffhand,
                        hurtWindowAfterOffhand,
                        serverResult.status());
            } catch (Throwable throwable) {
                fail("off-hand hurt-window verification exception", throwable);
            }
        });
    }

    private static void aimAtTarget(Minecraft minecraft, Entity target) {
        minecraft.player.setYRot(0.0F);
        minecraft.player.setXRot(0.0F);
        minecraft.hitResult = new EntityHitResult(target);
    }

    private static void fail(String reason) {
        if (phase != Phase.FAILED) {
            phase = Phase.FAILED;
            OffHandCombat.LOGGER.error("Off Hand Combat rapid alternating physical-input hurt-immunity E2E failed: {}", reason);
        }
    }

    private static void fail(String reason, Throwable throwable) {
        if (phase != Phase.FAILED) {
            phase = Phase.FAILED;
            OffHandCombat.LOGGER.error(
                    "Off Hand Combat rapid alternating physical-input hurt-immunity E2E failed: {}",
                    reason,
                    throwable);
        }
    }

    private enum Phase {
        WAITING_FOR_WORLD,
        OPENING_WORLD,
        SETTING_UP,
        WAITING_FOR_CLIENT_SYNC,
        WAITING_FOR_MAIN_DAMAGE,
        VERIFYING_MAIN_ATTACK,
        WAITING_TO_TRIGGER_OFFHAND,
        WAITING_FOR_OFFHAND_RESULT,
        VERIFYING_OFFHAND_ATTACK,
        PASSED,
        FAILED
    }
}
