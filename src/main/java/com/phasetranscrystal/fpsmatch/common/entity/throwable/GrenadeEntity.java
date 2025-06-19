package com.phasetranscrystal.fpsmatch.common.entity.throwable;

import com.phasetranscrystal.fpsmatch.FPSMConfig;
import com.phasetranscrystal.fpsmatch.core.entity.BaseProjectileLifeTimeEntity;
import com.phasetranscrystal.fpsmatch.common.entity.EntityRegister;
import com.phasetranscrystal.fpsmatch.common.item.FPSMItemRegister;
import com.phasetranscrystal.fpsmatch.common.client.sound.FPSMSoundRegister;
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
    private final int explosionRadius = FPSMConfig.common.grenadeRadius.get();
    private final int damage = FPSMConfig.common.grenadeDamage.get();

    public GrenadeEntity(EntityType<? extends GrenadeEntity> type, Level level) {
        super(type, level);
    }

    public GrenadeEntity(LivingEntity shooter, Level level) {
        super(EntityRegister.GRENADE.get(), shooter, level);
        this.setTimeLeft(1);
        this.setTimeoutTicks(FPSMConfig.common.grenadeFuseTime.get());
        this.setVerticalReduction(0.1F);
    }

    @Override
    protected void onTimeOut(){
        explode();
    }

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
        AABB smokeCheckArea = getBoundingBox().inflate(explosionRadius);

        SmokeShellEntity[] a = level().getEntitiesOfClass(SmokeShellEntity.class, smokeCheckArea)
                .stream()
                .filter(smoke -> smoke.getState() == 2)
                .toArray(SmokeShellEntity[]::new);

        for (SmokeShellEntity smokeShellEntity : a) {
            smokeShellEntity.setParticleCoolDown(30);
        }
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
        AABB explosionArea = getBoundingBox().inflate(explosionRadius);

        for (LivingEntity entity : level().getEntitiesOfClass(LivingEntity.class, explosionArea)) {
            if(entity instanceof ServerPlayer player && !player.gameMode.isSurvival()){
                continue;
            }
            // 计算距离
            double distance = distanceTo(entity);
            if (distance > explosionRadius) continue;

            // 计算伤害衰减
            float damage = this.damage * (1 - (float)(distance / explosionRadius));

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
