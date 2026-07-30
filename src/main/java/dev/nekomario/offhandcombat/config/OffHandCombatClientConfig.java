package dev.nekomario.offhandcombat.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class OffHandCombatClientConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.EnumValue<OffhandInputMode> INPUT_MODE;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("input");
        INPUT_MODE = builder
                .comment("USE_KEY_ALWAYS is the standard right-click off-hand attack mode. USE_KEY_WHEN_SNEAKING restricts right-click attacks to sneaking for compatibility.")
                .defineEnum("mode", OffhandInputMode.USE_KEY_ALWAYS);
        builder.pop();
        SPEC = builder.build();
    }

    private OffHandCombatClientConfig() {
    }
}
