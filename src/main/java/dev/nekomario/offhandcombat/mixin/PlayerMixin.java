package dev.nekomario.offhandcombat.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.nekomario.offhandcombat.api.OffhandAttackAccess;
import dev.nekomario.offhandcombat.attachment.OffhandCombatAttachments;
import dev.nekomario.offhandcombat.attachment.OffhandCombatState;
import dev.nekomario.offhandcombat.config.OffHandCombatConfig;
import dev.nekomario.offhandcombat.util.CooldownMath;
import net.minecraft.core.Holder;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@SuppressWarnings("unchecked")
@Mixin(Player.class)
public abstract class PlayerMixin extends LivingEntity implements OffhandAttackAccess {
    protected PlayerMixin(EntityType<? extends LivingEntity> type, Level level) {
        super(type, level);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void offhandcombat$tickCooldown(CallbackInfo ci) {
        OffhandCombatState state = offhandcombat$state();
        state.updateOffhandSnapshot(this.getOffhandItem());
        state.tickCooldown();
    }

    @WrapOperation(method = "attack",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getAttributeValue(Lnet/minecraft/core/Holder;)D"))
    private double offhandcombat$readOffhandAttribute(Player player, Holder<Attribute> attribute,
                                                       Operation<Double> original) {
        return ofc$isAttackingWithOffhand() ? ofc$getOffhandAttributeValue(attribute) : original.call(player, attribute);
    }

    @WrapOperation(method = "attack",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getAttackStrengthScale(F)F"))
    private float offhandcombat$readOffhandCooldown(Player player, float partialTick,
                                                     Operation<Float> original) {
        return ofc$isAttackingWithOffhand() ? ofc$getOffhandAttackStrengthScale(partialTick) : original.call(player, partialTick);
    }

    @WrapOperation(method = "attack",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;resetAttackStrengthTicker()V"))
    private void offhandcombat$resetCorrectCooldown(Player player, Operation<Void> original) {
        OffhandCombatState state = offhandcombat$state();
        double fraction = OffHandCombatConfig.OPPOSITE_HAND_COOLDOWN.getAsDouble();
        if (state.attackingWithOffhand()) {
            state.setOffhandAttackStrengthTicker(0);
            this.attackStrengthTicker = Math.min(this.attackStrengthTicker,
                    CooldownMath.oppositeHandCap(this.getAttributeValue(Attributes.ATTACK_SPEED), fraction));
        } else {
            original.call(player);
            state.setOffhandAttackStrengthTicker(Math.min(state.offhandAttackStrengthTicker(),
                    CooldownMath.oppositeHandCap(ofc$getOffhandAttributeValue(Attributes.ATTACK_SPEED), fraction)));
        }
    }

    @WrapOperation(method = "attack",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getItemInHand(Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack offhandcombat$readOffhandStack(Player player, InteractionHand hand,
                                                      Operation<ItemStack> original) {
        return ofc$isAttackingWithOffhand()
                ? original.call(player, InteractionHand.OFF_HAND)
                : original.call(player, hand);
    }

    @Override
    public int ofc$getOffhandAttackStrengthTicker() {
        return offhandcombat$state().offhandAttackStrengthTicker();
    }

    @Override
    public void ofc$setOffhandAttackStrengthTicker(int ticks) {
        offhandcombat$state().setOffhandAttackStrengthTicker(ticks);
    }

    @Override
    public float ofc$getOffhandAttackStrengthScale(float partialTick) {
        return CooldownMath.strength(ofc$getOffhandAttackStrengthTicker(), partialTick,
                ofc$getOffhandAttributeValue(Attributes.ATTACK_SPEED));
    }

    @Override
    public AttributeMap ofc$getOffhandAttributes() {
        OffhandCombatState state = offhandcombat$state();
        AttributeMap active = state.activeOffhandAttributes();
        return active != null ? active : offhandcombat$createOffhandAttributes();
    }

    @Override
    public double ofc$getOffhandAttributeValue(Holder<Attribute> attribute) {
        return ofc$getOffhandAttributes().getValue(attribute);
    }

    @Override
    public boolean ofc$isAttackingWithOffhand() {
        return offhandcombat$state().attackingWithOffhand();
    }

    @Override
    public void ofc$attackWithOffhand(Entity target) {
        OffhandCombatState state = offhandcombat$state();
        if (state.attackingWithOffhand()) {
            return;
        }
        state.setActiveOffhandAttributes(offhandcombat$createOffhandAttributes());
        state.setAttackingWithOffhand(true);
        try {
            ((Player) (Object) this).attack(target);
        } finally {
            state.setAttackingWithOffhand(false);
            state.setActiveOffhandAttributes(null);
        }
    }

    @Unique
    private OffhandCombatState offhandcombat$state() {
        return ((Player) (Object) this).getData(OffhandCombatAttachments.COMBAT_STATE);
    }

    @Unique
    private AttributeMap offhandcombat$createOffhandAttributes() {
        AttributeMap copy = new AttributeMap(DefaultAttributes.getSupplier((EntityType<? extends LivingEntity>) this.getType()));
        copy.assignAllValues(this.getAttributes());
        offhandcombat$swapHandModifiers(copy, this.getMainHandItem(), EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND);
        offhandcombat$swapHandModifiers(copy, this.getOffhandItem(), EquipmentSlot.OFFHAND, EquipmentSlot.MAINHAND);
        return copy;
    }

    @Unique
    private static void offhandcombat$swapHandModifiers(AttributeMap attributes, ItemStack stack,
                                                         EquipmentSlot removeSlot, EquipmentSlot addSlot) {
        if (stack.isEmpty()) {
            return;
        }
        stack.forEachModifier(removeSlot, (attribute, modifier) -> {
            AttributeInstance instance = attributes.getInstance(attribute);
            if (instance != null) {
                instance.removeModifier(modifier.id());
            }
        });
        stack.forEachModifier(addSlot, (attribute, modifier) -> {
            AttributeInstance instance = attributes.getInstance(attribute);
            if (instance != null) {
                instance.removeModifier(modifier.id());
                instance.addTransientModifier(modifier);
            }
        });
    }
}
