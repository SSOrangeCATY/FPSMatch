package com.phasetranscrystal.fpsmatch.entity;

import com.phasetranscrystal.fpsmatch.item.FPSMItemRegister;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
import org.joml.Vector3f;

import java.util.List;

public class GrenadeEntity extends ThrowableItemProjectile {
    private static final EntityDataAccessor<Integer> LIFE_LEFT = SynchedEntityData.defineId(GrenadeEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(GrenadeEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LIFE_TICK = SynchedEntityData.defineId(GrenadeEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> R = SynchedEntityData.defineId(GrenadeEntity.class, EntityDataSerializers.INT);
    private int particleTicker = 0;
    public GrenadeEntity(LivingEntity pShooter, Level pLevel, int lifeTick, int state, int r) {
        super(EntityRegister.GRENADE.get(), pShooter, pLevel);
        this.setLifeTick(lifeTick);
        this.setLifeLeft(lifeTick);
        this.setState(state);
        this.setR(r);
    }

    public GrenadeEntity(LivingEntity pShooter, Level pLevel){
        super(EntityRegister.GRENADE.get(), pShooter, pLevel);
    }

    public GrenadeEntity(EntityType<GrenadeEntity> pEntityType, Level pLevel) {
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

            if(this.particleTicker >= 30){
                AABB aabb = new AABB(this.getX() - this.getR(), this.getY() - this.getR(), this.getZ() - this.getR(), this.getX() + this.getR(), this.getY() + this.getR(), this.getZ() + this.getR());
                this.damageEntity(aabb);
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
            this.damageEntity(aabb);
            this.discard();
        }

        super.tick();
    }

    public void damageEntity(AABB aabb){
        ((ServerLevel)this.level()).sendParticles(ParticleTypes.EXPLOSION, this.getX(), this.getY(), this.getZ(), 3,0,0, 0, 1D);
        DustColorTransitionOptions particle = new DustColorTransitionOptions(new Vector3f(1, 0.25f, 0.25f),new Vector3f(1, 1f, 0.1F),3);
        ((ServerLevel)this.level()).sendParticles(particle, this.getX(), this.getY(), this.getZ(), 50,1.5,1, 1.5f, 1D);

        List<Entity> entities = this.level().getEntitiesOfClass(Entity.class,aabb);
        for(Entity entity : entities){
            if(entity instanceof LivingEntity livingEntity){
                double distance = this.distanceTo(livingEntity);
                // 发送射线给玩家检测是否被方块阻挡
                boolean isBlocked = this.level().isBlockInLine(new ClipBlockStateContext(this.position(), livingEntity.position(),  (block)-> !block.isAir())).isInside();
                //根据距离计算应用的伤害
                double modify = (this.getR() - distance) / this.getR();
                float damage = (float) (20 * modify);
                if(damage > 0 && damage <= 0.5){
                    damage = 1;
                }
                livingEntity.hurt(this.level().damageSources().outOfBorder(),!isBlocked ? damage : 1);
            } else if (entity instanceof SmokeShellEntity smoke_shell) {
                if (smoke_shell.getState() == 2) {
                    smoke_shell.setCanGenerateParticles(false);
                }
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
