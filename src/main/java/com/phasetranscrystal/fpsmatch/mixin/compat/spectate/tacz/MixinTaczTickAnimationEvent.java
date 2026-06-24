package com.phasetranscrystal.fpsmatch.mixin.compat.spectate.tacz;

import com.tacz.guns.client.event.TickAnimationEvent;
import com.phasetranscrystal.fpsmatch.compat.spectate.SpectatorView;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Disables TACZ movement tick animation while spectating to avoid local input overrides.
 */
@Mixin(value = TickAnimationEvent.class, remap = false)
public abstract class MixinTaczTickAnimationEvent {
    @Inject(method = "tickAnimation(Lnet/neoforged/neoforge/client/event/ClientTickEvent$Post;)V", at = @At("HEAD"), cancellable = true)
    private static void fpsmatch$skipWhenSpectating(ClientTickEvent.Post event, CallbackInfo ci) {
        if (SpectatorView.isSpectatingOther(Minecraft.getInstance().player)) {
            ci.cancel();
        }
    }
}
