package com.phasetranscrystal.fpsmatch.entity;

import com.phasetranscrystal.fpsmatch.item.FPSMItemRegister;
import com.phasetranscrystal.fpsmatch.item.FPSMSoundRegister;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;

import java.util.List;
import java.util.Random;

public class TIncendiaryGrenadeEntity extends ThrowableItemProjectile {
    private static final EntityDataAccessor<Integer> LIFE_LEFT = SynchedEntityData.defineId(TIncendiaryGrenadeEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(TIncendiaryGrenadeEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LIFE_TICK = SynchedEntityData.defineId(TIncendiaryGrenadeEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> R = SynchedEntityData.defineId(TIncendiaryGrenadeEntity.class, EntityDataSerializers.INT);
    private int particleTicker = 0;
    private AreaEffectCloud damageCloud;
    public TIncendiaryGrenadeEntity(LivingEntity pShooter, Level pLevel, int lifeTick, int state, int r) {
        super(EntityRegister.T_INCENDIARY_GRENADE.get(), pShooter, pLevel);
        this.setLifeTick(lifeTick);
        this.setLifeLeft(lifeTick);
        this.setState(state);
        this.setR(r);
    }

    public TIncendiaryGrenadeEntity(LivingEntity pShooter, Level pLevel){
        super(EntityRegister.T_INCENDIARY_GRENADE.get(), pShooter, pLevel);
    }

    public TIncendiaryGrenadeEntity(EntityType<TIncendiaryGrenadeEntity> pEntityType, Level pLevel) {
        super(pEntityType, pLevel);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(LIFE_TICK, 140);
        this.entityData.define(R, 4);
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
    public int getParticleTicker(){
        return particleTicker;
    }
    public void setParticleTicker(int particleTicker){
        this.particleTicker = particleTicker;
    }

    @Override
    public void tick() {
        if(this.isNoGravity()){
            this.setDeltaMovement(0, 0, 0);
        }

        if(!this.level().isClientSide){
            if(this.getState() == 2 && this.damageCloud == null){
                this.damageCloud = new AreaEffectCloud(this.level(),this.getX(),this.getY(),this.getZ());
                this.damageCloud.setRadius(this.getR());
                this.damageCloud.setDuration(140);
                this.damageCloud.setWaitTime(140);
                if(this.getOwner() instanceof LivingEntity livingEntity){
                    this.damageCloud.setOwner(livingEntity);
                }
                this.level().addFreshEntity(this.damageCloud);
            }

            if(this.damageCloud != null && this.getLifeLeft() % 10 == 0){
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.FIRE_AMBIENT, SoundSource.VOICE, 1.5F, 1F);
                AABB aabb = this.damageCloud.getBoundingBox();
                AABB apply = new AABB(aabb.minX, aabb.minY, aabb.minZ, aabb.maxX, aabb.maxY + 1F, aabb.maxZ);
                List<Entity> list1 = this.level().getEntitiesOfClass(Entity.class, apply);
                for(Entity entity : list1) {
                    if (entity instanceof Player player) {
                        player.setSecondsOnFire(1);
                        player.hurt(this.level().damageSources().onFire(), 3.0F);
                    }

                    if(entity instanceof SmokeShellEntity smokeShell && smokeShell.getState() == 2){
                        this.discard();
                        if(this.damageCloud != null){
                            this.damageCloud.discard();
                        }
                    }
                }
            }

            if(this.getState() != 2){
                this.particleTicker++;
            }

            if(this.particleTicker >= 30){
                this.discard();
                this.spawnParticle(new AABB(this.getX() - 2, this.getY() - 2, this.getZ() - 2, this.getX() + 2, this.getY() + 2, this.getZ() + 2),new Random(),(ServerLevel) this.level(),5);
                if(this.damageCloud != null){
                    this.damageCloud.discard();
                }
            }

            if (this.getLifeLeft() <= 0) {
                this.discard();
                if(this.damageCloud != null){
                    this.damageCloud.discard();
                }
                return;

            } else if (this.getLifeLeft() < this.getLifeTick() / 2 && this.getState() == 0) {
                this.setState(1);
            }

            if(this.getState() == 2){
                this.setLifeLeft(getLifeLeft() - 1);
            }
        }

        if (this.getState() == 2 && !this.level().isClientSide) {
            Random random = new Random();
            this.spawnRoundSmokeParticles(random, (ServerLevel) this.level());
        }

        super.tick();
    }

    public void spawnRoundSmokeParticles(Random random, ServerLevel serverLevel) {
        if(this.damageCloud != null){
            AABB aabb = this.damageCloud.getBoundingBox();
            this.spawnParticle(aabb,random,serverLevel,20);
        }
    }

    public void spawnParticle(AABB aabb , Random random, ServerLevel serverLevel,int time){
        for (int i = 0;i<time;i++){
            double x = random.nextDouble(aabb.minX,aabb.maxX);
            double y = random.nextDouble(aabb.minY,aabb.maxY);
            double z = random.nextDouble(aabb.minZ,aabb.maxZ);
            DustColorTransitionOptions particle = new DustColorTransitionOptions(new Vector3f(1, 0.25f, 0.25f),new Vector3f(1, 1f, 0.1F), random.nextFloat(3f));
            serverLevel.sendParticles(particle,x,y,z,2,0,0.2,0,1);
        }
    }



    @Override
    protected @NotNull Item getDefaultItem() {
        return FPSMItemRegister.T_INCENDIARY_GRENADE.get();
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
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.STONE_STEP, SoundSource.VOICE, 1.5F, 2F);
        } else if (result.getDirection() == Direction.DOWN || this.getDeltaMovement().y < -0.2) {
            this.setDeltaMovement(new Vec3(delta.x, -(delta.y * reductionFactor), delta.z));
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.STONE_STEP, SoundSource.VOICE, 1.5F, 2F);
        } else {
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.GLASS_BREAK, SoundSource.VOICE, 1.5F, 1.3F);
            this.setDeltaMovement(0, 0, 0);
            this.setNoGravity(true);
            this.setState(2);
        }
    }

}
