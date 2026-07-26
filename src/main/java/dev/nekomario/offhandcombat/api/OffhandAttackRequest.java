package dev.nekomario.offhandcombat.api;

public record OffhandAttackRequest(long sequence, int targetId, OffhandAttackSource source) {
    public OffhandAttackRequest {
        if (source == null) {
            throw new IllegalArgumentException("source must not be null");
        }
    }

    public static OffhandAttackRequest network(long sequence, int targetId) {
        return new OffhandAttackRequest(sequence, targetId, OffhandAttackSource.NETWORK);
    }
}
