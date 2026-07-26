package dev.nekomario.offhandcombat.registry;

import dev.nekomario.offhandcombat.OffHandCombat;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.enchantment.Enchantment;

public final class OffHandCombatTags {
    public static final TagKey<Item> OFFHAND_ATTACK_BLACKLIST =
            TagKey.create(Registries.ITEM, OffHandCombat.id("offhand_attack_blacklist"));
    public static final TagKey<Item> OFFHAND_ATTACK_WEAPONS =
            TagKey.create(Registries.ITEM, OffHandCombat.id("offhand_attack_weapons"));
    public static final TagKey<Enchantment> OFFHAND_ATTACK_ENCHANTMENT_BLACKLIST =
            TagKey.create(Registries.ENCHANTMENT, OffHandCombat.id("offhand_attack_blacklist"));

    private OffHandCombatTags() {
    }
}
