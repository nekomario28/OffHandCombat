package dev.nekomario.offhandcombat.attachment;

import dev.nekomario.offhandcombat.api.OffhandAttackResult;
import dev.nekomario.offhandcombat.util.SequenceWindow;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public final class OffhandCombatState {
    private int offhandAttackStrengthTicker;
    private long lastAcceptedRequestTick = Long.MIN_VALUE;
    private final SequenceWindow networkSequences = new SequenceWindow();
    private long nextClientSequence = 1L;
    private long nextApiSequence = 1L;
    private long lastClientCooldownResetSequence;
    private ItemStack previousOffhand = ItemStack.EMPTY;
    private boolean attackingWithOffhand;
    private @Nullable AttributeMap activeOffhandAttributes;
    private @Nullable OffhandAttackResult lastNetworkResult;
    private @Nullable OffhandAttackResult lastClientResult;

    public void tickCooldown() {
        if (offhandAttackStrengthTicker < Integer.MAX_VALUE) {
            offhandAttackStrengthTicker++;
        }
    }

    public int offhandAttackStrengthTicker() {
        return offhandAttackStrengthTicker;
    }

    public void setOffhandAttackStrengthTicker(int ticks) {
        offhandAttackStrengthTicker = Math.max(0, ticks);
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

    public boolean markClientCooldownReset(long sequence) {
        if (sequence <= 0L || sequence == lastClientCooldownResetSequence) {
            return false;
        }
        lastClientCooldownResetSequence = sequence;
        return true;
    }

    public void copyClientDimensionStateFrom(OffhandCombatState source) {
        nextClientSequence = source.nextClientSequence;
        lastClientCooldownResetSequence = source.lastClientCooldownResetSequence;
        lastClientResult = source.lastClientResult;
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
