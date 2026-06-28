package com.tacz.guns.api.event.common;

import com.tacz.guns.api.item.runtime.GunRuntimeContext;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.Event;

public class GunShotContextEvent extends Event implements KubeJSGunEventPoster<GunShotContextEvent> {
    private final LivingEntity shooter;
    private final ItemStack gunItemStack;
    private GunRuntimeContext context;

    public GunShotContextEvent(LivingEntity shooter, ItemStack gunItemStack, GunRuntimeContext context) {
        this.shooter = shooter;
        this.gunItemStack = gunItemStack == null ? ItemStack.EMPTY : gunItemStack;
        this.context = context;
        postEventToKubeJS(this);
    }

    public LivingEntity getShooter() {
        return shooter;
    }

    public ItemStack getGunItemStack() {
        return gunItemStack;
    }

    public GunRuntimeContext getContext() {
        return context;
    }

    public void setContext(GunRuntimeContext context) {
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }
        this.context = context;
    }
}
