package com.phasetranscrystal.fpsmatch.core.event;

import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import net.minecraftforge.eventbus.api.Event;

public class RegisterFPSMapEvent extends Event {
    private final FPSMCore fpsmCore;
    public RegisterFPSMapEvent(FPSMCore fpsmCore){
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
