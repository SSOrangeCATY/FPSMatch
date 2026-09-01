package com.ptcrys.fpsmatch.common.capability;

import com.ptcrys.fpsmatch.common.capability.map.DemolitionModeCapability;
import com.ptcrys.fpsmatch.common.capability.map.GameEndTeleportCapability;
import com.ptcrys.fpsmatch.common.capability.team.*;

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
    }
}
