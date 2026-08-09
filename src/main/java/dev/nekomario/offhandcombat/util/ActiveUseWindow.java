package dev.nekomario.offhandcombat.util;

public final class ActiveUseWindow {
    private ActiveUseWindow() {
    }

    public static int advance(int ticksSinceLastUse) {
        return ticksSinceLastUse == Integer.MAX_VALUE
                ? Integer.MAX_VALUE
                : ticksSinceLastUse + 1;
    }

    public static boolean isOpen(int ticksSinceLastUse, int windowTicks) {
        return ticksSinceLastUse >= 0
                && ticksSinceLastUse < Math.max(0, windowTicks);
    }
}
