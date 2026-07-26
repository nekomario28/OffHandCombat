package dev.nekomario.offhandcombat.gametest;

import com.mojang.authlib.GameProfile;
import dev.nekomario.offhandcombat.OffHandCombat;
import dev.nekomario.offhandcombat.api.OffhandAttackAccess;
import dev.nekomario.offhandcombat.api.OffhandAttackRequest;
import dev.nekomario.offhandcombat.api.OffhandAttackResult;
import dev.nekomario.offhandcombat.api.OffhandAttackSource;
import dev.nekomario.offhandcombat.api.OffhandAttackStatus;
import dev.nekomario.offhandcombat.combat.OffhandAttackService;
import dev.nekomario.offhandcombat.config.OffHandCombatConfig;
import dev.nekomario.offhandcombat.util.CooldownMath;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

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
        ServerPlayer player = makeSurvivalPlayer(helper);
        ItemStack mainHand = new ItemStack(Items.WOODEN_SWORD);
        ItemStack offHand = new ItemStack(Items.IRON_SWORD);
        player.setItemInHand(InteractionHand.MAIN_HAND, mainHand);
        player.setItemInHand(InteractionHand.OFF_HAND, offHand);

        Zombie target = spawnTarget(helper);
        OffhandAttackAccess access = (OffhandAttackAccess) player;
        access.ofc$setOffhandAttackStrengthTicker(100);
        OffhandAttackResult first = OffhandAttackService.INSTANCE.request(player, target);

        helper.assertValueEqual(first.status(), OffhandAttackStatus.SUCCESS,
                "the first off-hand attack should execute");
        helper.assertTrue(first.targetHealthAfter() < first.targetHealthBefore(),
                "the first off-hand attack should damage the target");
        helper.assertTrue(first.targetHealthBefore() - first.targetHealthAfter() > 5.5F,
                "damage should be sourced from the iron sword in the off hand");
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
        ServerPlayer player = makeSurvivalPlayer(helper);
        ItemStack offHand = new ItemStack(Items.IRON_SWORD);
        player.setItemInHand(InteractionHand.OFF_HAND, offHand);
        Zombie target = spawnTarget(helper);

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

    @GameTest(template = EMPTY, timeoutTicks = 40)
    public static void freshNetworkSequenceIsRateLimitedWithoutExecution(GameTestHelper helper) {
        ServerPlayer player = makeSurvivalPlayer(helper);
        ItemStack offHand = new ItemStack(Items.IRON_SWORD);
        player.setItemInHand(InteractionHand.OFF_HAND, offHand);
        Zombie target = spawnTarget(helper);

        OffhandAttackAccess access = (OffhandAttackAccess) player;
        access.ofc$setOffhandAttackStrengthTicker(100);
        OffhandAttackResult first = OffhandAttackService.INSTANCE.request(player,
                new OffhandAttackRequest(1L, target.getId(), OffhandAttackSource.NETWORK));
        float healthAfterFirst = target.getHealth();
        int durabilityAfterFirst = offHand.getDamageValue();

        access.ofc$setOffhandAttackStrengthTicker(100);
        OffhandAttackResult second = OffhandAttackService.INSTANCE.request(player,
                new OffhandAttackRequest(2L, target.getId(), OffhandAttackSource.NETWORK));

        helper.assertValueEqual(first.status(), OffhandAttackStatus.SUCCESS,
                "the first network sequence should execute");
        helper.assertValueEqual(second.status(), OffhandAttackStatus.RATE_LIMITED,
                "a fresh sequence in the same rate-limit window should be rejected");
        helper.assertValueEqual(target.getHealth(), healthAfterFirst,
                "a rate-limited request must not damage the target");
        helper.assertValueEqual(offHand.getDamageValue(), durabilityAfterFirst,
                "a rate-limited request must not consume durability");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 40)
    public static void serverValidationRejectsUnavailableInvalidAndDistantRequests(GameTestHelper helper) {
        ServerPlayer player = makeSurvivalPlayer(helper);
        Zombie validTarget = spawnTarget(helper);

        OffhandAttackResult ineligible = OffhandAttackService.INSTANCE.request(player, validTarget);
        helper.assertValueEqual(ineligible.status(), OffhandAttackStatus.INELIGIBLE_WEAPON,
                "an empty off hand should be rejected server-side");

        ServerPlayer spectator = makeSpectatorPlayer(helper);
        spectator.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.IRON_SWORD));
        OffhandAttackResult unavailable = OffhandAttackService.INSTANCE.request(spectator, validTarget);
        helper.assertValueEqual(unavailable.status(), OffhandAttackStatus.PLAYER_UNAVAILABLE,
                "a spectator should be rejected server-side");

        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.IRON_SWORD));
        OffhandAttackResult self = OffhandAttackService.INSTANCE.request(player, player);
        helper.assertValueEqual(self.status(), OffhandAttackStatus.INVALID_TARGET,
                "self-targeting should be rejected server-side");

        Zombie removedTarget = spawnTarget(helper);
        removedTarget.discard();
        OffhandAttackResult removed = OffhandAttackService.INSTANCE.request(player, removedTarget);
        helper.assertValueEqual(removed.status(), OffhandAttackStatus.INVALID_TARGET,
                "a removed target should be rejected server-side");

        Zombie farTarget = spawnTarget(helper);
        farTarget.setPos(player.getX() + 10.0D, player.getY(), player.getZ());
        OffhandAttackResult distant = OffhandAttackService.INSTANCE.request(player, farTarget);
        helper.assertValueEqual(distant.status(), OffhandAttackStatus.OUT_OF_RANGE,
                "entity interaction range should be enforced server-side");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 40)
    public static void mainHandAttackCapsOffhandCooldownUsingOffhandSpeed(GameTestHelper helper) {
        ServerPlayer player = makeSurvivalPlayer(helper);
        ItemStack offHand = new ItemStack(Items.IRON_SWORD);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_AXE));
        player.setItemInHand(InteractionHand.OFF_HAND, offHand);
        Zombie target = spawnTarget(helper);

        OffhandAttackAccess access = (OffhandAttackAccess) player;
        access.ofc$setOffhandAttackStrengthTicker(100);
        int expectedCap = CooldownMath.oppositeHandCap(
                access.ofc$getOffhandAttributeValue(Attributes.ATTACK_SPEED),
                OffHandCombatConfig.OPPOSITE_HAND_COOLDOWN.getAsDouble());

        player.attack(target);

        helper.assertValueEqual(access.ofc$getOffhandAttackStrengthTicker(), expectedCap,
                "a main-hand attack should cap off-hand readiness using the off-hand attack speed");
        helper.assertValueEqual(offHand.getDamageValue(), 0,
                "a main-hand attack must not consume off-hand durability");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 40)
    public static void offhandAttackCapsMainCooldownUsingMainSpeed(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos playerPos = helper.absolutePos(PLAYER_POS);
        player.setPos(playerPos.getX() + 0.5D, playerPos.getY(), playerPos.getZ() + 0.5D);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_AXE));
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.IRON_SWORD));
        Zombie target = spawnTarget(helper);

        player.resetAttackStrengthTicker();
        for (int tick = 0; tick < 40; tick++) {
            player.tick();
        }
        helper.assertTrue(player.getAttackStrengthScale(0.0F) >= 0.99F,
                "the main hand should be fully charged before the off-hand attack");

        double mainAttackSpeed = player.getAttributeValue(Attributes.ATTACK_SPEED);
        int expectedCap = CooldownMath.oppositeHandCap(
                mainAttackSpeed, OffHandCombatConfig.OPPOSITE_HAND_COOLDOWN.getAsDouble());
        float expectedStrength = CooldownMath.strength(expectedCap, 0.0F, mainAttackSpeed);

        OffhandAttackAccess access = (OffhandAttackAccess) player;
        access.ofc$setOffhandAttackStrengthTicker(100);
        access.ofc$attackWithOffhand(target);
        float actualStrength = player.getAttackStrengthScale(0.0F);

        helper.assertTrue(Math.abs(actualStrength - expectedStrength) < 0.0001F,
                "an off-hand attack should cap main-hand readiness using the main-hand attack speed");
        helper.succeed();
    }

    private static ServerPlayer makeSurvivalPlayer(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        BlockPos playerPos = helper.absolutePos(PLAYER_POS);
        player.setPos(playerPos.getX() + 0.5D, playerPos.getY(), playerPos.getZ() + 0.5D);
        return player;
    }

    private static ServerPlayer makeSpectatorPlayer(GameTestHelper helper) {
        BlockPos playerPos = helper.absolutePos(PLAYER_POS);
        ServerPlayer player = new ServerPlayer(
                helper.getLevel().getServer(),
                helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "test-spectator"),
                ClientInformation.createDefault()) {
            @Override
            public boolean isSpectator() {
                return true;
            }

            @Override
            public boolean isCreative() {
                return false;
            }
        };
        player.setPos(playerPos.getX() + 0.5D, playerPos.getY(), playerPos.getZ() + 0.5D);
        return player;
    }

    private static Zombie spawnTarget(GameTestHelper helper) {
        Zombie target = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, TARGET_POS);
        target.setHealth(target.getMaxHealth());
        return target;
    }
}
