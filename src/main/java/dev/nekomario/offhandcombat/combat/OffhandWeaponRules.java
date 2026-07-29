package dev.nekomario.offhandcombat.combat;

import dev.nekomario.offhandcombat.api.OffhandCompatibilityRegistry;
import dev.nekomario.offhandcombat.api.OffhandEligibilityRule;
import dev.nekomario.offhandcombat.api.OffhandWeaponEligibility;
import dev.nekomario.offhandcombat.registry.OffHandCombatTags;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

public final class OffhandWeaponRules {
    private OffhandWeaponRules() {
    }

    public static OffhandWeaponEligibility evaluate(Player player, ItemStack stack) {
        if (stack.isEmpty()) {
            return OffhandWeaponEligibility.deny("empty_stack");
        }
        if (stack.is(OffHandCombatTags.OFFHAND_ATTACK_BLACKLIST)) {
            return OffhandWeaponEligibility.deny("item_blacklist");
        }
        for (Holder<Enchantment> enchantment : EnchantmentHelper.getEnchantmentsForCrafting(stack).keySet()) {
            if (enchantment.is(OffHandCombatTags.OFFHAND_ATTACK_ENCHANTMENT_BLACKLIST)) {
                return OffhandWeaponEligibility.deny("enchantment_blacklist");
            }
        }

        OffhandEligibilityRule.Decision extensionDecision = OffhandCompatibilityRegistry.evaluate(player, stack);
        if (extensionDecision == OffhandEligibilityRule.Decision.DENY) {
            return OffhandWeaponEligibility.deny("compatibility_rule");
        }
        if (extensionDecision == OffhandEligibilityRule.Decision.ALLOW) {
            return OffhandWeaponEligibility.allow();
        }

        if (stack.getUseAnimation() != UseAnim.NONE) {
            return OffhandWeaponEligibility.deny("active_use_item");
        }
        if (stack.is(OffHandCombatTags.OFFHAND_ATTACK_WEAPONS)) {
            return OffhandWeaponEligibility.allow();
        }

        boolean[] hasAttackDamage = {false};
        stack.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
            if (attribute.equals(Attributes.ATTACK_DAMAGE)) {
                hasAttackDamage[0] = true;
            }
        });
        return hasAttackDamage[0]
                ? OffhandWeaponEligibility.allow()
                : OffhandWeaponEligibility.deny("no_mainhand_attack_damage");
    }
}
