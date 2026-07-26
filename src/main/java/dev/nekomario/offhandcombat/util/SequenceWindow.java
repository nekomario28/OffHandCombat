package dev.nekomario.offhandcombat.util;

public final class SequenceWindow {
    private long lastAccepted;

    public Decision classify(long sequence) {
        if (sequence <= 0L) {
            return Decision.INVALID;
        }
        if (sequence == lastAccepted) {
            return Decision.DUPLICATE;
        }
        if (sequence < lastAccepted) {
            return Decision.STALE;
        }
        lastAccepted = sequence;
        return Decision.NEW;
    }

    public long lastAccepted() {
        return lastAccepted;
    }

    public enum Decision {
        NEW,
        DUPLICATE,
        STALE,
        INVALID
    }
}
