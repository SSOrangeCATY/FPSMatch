package com.tacz.guns.api.event.common;

import com.tacz.guns.api.item.ammo.GunAmmoSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

public class GunAmmoSlotChangeEvent extends Event implements KubeJSGunEventPoster<GunAmmoSlotChangeEvent>, ICancellableEvent {
    private final LivingEntity entity;
    private final ItemStack gunItemStack;
    private final GunAmmoSlot oldSlot;
    private final GunAmmoSlot newSlot;

    public GunAmmoSlotChangeEvent(LivingEntity entity, ItemStack gunItemStack, GunAmmoSlot oldSlot, GunAmmoSlot newSlot) {
        this.entity = entity;
        this.gunItemStack = gunItemStack;
        this.oldSlot = oldSlot;
        this.newSlot = newSlot;
        postEventToKubeJS(this);
    }

    public LivingEntity getEntity() {
        return entity;
    }

    public ItemStack getGunItemStack() {
        return gunItemStack;
    }

    public GunAmmoSlot getOldSlot() {
        return oldSlot;
    }

    public GunAmmoSlot getNewSlot() {
        return newSlot;
    }
}
