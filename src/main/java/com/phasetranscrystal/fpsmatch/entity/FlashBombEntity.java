package com.phasetranscrystal.fpsmatch.entity;

import com.phasetranscrystal.fpsmatch.effect.FPSMEffectRegister;
import com.phasetranscrystal.fpsmatch.effect.FlashBlindnessMobEffect;
import com.phasetranscrystal.fpsmatch.item.FPSMItemRegister;
import net.minecraft.core.Direction;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ClipContext;
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
        this.entityData.define(R, 40);
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

            if(this.particleTicker >= 30){
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
            Vec3 eye = new Vec3(entity.getX(),entity.getEyeY(),entity.getZ());
            boolean isBlocked = this.level().clip(new ClipContext(eye, this.position(), ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, null)).getType() == HitResult.Type.BLOCK;
            // 检查是否被方块遮挡
            if(isBlocked){
                continue;
            }

            // 检查玩家是否看向闪光弹
            if(entity instanceof LivingEntity livingEntity){
                Vec3 vec3 = livingEntity.getLookAngle();
                Vec3 vec31 = new Vec3(this.getX() - eye.x(), this.getY() - eye.y(), this.getZ() - eye.z());
                double d = vec3.x * vec31.x + vec3.y * vec31.y + vec3.z * vec31.z;
                double d1 = vec3.length() * vec31.length();
                // 计算余弦值
                double c = d / d1;

                int fullBlindnessTime;
                int totalBlindnessTime;

                if (c > Math.cos(Math.toRadians(53))) {
                    fullBlindnessTime = 38;
                    totalBlindnessTime = 98;
                } else if (c > Math.cos(Math.toRadians(72))) {
                    fullBlindnessTime = 9;
                    totalBlindnessTime = 68;
                } else if (c > Math.cos(Math.toRadians(101))) {
                    fullBlindnessTime = 2;
                    totalBlindnessTime = 39;
                } else {
                    fullBlindnessTime = 2;
                    totalBlindnessTime = 19;
                }

                MobEffectInstance instance = new MobEffectInstance(FPSMEffectRegister.FLASH_BLINDNESS.get(),totalBlindnessTime,1);
                if(instance.getEffect() instanceof FlashBlindnessMobEffect flashBlindnessMobEffect){
                    flashBlindnessMobEffect.setFullBlindnessTime(fullBlindnessTime);
                    flashBlindnessMobEffect.setTotalAndTicker(totalBlindnessTime - fullBlindnessTime);
                }
                livingEntity.addEffect(instance);
            }
        }
    }

    @Override
    protected @NotNull Item getDefaultItem() {
        return FPSMItemRegister.FLASH_BOMB.get();
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
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.CALCITE_PLACE, SoundSource.VOICE, 1.5F, 1.6F);
        } else if (result.getDirection() == Direction.DOWN || this.getDeltaMovement().y < -0.2) {
            this.setDeltaMovement(new Vec3(delta.x, -(delta.y * reductionFactor), delta.z));
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.CALCITE_PLACE, SoundSource.VOICE, 1.5F, 1.6F);
        } else {
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.CALCITE_PLACE, SoundSource.VOICE, 1.5F, 1.6F);
            this.setDeltaMovement(0, 0, 0);
            this.setNoGravity(true);
            this.setState(2);
        }
    }

}
