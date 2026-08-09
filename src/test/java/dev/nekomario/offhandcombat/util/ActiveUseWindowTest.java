package dev.nekomario.offhandcombat.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActiveUseWindowTest {
    @Test
    void expiresAfterConfiguredWindow() {
        int ticks = 0;
        assertTrue(ActiveUseWindow.isOpen(ticks, 3));

        ticks = ActiveUseWindow.advance(ticks);
        assertTrue(ActiveUseWindow.isOpen(ticks, 3));
        ticks = ActiveUseWindow.advance(ticks);
        assertTrue(ActiveUseWindow.isOpen(ticks, 3));
        ticks = ActiveUseWindow.advance(ticks);
        assertFalse(ActiveUseWindow.isOpen(ticks, 3));
    }

    @Test
    void zeroLengthAndExpiredSentinelStayClosed() {
        assertFalse(ActiveUseWindow.isOpen(0, 0));
        assertFalse(ActiveUseWindow.isOpen(Integer.MAX_VALUE, 3));
        assertTrue(ActiveUseWindow.advance(Integer.MAX_VALUE) == Integer.MAX_VALUE);
    }
}
