package com.phasetranscrystal.fpsmatch.mixin.ban3prs;

import com.phasetranscrystal.fpsmatch.config.FPSMConfig;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class Ban3rdPerson {

    @Inject(method = "handleKeybinds", at = @At("HEAD"))
    private void onHandleKeybinds(CallbackInfo ci) {
        if(Minecraft.getInstance().player == null) return;
        if(!FPSMConfig.Server.lock3PersonCamera.get()) return;

        while (Minecraft.getInstance().options.keyTogglePerspective.consumeClick()) {
        }
    }
}