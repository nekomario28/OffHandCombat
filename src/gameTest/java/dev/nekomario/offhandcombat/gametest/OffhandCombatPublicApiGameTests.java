package dev.nekomario.offhandcombat.gametest;

import dev.nekomario.offhandcombat.OffHandCombat;
import dev.nekomario.offhandcombat.api.OffhandAttackResult;
import dev.nekomario.offhandcombat.api.OffhandAttackStatus;
import dev.nekomario.offhandcombat.combat.OffhandAttackService;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
        helper.succeed();
    }
}
