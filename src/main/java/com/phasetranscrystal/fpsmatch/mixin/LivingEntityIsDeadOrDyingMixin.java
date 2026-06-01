package com.phasetranscrystal.fpsmatch.mixin;

import com.phasetranscrystal.fpsmatch.common.event.FPSMDeathPipelineEventHook;
import com.tacz.guns.entity.EntityKineticBullet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = EntityKineticBullet.class, remap = false)
public class LivingEntityIsDeadOrDyingMixin {

    @Redirect(
            method = "onHitEntity(Lcom/tacz/guns/util/TacHitResult;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;isDeadOrDying()Z"
            )
    )
    private boolean fpsmatch$proxyDeathCheck(LivingEntity livingCore) {
        if (livingCore.isDeadOrDying()) {
            return true;
        }
        if (livingCore instanceof ServerPlayer player && FPSMDeathPipelineEventHook.isRecentlyKilled(player.getUUID())) {
            return true;
        }
        return false;
    }
}
