package dev.nekomario.offhandcombat.gametest;

import dev.nekomario.offhandcombat.OffHandCombat;
import dev.nekomario.offhandcombat.api.OffhandAttackAccess;
import dev.nekomario.offhandcombat.api.OffhandAttackRequest;
import dev.nekomario.offhandcombat.api.OffhandAttackResult;
import dev.nekomario.offhandcombat.api.OffhandAttackSource;
import dev.nekomario.offhandcombat.api.OffhandAttackStatus;
import dev.nekomario.offhandcombat.combat.OffhandAttackService;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(OffHandCombat.MOD_ID)
@PrefixGameTestTemplate(false)
public final class OffhandCombatGameTests {
    private static final String EMPTY = "gametest/empty3x3x3";
    private static final BlockPos PLAYER_POS = new BlockPos(1, 1, 1);
    private static final BlockPos TARGET_POS = new BlockPos(2, 1, 1);

    private OffhandCombatGameTests() {
    }

    @GameTest(template = EMPTY, timeoutTicks = 40)
    public static void authoritativeAttackPreservesVanillaHurtWindow(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        BlockPos playerPos = helper.absolutePos(PLAYER_POS);
        player.setPos(playerPos.getX() + 0.5D, playerPos.getY(), playerPos.getZ() + 0.5D);

        ItemStack mainHand = new ItemStack(Items.WOODEN_SWORD);
        ItemStack offHand = new ItemStack(Items.IRON_SWORD);
        player.setItemInHand(InteractionHand.MAIN_HAND, mainHand);
        player.setItemInHand(InteractionHand.OFF_HAND, offHand);

        Zombie target = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, TARGET_POS);
        target.setHealth(target.getMaxHealth());

        OffhandAttackAccess access = (OffhandAttackAccess) player;
        access.ofc$setOffhandAttackStrengthTicker(100);
        OffhandAttackResult first = OffhandAttackService.INSTANCE.request(player, target);

        helper.assertValueEqual(first.status(), OffhandAttackStatus.SUCCESS,
                "the first off-hand attack should execute");
        helper.assertTrue(first.targetHealthAfter() < first.targetHealthBefore(),
                "the first off-hand attack should damage the target");
        helper.assertValueEqual(first.durabilityAfter() - first.durabilityBefore(), 1,
                "the accepted off-hand attack should consume durability once");
        helper.assertValueEqual(mainHand.getDamageValue(), 0,
                "the main-hand weapon must not lose durability");

        float healthAfterFirst = target.getHealth();
        int durabilityAfterFirst = offHand.getDamageValue();
        access.ofc$setOffhandAttackStrengthTicker(100);
        OffhandAttackResult second = OffhandAttackService.INSTANCE.request(player, target);

        helper.assertValueEqual(second.status(), OffhandAttackStatus.SUCCESS,
                "the authoritative vanilla attack path should still execute inside the hurt window");
        helper.assertValueEqual(target.getHealth(), healthAfterFirst,
                "vanilla hurt immunity must prevent immediate duplicate damage");
        helper.assertValueEqual(offHand.getDamageValue(), durabilityAfterFirst,
                "a rejected vanilla hurt must not consume durability again");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 40)
    public static void duplicateNetworkSequenceIsExactlyOnce(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        BlockPos playerPos = helper.absolutePos(PLAYER_POS);
        player.setPos(playerPos.getX() + 0.5D, playerPos.getY(), playerPos.getZ() + 0.5D);

        ItemStack offHand = new ItemStack(Items.IRON_SWORD);
        player.setItemInHand(InteractionHand.OFF_HAND, offHand);
        Zombie target = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, TARGET_POS);
        target.setHealth(target.getMaxHealth());

        OffhandAttackAccess access = (OffhandAttackAccess) player;
        access.ofc$setOffhandAttackStrengthTicker(100);
        OffhandAttackRequest request = new OffhandAttackRequest(
                1L, target.getId(), OffhandAttackSource.NETWORK);
        OffhandAttackResult first = OffhandAttackService.INSTANCE.request(player, request);
        float healthAfterFirst = target.getHealth();
        int durabilityAfterFirst = offHand.getDamageValue();

        OffhandAttackResult duplicate = OffhandAttackService.INSTANCE.request(player, request);

        helper.assertValueEqual(first.status(), OffhandAttackStatus.SUCCESS,
                "the first network sequence should execute");
        helper.assertValueEqual(duplicate, first,
                "a duplicate sequence should return the cached result");
        helper.assertValueEqual(target.getHealth(), healthAfterFirst,
                "a duplicate sequence must not damage the target twice");
        helper.assertValueEqual(offHand.getDamageValue(), durabilityAfterFirst,
                "a duplicate sequence must not consume durability twice");
        helper.succeed();
    }
}
