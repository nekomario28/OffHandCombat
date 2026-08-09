package dev.nekomario.offhandcombat.attachment;

import net.minecraft.world.InteractionHand;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OffhandCombatStateActiveUseTest {
    @Test
    void recentlyUsedHandExpiresAfterConfiguredWindow() {
        OffhandCombatState state = new OffhandCombatState();

        state.recordActiveUseStopped(InteractionHand.MAIN_HAND);
        assertTrue(state.shouldDeferRecentlyUsedHand(InteractionHand.MAIN_HAND, 3));
        assertFalse(state.shouldDeferRecentlyUsedHand(InteractionHand.OFF_HAND, 3));

        state.tickActiveUseWindow();
        assertTrue(state.shouldDeferRecentlyUsedHand(InteractionHand.MAIN_HAND, 3));
        state.tickActiveUseWindow();
        assertTrue(state.shouldDeferRecentlyUsedHand(InteractionHand.MAIN_HAND, 3));
        state.tickActiveUseWindow();
        assertFalse(state.shouldDeferRecentlyUsedHand(InteractionHand.MAIN_HAND, 3));
    }

    @Test
    void zeroLengthWindowNeverDefers() {
        OffhandCombatState state = new OffhandCombatState();
        state.recordActiveUseStopped(InteractionHand.MAIN_HAND);

        assertFalse(state.shouldDeferRecentlyUsedHand(InteractionHand.MAIN_HAND, 0));
    }
}
