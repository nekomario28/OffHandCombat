package dev.nekomario.offhandcombat.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientCooldownResetWindowTest {
    @Test
    void appliesOnlyNewerValidSequences() {
        ClientCooldownResetWindow window = new ClientCooldownResetWindow();

        assertFalse(window.mark(0L));
        assertFalse(window.mark(-1L));
        assertTrue(window.mark(1L));
        assertFalse(window.mark(1L));
        assertTrue(window.mark(3L));
        assertFalse(window.mark(2L));
        assertTrue(window.mark(4L));
    }

    @Test
    void copiedWindowPreservesTheResetHighWaterMark() {
        ClientCooldownResetWindow source = new ClientCooldownResetWindow();
        ClientCooldownResetWindow clone = new ClientCooldownResetWindow();

        assertTrue(source.mark(7L));
        clone.copyFrom(source);

        assertFalse(clone.mark(6L));
        assertFalse(clone.mark(7L));
        assertTrue(clone.mark(8L));
    }
}
