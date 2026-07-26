package dev.nekomario.offhandcombat.api;

public record OffhandAttackResult(
        long sequence,
        int targetId,
        OffhandAttackStatus status,
        float targetHealthBefore,
        float targetHealthAfter,
        int durabilityBefore,
        int durabilityAfter,
        long gameTime
) {
    public boolean executed() {
        return status == OffhandAttackStatus.SUCCESS;
    }

    public static OffhandAttackResult rejected(long sequence, int targetId, OffhandAttackStatus status, long gameTime) {
        return new OffhandAttackResult(sequence, targetId, status, Float.NaN, Float.NaN, -1, -1, gameTime);
    }
}
