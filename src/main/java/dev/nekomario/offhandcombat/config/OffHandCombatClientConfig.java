package dev.nekomario.offhandcombat.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class OffHandCombatClientConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.EnumValue<OffhandInputMode> INPUT_MODE;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("input");
        INPUT_MODE = builder
                .comment("DEDICATED_KEY is safest. USE_KEY modes are explicit opt-ins for legacy right-click behavior.")
                .defineEnum("mode", OffhandInputMode.DEDICATED_KEY);
        builder.pop();
        SPEC = builder.build();
    }

    private OffHandCombatClientConfig() {
    }
}
