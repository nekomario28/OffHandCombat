package dev.nekomario.offhandcombat.gametest;

import dev.nekomario.offhandcombat.OffHandCombat;
import dev.nekomario.offhandcombat.api.OffhandAttackAccess;
import dev.nekomario.offhandcombat.api.OffhandAttackResult;
import dev.nekomario.offhandcombat.api.OffhandAttackStatus;
import dev.nekomario.offhandcombat.combat.OffhandAttackService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(OffHandCombat.MOD_ID)
@PrefixGameTestTemplate(false)
public final class OffhandCombatPublicApiGameTests {
    private static final String EMPTY = "gametest/empty3x3x3";

    private OffhandCombatPublicApiGameTests() {
    }

    @GameTest(template = EMPTY, timeoutTicks = 40)
    public static void publicApiRejectsNullAndForeignLevelEntities(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        BlockPos playerPos = helper.absolutePos(new BlockPos(1, 1, 1));
        player.setPos(playerPos.getX() + 0.5D, playerPos.getY(), playerPos.getZ() + 0.5D);
        ItemStack offhand = new ItemStack(Items.IRON_SWORD);
        player.setItemInHand(InteractionHand.OFF_HAND, offhand);

        OffhandAttackResult nullResult = OffhandAttackService.INSTANCE.request(player, (Entity) null);
        helper.assertValueEqual(nullResult.status(), OffhandAttackStatus.INVALID_TARGET,
                "the public API should reject a null Entity without dereferencing it");

        ServerLevel foreignLevel = helper.getLevel().getServer().getLevel(Level.NETHER);
        helper.assertTrue(foreignLevel != null,
                "the GameTest server should expose the Nether for the foreign-level fixture");
        Zombie foreignTarget = EntityType.ZOMBIE.create(foreignLevel);
        helper.assertTrue(foreignTarget != null,
                "the foreign-level fixture should create a zombie");
        foreignTarget.moveTo(0.5D, 80.0D, 0.5D, 0.0F, 0.0F);
        helper.assertTrue(foreignLevel.addFreshEntity(foreignTarget),
                "the foreign-level fixture should add the zombie to the Nether");

        OffhandAttackResult foreignResult = OffhandAttackService.INSTANCE.request(player, foreignTarget);
        helper.assertValueEqual(foreignResult.status(), OffhandAttackStatus.INVALID_TARGET,
                "the public API must reject an Entity from another Level before resolving its numeric ID");
        helper.assertValueEqual(offhand.getDamageValue(), 0,
                "rejecting a foreign-level Entity must not consume off-hand durability");
        foreignTarget.discard();

        Holder<Enchantment> fireAspect = helper.getLevel().registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.FIRE_ASPECT);
        Holder<Enchantment> knockback = helper.getLevel().registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.KNOCKBACK);
        ItemStack mainHand = new ItemStack(Items.IRON_AXE);
        ItemStack enchantedOffhand = new ItemStack(Items.IRON_SWORD);
        enchantedOffhand.enchant(fireAspect, 1);
        enchantedOffhand.enchant(knockback, 1);
        player.setItemInHand(InteractionHand.MAIN_HAND, mainHand);
        player.setItemInHand(InteractionHand.OFF_HAND, enchantedOffhand);
        player.setOnGround(true);
        player.fallDistance = 0.0F;
        player.setSprinting(false);
        player.setDeltaMovement(0.0D, 0.0D, 0.0D);

        Cow localTarget = helper.spawnWithNoFreeWill(EntityType.COW, new BlockPos(2, 1, 1));
        localTarget.setHealth(localTarget.getMaxHealth());
        ((OffhandAttackAccess) player).ofc$setOffhandAttackStrengthTicker(100);
        OffhandAttackResult enchantedResult = OffhandAttackService.INSTANCE.request(player, localTarget);
        double knockbackX = localTarget.getDeltaMovement().x;
        double knockbackZ = localTarget.getDeltaMovement().z;
        double horizontalKnockback = Math.sqrt(knockbackX * knockbackX + knockbackZ * knockbackZ);

        helper.assertValueEqual(enchantedResult.status(), OffhandAttackStatus.SUCCESS,
                "the public Entity API should execute the enchanted off-hand attack");
        helper.assertTrue(enchantedResult.targetHealthBefore() - enchantedResult.targetHealthAfter() > 5.5F,
                "the public Entity API attack damage must come from the off-hand iron sword");
        helper.assertTrue(localTarget.isOnFire(),
                "Fire Aspect from the off-hand weapon must ignite the target");
        helper.assertTrue(localTarget.getRemainingFireTicks() >= 75
                        && localTarget.getRemainingFireTicks() <= 80,
                "Fire Aspect I must apply one vanilla four-second fire duration");
        helper.assertTrue(horizontalKnockback > 0.35D && horizontalKnockback < 0.65D,
                "Knockback I from the off-hand weapon must apply one vanilla knockback impulse");
        helper.assertValueEqual(enchantedResult.durabilityAfter() - enchantedResult.durabilityBefore(), 1,
                "off-hand enchantment hooks must consume durability exactly once");
        helper.assertValueEqual(mainHand.getDamageValue(), 0,
                "off-hand enchantment hooks must not consume main-hand durability");
        helper.succeed();
    }
}
