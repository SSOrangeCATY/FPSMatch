package com.phasetranscrystal.fpsmatch.mixin;

import com.phasetranscrystal.fpsmatch.core.entity.BaseProjectileEntity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LivingEntity.class, remap = false)
public abstract class PlayerKnockbackMixin extends Entity {

    public PlayerKnockbackMixin(EntityType<?> pEntityType, Level pLevel) {
        super(pEntityType, pLevel);
    }

    @Inject(
            method = "knockback(DDDLnet/minecraft/world/damagesource/DamageSource;FZ)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    private void injectKnockback(double pStrength, double pX, double pZ, DamageSource source, float damage, boolean comesFromEffect, CallbackInfo ci) {
        DamageSource ds = ((LivingEntity) (Object) this).getLastDamageSource();
        if (ds != null && ds.getDirectEntity() instanceof BaseProjectileEntity) {
            ci.cancel();
        }
    }
}
