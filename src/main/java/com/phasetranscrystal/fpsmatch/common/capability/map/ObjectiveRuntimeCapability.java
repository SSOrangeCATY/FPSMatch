package com.phasetranscrystal.fpsmatch.common.capability.map;

import com.phasetranscrystal.fpsmatch.core.capability.FPSMCapability;
import com.phasetranscrystal.fpsmatch.core.capability.FPSMCapabilityManager;
import com.phasetranscrystal.fpsmatch.core.capability.map.MapCapability;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import com.phasetranscrystal.fpsmatch.core.objective.ObjectiveRuntime;

public class ObjectiveRuntimeCapability extends MapCapability {
    private ObjectiveRuntime runtime = new ObjectiveRuntime();

    public ObjectiveRuntimeCapability(BaseMap map) {
        super(map);
    }

    public static void register() {
        FPSMCapabilityManager.register(FPSMCapabilityManager.CapabilityType.MAP, ObjectiveRuntimeCapability.class, new Factory<>() {
            @Override
            public ObjectiveRuntimeCapability create(BaseMap map) {
                return new ObjectiveRuntimeCapability(map);
            }
        });
    }

    public ObjectiveRuntime runtime() {
        return runtime;
    }

    @Override
    public void tick() {
        runtime.tick();
    }

    @Override
    public void reset() {
        runtime = new ObjectiveRuntime();
    }
}
