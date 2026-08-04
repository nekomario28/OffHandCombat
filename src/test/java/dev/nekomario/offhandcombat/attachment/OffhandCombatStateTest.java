package dev.nekomario.offhandcombat.attachment;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OffhandCombatStateTest {
    @Test
    void clientCooldownResetIsAppliedOncePerSuccessfulSequence() {
        OffhandCombatState state = new OffhandCombatState();

        assertFalse(state.markClientCooldownReset(0L));
        assertFalse(state.markClientCooldownReset(-1L));
        assertTrue(state.markClientCooldownReset(1L));
        assertFalse(state.markClientCooldownReset(1L));
        assertTrue(state.markClientCooldownReset(2L));
    }

    @Test
    void dimensionClonePreservesTheCooldownResetAnchor() {
        OffhandCombatState source = new OffhandCombatState();
        OffhandCombatState clone = new OffhandCombatState();

        assertTrue(source.markClientCooldownReset(7L));
        clone.copyClientDimensionStateFrom(source);

        assertFalse(clone.markClientCooldownReset(7L));
        assertTrue(clone.markClientCooldownReset(8L));
    }
}
