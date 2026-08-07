package dev.nekomario.offhandcombat.client;

import dev.nekomario.offhandcombat.OffHandCombat;
import dev.nekomario.offhandcombat.api.OffhandAttackAccess;
import dev.nekomario.offhandcombat.combat.OffhandWeaponRules;
import dev.nekomario.offhandcombat.network.OffhandAttackRequestPayload;
import dev.nekomario.offhandcombat.util.CooldownMath;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

@EventBusSubscriber(modid = OffHandCombat.MOD_ID, value = Dist.CLIENT)
public final class ClientHudHandler {
    private static final ResourceLocation HOTBAR_BACKGROUND = ResourceLocation.fromNamespaceAndPath(
            "minecraft", "hud/hotbar_attack_indicator_background");
    private static final ResourceLocation HOTBAR_PROGRESS = ResourceLocation.fromNamespaceAndPath(
            "minecraft", "hud/hotbar_attack_indicator_progress");
    private static final ResourceLocation CROSSHAIR_BACKGROUND = ResourceLocation.fromNamespaceAndPath(
            "minecraft", "hud/crosshair_attack_indicator_background");
    private static final ResourceLocation CROSSHAIR_PROGRESS = ResourceLocation.fromNamespaceAndPath(
            "minecraft", "hud/crosshair_attack_indicator_progress");
    private static final ResourceLocation CROSSHAIR_FULL = ResourceLocation.fromNamespaceAndPath(
            "minecraft", "hud/crosshair_attack_indicator_full");
    private static boolean renderLogged;

    private ClientHudHandler() {
    }

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(OffHandCombat.id("offhand_attack_indicator"), ClientHudHandler::render);
    }

    private static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        var connection = minecraft.getConnection();
        if (minecraft.options.hideGui
                || minecraft.screen != null
                || player == null
                || player.isSpectator()
                || connection == null
                || !connection.hasChannel(OffhandAttackRequestPayload.TYPE)
                || !OffhandWeaponRules.evaluate(player, player.getOffhandItem()).eligible()) {
            return;
        }

        OffhandAttackAccess access = (OffhandAttackAccess) player;
        float partialTick = deltaTracker.getGameTimeDeltaPartialTick(true);
        float strength = Mth.clamp(access.ofc$getOffhandAttackStrengthScale(partialTick), 0.0F, 1.0F);
        AttackIndicatorStatus indicator = minecraft.options.attackIndicator().get();
        if (indicator == AttackIndicatorStatus.HOTBAR) {
            if (strength < 1.0F) {
                renderHotbar(graphics, strength, player.getMainArm());
                logRendered(indicator, strength, false);
            }
        } else if (indicator == AttackIndicatorStatus.CROSSHAIR) {
            boolean readyTarget = strength >= 1.0F
                    && minecraft.hitResult instanceof EntityHitResult entityHitResult
                    && entityHitResult.getEntity() instanceof LivingEntity living
                    && living.isAlive()
                    && CooldownMath.fullCooldownTicks(access.ofc$getOffhandAttributeValue(Attributes.ATTACK_SPEED)) > 5.0D;
            if (readyTarget) {
                renderCrosshairFull(graphics);
                logRendered(indicator, strength, true);
            } else if (strength < 1.0F) {
                renderCrosshairProgress(graphics, strength);
                logRendered(indicator, strength, false);
            }
        }
    }

    private static void renderHotbar(GuiGraphics graphics, float strength, HumanoidArm mainArm) {
        int centerX = graphics.guiWidth() / 2;
        int x = mainArm == HumanoidArm.RIGHT ? centerX - 91 - 22 : centerX + 91 + 6;
        int y = graphics.guiHeight() - 20;
        int progress = Mth.ceil(strength * 18.0F);

        graphics.blitSprite(HOTBAR_BACKGROUND, x, y, 18, 18);
        if (progress <= 0) {
            return;
        }
        graphics.enableScissor(x, y + 18 - progress, x + 18, y + 18);
        try {
            graphics.blitSprite(HOTBAR_PROGRESS, x, y, 18, 18);
        } finally {
            graphics.disableScissor();
        }
    }

    private static void renderCrosshairProgress(GuiGraphics graphics, float strength) {
        int x = graphics.guiWidth() / 2 - 8;
        int y = graphics.guiHeight() / 2 + 15;
        int progress = Mth.ceil(strength * 16.0F);

        graphics.blitSprite(CROSSHAIR_BACKGROUND, x, y, 16, 4);
        if (progress <= 0) {
            return;
        }
        graphics.enableScissor(x, y, x + progress, y + 4);
        try {
            graphics.blitSprite(CROSSHAIR_PROGRESS, x, y, 16, 4);
        } finally {
            graphics.disableScissor();
        }
    }

    private static void renderCrosshairFull(GuiGraphics graphics) {
        int x = graphics.guiWidth() / 2 - 8;
        int y = graphics.guiHeight() / 2 + 15;
        graphics.blitSprite(CROSSHAIR_FULL, x, y, 16, 16);
    }

    private static void logRendered(AttackIndicatorStatus indicator, float strength, boolean full) {
        if (renderLogged) {
            return;
        }
        renderLogged = true;
        OffHandCombat.LOGGER.info(
                "Off Hand Combat off-hand cooldown HUD rendered: mode={}, strength={}, full={}",
                indicator,
                strength,
                full
        );
    }
}
