package com.phasetranscrystal.fpsmatch.common.capability;

import com.phasetranscrystal.fpsmatch.common.capability.map.DemolitionModeCapability;
import com.phasetranscrystal.fpsmatch.common.capability.map.GameEndTeleportCapability;
import com.phasetranscrystal.fpsmatch.common.capability.team.*;
import com.phasetranscrystal.fpsmatch.core.event.team.FPSMTeamCapabilityRegisterEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber
public class FPSMCapabilityRegister {

    @SubscribeEvent
    public static void register(FPSMTeamCapabilityRegisterEvent event) {
        // TEAM
        CompensationCapability.register();
        PauseCapability.register();
        SpawnPointCapability.register();
        TeamSwitchRestrictionCapability.register();
        StartKitsCapability.register();

        // MAP
        DemolitionModeCapability.register();
        GameEndTeleportCapability.register();
    }
}
