package com.phasetranscrystal.fpsmatch.mixin.collisiobox;

import com.phasetranscrystal.fpsmatch.config.FPSMConfig;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.EntityHitboxDebugRenderer;
import net.minecraft.util.debug.DebugValueAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = EntityHitboxDebugRenderer.class, remap = false)
public class MixinEntityHitboxDebugRenderer {
    @Inject(method = "emitGizmos", at = @At("HEAD"), cancellable = true, remap = false)
    private void disableHitboxRender(
            double camX,
            double camY,
            double camZ,
            DebugValueAccess debugValues,
            Frustum frustum,
            float partialTicks,
            CallbackInfo ci
    ) {
        if (FPSMConfig.Server.disableRenderHitBox.get()) {
            ci.cancel();
        }
    }
}
