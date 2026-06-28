package com.tacz.guns.mixin.client;

import com.tacz.guns.client.event.CameraSetupEvent;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Camera.class, remap = false)
public class CameraHudFovMixin {
    @Inject(method = "calculateFov(F)F", at = @At("RETURN"), cancellable = true, remap = false)
    private void tacz$applyWorldScopeFov(float partialTicks, CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(CameraSetupEvent.computeWorldFov(cir.getReturnValue(), partialTicks));
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/client/renderer/state/level/CameraRenderState;F)V", at = @At("TAIL"), remap = false)
    private void tacz$applyGunModelHudFov(CameraRenderState cameraState, float cameraEntityPartialTicks, CallbackInfo ci) {
        cameraState.hudFov = CameraSetupEvent.computeGunModelHudFov(cameraState.hudFov, cameraEntityPartialTicks);
    }
}
