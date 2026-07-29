package dev.nekomario.offhandcombat.api;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public record OffhandInputContext(
        Player player,
        @Nullable Entity target,
        ItemStack offHandSnapshot,
        OffhandInputSource source
) {
}
