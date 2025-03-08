package com.phasetranscrystal.fpsmatch.client.event;

import net.minecraftforge.eventbus.api.Event;

public class FPSClientMusicStopEvent extends Event {

    public FPSClientMusicStopEvent(){
    }

    public boolean isCancelable()
    {
        return true;
    }
}
