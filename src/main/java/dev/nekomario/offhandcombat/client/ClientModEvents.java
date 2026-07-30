package dev.nekomario.offhandcombat.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.nekomario.offhandcombat.OffHandCombat;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.Lazy;
import org.lwjgl.glfw.GLFW;

/**
 * Test-harness bridge for exercising Minecraft's vanilla right-click/use input event.
 *
 * <p>No custom key mapping is registered. Production input enters exclusively through the vanilla
 * use mapping. The bridge uses an unbound mapping and posts the same NeoForge OFF_HAND use event
 * only when an integration harness explicitly evaluates {@link #OFFHAND_ATTACK}.</p>
 */
public final class ClientModEvents {
    @Deprecated(forRemoval = true)
    public static final Lazy<KeyMapping> OFFHAND_ATTACK = Lazy.of(TestUseKeyMapping::new);

    private ClientModEvents() {
    }

    private static final class TestUseKeyMapping extends KeyMapping {
        private TestUseKeyMapping() {
            super(
                    "key.offhandcombat.test_use",
                    KeyConflictContext.IN_GAME,
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_UNKNOWN,
                    "key.categories.misc"
            );
        }

        @Override
        public InputConstants.Key getKey() {
            Minecraft minecraft = Minecraft.getInstance();
            NeoForge.EVENT_BUS.post(new InputEvent.InteractionKeyMappingTriggered(
                    1,
                    minecraft.options.keyUse,
                    InteractionHand.OFF_HAND
            ));
            return InputConstants.UNKNOWN;
        }
    }
}
