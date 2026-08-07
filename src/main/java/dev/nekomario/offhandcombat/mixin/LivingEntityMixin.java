package dev.nekomario.offhandcombat.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.nekomario.offhandcombat.api.OffhandAttackAccess;
import dev.nekomario.offhandcombat.attachment.OffhandCombatAttachments;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @ModifyReturnValue(method = "getWeaponItem", at = @At("RETURN"))
    private ItemStack offhandcombat$useOffhandWeapon(ItemStack original) {
        if ((Object) this instanceof Player player
                && ((OffhandAttackAccess) player).ofc$isAttackingWithOffhand()) {
            return player.getOffhandItem();
        }
        return original;
    }

    @Inject(
            method = "swing(Lnet/minecraft/world/InteractionHand;Z)V",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/world/entity/LivingEntity;swinging:Z",
                    opcode = Opcodes.GETFIELD,
                    ordinal = 0
            )
    )
    private void offhandcombat$applySwingCooldown(InteractionHand hand, boolean updateSelf, CallbackInfo ci) {
        if ((Object) this instanceof Player player) {
            ((OffhandAttackAccess) player).ofc$applySwingCooldown(hand);
        }
    }

    @Inject(method = "stopUsingItem", at = @At("HEAD"))
    private void offhandcombat$recordStoppedActiveHand(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self instanceof Player player && self.isUsingItem()) {
            player.getData(OffhandCombatAttachments.COMBAT_STATE)
                    .recordActiveUseStopped(self.getUsedItemHand());
        }
    }
}
