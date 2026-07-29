package dev.nekomario.offhandcombat.api;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OffhandAttackStatusTest {
    @Test
    void wireIdsAreUniqueAndRoundTrip() {
        Set<Integer> ids = new HashSet<>();
        for (OffhandAttackStatus status : OffhandAttackStatus.values()) {
            assertTrue(ids.add(status.wireId()), () -> "duplicate wire id " + status.wireId());
            assertEquals(status, OffhandAttackStatus.fromWireId(status.wireId()));
        }
        assertEquals(OffhandAttackStatus.INTERNAL_ERROR, OffhandAttackStatus.fromWireId(Integer.MAX_VALUE));
    }
}
