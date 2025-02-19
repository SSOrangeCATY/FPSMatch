package com.phasetranscrystal.fpsmatch.client.music;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;

public class FPSClientMusicManager {
    static Minecraft mc = Minecraft.getInstance();
    private static SoundInstance currentMusic;
    public static void play(ResourceLocation musicResource){
        SoundManager soundManager = mc.getSoundManager();
        WeighedSoundEvents sound = soundManager.getSoundEvent(musicResource);
        if (sound != null) {
            play(SoundEvent.createVariableRangeEvent(sound.getSound(RandomSource.create()).getLocation()));
        }else{
            FPSMatch.LOGGER.error("failed to play music: " + musicResource);
        }
    }

    public static void play(SoundEvent musicResource){
        SoundManager soundManager = mc.getSoundManager();
        stop();
        if (musicResource != null && soundManager.getAvailableSounds().contains(musicResource.getLocation())) {
            SimpleSoundInstance instance = SimpleSoundInstance.forMusic(musicResource);
            soundManager.play(instance);
            currentMusic = instance;
        }else{
            FPSMatch.LOGGER.error(musicResource == null ? "failed to play music: music is null" : "failed to play music: couldn't find music in sound system -> " + musicResource.getLocation());
        }
    }

    public static void stop(){
        if(currentMusic != null){
            mc.getSoundManager().stop(currentMusic);
            currentMusic = null;
        }
    }

    public static SoundInstance getCurrentMusic() {
        return currentMusic;
    }
}
