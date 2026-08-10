package dev.nekomario.offhandcombat.client;

import dev.nekomario.offhandcombat.attachment.OffhandCombatAttachments;
import dev.nekomario.offhandcombat.attachment.OffhandCombatState;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.InteractionHand;

public final class ClientDualSwingProgress {
    private ClientDualSwingProgress() {
    }

    public static float resolve(AbstractClientPlayer player, InteractionHand hand,
                                float vanillaProgress, float partialTick) {
        OffhandCombatState state = player.getData(OffhandCombatAttachments.COMBAT_STATE);
        if (!state.hasAuxiliarySwing() || player.swingingArm == hand) {
            return vanillaProgress;
        }
        return state.hasAuxiliarySwing(hand)
                ? state.auxiliarySwingProgress(hand, partialTick)
                : vanillaProgress;
    }
}
