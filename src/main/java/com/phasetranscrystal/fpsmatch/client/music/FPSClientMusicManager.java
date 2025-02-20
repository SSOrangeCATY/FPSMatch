package com.phasetranscrystal.fpsmatch.client.music;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

public class FPSClientMusicManager {
    static Minecraft mc = Minecraft.getInstance();
    private static SoundInstance currentMusic;
    public static void play(ResourceLocation musicResource){
        SoundManager soundManager = mc.getSoundManager();
        stop();
        if (musicResource != null) {
            SimpleSoundInstance instance = new SimpleSoundInstance(musicResource, SoundSource.VOICE, 1.0F, 1.0F, SoundInstance.createUnseededRandom(), false, 0, SoundInstance.Attenuation.LINEAR, 0.0D, 0.0D, 0.0D, true);
            soundManager.play(instance);
            currentMusic = instance;
        }else{
            FPSMatch.LOGGER.error("failed to play music: music is null");
        }
    }

    public static void play(SoundEvent musicResource){
        if (musicResource != null) {
            play(musicResource.getLocation());
        }else{
            FPSMatch.LOGGER.error("failed to play music: music is null");
        }
    }

    public static void stop(){
        mc.getMusicManager().stopPlaying();
        if(currentMusic != null){
            mc.getSoundManager().stop(currentMusic);
            currentMusic = null;
        }
    }

    public static SoundInstance getCurrentMusic() {
        return currentMusic;
    }
}
