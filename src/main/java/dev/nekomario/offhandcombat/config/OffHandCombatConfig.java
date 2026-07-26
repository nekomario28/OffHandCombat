package dev.nekomario.offhandcombat.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class OffHandCombatConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.DoubleValue OPPOSITE_HAND_COOLDOWN;
    public static final ModConfigSpec.DoubleValue MINIMUM_OFFHAND_ATTACK_STRENGTH;
    public static final ModConfigSpec.BooleanValue REQUIRE_LINE_OF_SIGHT;
    public static final ModConfigSpec.IntValue REQUEST_COOLDOWN_TICKS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("combat");
        OPPOSITE_HAND_COOLDOWN = builder
                .comment("Fraction of the opposite hand's full cooldown retained after an attack.")
                .defineInRange("oppositeHandCooldown", 0.5D, 0.0D, 1.0D);
        MINIMUM_OFFHAND_ATTACK_STRENGTH = builder
                .comment("Minimum server-side off-hand attack charge.")
                .defineInRange("minimumOffhandAttackStrength", 0.0D, 0.0D, 1.0D);
        REQUIRE_LINE_OF_SIGHT = builder
                .comment("Reject off-hand attacks when the server cannot confirm line of sight.")
                .define("requireLineOfSight", true);
        REQUEST_COOLDOWN_TICKS = builder
                .comment("Minimum game ticks between newly accepted network requests.")
                .defineInRange("requestCooldownTicks", 1, 1, 20);
        builder.pop();
        SPEC = builder.build();
    }

    private OffHandCombatConfig() {
    }
}
