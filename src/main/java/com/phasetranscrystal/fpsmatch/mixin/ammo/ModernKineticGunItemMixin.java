package com.phasetranscrystal.fpsmatch.mixin.ammo;

import com.phasetranscrystal.fpsmatch.compat.IPassThroughEntity;
import com.phasetranscrystal.fpsmatch.compat.gun.GunTabTypeEnum;
import com.phasetranscrystal.fpsmatch.util.FPSMUtil;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.entity.shooter.ShooterDataHolder;
import com.tacz.guns.item.ModernKineticGunItem;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ModernKineticGunItem.class, remap = false)
public class ModernKineticGunItemMixin {
    @Inject(method = "doBulletSpread", at = @At("HEAD"))
    private void fpsmatch$captureScopedState(ShooterDataHolder data, ItemStack itemStack, LivingEntity shooter, Projectile projectile, int bulletIndex, float pitch, float yaw, float speed, float inaccuracy, CallbackInfo ci) {
        if (!(projectile instanceof IPassThroughEntity passThroughEntity)) return;
        if (!(itemStack.getItem() instanceof IGun gun)) return;

        boolean scoped = FPSMUtil.getGunTypeByGunId(gun.getGunId(itemStack))
                .filter(gunTabType -> gunTabType == GunTabTypeEnum.SNIPER)
                .map(gunTabType -> IGunOperator.fromLivingEntity(shooter).getSynAimingProgress() > 0.5f)
                .orElse(false);
        passThroughEntity.fpsmatch$setScoped(scoped);
    }
}
