package com.phasetranscrystal.fpsmatch.entity;

import com.phasetranscrystal.fpsmatch.item.FPSMItemRegister;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ClipBlockStateContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class FlashBombEntity extends ThrowableItemProjectile {
    private static final EntityDataAccessor<Integer> LIFE_LEFT = SynchedEntityData.defineId(FlashBombEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(FlashBombEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LIFE_TICK = SynchedEntityData.defineId(FlashBombEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> R = SynchedEntityData.defineId(FlashBombEntity.class, EntityDataSerializers.INT);
    private int particleTicker = 0;
    public FlashBombEntity(LivingEntity pShooter, Level pLevel, int lifeTick, int state, int r) {
        super(EntityRegister.FLASH_BOMB.get(), pShooter, pLevel);
        this.setLifeTick(lifeTick);
        this.setLifeLeft(lifeTick);
        this.setState(state);
        this.setR(r);
    }

    public FlashBombEntity(LivingEntity pShooter, Level pLevel){
        super(EntityRegister.FLASH_BOMB.get(), pShooter, pLevel);
    }

    public FlashBombEntity(EntityType<FlashBombEntity> pEntityType, Level pLevel) {
        super(pEntityType, pLevel);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(LIFE_TICK, 140);
        this.entityData.define(R, 5);
        this.entityData.define(LIFE_LEFT, 140);
        this.entityData.define(STATE, 0);
    }
    public int getR(){
        return this.entityData.get(R);
    }
    public void setR(int r){
        this.entityData.set(R,r);
    }
    public int getLifeTick(){
        return this.entityData.get(LIFE_TICK);
    }

    public void setLifeTick(int lifeTick){
        this.entityData.set(LIFE_TICK,lifeTick);
    }

    public void setState(int state){
        this.entityData.set(STATE,state);
    }

    public int getState(){
        return this.entityData.get(STATE);
    }

    public int getLifeLeft(){
        return this.entityData.get(LIFE_LEFT);
    }

    public void setLifeLeft(int lifeLeft){
        this.entityData.set(LIFE_LEFT,lifeLeft);
    }

    @Override
    public void tick() {
        if(this.isNoGravity()){
            this.setDeltaMovement(0, 0, 0);
        }

        if(!this.level().isClientSide){

            this.particleTicker++;

            if(this.particleTicker >= 40){
                AABB aabb = new AABB(this.getX() - this.getR(), this.getY() - this.getR(), this.getZ() - this.getR(), this.getX() + this.getR(), this.getY() + this.getR(), this.getZ() + this.getR());
                this.applyEntity(aabb);
                this.discard();
            }

            if (this.getLifeLeft() <= 0) {
                this.discard();
                return;

            } else if (this.getLifeLeft() < this.getLifeTick() / 2 && this.getState() == 0) {
                this.setState(1);
            }

            if(this.getState() == 2){
                this.setLifeLeft(getLifeLeft() - 1);
            }
        }

        if (this.getState() == 2 && !this.level().isClientSide) {
            AABB aabb = new AABB(this.getX() - this.getR(), this.getY() - this.getR(), this.getZ() - this.getR(), this.getX() + this.getR(), this.getY() + this.getR(), this.getZ() + this.getR());
            this.applyEntity(aabb);
            this.discard();
        }

        super.tick();
    }

    public void applyEntity(AABB aabb){
        List<Entity> entities = this.level().getEntities(this, aabb);
        for(Entity entity : entities){
            if(entity instanceof LivingEntity livingEntity){
                livingEntity.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100, 0));
            }
        }
    }

    @Override
    protected @NotNull Item getDefaultItem() {
        return FPSMItemRegister.GRENADE.get();
    }

    @Override
    protected void onHit(@NotNull HitResult r) {
        if (this.getState() == 2) return;
        super.onHit(r);
        if (!(r instanceof BlockHitResult result)) return;
        if (this.getState() == 0) this.setState(1);

        Vec3 delta = getDeltaMovement();
        double reductionFactor = 0.25;

        if (result.getDirection().getAxis().isHorizontal()) {
            this.setDeltaMovement(result.getDirection().getAxis() == Direction.Axis.X ? new Vec3(-delta.x * reductionFactor, delta.y * reductionFactor, delta.z * reductionFactor) : new Vec3(delta.x * reductionFactor, delta.y * reductionFactor, -delta.z * reductionFactor));
        } else if (result.getDirection() == Direction.DOWN || this.getDeltaMovement().y < -0.2) {
            this.setDeltaMovement(new Vec3(delta.x, -(delta.y * reductionFactor), delta.z));
        } else {
            this.setDeltaMovement(0, 0, 0);
            this.setNoGravity(true);
            this.setState(2);
        }
    }

}
