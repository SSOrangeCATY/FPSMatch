package com.phasetranscrystal.fpsmatch.core.event;

import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventListenerHelper;

public class RegisterFPSMapTypeEvent extends Event {
    private final FPSMCore fpsmCore;
    public RegisterFPSMapTypeEvent(FPSMCore fpsmCore){
        this.fpsmCore = fpsmCore;
    }
    @Override
    public boolean isCancelable()
    {
        return false;
    }
    public FPSMCore getFpsmCore() {
        return fpsmCore;
    }
}
