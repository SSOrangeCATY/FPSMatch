package com.phasetranscrystal.fpsmatch.common.event;

import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.eventbus.api.Event;

/**
 * FPSMatch 枪械伤害事件（替代 TACZ EntityHurtByGunEvent）。
 * 由 TACZ 兼容层（TACZGunEventBridge）桥接触发。
 */
public class FPSMGunDamageEvent extends Event {
    private final LivingEntity hurtEntity;
    private float baseAmount;
    private final boolean isHeadShot;
    private float headshotMultiplier = 1.0F;

    public FPSMGunDamageEvent(LivingEntity hurtEntity, float baseAmount, boolean isHeadShot) {
        this.hurtEntity = hurtEntity;
        this.baseAmount = baseAmount;
        this.isHeadShot = isHeadShot;
    }

    public LivingEntity getHurtEntity() { return hurtEntity; }
    public float getBaseAmount() { return baseAmount; }
    public void setBaseAmount(float baseAmount) { this.baseAmount = baseAmount; }
    public boolean isHeadShot() { return isHeadShot; }
    public float getHeadshotMultiplier() { return headshotMultiplier; }
    public void setHeadshotMultiplier(float headshotMultiplier) { this.headshotMultiplier = headshotMultiplier; }
}