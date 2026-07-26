package dev.nekomario.offhandcombat.api;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

public record OffhandAttackContext(
        ServerPlayer player,
        Entity target,
        OffhandAttackRequest request,
        ItemStack mainHandSnapshot,
        ItemStack offHandSnapshot,
        float mainHandReadiness,
        float offHandReadiness
) {
}
