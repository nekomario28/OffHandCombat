package dev.nekomario.offhandcombat.api;

import java.util.Arrays;

public enum OffhandAttackStatus {
    SUCCESS(0),
    INVALID_SEQUENCE(1),
    STALE_SEQUENCE(2),
    DUPLICATE_WITHOUT_RESULT(3),
    RATE_LIMITED(4),
    PLAYER_UNAVAILABLE(5),
    PLAYER_BUSY(6),
    INELIGIBLE_WEAPON(7),
    INVALID_TARGET(8),
    OUT_OF_RANGE(9),
    NO_LINE_OF_SIGHT(10),
    NOT_READY(11),
    CANCELED_BY_EVENT(12),
    INTERNAL_ERROR(13);

    private final int wireId;

    OffhandAttackStatus(int wireId) {
        this.wireId = wireId;
    }

    public int wireId() {
        return wireId;
    }

    public static OffhandAttackStatus fromWireId(int wireId) {
        return Arrays.stream(values())
                .filter(status -> status.wireId == wireId)
                .findFirst()
                .orElse(INTERNAL_ERROR);
    }
}
