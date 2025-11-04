package com.phasetranscrystal.fpsmatch.mixin.collisiobox;

import com.tacz.guns.client.event.RenderHeadShotAABB;
import net.minecraftforge.client.event.RenderLivingEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RenderHeadShotAABB.class, remap = false)
public abstract class MixinRenderHeadShotAABB {

    private static final boolean DISABLE_HEADSHOT_AABB_RENDER = true;

    @Inject(
            method = "onRenderEntity(Lnet/minecraftforge/client/event/RenderLivingEvent$Post;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void shmccsk$blockHeadAABB(RenderLivingEvent.Post<?, ?> event, CallbackInfo ci) {
        if (DISABLE_HEADSHOT_AABB_RENDER) {
            ci.cancel();
        }
    }
}