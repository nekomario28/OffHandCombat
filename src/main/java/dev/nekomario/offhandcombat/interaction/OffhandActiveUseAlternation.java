package dev.nekomario.offhandcombat.interaction;

import dev.nekomario.offhandcombat.OffHandCombat;
import dev.nekomario.offhandcombat.attachment.OffhandCombatAttachments;
import dev.nekomario.offhandcombat.attachment.OffhandCombatState;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = OffHandCombat.MOD_ID)
public final class OffhandActiveUseAlternation {
    private static final int UPSTREAM_ALTERNATION_WINDOW_TICKS = 3;

    private OffhandActiveUseAlternation() {
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        OffhandCombatState state = event.getEntity().getData(OffhandCombatAttachments.COMBAT_STATE);
        if (!state.shouldDeferRecentlyUsedHand(event.getHand(), UPSTREAM_ALTERNATION_WINDOW_TICKS)) {
            return;
        }

        // The original mod temporarily made the just-used hand appear empty so vanilla would
        // continue to the opposite hand. NeoForge exposes the same intent safely: a cancelled
        // RightClickItem with PASS skips Item#use for this hand and lets the client try the next one.
        event.setCancellationResult(InteractionResult.PASS);
        event.setCanceled(true);
    }
}
