package dev.nekomario.offhandcombat.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.nekomario.offhandcombat.api.OffhandAttackAccess;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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

    @Inject(method = "swing(Lnet/minecraft/world/InteractionHand;Z)V", at = @At("HEAD"))
    private void offhandcombat$applySwingCooldown(InteractionHand hand, boolean updateSelf, CallbackInfo ci) {
        if ((Object) this instanceof Player player) {
            ((OffhandAttackAccess) player).ofc$applySwingCooldown(hand);
        }
    }
}
