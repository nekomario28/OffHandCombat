package dev.nekomario.offhandcombat.client;

import dev.nekomario.offhandcombat.OffHandCombat;
import dev.nekomario.offhandcombat.api.OffhandInputArbitrationRegistry;
import dev.nekomario.offhandcombat.api.OffhandInputArbitrationRule;
import dev.nekomario.offhandcombat.api.OffhandInputContext;
import dev.nekomario.offhandcombat.api.OffhandInputSource;
import dev.nekomario.offhandcombat.attachment.OffhandCombatAttachments;
import dev.nekomario.offhandcombat.attachment.OffhandCombatState;
import dev.nekomario.offhandcombat.combat.OffhandWeaponRules;
import dev.nekomario.offhandcombat.config.OffHandCombatClientConfig;
import dev.nekomario.offhandcombat.config.OffhandInputMode;
import dev.nekomario.offhandcombat.network.OffhandAttackRequestPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = OffHandCombat.MOD_ID, value = Dist.CLIENT)
public final class ClientInputHandler {
    private static boolean runtimeReadyLogged;

    private ClientInputHandler() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!runtimeReadyLogged) {
            runtimeReadyLogged = true;
            OffHandCombat.LOGGER.info("Off Hand Combat client runtime ready");
        }
    }

    @SubscribeEvent
    public static void onClientPlayerClone(ClientPlayerNetworkEvent.Clone event) {
        LocalPlayer oldPlayer = event.getOldPlayer();
        LocalPlayer newPlayer = event.getNewPlayer();
        if (oldPlayer.isDeadOrDying()
                || oldPlayer.level().dimension().equals(newPlayer.level().dimension())) {
            return;
        }

        OffhandCombatState oldState = oldPlayer.getData(OffhandCombatAttachments.COMBAT_STATE);
        newPlayer
                .getData(OffhandCombatAttachments.COMBAT_STATE)
                .copyClientDimensionStateFrom(oldState);
    }

    @SubscribeEvent
    public static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem() || event.getHand() != InteractionHand.OFF_HAND || event.isCanceled()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null
                || (OffHandCombatClientConfig.INPUT_MODE.get() == OffhandInputMode.USE_KEY_WHEN_SNEAKING
                && !player.isCrouching())) {
            return;
        }
        if (trySendAttack(OffhandInputSource.USE_KEY)) {
            event.setSwingHand(false);
            event.setCanceled(true);
        }
    }

    private static boolean trySendAttack(OffhandInputSource inputSource) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        ClientPacketListener connection = minecraft.getConnection();
        if (minecraft.screen != null || player == null || connection == null || player.isUsingItem()) {
            return false;
        }
        if (!connection.hasChannel(OffhandAttackRequestPayload.TYPE)) {
            return false;
        }
        if (!(minecraft.hitResult instanceof EntityHitResult entityHitResult)) {
            return false;
        }
        Entity target = entityHitResult.getEntity();
        if (!OffhandWeaponRules.evaluate(player, player.getOffhandItem()).eligible()) {
            return false;
        }
        OffhandInputArbitrationRule.Decision decision = OffhandInputArbitrationRegistry.evaluate(
                new OffhandInputContext(player, target, player.getOffhandItem().copy(), inputSource));
        if (decision == OffhandInputArbitrationRule.Decision.DENY) {
            return false;
        }

        long sequence = player.getData(OffhandCombatAttachments.COMBAT_STATE).nextClientSequence();
        PacketDistributor.sendToServer(new OffhandAttackRequestPayload(sequence, target.getId()));
        return true;
    }
}
