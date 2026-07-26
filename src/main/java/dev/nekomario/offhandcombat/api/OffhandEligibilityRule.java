package dev.nekomario.offhandcombat.api;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

@FunctionalInterface
public interface OffhandEligibilityRule {
    Decision evaluate(Player player, ItemStack stack);

    enum Decision {
        PASS,
        ALLOW,
        DENY
    }
}
