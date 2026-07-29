package dev.nekomario.offhandcombat.util;

public final class CooldownMath {
    private CooldownMath() {
    }

    public static double fullCooldownTicks(double attackSpeed) {
        if (!Double.isFinite(attackSpeed) || attackSpeed <= 0.0D) {
            return Double.POSITIVE_INFINITY;
        }
        return 20.0D / attackSpeed;
    }

    public static float strength(int ticker, float partialTick, double attackSpeed) {
        double delay = fullCooldownTicks(attackSpeed);
        if (!Double.isFinite(delay)) {
            return 0.0F;
        }
        return clamp((float) ((ticker + partialTick) / delay), 0.0F, 1.0F);
    }

    public static int oppositeHandCap(double attackSpeed, double retainedFraction) {
        double delay = fullCooldownTicks(attackSpeed);
        if (!Double.isFinite(delay)) {
            return 0;
        }
        double fraction = clamp(retainedFraction, 0.0D, 1.0D);
        return Math.max(0, (int) Math.floor(delay * fraction));
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
