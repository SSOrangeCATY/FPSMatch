package com.tacz.guns.api.event.common;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.Event;

public class GunAimStateEvent extends Event implements KubeJSGunEventPoster<GunAimStateEvent> {
    private final LivingEntity shooter;
    private final ItemStack gunItemStack;
    private boolean retainAimAfterCycle;
    private boolean retainScopeAfterShot;

    public GunAimStateEvent(LivingEntity shooter, ItemStack gunItemStack, boolean retainAimAfterCycle, boolean retainScopeAfterShot) {
        this.shooter = shooter;
        this.gunItemStack = gunItemStack == null ? ItemStack.EMPTY : gunItemStack;
        this.retainAimAfterCycle = retainAimAfterCycle;
        this.retainScopeAfterShot = retainScopeAfterShot;
        postEventToKubeJS(this);
    }

    public LivingEntity getShooter() {
        return shooter;
    }

    public ItemStack getGunItemStack() {
        return gunItemStack;
    }

    public boolean isRetainAimAfterCycle() {
        return retainAimAfterCycle;
    }

    public void setRetainAimAfterCycle(boolean retainAimAfterCycle) {
        this.retainAimAfterCycle = retainAimAfterCycle;
    }

    public boolean isRetainScopeAfterShot() {
        return retainScopeAfterShot;
    }

    public void setRetainScopeAfterShot(boolean retainScopeAfterShot) {
        this.retainScopeAfterShot = retainScopeAfterShot;
    }
}
