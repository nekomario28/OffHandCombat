package dev.nekomario.offhandcombat.api;

import dev.nekomario.offhandcombat.combat.OffhandAttackService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

public interface OffhandCombatApi {
    static OffhandCombatApi get() {
        return OffhandAttackService.INSTANCE;
    }

    OffhandAttackResult request(ServerPlayer player, OffhandAttackRequest request);

    OffhandAttackResult request(ServerPlayer player, Entity target);

    OffhandWeaponEligibility checkEligibility(ServerPlayer player, ItemStack stack);

    HandReadiness getReadiness(ServerPlayer player, float partialTick);

    default float getOffhandReadiness(ServerPlayer player, float partialTick) {
        return getReadiness(player, partialTick).offHand();
    }
}
