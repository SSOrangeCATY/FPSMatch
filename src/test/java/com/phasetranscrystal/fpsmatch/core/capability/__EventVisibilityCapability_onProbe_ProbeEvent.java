package com.phasetranscrystal.fpsmatch.core.capability;

import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.IEventListener;

public final class __EventVisibilityCapability_onProbe_ProbeEvent
        implements IEventListener {
    private final CapabilityMapLifecycleTest.EventVisibilityCapability target;

    public __EventVisibilityCapability_onProbe_ProbeEvent(Object target) {
        this.target = (CapabilityMapLifecycleTest.EventVisibilityCapability) target;
    }

    @Override
    public void invoke(Event event) {
        target.onProbe((CapabilityMapLifecycleTest.ProbeEvent) event);
    }
}
