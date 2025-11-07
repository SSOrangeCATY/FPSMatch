package com.phasetranscrystal.fpsmatch.mixin.spec.glow;

import com.phasetranscrystal.fpsmatch.config.FPSMConfig;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.InputEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 禁用原版高亮玩家按键
 */
@Mixin(KeyMapping.class)
public abstract class MixinKeyMappingBlockSpectatorOutlines {

    @Inject(method = "setDown(Z)V", at = @At("HEAD"), cancellable = true)
    private void onSetDown(boolean isDown, CallbackInfo ci) {
        if (Minecraft.getInstance().player == null || Minecraft.getInstance().gameMode == null) return;
        KeyMapping self = (KeyMapping)(Object)this;
        if ("key.spectatorOutlines".equals(self.getName()) && FPSMConfig.Server.disableSpecGlowKey.get()) {
            if (isDown) {
                ci.cancel();
            }
        }
    }
}
