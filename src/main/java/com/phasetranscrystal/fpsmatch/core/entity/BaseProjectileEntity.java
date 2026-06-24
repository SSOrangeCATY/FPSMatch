package com.phasetranscrystal.fpsmatch.core.entity;

import com.phasetranscrystal.fpsmatch.common.gamerule.FPSMatchRule;
import net.minecraft.core.Direction;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

public abstract class BaseProjectileEntity extends ThrowableItemProjectile {
    private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(BaseProjectileEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> ACTIVATED = SynchedEntityData.defineId(BaseProjectileEntity.class, EntityDataSerializers.BOOLEAN);

    protected boolean activateOnGroundHit = false;
    protected double horizontalReduction = 0.25;
    protected double verticalReduction = 0.25;
    protected double minVerticalSpeed = -0.1;

    public BaseProjectileEntity(EntityType<? extends BaseProjectileEntity> type, Level level) {
        super(type, level);
    }

    public BaseProjectileEntity(EntityType<? extends BaseProjectileEntity> type, LivingEntity shooter, Level level) {
        super(type, shooter, level, ItemStack.EMPTY);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(STATE, 0);
        entityData.define(ACTIVATED, false);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide() && this.isActivated()) {
            onActiveTick();
        }
    }

    @Override
    public boolean isAlwaysTicking() {
        return true;
    }

    @Override
    protected void onHit(@NotNull HitResult r) {
        if (this.getState() == 2) return;
        super.onHit(r);

        if (r instanceof EntityHitResult entityHitResult) {
            entityHitResult.getEntity().hurt(this.damageSource(), 1);
        }

        if (!(r instanceof BlockHitResult result)) return;
        if (this.level() instanceof ServerLevel serverLevel
                && serverLevel.getGameRules().get(FPSMatchRule.RULE_THROWABLE_CAN_CROSS_BARRIER)) {
            if (this.level().getBlockState(result.getBlockPos()).getBlock() == Blocks.BARRIER) {
                return;
            }
        }
        if (getState() == 0) setState(1);

        Vec3 delta = getDeltaMovement();

        playCollisionSound(level().getBlockState(result.getBlockPos()));

        if (result.getDirection() == Direction.UP) {
            if (activateOnGroundHit) {
                markActivated();
                handleSurfaceStick(result);
            } else {
                if (delta.y >= minVerticalSpeed) {
                    markActivated();
                    handleSurfaceStick(result);
                } else {
                    handleVerticalCollision(delta);
                }
            }
        } else {
            handleGeneralCollision(result, delta);
        }

        playCollisionSound(level().getBlockState(result.getBlockPos()));
    }

    private void handleGeneralCollision(BlockHitResult result, Vec3 delta) {
        Direction dir = result.getDirection();

        if (dir.getAxis().isHorizontal()) {
            handleHorizontalCollision(dir, delta);
        } else if (dir == Direction.DOWN || delta.y < minVerticalSpeed) {
            handleVerticalCollision(delta);
        } else {
            handleHorizontalCollision(dir, delta);
            handleVerticalCollision(getDeltaMovement());
        }
    }

    private void handleSurfaceStick(BlockHitResult result) {
        Vec3 hitPos = result.getLocation();
        Direction dir = result.getDirection();
        Vec3 correctedPos = hitPos.add(dir.getStepX() * 0.001,
                dir.getStepY() * 0.001,
                dir.getStepZ() * 0.001);
        this.setPos(correctedPos.x, correctedPos.y, correctedPos.z);

        this.setDeltaMovement(Vec3.ZERO);
        this.setNoGravity(true);
        this.setState(2);
    }

    private void handleHorizontalCollision(Direction direction, Vec3 delta) {
        double reducedX = delta.x * horizontalReduction;
        double reducedZ = delta.z * horizontalReduction;

        if (direction.getAxis() == Direction.Axis.X) {
            this.setDeltaMovement(-reducedX, delta.y, reducedZ);
        } else {
            this.setDeltaMovement(reducedX, delta.y, -reducedZ);
        }
    }

    private void handleVerticalCollision(Vec3 delta) {
        this.setDeltaMovement(
                delta.x * verticalReduction,
                -(delta.y * verticalReduction),
                delta.z * verticalReduction
        );
    }

    private void playCollisionSound(BlockState blockState) {
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                blockState.getSoundType().getStepSound(), SoundSource.PLAYERS, 1, 1.6F);
    }

    protected final void markActivated() {
        if (!level().isClientSide()) {
            entityData.set(ACTIVATED, true);
            onActivated();
        }
    }

    protected void onActivated() {}

    protected void onActiveTick() {}

    public int getState() {
        return entityData.get(STATE);
    }

    protected void setState(int state) {
        entityData.set(STATE, state);
    }

    public boolean isActivated() {
        return entityData.get(ACTIVATED);
    }

    public boolean isActivateOnGroundHit() {
        return activateOnGroundHit;
    }

    public void setActivateOnGroundHit(boolean activateOnGroundHit) {
        this.activateOnGroundHit = activateOnGroundHit;
    }

    public double getHorizontalReduction() {
        return horizontalReduction;
    }

    public void setHorizontalReduction(double horizontalReduction) {
        this.horizontalReduction = horizontalReduction;
    }

    public double getVerticalReduction() {
        return verticalReduction;
    }

    public void setVerticalReduction(double verticalReduction) {
        this.verticalReduction = verticalReduction;
    }

    @Override
    public boolean shouldRender(double pX, double pY, double pZ) {
        return true;
    }

    public DamageSource damageSource() {
        if (this.getOwner() instanceof LivingEntity livingEntity) {
            return this.damageSources().mobProjectile(this, livingEntity);
        } else {
            return this.damageSources().mobProjectile(this, null);
        }
    }

    @Override
    public @NotNull ItemStack getItem() {
        return new ItemStack(this.getDefaultItem());
    }
}
