package com.phasetranscrystal.fpsmatch.client.music;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.client.event.FPSClientMusicPlayEvent;
import com.phasetranscrystal.fpsmatch.client.event.FPSClientMusicStopEvent;
import com.phasetranscrystal.fpsmatch.core.event.CSGameRoundEndEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.common.MinecraftForge;

/**
 * 客户端音乐管理器，用于播放和停止音乐。
 * <p>
 * 该类通过 Minecraft 的音频系统播放音乐，并支持事件广播，允许其他模块在播放和停止音乐时进行干预。
 * 提供了播放音乐和停止当前音乐的功能。
 */
public class FPSClientMusicManager {
    /**
     * 获取当前 Minecraft 客户端实例。
     */
    static Minecraft mc = Minecraft.getInstance();

    /**
     * 当前播放的音乐实例。
     */
    private static SoundInstance currentMusic;

    /**
     * 播放指定的音乐资源。
     * <p>
     * 该方法会广播 {@link FPSClientMusicPlayEvent}，允许其他模块干预音乐播放。
     * 如果事件被取消，则不会播放音乐。
     *
     * @param musicResource 音乐资源的 ResourceLocation
     */
    public static void play(ResourceLocation musicResource) {
        SoundManager soundManager = mc.getSoundManager();
        var event = new FPSClientMusicPlayEvent(musicResource);
        MinecraftForge.EVENT_BUS.post(event);
        if (event.isCanceled()) return;

        musicResource = event.getMusicName();
        if (musicResource != null) {
            mc.getMusicManager().stopPlaying();
            stop();
            SimpleSoundInstance instance = new SimpleSoundInstance(musicResource, SoundSource.VOICE, 1.0F, 1.0F, SoundInstance.createUnseededRandom(), false, 0, SoundInstance.Attenuation.LINEAR, 0.0D, 0.0D, 0.0D, true);
            soundManager.play(instance);
            currentMusic = instance;
        } else {
            FPSMatch.LOGGER.error("failed to play music: music is null");
        }
    }

    /**
     * 播放指定的音乐事件。
     * <p>
     * 该方法会调用 {@link #play(ResourceLocation)}，并传入音乐事件的资源路径。
     *
     * @param musicResource 音乐事件
     */
    public static void play(SoundEvent musicResource) {
        if (musicResource != null) {
            play(musicResource.getLocation());
        } else {
            FPSMatch.LOGGER.error("failed to play music: music is null");
        }
    }

    /**
     * 停止当前播放的音乐。
     * <p>
     * 该方法会广播 {@link FPSClientMusicStopEvent}，允许其他模块干预音乐停止。
     * 如果事件被取消，则不会停止音乐。
     */
    public static void stop() {
        var event = new FPSClientMusicStopEvent();
        MinecraftForge.EVENT_BUS.post(event);
        if (event.isCanceled()) return;

        if (currentMusic != null) {
            mc.getSoundManager().stop(currentMusic);
            currentMusic = null;
        }
    }

    /**
     * 获取当前播放的音乐实例。
     *
     * @return 当前播放的音乐实例，如果没有音乐正在播放，则返回 null
     */
    public static SoundInstance getCurrentMusic() {
        return currentMusic;
    }
}