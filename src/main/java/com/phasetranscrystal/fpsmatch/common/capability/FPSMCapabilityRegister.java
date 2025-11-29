package com.phasetranscrystal.fpsmatch.common.capability;

import com.phasetranscrystal.fpsmatch.common.capability.map.DemolitionModeCapability;
import com.phasetranscrystal.fpsmatch.common.capability.map.GameEndTeleportCapability;
import com.phasetranscrystal.fpsmatch.common.capability.team.*;
import com.phasetranscrystal.fpsmatch.core.event.register.RegisterFPSMCapabilityEvent;
import net.minecraftforge.common.MinecraftForge;

public class FPSMCapabilityRegister {

    public static void register() {
        // TEAM
        CompensationCapability.register();
        PauseCapability.register();
        SpawnPointCapability.register();
        TeamSwitchRestrictionCapability.register();
        StartKitsCapability.register();
        ShopCapability.register();
        // MAP
        DemolitionModeCapability.register();
        GameEndTeleportCapability.register();

        MinecraftForge.EVENT_BUS.post(new RegisterFPSMCapabilityEvent());
    }
}
