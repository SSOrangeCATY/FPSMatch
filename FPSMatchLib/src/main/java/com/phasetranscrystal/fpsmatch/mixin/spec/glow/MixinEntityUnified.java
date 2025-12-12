package com.phasetranscrystal.fpsmatch.mixin.spec.glow;

import com.phasetranscrystal.fpsmatch.common.client.spec.SpectatorGlowManager;
import com.phasetranscrystal.fpsmatch.config.FPSMConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class MixinEntityUnified {

    @Inject(method = "isCurrentlyGlowing", at = @At("HEAD"), cancellable = true)
    private void onIsCurrentlyGlowing(CallbackInfoReturnable<Boolean> cir) {
        LocalPlayer localPlayer = Minecraft.getInstance().player;
        if (localPlayer == null) {
            return;
        }
        if(!FPSMConfig.Server.disableDefaultGlow.get()) return;

        Entity self = (Entity) (Object) this;

        if (self instanceof LivingEntity living) {
            if (SpectatorGlowManager.shouldGlow(living)) {
                cir.setReturnValue(true);
            }else{
                cir.setReturnValue(false);
            }
            cir.cancel();
        } else {
            if (localPlayer.isSpectator()) {
                cir.setReturnValue(true);
            } else {
                cir.setReturnValue(false);
            }
            cir.cancel();
        }
    }
}
