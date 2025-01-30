package com.phasetranscrystal.fpsmatch.core.entity;

import net.minecraft.core.Direction;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

public abstract class BaseProjectileEntity extends ThrowableItemProjectile {
    // 状态同步字段
    private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(BaseProjectileEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> ACTIVATED = SynchedEntityData.defineId(BaseProjectileEntity.class, EntityDataSerializers.BOOLEAN);

    // 碰撞参数（服务端）
    protected boolean activateOnGroundHit = false;
    protected double horizontalReduction = 0.25;  // 水平方向减速系数
    protected double verticalReduction = 0.25;    // 垂直方向减速系数
    protected double minVerticalSpeed = -0.1;     // 垂直速度阈值

    public BaseProjectileEntity(EntityType<? extends BaseProjectileEntity> type, Level level) {
        super(type, level);
    }

    public BaseProjectileEntity(EntityType<? extends BaseProjectileEntity> type, LivingEntity shooter, Level level) {
        super(type, shooter, level);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(STATE, 0);
        entityData.define(ACTIVATED, false);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide) {
            if(this.isActivated()) onActiveTick();
        }
    }

    // region 核心碰撞逻辑
    @Override
    protected void onHit(@NotNull HitResult r) {
        if (this.getState() == 2) return;
        super.onHit(r);
        if (!(r instanceof BlockHitResult result)) return;

        // 状态管理
        if (getState() == 0) setState(1);

        Vec3 delta = getDeltaMovement();

        // 地面碰撞处理
        if (result.getDirection() == Direction.UP) {
            if (activateOnGroundHit) {
                markActivated();
                handleSurfaceStick(result);
            }else{
                if(delta.y >= minVerticalSpeed){
                    markActivated();
                    handleSurfaceStick(result);
                }else{
                    handleVerticalCollision(delta);
                }
            }
            return;
        }

        // 通用碰撞处理
        handleGeneralCollision(result, delta);
        playCollisionSound(level().getBlockState(result.getBlockPos()));
    }

    private void handleGeneralCollision(BlockHitResult result, Vec3 delta) {
        Direction dir = result.getDirection();

        if (dir.getAxis().isHorizontal()) {
            handleHorizontalCollision(dir, delta);
        } else if (dir == Direction.DOWN || delta.y < minVerticalSpeed) {
            handleVerticalCollision(delta);
        } else {
            // 斜向碰撞混合处理
            handleHorizontalCollision(dir, delta);
            handleVerticalCollision(getDeltaMovement());
        }
    }

    private void handleSurfaceStick(BlockHitResult result) { // 添加BlockHitResult参数
        // 获取碰撞点并微调位置到碰撞面外侧
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
        // 只反转水平方向速度，保留垂直速度
        double reducedX = delta.x * horizontalReduction;
        double reducedZ = delta.z * horizontalReduction;

        if (direction.getAxis() == Direction.Axis.X) {
            this.setDeltaMovement(-reducedX, delta.y, reducedZ); // 保留Y速度
        } else {
            this.setDeltaMovement(reducedX, delta.y, -reducedZ); // 保留Y速度
        }
    }

    private void handleVerticalCollision(Vec3 delta) {
        // 反转Y方向速度，保留水平动量
        this.setDeltaMovement(
                delta.x * verticalReduction ,
                -(delta.y * verticalReduction),
                delta.z * verticalReduction
        );
    }


    private void playCollisionSound(BlockState blockState) {
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                blockState.getSoundType().getStepSound(), SoundSource.PLAYERS, 1, 1.6F);
    }
    // endregion

    // region 可扩展方法
    protected final void markActivated() {
        if (!level().isClientSide) {
            entityData.set(ACTIVATED, true);
            onActivated();
        }
    }

    protected void onActivated() {}
    protected void onActiveTick(){};

    // endregion

    // region 访问方法
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

    // endregion
}