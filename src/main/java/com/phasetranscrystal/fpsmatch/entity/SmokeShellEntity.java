package com.phasetranscrystal.fpsmatch.entity;

import com.phasetranscrystal.fpsmatch.item.FPSMItemRegister;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.TheEndGatewayBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

public class SmokeShellEntity extends ThrowableItemProjectile {
    public final int lifeTick;
    private int lifeLeft;
    private int state;
    private int particleTicker = 0;
    public final int r;

    public SmokeShellEntity(LivingEntity pShooter, Level pLevel, int lifeTick, int state, int r) {
        super(EntityRegister.SMOKE_SHELL.get(), pShooter, pLevel);
        this.lifeTick = lifeTick;
        this.lifeLeft = lifeTick;
        this.state = state;
        this.r = r;
    }

    public SmokeShellEntity(LivingEntity pShooter, Level pLevel){
        super(EntityRegister.SMOKE_SHELL.get(), pShooter, pLevel);
        this.lifeTick = 300;
        this.r = 2;
    }

    public SmokeShellEntity(EntityType<SmokeShellEntity> pEntityType, Level pLevel) {
        super(pEntityType, pLevel);
        this.lifeTick = 300;
        this.r = 2;
    }

    @Override
    public void tick() {
        if (lifeLeft-- <= 0) {
            this.discard();
            return;
        } else if (lifeLeft < lifeTick / 2 && state == 0) {
            this.state = 1;
        }
        this.lifeLeft--;
        //TODO particles generate


        if (state == 1) {
            if (particleTicker == 5) {
                this.level().addParticle(ParticleTypes.SMOKE, this.getX(), this.getY(), this.getZ(), 0.0D, 0.0D, 0.0D);
                particleTicker = 0;
            } else {
                particleTicker++;
            }
        } else if (state == 2) {
            Random random = new Random();
            this.level().addParticle(ParticleTypes.SMOKE, this.getX() + random.nextFloat(r), this.getY() + random.nextFloat(r), this.getZ() + random.nextFloat(r), 0.0D, 0.0D, 0.0D);
            this.level().addParticle(ParticleTypes.ASH, this.getX(), this.getY(), this.getZ(), random.nextFloat(-0.2F, 0.2F), random.nextFloat(0.3F), random.nextFloat(-0.2F, 0.2F));
        }

        super.tick();
    }

    @Override
    protected Item getDefaultItem() {
        return FPSMItemRegister.SMOKE_SHELL.get();
    }

    @Override
    protected void onHit(HitResult r) {
        if (state == 2) return;
        super.onHit(r);
        if (!(r instanceof BlockHitResult result)) return;
        if (state == 0) state = 1;

        if (result.getDirection().getAxis().isHorizontal()) {
            Vec3 delta = getDeltaMovement();
            this.setDeltaMovement(result.getDirection().getAxis() == Direction.Axis.X ? new Vec3(-delta.x, delta.y, delta.z) : new Vec3(delta.x, delta.y, -delta.z));
        } else if (result.getDirection() == Direction.DOWN || this.getDeltaMovement().y > -0.2) {
            Vec3 delta = getDeltaMovement();
            this.setDeltaMovement(new Vec3(delta.x, -delta.y, delta.z));
        } else {
            this.setDeltaMovement(0, 0, 0);
            this.setNoGravity(true);
            this.state = 2;
        }
    }
}
