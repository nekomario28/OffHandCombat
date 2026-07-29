package dev.nekomario.offhandcombat.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SequenceWindowTest {
    @Test
    void classifiesNewDuplicateAndStaleSequences() {
        SequenceWindow window = new SequenceWindow();
        assertEquals(SequenceWindow.Decision.INVALID, window.classify(0));
        assertEquals(SequenceWindow.Decision.NEW, window.classify(1));
        assertEquals(SequenceWindow.Decision.DUPLICATE, window.classify(1));
        assertEquals(SequenceWindow.Decision.NEW, window.classify(3));
        assertEquals(SequenceWindow.Decision.STALE, window.classify(2));
        assertEquals(3, window.lastAccepted());
    }
}
