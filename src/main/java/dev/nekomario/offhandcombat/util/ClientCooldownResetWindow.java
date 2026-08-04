package dev.nekomario.offhandcombat.util;

public final class ClientCooldownResetWindow {
    private long lastSequence;

    public boolean mark(long sequence) {
        if (sequence <= 0L || sequence <= lastSequence) {
            return false;
        }
        lastSequence = sequence;
        return true;
    }

    public void copyFrom(ClientCooldownResetWindow source) {
        lastSequence = source.lastSequence;
    }
}
