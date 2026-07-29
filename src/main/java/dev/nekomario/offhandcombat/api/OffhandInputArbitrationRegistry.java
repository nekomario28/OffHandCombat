package dev.nekomario.offhandcombat.api;

import net.minecraft.resources.ResourceLocation;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class OffhandInputArbitrationRegistry {
    private static final CopyOnWriteArrayList<Entry> RULES = new CopyOnWriteArrayList<>();

    private OffhandInputArbitrationRegistry() {
    }

    public static void register(ResourceLocation id, int priority, OffhandInputArbitrationRule rule) {
        if (id == null || rule == null) {
            throw new IllegalArgumentException("id and rule must not be null");
        }
        RULES.removeIf(entry -> entry.id().equals(id));
        RULES.add(new Entry(id, priority, rule));
        RULES.sort(Comparator.comparingInt(Entry::priority).reversed().thenComparing(entry -> entry.id().toString()));
    }

    public static OffhandInputArbitrationRule.Decision evaluate(OffhandInputContext context) {
        for (Entry entry : List.copyOf(RULES)) {
            OffhandInputArbitrationRule.Decision decision = entry.rule().evaluate(context);
            if (decision != OffhandInputArbitrationRule.Decision.PASS) {
                return decision;
            }
        }
        return OffhandInputArbitrationRule.Decision.PASS;
    }

    private record Entry(ResourceLocation id, int priority, OffhandInputArbitrationRule rule) {
    }
}
