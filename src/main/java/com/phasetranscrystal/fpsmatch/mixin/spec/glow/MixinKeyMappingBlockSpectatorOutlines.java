package com.phasetranscrystal.fpsmatch.mixin.spec.glow;

import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 禁用原版高亮玩家按键
 */
@Mixin(KeyMapping.class)
public abstract class MixinKeyMappingBlockSpectatorOutlines {

    /**
     * 当游戏尝试对某个 KeyMapping 调用 setDown(true),
     * 若它的名称为 "key.spectatorOutlines", 就拦截并取消，
     * 使其实际 setDown(false) => 一直不被视为“按下”。
     */
    @Inject(method = "setDown(Z)V", at = @At("HEAD"), cancellable = true)
    private void onSetDown(boolean isDown, CallbackInfo ci) {
        KeyMapping self = (KeyMapping)(Object)this;
        if ("key.spectatorOutlines".equals(self.getName())) {
            if (isDown) {
                ci.cancel();
            }
        }
    }
}
