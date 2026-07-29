package dev.nekomario.offhandcombat.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CooldownMathTest {
    @Test
    void computesVanillaStyleCooldown() {
        assertEquals(5.0D, CooldownMath.fullCooldownTicks(4.0D), 0.0001D);
        assertEquals(0.5F, CooldownMath.strength(2, 0.5F, 4.0D), 0.0001F);
        assertEquals(2, CooldownMath.oppositeHandCap(4.0D, 0.5D));
    }

    @Test
    void rejectsInvalidAttackSpeed() {
        assertEquals(0.0F, CooldownMath.strength(100, 0.0F, 0.0D));
        assertEquals(0, CooldownMath.oppositeHandCap(Double.NaN, 0.5D));
    }
}
