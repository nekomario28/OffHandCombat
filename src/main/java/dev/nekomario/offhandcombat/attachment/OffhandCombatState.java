package dev.nekomario.offhandcombat.attachment;

import dev.nekomario.offhandcombat.api.OffhandAttackResult;
import dev.nekomario.offhandcombat.util.ActiveUseWindow;
import dev.nekomario.offhandcombat.util.SequenceWindow;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public final class OffhandCombatState {
    private int offhandAttackStrengthTicker;
    private int airSwingMissTicks;
    private int ticksSinceLastActiveUse = Integer.MAX_VALUE;
    private @Nullable InteractionHand lastActiveUseHand;
    private boolean auxiliarySwinging;
    private int auxiliarySwingTime;
    private int auxiliarySwingDuration;
    private @Nullable InteractionHand auxiliarySwingHand;
    private long lastAcceptedRequestTick = Long.MIN_VALUE;
    private final SequenceWindow networkSequences = new SequenceWindow();
    private long nextClientSequence = 1L;
    private long nextApiSequence = 1L;
    private ItemStack previousOffhand = ItemStack.EMPTY;
    private boolean attackingWithOffhand;
    private @Nullable AttributeMap activeOffhandAttributes;
    private @Nullable OffhandAttackResult lastNetworkResult;
    private @Nullable OffhandAttackResult lastClientResult;

    public void tickCooldown() {
        if (offhandAttackStrengthTicker < Integer.MAX_VALUE) {
            offhandAttackStrengthTicker++;
        }
        if (airSwingMissTicks > 0) {
            airSwingMissTicks--;
        }
    }

    public void tickActiveUseWindow() {
        ticksSinceLastActiveUse = ActiveUseWindow.advance(ticksSinceLastActiveUse);
    }

    public void captureAuxiliarySwing(InteractionHand hand, int swingTime, int swingDuration) {
        if (swingDuration <= 0) {
            clearAuxiliarySwing();
            return;
        }
        auxiliarySwinging = true;
        auxiliarySwingHand = hand;
        auxiliarySwingTime = Math.max(-1, swingTime);
        auxiliarySwingDuration = swingDuration;
    }

    public void tickAuxiliarySwing() {
        if (!auxiliarySwinging) {
            return;
        }
        auxiliarySwingTime++;
        if (auxiliarySwingTime >= auxiliarySwingDuration) {
            clearAuxiliarySwing();
        }
    }

    public boolean hasAuxiliarySwing() {
        return auxiliarySwinging;
    }

    public boolean hasAuxiliarySwing(InteractionHand hand) {
        return auxiliarySwinging && auxiliarySwingHand == hand;
    }

    public float auxiliarySwingProgress(InteractionHand hand, float partialTick) {
        if (!hasAuxiliarySwing(hand) || auxiliarySwingDuration <= 0) {
            return 0.0F;
        }
        float partial = Math.max(0.0F, Math.min(1.0F, partialTick));
        float progress = (auxiliarySwingTime + partial) / (float) auxiliarySwingDuration;
        return Math.max(0.0F, Math.min(1.0F, progress));
    }

    private void clearAuxiliarySwing() {
        auxiliarySwinging = false;
        auxiliarySwingTime = 0;
        auxiliarySwingDuration = 0;
        auxiliarySwingHand = null;
    }

    public int offhandAttackStrengthTicker() {
        return offhandAttackStrengthTicker;
    }

    public void setOffhandAttackStrengthTicker(int ticks) {
        offhandAttackStrengthTicker = Math.max(0, ticks);
    }

    public int airSwingMissTicks() {
        return airSwingMissTicks;
    }

    public void setAirSwingMissTicks(int ticks) {
        airSwingMissTicks = Math.max(0, ticks);
    }

    public void recordActiveUseStopped(InteractionHand hand) {
        lastActiveUseHand = hand;
        ticksSinceLastActiveUse = 0;
    }

    public boolean shouldDeferRecentlyUsedHand(InteractionHand hand, int windowTicks) {
        return lastActiveUseHand == hand
                && ActiveUseWindow.isOpen(ticksSinceLastActiveUse, windowTicks);
    }

    public boolean updateOffhandSnapshot(ItemStack current) {
        if (ItemStack.matches(previousOffhand, current)) {
            return false;
        }
        previousOffhand = current.copy();
        offhandAttackStrengthTicker = 0;
        return true;
    }

    public boolean acceptRateLimitedRequest(long gameTime, int minimumIntervalTicks) {
        if (lastAcceptedRequestTick != Long.MIN_VALUE) {
            long elapsed = gameTime - lastAcceptedRequestTick;
            if (elapsed >= 0L && elapsed < minimumIntervalTicks) {
                return false;
            }
        }
        lastAcceptedRequestTick = gameTime;
        return true;
    }

    public SequenceWindow.Decision classifyNetworkSequence(long sequence) {
        return networkSequences.classify(sequence);
    }

    public long lastNetworkSequence() {
        return networkSequences.lastAccepted();
    }

    public long nextClientSequence() {
        long value = nextClientSequence;
        nextClientSequence = incrementSequence(nextClientSequence);
        return value;
    }

    public void copyClientDimensionStateFrom(OffhandCombatState source) {
        nextClientSequence = source.nextClientSequence;
        lastClientResult = source.lastClientResult;
        ticksSinceLastActiveUse = source.ticksSinceLastActiveUse;
        lastActiveUseHand = source.lastActiveUseHand;
    }

    public long nextApiSequence() {
        long value = nextApiSequence;
        nextApiSequence = incrementSequence(nextApiSequence);
        return value;
    }

    private static long incrementSequence(long sequence) {
        return sequence == Long.MAX_VALUE ? 1L : sequence + 1L;
    }

    public boolean attackingWithOffhand() {
        return attackingWithOffhand;
    }

    public void setAttackingWithOffhand(boolean attacking) {
        attackingWithOffhand = attacking;
    }

    public @Nullable AttributeMap activeOffhandAttributes() {
        return activeOffhandAttributes;
    }

    public void setActiveOffhandAttributes(@Nullable AttributeMap attributes) {
        activeOffhandAttributes = attributes;
    }

    public @Nullable OffhandAttackResult lastNetworkResult() {
        return lastNetworkResult;
    }

    public void setLastNetworkResult(OffhandAttackResult result) {
        lastNetworkResult = result;
    }

    public @Nullable OffhandAttackResult lastClientResult() {
        return lastClientResult;
    }

    public void setLastClientResult(OffhandAttackResult result) {
        lastClientResult = result;
    }
}
