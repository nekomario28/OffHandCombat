package dev.nekomario.offhandcombat.combat;

import dev.nekomario.offhandcombat.OffHandCombat;
import dev.nekomario.offhandcombat.api.HandReadiness;
import dev.nekomario.offhandcombat.api.OffhandAttackAccess;
import dev.nekomario.offhandcombat.api.OffhandAttackContext;
import dev.nekomario.offhandcombat.api.OffhandAttackRequest;
import dev.nekomario.offhandcombat.api.OffhandAttackResult;
import dev.nekomario.offhandcombat.api.OffhandAttackSource;
import dev.nekomario.offhandcombat.api.OffhandAttackStatus;
import dev.nekomario.offhandcombat.api.OffhandCombatApi;
import dev.nekomario.offhandcombat.api.OffhandWeaponEligibility;
import dev.nekomario.offhandcombat.api.event.OffhandAttackEvent;
import dev.nekomario.offhandcombat.attachment.OffhandCombatAttachments;
import dev.nekomario.offhandcombat.attachment.OffhandCombatState;
import dev.nekomario.offhandcombat.config.OffHandCombatConfig;
import dev.nekomario.offhandcombat.util.SequenceWindow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;

public final class OffhandAttackService implements OffhandCombatApi {
    public static final OffhandAttackService INSTANCE = new OffhandAttackService();

    private OffhandAttackService() {
    }

    @Override
    public OffhandAttackResult request(ServerPlayer player, Entity target) {
        OffhandCombatState state = player.getData(OffhandCombatAttachments.COMBAT_STATE);
        return request(player, new OffhandAttackRequest(
                state.nextApiSequence(), target.getId(), OffhandAttackSource.PUBLIC_API));
    }

    @Override
    public OffhandAttackResult request(ServerPlayer player, OffhandAttackRequest request) {
        long gameTime = player.level().getGameTime();
        OffhandCombatState state = player.getData(OffhandCombatAttachments.COMBAT_STATE);

        if (request.source() == OffhandAttackSource.NETWORK) {
            SequenceWindow.Decision sequenceDecision = state.classifyNetworkSequence(request.sequence());
            if (sequenceDecision == SequenceWindow.Decision.DUPLICATE) {
                OffhandAttackResult cached = state.lastNetworkResult();
                return cached != null ? cached : finishNetwork(state,
                        OffhandAttackResult.rejected(request.sequence(), request.targetId(),
                                OffhandAttackStatus.DUPLICATE_WITHOUT_RESULT, gameTime));
            }
            if (sequenceDecision == SequenceWindow.Decision.STALE) {
                return OffhandAttackResult.rejected(request.sequence(), request.targetId(),
                        OffhandAttackStatus.STALE_SEQUENCE, gameTime);
            }
            if (sequenceDecision == SequenceWindow.Decision.INVALID) {
                return OffhandAttackResult.rejected(request.sequence(), request.targetId(),
                        OffhandAttackStatus.INVALID_SEQUENCE, gameTime);
            }
            if (!state.acceptRateLimitedRequest(gameTime, OffHandCombatConfig.REQUEST_COOLDOWN_TICKS.getAsInt())) {
                return finishNetwork(state, OffhandAttackResult.rejected(request.sequence(), request.targetId(),
                        OffhandAttackStatus.RATE_LIMITED, gameTime));
            }
        }

        OffhandAttackResult result;
        try {
            result = executeValidated(player, request, gameTime);
        } catch (RuntimeException exception) {
            OffHandCombat.LOGGER.error("Off-hand attack failed for {}", player.getGameProfile().getName(), exception);
            result = OffhandAttackResult.rejected(request.sequence(), request.targetId(),
                    OffhandAttackStatus.INTERNAL_ERROR, gameTime);
        }
        return request.source() == OffhandAttackSource.NETWORK ? finishNetwork(state, result) : result;
    }

    private OffhandAttackResult executeValidated(ServerPlayer player, OffhandAttackRequest request, long gameTime) {
        if (!player.isAlive() || player.isSpectator()) {
            return OffhandAttackResult.rejected(request.sequence(), request.targetId(),
                    OffhandAttackStatus.PLAYER_UNAVAILABLE, gameTime);
        }
        if (player.isUsingItem()) {
            return OffhandAttackResult.rejected(request.sequence(), request.targetId(),
                    OffhandAttackStatus.PLAYER_BUSY, gameTime);
        }

        ItemStack offhand = player.getItemInHand(InteractionHand.OFF_HAND);
        OffhandWeaponEligibility eligibility = checkEligibility(player, offhand);
        if (!eligibility.eligible()) {
            return OffhandAttackResult.rejected(request.sequence(), request.targetId(),
                    OffhandAttackStatus.INELIGIBLE_WEAPON, gameTime);
        }

        Entity target = player.level().getEntity(request.targetId());
        if (target == null || target == player || !target.isAlive() || target.isRemoved() || !target.isAttackable()) {
            return OffhandAttackResult.rejected(request.sequence(), request.targetId(),
                    OffhandAttackStatus.INVALID_TARGET, gameTime);
        }
        if (target instanceof LivingEntity living && living.isDeadOrDying()) {
            return OffhandAttackResult.rejected(request.sequence(), request.targetId(),
                    OffhandAttackStatus.INVALID_TARGET, gameTime);
        }
        if (!player.canInteractWithEntity(target, 0.0D)) {
            return OffhandAttackResult.rejected(request.sequence(), request.targetId(),
                    OffhandAttackStatus.OUT_OF_RANGE, gameTime);
        }
        if (OffHandCombatConfig.REQUIRE_LINE_OF_SIGHT.getAsBoolean() && !player.hasLineOfSight(target)) {
            return OffhandAttackResult.rejected(request.sequence(), request.targetId(),
                    OffhandAttackStatus.NO_LINE_OF_SIGHT, gameTime);
        }

        OffhandAttackAccess access = (OffhandAttackAccess) player;
        float offReadiness = access.ofc$getOffhandAttackStrengthScale(0.5F);
        if (offReadiness < OffHandCombatConfig.MINIMUM_OFFHAND_ATTACK_STRENGTH.getAsDouble()) {
            return OffhandAttackResult.rejected(request.sequence(), request.targetId(),
                    OffhandAttackStatus.NOT_READY, gameTime);
        }

        ItemStack mainSnapshot = player.getMainHandItem().copy();
        ItemStack offSnapshot = offhand.copy();
        OffhandAttackContext context = new OffhandAttackContext(
                player, target, request, mainSnapshot, offSnapshot,
                player.getAttackStrengthScale(0.5F), offReadiness);
        OffhandAttackEvent.Before before = new OffhandAttackEvent.Before(context);
        if (NeoForge.EVENT_BUS.post(before).isCanceled()) {
            return OffhandAttackResult.rejected(request.sequence(), request.targetId(),
                    OffhandAttackStatus.CANCELED_BY_EVENT, gameTime);
        }

        float healthBefore = target instanceof LivingEntity living ? living.getHealth() : Float.NaN;
        int durabilityBefore = offhand.isDamageableItem() ? offhand.getDamageValue() : -1;
        access.ofc$attackWithOffhand(target);
        player.swing(InteractionHand.OFF_HAND, true);
        float healthAfter = target instanceof LivingEntity living ? living.getHealth() : Float.NaN;
        int durabilityAfter = offhand.isDamageableItem() ? offhand.getDamageValue() : -1;

        // SUCCESS means the authoritative vanilla attack path executed exactly once. Damage may
        // legitimately remain unchanged because vanilla invulnerability or another mod rejected it.
        OffhandAttackResult result = new OffhandAttackResult(
                request.sequence(), request.targetId(), OffhandAttackStatus.SUCCESS,
                healthBefore, healthAfter, durabilityBefore, durabilityAfter, gameTime);
        NeoForge.EVENT_BUS.post(new OffhandAttackEvent.After(context, result));
        return result;
    }

    private static OffhandAttackResult finishNetwork(OffhandCombatState state, OffhandAttackResult result) {
        state.setLastNetworkResult(result);
        return result;
    }

    @Override
    public OffhandWeaponEligibility checkEligibility(ServerPlayer player, ItemStack stack) {
        return OffhandWeaponRules.evaluate(player, stack);
    }

    @Override
    public HandReadiness getReadiness(ServerPlayer player, float partialTick) {
        return new HandReadiness(player.getAttackStrengthScale(partialTick),
                ((OffhandAttackAccess) player).ofc$getOffhandAttackStrengthScale(partialTick));
    }
}
