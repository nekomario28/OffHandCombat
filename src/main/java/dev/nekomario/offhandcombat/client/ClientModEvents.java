package dev.nekomario.offhandcombat.client;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.common.util.Lazy;

/**
 * Compatibility bridge used by the integration harnesses to trigger Minecraft's vanilla Use Item mapping.
 *
 * <p>No custom key mapping is registered. The removed implementation used
 * {@code KeyConflictContext.IN_GAME}; production input now enters exclusively through the vanilla
 * right-click/use pipeline.</p>
 */
public final class ClientModEvents {
    @Deprecated(forRemoval = true)
    public static final Lazy<KeyMapping> OFFHAND_ATTACK = Lazy.of(
            () -> Minecraft.getInstance().options.keyUse);

    private ClientModEvents() {
    }
}
