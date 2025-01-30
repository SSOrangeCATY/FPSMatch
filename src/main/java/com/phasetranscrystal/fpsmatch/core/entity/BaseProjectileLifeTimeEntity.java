package com.phasetranscrystal.fpsmatch.core.entity;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
public abstract class BaseProjectileLifeTimeEntity extends BaseProjectileEntity {
    private static final EntityDataAccessor<Integer> TIMEOUT_TICKS = SynchedEntityData.defineId(BaseProjectileLifeTimeEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> TIME_LEFT = SynchedEntityData.defineId(BaseProjectileLifeTimeEntity.class, EntityDataSerializers.INT);

    public BaseProjectileLifeTimeEntity(EntityType<? extends BaseProjectileLifeTimeEntity> type, Level level) {
        super(type, level);
    }

    public BaseProjectileLifeTimeEntity(EntityType<? extends BaseProjectileLifeTimeEntity> type, LivingEntity shooter, Level level) {
        super(type, shooter, level);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(TIMEOUT_TICKS, -1);
        entityData.define(TIME_LEFT, -1);
    }

    @Override
    public void tick() {
        super.tick();

        if (!level().isClientSide) {
            handleTimeoutLogic();
            handleActiveTimeLogic();
        }
    }

    protected final void handleTimeoutLogic() {
        if (!isActivated() && getTimeoutTicks() > 0) {
            setTimeoutTicks(getTimeoutTicks() - 1);
            if (getTimeoutTicks() <= 0) {
                onTimeOut();
                discard();
            }
        }
    }

    protected final void handleActiveTimeLogic() {
        if (isActivated() && getTimeLeft() > 0) {
            setTimeLeft(getTimeLeft() - 1);
            if (getTimeLeft() <= 0) {
                onActiveTimeExpired();
            }
        }
    }


    // 需要子类实现的方法
    protected void onActiveTimeExpired(){
        discard();
    };

    protected void onTimeOut(){
    };

    // 访问方法
    public int getTimeoutTicks() {
        return entityData.get(TIMEOUT_TICKS);
    }

    public void setTimeoutTicks(int ticks) {
        entityData.set(TIMEOUT_TICKS, ticks);
    }

    public int getTimeLeft() {
        return entityData.get(TIME_LEFT);
    }

    public void setTimeLeft(int ticks) {
        entityData.set(TIME_LEFT, ticks);
    }
}