package com.tacz.guns.api.event.common;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.Event;

import javax.annotation.Nullable;

public class GunOwnerChangeEvent extends Event implements KubeJSGunEventPoster<GunOwnerChangeEvent> {
    private final ItemStack gunItemStack;
    private final @Nullable LivingEntity oldOwner;
    private final @Nullable LivingEntity newOwner;

    public GunOwnerChangeEvent(ItemStack gunItemStack, @Nullable LivingEntity oldOwner, @Nullable LivingEntity newOwner) {
        this.gunItemStack = gunItemStack == null ? ItemStack.EMPTY : gunItemStack;
        this.oldOwner = oldOwner;
        this.newOwner = newOwner;
        postEventToKubeJS(this);
    }

    public ItemStack getGunItemStack() {
        return gunItemStack;
    }

    @Nullable
    public LivingEntity getOldOwner() {
        return oldOwner;
    }

    @Nullable
    public LivingEntity getNewOwner() {
        return newOwner;
    }
}
