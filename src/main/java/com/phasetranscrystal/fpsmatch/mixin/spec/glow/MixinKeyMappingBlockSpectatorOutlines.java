package com.phasetranscrystal.fpsmatch.mixin.spec.glow;

import com.phasetranscrystal.fpsmatch.common.event.RequestSpectatorOutlinesEvent;
import com.phasetranscrystal.fpsmatch.config.FPSMConfig;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 禁用原版高亮玩家按键
 */
@Mixin(value = KeyMapping.class, remap = false)
public abstract class MixinKeyMappingBlockSpectatorOutlines {

    @Inject(method = "setDown(Z)V", at = @At("HEAD"), cancellable = true, remap = false)
    private void onSetDown(boolean isDown, CallbackInfo ci) {
        LocalPlayer player = Minecraft.getInstance().player;
        MultiPlayerGameMode gameMode = Minecraft.getInstance().gameMode;
        if (player == null || gameMode == null) return;
        KeyMapping self = (KeyMapping)(Object)this;
        if ("key.spectatorOutlines".equals(self.getName())) {
            if (isDown) {
                if(FPSMConfig.Server.disableSpecGlowKey.get() || NeoForge.EVENT_BUS.post(new RequestSpectatorOutlinesEvent(player, gameMode)).isCanceled()){
                    ci.cancel();
                }
            }
        }
    }
}
