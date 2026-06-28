package com.tacz.guns.api.event.common;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.Event;

public class GunCycleEvent extends Event implements KubeJSGunEventPoster<GunCycleEvent> {
    public enum Phase {
        START,
        FEED,
        FINISH
    }

    private final LivingEntity shooter;
    private final ItemStack gunItemStack;
    private final Phase phase;
    private long durationMs;

    public GunCycleEvent(LivingEntity shooter, ItemStack gunItemStack, Phase phase, long durationMs) {
        this.shooter = shooter;
        this.gunItemStack = gunItemStack == null ? ItemStack.EMPTY : gunItemStack;
        this.phase = phase;
        this.durationMs = Math.max(durationMs, 0);
        postEventToKubeJS(this);
    }

    public LivingEntity getShooter() {
        return shooter;
    }

    public ItemStack getGunItemStack() {
        return gunItemStack;
    }

    public Phase getPhase() {
        return phase;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = Math.max(durationMs, 0);
    }
}
