package dev.nekomario.offhandcombat.network;

import dev.nekomario.offhandcombat.OffHandCombat;
import dev.nekomario.offhandcombat.api.OffhandAttackResult;
import dev.nekomario.offhandcombat.api.OffhandAttackStatus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record OffhandAttackResultPayload(
        long sequence,
        int targetId,
        OffhandAttackStatus status,
        float targetHealthBefore,
        float targetHealthAfter,
        int durabilityBefore,
        int durabilityAfter,
        long gameTime
) implements CustomPacketPayload {
    public static final Type<OffhandAttackResultPayload> TYPE = new Type<>(OffHandCombat.id("offhand_attack_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OffhandAttackResultPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeVarLong(payload.sequence());
                buffer.writeVarInt(payload.targetId());
                buffer.writeVarInt(payload.status().wireId());
                buffer.writeFloat(payload.targetHealthBefore());
                buffer.writeFloat(payload.targetHealthAfter());
                buffer.writeVarInt(payload.durabilityBefore() + 1);
                buffer.writeVarInt(payload.durabilityAfter() + 1);
                buffer.writeVarLong(payload.gameTime());
            },
            buffer -> new OffhandAttackResultPayload(
                    buffer.readVarLong(),
                    buffer.readVarInt(),
                    OffhandAttackStatus.fromWireId(buffer.readVarInt()),
                    buffer.readFloat(),
                    buffer.readFloat(),
                    buffer.readVarInt() - 1,
                    buffer.readVarInt() - 1,
                    buffer.readVarLong())
    );

    public static OffhandAttackResultPayload from(OffhandAttackResult result) {
        return new OffhandAttackResultPayload(result.sequence(), result.targetId(), result.status(),
                result.targetHealthBefore(), result.targetHealthAfter(), result.durabilityBefore(),
                result.durabilityAfter(), result.gameTime());
    }

    public OffhandAttackResult toResult() {
        return new OffhandAttackResult(sequence, targetId, status, targetHealthBefore, targetHealthAfter,
                durabilityBefore, durabilityAfter, gameTime);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
