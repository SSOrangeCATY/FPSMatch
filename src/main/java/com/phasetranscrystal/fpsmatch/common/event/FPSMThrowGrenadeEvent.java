package com.phasetranscrystal.fpsmatch.common.event;

import com.phasetranscrystal.fpsmatch.common.item.BaseThrowAbleItem;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

public class FPSMThrowGrenadeEvent extends Event implements ICancellableEvent {
    private final LivingEntity entity;
    private final ItemStack itemStack;
    private final BaseThrowAbleItem.ThrowType type;

    public FPSMThrowGrenadeEvent(LivingEntity entity, ItemStack itemStack, BaseThrowAbleItem.ThrowType type) {
        this.entity = entity;
        this.itemStack = itemStack;
        this.type = type;
    }

    public LivingEntity getEntity() {
        return entity;
    }

    public ItemStack getItemStack() {
        return itemStack;
    }

    public BaseThrowAbleItem.ThrowType getType() {
        return type;
    }
}
