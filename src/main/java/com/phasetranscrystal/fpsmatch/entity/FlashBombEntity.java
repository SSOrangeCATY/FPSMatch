package com.phasetranscrystal.fpsmatch.entity;

import com.phasetranscrystal.fpsmatch.core.entity.BaseProjectileLifeTimeEntity;
import com.phasetranscrystal.fpsmatch.effect.FPSMEffectRegister;
import com.phasetranscrystal.fpsmatch.effect.FlashBlindnessMobEffect;
import com.phasetranscrystal.fpsmatch.item.FPSMItemRegister;
import com.phasetranscrystal.fpsmatch.item.FPSMSoundRegister;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;


public class FlashBombEntity extends BaseProjectileLifeTimeEntity {
    // 配置参数
    private static final int EFFECT_RADIUS = 40;

    public FlashBombEntity(EntityType<? extends FlashBombEntity> type, Level level) {
        super(type, level);
    }

    public FlashBombEntity(LivingEntity shooter, Level level) {
        super(EntityRegister.FLASH_BOMB.get(), shooter, level);
        setTimeLeft(1);
        setTimeoutTicks(20);
    }

    @Override
    protected void onActivated() {
        applyFlashEffect();
    }
    @Override
    protected void onTimeOut(){
        applyFlashEffect();
    }


    private void applyFlashEffect() {
        AABB area = new AABB(
                getX() - EFFECT_RADIUS, getY() - EFFECT_RADIUS, getZ() - EFFECT_RADIUS,
                getX() + EFFECT_RADIUS, getY() + EFFECT_RADIUS, getZ() + EFFECT_RADIUS
        );

        for (Entity entity : level().getEntitiesOfClass(Entity.class, area)) {
            if (entity instanceof LivingEntity living) {
                if(entity instanceof ServerPlayer player && !player.gameMode.isSurvival()){
                    continue;
                }
                applyBlindnessEffect(living);
            }
        }
    }

    private void applyBlindnessEffect(LivingEntity target) {
        Vec3 eyePos = new Vec3(target.getX(), target.getEyeY(), target.getZ());
        if (isLineOfSightBlocked(eyePos)) return;

        double angle = calculateViewAngle(target);
        int[] duration = calculateBlindnessDuration(angle);

        MobEffectInstance effect = new MobEffectInstance(
                FPSMEffectRegister.FLASH_BLINDNESS.get(),
                duration[1], 1
        );

        if (effect.getEffect() instanceof FlashBlindnessMobEffect flashEffect) {
            flashEffect.setFullBlindnessTime(duration[0]);
            flashEffect.setTotalAndTicker(duration[1] - duration[0]);
        }

        target.addEffect(effect);


        if(target instanceof ServerPlayer player){
            player.connection.send(new ClientboundSoundPacket(Holder.direct(FPSMSoundRegister.flash.get()), SoundSource.PLAYERS, player.getX(), player.getY(), player.getZ(), 0.8f, 1, 0));
        }
    }

    private boolean isLineOfSightBlocked(Vec3 eyePos) {
        return level().clip(new ClipContext(
                eyePos, position(),
                ClipContext.Block.VISUAL,
                ClipContext.Fluid.NONE,
                null
        )).getType() == HitResult.Type.BLOCK;
    }

    private double calculateViewAngle(LivingEntity target) {
        Vec3 lookVec = target.getLookAngle();
        Vec3 toBomb = position().subtract(target.getEyePosition()).normalize();
        return Math.toDegrees(Math.acos(lookVec.dot(toBomb)));
    }

    private int[] calculateBlindnessDuration(double angle) {
        if (angle < 53) return new int[]{38, 98};
        if (angle < 72) return new int[]{9, 68};
        if (angle < 101) return new int[]{2, 39};
        return new int[]{2, 19};
    }



    @Override
    protected @NotNull Item getDefaultItem() {
        return FPSMItemRegister.FLASH_BOMB.get();
    }

}
