package com.phasetranscrystal.fpsmatch.mixin.spec.teammate;

import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = Camera.class, remap = false)
public interface CameraInvokerMixin {

    @Invoker(value = "setPosition", remap = false)
    void invokeSetPosition(double x, double y, double z);
}
