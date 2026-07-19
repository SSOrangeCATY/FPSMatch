package com.phasetranscrystal.fpsmatch.mixin.spec.teammate;

import com.phasetranscrystal.fpsmatch.common.client.spec.SpectateMode;
import com.phasetranscrystal.fpsmatch.common.client.spec.SpectateState;
import com.phasetranscrystal.fpsmatch.common.client.spec.SpectatorCameraController;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraRestrictedSpectatorMixin {
    @Inject(method = "setup", at = @At("RETURN"))
    private void fpsmatch$applyRestrictedCamera(net.minecraft.world.level.BlockGetter level, net.minecraft.world.entity.Entity entity, boolean detached, boolean thirdPerson, float partialTick, CallbackInfo ci) {
        SpectateMode mode = SpectateState.get();
        if (mode != SpectateMode.C4_ORBIT && mode != SpectateMode.DEATH_SPOT) return;
        SpectatorCameraController.applyToCamera((Camera) (Object) this, partialTick);
        ((CameraInvokerMixin) (Object) this).invokeSetRotation(SpectatorCameraController.yaw(), SpectatorCameraController.pitch());
    }
}
