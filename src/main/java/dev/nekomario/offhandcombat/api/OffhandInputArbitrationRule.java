package dev.nekomario.offhandcombat.api;

@FunctionalInterface
public interface OffhandInputArbitrationRule {
    Decision evaluate(OffhandInputContext context);

    enum Decision {
        PASS,
        ALLOW,
        DENY
    }
}
