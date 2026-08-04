package dev.nekomario.offhandcombat.network;

import dev.nekomario.offhandcombat.OffHandCombat;
import dev.nekomario.offhandcombat.api.OffhandAttackRequest;
import dev.nekomario.offhandcombat.api.OffhandAttackResult;
import dev.nekomario.offhandcombat.api.OffhandAttackStatus;
import dev.nekomario.offhandcombat.api.OffhandCombatApi;
import dev.nekomario.offhandcombat.attachment.OffhandCombatAttachments;
import dev.nekomario.offhandcombat.attachment.OffhandCombatState;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class OffHandCombatNetwork {
    private OffHandCombatNetwork() {
    }

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(OffHandCombat.PROTOCOL_VERSION).optional();
        registrar.playToServer(OffhandAttackRequestPayload.TYPE, OffhandAttackRequestPayload.STREAM_CODEC,
                OffHandCombatNetwork::handleRequest);
        registrar.playToClient(OffhandAttackResultPayload.TYPE, OffhandAttackResultPayload.STREAM_CODEC,
                OffHandCombatNetwork::handleResult);
    }

    private static void handleRequest(OffhandAttackRequestPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        OffhandAttackResult result = OffhandCombatApi.get().request(player,
                OffhandAttackRequest.network(payload.sequence(), payload.targetId()));
        PacketDistributor.sendToPlayer(player, OffhandAttackResultPayload.from(result));
    }

    private static void handleResult(OffhandAttackResultPayload payload, IPayloadContext context) {
        OffhandAttackResult result = payload.toResult();
        OffhandCombatState state = context.player().getData(OffhandCombatAttachments.COMBAT_STATE);
        state.setLastClientResult(result);
        if (result.status() == OffhandAttackStatus.SUCCESS
                && state.markClientCooldownReset(result.sequence())) {
            state.setOffhandAttackStrengthTicker(0);
        }
    }
}
