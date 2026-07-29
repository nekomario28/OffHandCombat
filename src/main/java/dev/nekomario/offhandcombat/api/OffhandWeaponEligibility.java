package dev.nekomario.offhandcombat.api;

public record OffhandWeaponEligibility(boolean eligible, String reason) {
    public static OffhandWeaponEligibility allow() {
        return new OffhandWeaponEligibility(true, "eligible");
    }

    public static OffhandWeaponEligibility deny(String reason) {
        return new OffhandWeaponEligibility(false, reason);
    }
}
