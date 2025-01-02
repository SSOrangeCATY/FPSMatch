package com.phasetranscrystal.fpsmatch.entity;

import com.phasetranscrystal.fpsmatch.item.FPSMItemRegister;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.entity.TheEndGatewayBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;

import java.util.Random;

public class SmokeShellEntity extends ThrowableItemProjectile {
    private static final EntityDataAccessor<Integer> LIFE_LEFT = SynchedEntityData.defineId(SmokeShellEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(SmokeShellEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LIFE_TICK = SynchedEntityData.defineId(SmokeShellEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> R = SynchedEntityData.defineId(SmokeShellEntity.class, EntityDataSerializers.INT);

    private int particleTicker = 0;
    public SmokeShellEntity(LivingEntity pShooter, Level pLevel, int lifeTick, int state, int r) {
        super(EntityRegister.SMOKE_SHELL.get(), pShooter, pLevel);
        this.setLifeTick(lifeTick);
        this.setLifeLeft(lifeTick);;
        this.setState(state);
        this.setR(r);
    }

    public SmokeShellEntity(LivingEntity pShooter, Level pLevel){
        super(EntityRegister.SMOKE_SHELL.get(), pShooter, pLevel);
    }

    public SmokeShellEntity(EntityType<SmokeShellEntity> pEntityType, Level pLevel) {
        super(pEntityType, pLevel);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(LIFE_TICK, 300);
        this.entityData.define(R, 4);
        this.entityData.define(LIFE_LEFT, 300);
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
        if(!this.level().isClientSide){
            if (this.getLifeLeft() <= 0) {
                this.discard();
                return;
            } else if (this.getLifeLeft() < this.getLifeTick() / 2 && this.getState() == 0) {
                this.setState(1);
            }
            this.setLifeLeft(getLifeLeft() - 1);
        }
        //TODO particles generate


        if (this.getState() == 1) {
            if (this.getParticleTicker() == 5) {
                this.level().addParticle(ParticleTypes.SMOKE, this.getX(), this.getY(), this.getZ(), 0.0D, 0.0D, 0.0D);
                this.setParticleTicker(0);
            } else {
                this.setParticleTicker(this.getParticleTicker() + 1);
            }
        } else if (this.getState() == 2 && this.level().isClientSide) {
            Random random = new Random();
            for(int i = 0 ; i < 4 ; i++){
                this.spawnRoundSmokeParticles(random);
            }
            this.level().addParticle(ParticleTypes.ASH, this.getX(), this.getY(), this.getZ(), random.nextFloat(-0.2F, 0.2F), random.nextFloat(0.3F), random.nextFloat(-0.2F, 0.2F));
        }

        super.tick();
    }
    public void spawnRoundSmokeParticles(Random random) {
        DustParticleOptions dust = new DustParticleOptions(new Vector3f(1, 1, 1), 10F);
        boolean flag = this.level().getBlockState(this.blockPosition().below().below()).isAir();
        int y_ = flag ? -1 : 1;
        int r = getR();
        for(float j = r ;j > 0 ; j -= 0.5f){
            if(!flag){
                j+=0.25f;
            }
            int theta = random.nextInt(360);
            int phi = random.nextInt(180);
            double k = Math.toRadians(theta);
            double n = Math.toRadians(phi);
            double a = Math.sin(k);
            double b = Math.cos(n);
            double c = Math.cos(k);
            double x = this.getX() + j * a * b;
            double y = this.getY() + j * Math.sin(n) * (random.nextBoolean() ? 1 : y_);
            double z = this.getZ() + j * b * c;
            this.level().addAlwaysVisibleParticle(dust, true, x, y, z, 0.0D, 0.0D, 0.0D);
        }
    }

    @Override
    protected @NotNull Item getDefaultItem() {
        return FPSMItemRegister.SMOKE_SHELL.get();
    }

    @Override
    protected void onHit(HitResult r) {
        if (this.getState() == 2) return;
        super.onHit(r);
        if (!(r instanceof BlockHitResult result)) return;
        if (this.getState() == 0) this.setState(1);

        if (result.getDirection().getAxis().isHorizontal()) {
            Vec3 delta = getDeltaMovement();
            this.setDeltaMovement(result.getDirection().getAxis() == Direction.Axis.X ? new Vec3(-delta.x, delta.y, delta.z) : new Vec3(delta.x, delta.y, -delta.z));
        } else if (result.getDirection() == Direction.DOWN || this.getDeltaMovement().y > -0.2) {
            Vec3 delta = getDeltaMovement();
            this.setDeltaMovement(new Vec3(delta.x, -delta.y, delta.z));
        } else {
            this.setDeltaMovement(0, 0, 0);
            this.setNoGravity(true);
            this.setState(2);
        }
    }
}
