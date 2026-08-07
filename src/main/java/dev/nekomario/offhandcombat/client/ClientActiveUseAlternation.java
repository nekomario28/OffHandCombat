package dev.nekomario.offhandcombat.client;

import dev.nekomario.offhandcombat.OffHandCombat;
import dev.nekomario.offhandcombat.attachment.OffhandCombatAttachments;
import dev.nekomario.offhandcombat.network.OffhandAttackRequestPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.UseAnim;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = OffHandCombat.MOD_ID, value = Dist.CLIENT)
public final class ClientActiveUseAlternation {
    private static final int UPSTREAM_ALTERNATION_WINDOW_TICKS = 3;

    private ClientActiveUseAlternation() {
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !(event.getEntity() instanceof LocalPlayer player)) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        boolean hasChannel = minecraft.getConnection() != null
                && minecraft.getConnection().hasChannel(OffhandAttackRequestPayload.TYPE);
        boolean defer = player.getData(OffhandCombatAttachments.COMBAT_STATE)
                .shouldDeferRecentlyUsedHand(InteractionHand.MAIN_HAND, UPSTREAM_ALTERNATION_WINDOW_TICKS);
        UseAnim mainAnim = player.getMainHandItem().getUseAnimation();
        UseAnim offAnim = player.getOffhandItem().getUseAnimation();
        OffHandCombat.LOGGER.info(
                "Off Hand Combat alternation probe: defer={}, using={}, channel={}, mainAnim={}, offAnim={}",
                defer, player.isUsingItem(), hasChannel, mainAnim, offAnim);

        if (minecraft.screen != null
                || minecraft.gameMode == null
                || !hasChannel
                || player.isUsingItem()
                || !defer
                || mainAnim == UseAnim.NONE
                || offAnim == UseAnim.NONE) {
            return;
        }

        InteractionResult offhandResult = minecraft.gameMode.useItem(player, InteractionHand.OFF_HAND);
        OffHandCombat.LOGGER.info("Off Hand Combat alternation result: {}", offhandResult);
        if (offhandResult.consumesAction()) {
            event.setCancellationResult(offhandResult);
            event.setCanceled(true);
        }
    }
}
