package dev.nekomario.offhandcombat.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class OffHandCombatClientConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.EnumValue<OffhandInputMode> INPUT_MODE;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("input");
        INPUT_MODE = builder
                .comment("USE_KEY_ALWAYS preserves the original control scheme: right-click attacks normally, while sneaking bypasses Off Hand Combat for vanilla interaction. USE_KEY_WHEN_SNEAKING keeps the inverse compatibility mode for existing users.")
                .defineEnum("mode", OffhandInputMode.USE_KEY_ALWAYS);
        builder.pop();
        SPEC = builder.build();
    }

    private OffHandCombatClientConfig() {
    }
}
