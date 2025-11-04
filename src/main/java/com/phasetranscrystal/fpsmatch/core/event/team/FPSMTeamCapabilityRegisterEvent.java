package com.phasetranscrystal.fpsmatch.core.event.team;

import net.minecraftforge.eventbus.api.Event;

public class FPSMTeamCapabilityRegisterEvent extends Event {
    public FPSMTeamCapabilityRegisterEvent(){
    }
    @Override
    public boolean isCancelable()
    {
        return false;
    }
}
