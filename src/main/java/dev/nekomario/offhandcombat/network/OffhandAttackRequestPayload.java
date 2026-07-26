package dev.nekomario.offhandcombat.network;

import dev.nekomario.offhandcombat.OffHandCombat;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record OffhandAttackRequestPayload(long sequence, int targetId) implements CustomPacketPayload {
    public static final Type<OffhandAttackRequestPayload> TYPE = new Type<>(OffHandCombat.id("offhand_attack_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OffhandAttackRequestPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeVarLong(payload.sequence());
                buffer.writeVarInt(payload.targetId());
            },
            buffer -> new OffhandAttackRequestPayload(buffer.readVarLong(), buffer.readVarInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
