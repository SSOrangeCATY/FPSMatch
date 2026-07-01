package com.phasetranscrystal.fpsmatch.mixin.sound;

import com.phasetranscrystal.fpsmatch.common.effect.FPSMEffectRegister;
import com.phasetranscrystal.fpsmatch.common.sound.FPSMSoundRegister;
import com.phasetranscrystal.fpsmatch.mixin.accessor.SoundEngineAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Map;

@Mixin(value = SoundEngine.class, remap = false)
public class SoundEngineMixin {
    
    @Inject(
        method = "play",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private void onPlaySound(SoundInstance sound, CallbackInfoReturnable<SoundEngine.PlayResult> cir) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        
        if (player != null && player.hasEffect(FPSMEffectRegister.FLASH_BLINDNESS)) {
            if (!fPSMatch$isAllowedSound(sound)) {
                cir.setReturnValue(SoundEngine.PlayResult.NOT_STARTED);
            }
        }
    }
    
    @Inject(
        method = "stop(Lnet/minecraft/client/resources/sounds/SoundInstance;)V",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private void onStopSound(SoundInstance sound, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        
        if (player != null && player.hasEffect(FPSMEffectRegister.FLASH_BLINDNESS)) {
            if (fPSMatch$isAllowedSound(sound)) {
                ci.cancel();
            }
        }
    }
    
    @Inject(
        method = "isActive",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private void onIsActive(SoundInstance sound, CallbackInfoReturnable<Boolean> cir) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        
        if (player != null && player.hasEffect(FPSMEffectRegister.FLASH_BLINDNESS)) {
            if (!fPSMatch$isAllowedSound(sound)) {
                cir.setReturnValue(false);
            }
        }
    }
    
    @Inject(
        method = "stopAll",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private void onStopAll(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        
        if (player != null && player.hasEffect(FPSMEffectRegister.FLASH_BLINDNESS)) {
            fPSMatch$stopNonAllowedSounds();
            ci.cancel();
        }
    }
    

    @Unique
    private boolean fPSMatch$isAllowedSound(SoundInstance sound) {
        return sound.getIdentifier().equals(FPSMSoundRegister.FLASH.get().location());
    }
    

    @Unique
    private void fPSMatch$stopNonAllowedSounds() {
        SoundEngine self = (SoundEngine) (Object) this;
        fPSMatch$stopNonAllowedSounds(self);
    }
    
    @Unique
    private void fPSMatch$stopNonAllowedSounds(SoundEngine engine) {
        Map<SoundInstance, ChannelAccess.ChannelHandle> instanceToChannel = ((SoundEngineAccessor) engine).getInstanceToChannel();

        List<SoundInstance> toStop = instanceToChannel.keySet().stream()
                .filter(sound -> !fPSMatch$isAllowedSound(sound))
                .toList();

        for (SoundInstance sound : toStop) {
            engine.stop(sound);
        }
    }
}
