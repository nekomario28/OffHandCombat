package dev.nekomario.offhandcombat.api;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeMap;

public interface OffhandAttackAccess {
    int ofc$getOffhandAttackStrengthTicker();

    void ofc$setOffhandAttackStrengthTicker(int ticks);

    float ofc$getOffhandAttackStrengthScale(float partialTick);

    AttributeMap ofc$getOffhandAttributes();

    double ofc$getOffhandAttributeValue(Holder<Attribute> attribute);

    boolean ofc$isAttackingWithOffhand();

    void ofc$attackWithOffhand(Entity target);
}
