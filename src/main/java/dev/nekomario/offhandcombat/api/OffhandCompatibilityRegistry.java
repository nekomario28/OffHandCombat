package dev.nekomario.offhandcombat.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class OffhandCompatibilityRegistry {
    private static final CopyOnWriteArrayList<Entry> RULES = new CopyOnWriteArrayList<>();

    private OffhandCompatibilityRegistry() {
    }

    public static void register(ResourceLocation id, int priority, OffhandEligibilityRule rule) {
        if (id == null || rule == null) {
            throw new IllegalArgumentException("id and rule must not be null");
        }
        RULES.removeIf(entry -> entry.id().equals(id));
        RULES.add(new Entry(id, priority, rule));
        RULES.sort(Comparator.comparingInt(Entry::priority).reversed().thenComparing(entry -> entry.id().toString()));
    }

    public static OffhandEligibilityRule.Decision evaluate(Player player, ItemStack stack) {
        for (Entry entry : List.copyOf(RULES)) {
            OffhandEligibilityRule.Decision decision = entry.rule().evaluate(player, stack);
            if (decision != OffhandEligibilityRule.Decision.PASS) {
                return decision;
            }
        }
        return OffhandEligibilityRule.Decision.PASS;
    }

    private record Entry(ResourceLocation id, int priority, OffhandEligibilityRule rule) {
    }
}
