package dev.nekomario.offhandcombat.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.nekomario.offhandcombat.OffHandCombat;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.util.Lazy;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = OffHandCombat.MOD_ID, value = Dist.CLIENT)
public final class ClientModEvents {
    public static final Lazy<KeyMapping> OFFHAND_ATTACK = Lazy.of(() -> new KeyMapping(
            "key.offhandcombat.attack",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            "key.categories.offhandcombat"
    ));

    private ClientModEvents() {
    }

    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OFFHAND_ATTACK.get());
    }
}
