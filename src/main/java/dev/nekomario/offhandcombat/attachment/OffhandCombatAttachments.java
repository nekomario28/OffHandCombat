package dev.nekomario.offhandcombat.attachment;

import dev.nekomario.offhandcombat.OffHandCombat;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public final class OffhandCombatAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, OffHandCombat.MOD_ID);

    public static final Supplier<AttachmentType<OffhandCombatState>> COMBAT_STATE =
            ATTACHMENT_TYPES.register("combat_state",
                    () -> AttachmentType.builder(OffhandCombatState::new).build());

    private OffhandCombatAttachments() {
    }
}
