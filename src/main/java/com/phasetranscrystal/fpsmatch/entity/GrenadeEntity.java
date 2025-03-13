package com.phasetranscrystal.fpsmatch.entity;

import com.phasetranscrystal.fpsmatch.core.entity.BaseProjectileLifeTimeEntity;
import com.phasetranscrystal.fpsmatch.item.FPSMItemRegister;
import com.phasetranscrystal.fpsmatch.item.FPSMSoundRegister;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.NotNull;

public class GrenadeEntity extends BaseProjectileLifeTimeEntity {
    // 配置参数
    private static final float EXPLOSION_RADIUS = 5.0f;
    private static final int FUSE_TIME = 20; // 4秒（20 ticks/秒）
    private static final float BASE_DAMAGE = 19.0f;

    public GrenadeEntity(EntityType<? extends GrenadeEntity> type, Level level) {
        super(type, level);
    }

    public GrenadeEntity(LivingEntity shooter, Level level) {
        super(EntityRegister.GRENADE.get(), shooter, level);
        this.setTimeLeft(1);
        this.setTimeoutTicks(FUSE_TIME);
        this.setVerticalReduction(0.1F);
    }

    @Override
    protected void onTimeOut(){
        explode();
    };

    @Override
    protected void onActivated() {
        explode();
    }

    private void explode() {
        if (level().isClientSide) return;

        // 爆炸效果
        spawnExplosionParticles();
        applyExplosionDamage();
        playExplosionSound();
        applyStopSmokeShell();

        // 销毁实体
        discard();
    }

    private void applyStopSmokeShell(){
        AABB smokeCheckArea = getBoundingBox().inflate(EXPLOSION_RADIUS);
        level().getEntitiesOfClass(SmokeShellEntity.class, smokeCheckArea)
                .stream()
                .filter(smoke -> smoke.getState() == 2)
                .findFirst()
                .ifPresent(smoke -> smoke.setParticleCoolDown(30));
    }

    private void spawnExplosionParticles() {
        ServerLevel serverLevel = (ServerLevel) level();

        // 爆炸核心粒子
        serverLevel.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                getX(), getY(), getZ(), 20,
                0, 0, 0, 0.5);

        // 冲击波粒子
        serverLevel.sendParticles(ParticleTypes.POOF,
                getX(), getY(), getZ(), 100,
                1.5, 1.0, 1.5, 0.2);
    }

    private void applyExplosionDamage() {
        AABB explosionArea = new AABB(
                getX() - EXPLOSION_RADIUS, getY() - EXPLOSION_RADIUS, getZ() - EXPLOSION_RADIUS,
                getX() + EXPLOSION_RADIUS, getY() + EXPLOSION_RADIUS, getZ() + EXPLOSION_RADIUS
        );

        for (LivingEntity entity : level().getEntitiesOfClass(LivingEntity.class, explosionArea)) {
            if(entity instanceof ServerPlayer player && !player.gameMode.isSurvival()){
                continue;
            }
            // 计算距离
            double distance = distanceTo(entity);
            if (distance > EXPLOSION_RADIUS) continue;

            // 计算伤害衰减
            float damage = BASE_DAMAGE * (1 - (float)(distance / EXPLOSION_RADIUS));

            // 视线检测
            if (!hasClearLineOfSight(entity)) {
                damage *= 0.1f;
            }

            // 应用伤害
            entity.hurt(this.level().damageSources().fellOutOfWorld(), damage);
        }
    }

    private boolean hasClearLineOfSight(Entity target) {
        return level().clip(new ClipContext(
                position(),
                target.position(),
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                this
        )).getType() == HitResult.Type.MISS;
    }

    private void playExplosionSound() {
        level().playSound(null, getX(), getY(), getZ(),
                FPSMSoundRegister.boom.get(), SoundSource.HOSTILE,
                4.0F, (1.0F + (random.nextFloat() - random.nextFloat()) * 0.2F) * 0.7F);
    }

    @Override
    protected @NotNull Item getDefaultItem() {
        return FPSMItemRegister.GRENADE.get(); // 你的物品注册类
    }

    @Override
    public void onActiveTick() {
        // 自定义激活粒子
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.FLASH,
                    getX(), getY(), getZ(), 2,
                    0, 0, 0, 0.1);
        }
    }
}
