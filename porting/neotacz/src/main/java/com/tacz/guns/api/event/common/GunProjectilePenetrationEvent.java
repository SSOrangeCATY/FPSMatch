package com.tacz.guns.api.event.common;

import com.tacz.guns.api.item.runtime.GunRuntimeContext;
import com.tacz.guns.entity.EntityKineticBullet;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.Event;

public class GunProjectilePenetrationEvent extends Event implements KubeJSGunEventPoster<GunProjectilePenetrationEvent> {
    private final EntityKineticBullet bullet;
    private final GunRuntimeContext context;
    private final BlockHitResult hitResult;
    private final BlockState blockState;
    private final Vec3 tickStart;
    private final Vec3 tickEnd;
    private boolean continueProjectile;
    private int pierceCost = 1;
    private float damageMultiplier = 1.0f;

    public GunProjectilePenetrationEvent(EntityKineticBullet bullet, GunRuntimeContext context, BlockHitResult hitResult,
                                         BlockState blockState, Vec3 tickStart, Vec3 tickEnd) {
        this.bullet = bullet;
        this.context = context;
        this.hitResult = hitResult;
        this.blockState = blockState;
        this.tickStart = tickStart;
        this.tickEnd = tickEnd;
        postEventToKubeJS(this);
    }

    public EntityKineticBullet getBullet() {
        return bullet;
    }

    public GunRuntimeContext getContext() {
        return context;
    }

    public BlockHitResult getHitResult() {
        return hitResult;
    }

    public BlockState getBlockState() {
        return blockState;
    }

    public Vec3 getTickStart() {
        return tickStart;
    }

    public Vec3 getTickEnd() {
        return tickEnd;
    }

    public boolean shouldContinueProjectile() {
        return continueProjectile;
    }

    public void setContinueProjectile(boolean continueProjectile) {
        this.continueProjectile = continueProjectile;
    }

    public int getPierceCost() {
        return pierceCost;
    }

    public void setPierceCost(int pierceCost) {
        this.pierceCost = Math.max(1, pierceCost);
    }

    public float getDamageMultiplier() {
        return damageMultiplier;
    }

    public void setDamageMultiplier(float damageMultiplier) {
        this.damageMultiplier = Math.max(0, damageMultiplier);
    }
}
