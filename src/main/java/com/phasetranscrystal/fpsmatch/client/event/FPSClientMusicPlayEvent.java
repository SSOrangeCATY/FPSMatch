package com.phasetranscrystal.fpsmatch.client.event;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.Event;

public class FPSClientMusicPlayEvent extends Event {
    private ResourceLocation musicName;

    public FPSClientMusicPlayEvent(ResourceLocation musicName) {
        this.musicName = musicName;
    }

    public ResourceLocation getMusicName() {
        return musicName;
    }

    public void setMusicName(ResourceLocation resourceLocation){
        this.musicName = resourceLocation;
    }

    public boolean isCancelable()
    {
        return true;
    }
}
