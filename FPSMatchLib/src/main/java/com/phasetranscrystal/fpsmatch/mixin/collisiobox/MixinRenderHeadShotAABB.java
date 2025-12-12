package com.phasetranscrystal.fpsmatch.mixin.collisiobox;

import com.phasetranscrystal.fpsmatch.config.FPSMConfig;
import com.tacz.guns.client.event.RenderHeadShotAABB;
import net.minecraftforge.client.event.RenderLivingEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RenderHeadShotAABB.class, remap = false)
public abstract class MixinRenderHeadShotAABB {

    @Inject(
            method = "onRenderEntity(Lnet/minecraftforge/client/event/RenderLivingEvent$Post;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void fpsmatch$blockHeadAABB(RenderLivingEvent.Post<?, ?> event, CallbackInfo ci) {
        if (FPSMConfig.Server.disableRenderHitBox.get() && FPSMConfig.Server.disableRenderHeadShotHitBox.get()) {
            ci.cancel();
        }
    }
}