package com.phasetranscrystal.fpsmatch.core.event.register;

import net.minecraftforge.eventbus.api.Event;

public class RegisterFPSMCapabilityEvent extends Event {
    public RegisterFPSMCapabilityEvent(){
    }
    @Override
    public boolean isCancelable()
    {
        return false;
    }
}
