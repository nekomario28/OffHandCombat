package dev.nekomario.offhandcombat.gametest;

import dev.nekomario.offhandcombat.OffHandCombat;
import dev.nekomario.offhandcombat.api.OffhandAttackResult;
import dev.nekomario.offhandcombat.api.OffhandAttackStatus;
import dev.nekomario.offhandcombat.api.OffhandWeaponEligibility;
import dev.nekomario.offhandcombat.combat.OffhandAttackService;
import dev.nekomario.offhandcombat.combat.OffhandWeaponRules;
import dev.nekomario.offhandcombat.registry.OffHandCombatTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(OffHandCombat.MOD_ID)
@PrefixGameTestTemplate(false)
public final class OffhandCombatBlacklistGameTests {
    private static final String EMPTY = "gametest/empty3x3x3";
    private static final BlockPos PLAYER_POS = new BlockPos(1, 1, 1);
    private static final BlockPos TARGET_POS = new BlockPos(2, 1, 1);

    private OffhandCombatBlacklistGameTests() {
    }

    @GameTest(template = EMPTY, timeoutTicks = 40)
    public static void blacklistedEnchantmentIsRejectedWithoutSideEffects(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        BlockPos playerPos = helper.absolutePos(PLAYER_POS);
        player.setPos(playerPos.getX() + 0.5D, playerPos.getY(), playerPos.getZ() + 0.5D);

        Holder<Enchantment> sharpness = helper.getLevel().registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.SHARPNESS);
        helper.assertTrue(sharpness.is(OffHandCombatTags.OFFHAND_ATTACK_ENCHANTMENT_BLACKLIST),
                "the GameTest-only datapack must place Sharpness in the enchantment blacklist");

        ItemStack offHand = new ItemStack(Items.IRON_SWORD);
        offHand.enchant(sharpness, 1);
        player.setItemInHand(InteractionHand.OFF_HAND, offHand);

        Zombie target = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, TARGET_POS);
        target.setHealth(target.getMaxHealth());
        float healthBefore = target.getHealth();
        int durabilityBefore = offHand.getDamageValue();

        OffhandWeaponEligibility eligibility = OffhandWeaponRules.evaluate(player, offHand);
        helper.assertTrue(!eligibility.eligible(),
                "a weapon carrying a blacklisted enchantment must be ineligible");
        helper.assertValueEqual(eligibility.reason(), "enchantment_blacklist",
                "the rejection reason must identify the enchantment blacklist");

        OffhandAttackResult result = OffhandAttackService.INSTANCE.request(player, target);
        helper.assertValueEqual(result.status(), OffhandAttackStatus.INELIGIBLE_WEAPON,
                "the authoritative service must reject a blacklisted enchantment");
        helper.assertValueEqual(target.getHealth(), healthBefore,
                "a blacklisted enchantment must not damage the target");
        helper.assertValueEqual(offHand.getDamageValue(), durabilityBefore,
                "a blacklisted enchantment must not consume durability");
        helper.succeed();
    }
}
