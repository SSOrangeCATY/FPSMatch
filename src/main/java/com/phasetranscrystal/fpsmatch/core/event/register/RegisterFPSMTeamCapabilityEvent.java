package com.phasetranscrystal.fpsmatch.core.event.register;

import net.minecraftforge.eventbus.api.Event;

public class RegisterFPSMTeamCapabilityEvent extends Event {
    public RegisterFPSMTeamCapabilityEvent(){
    }
    @Override
    public boolean isCancelable()
    {
        return false;
    }
}
