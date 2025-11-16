package com.phasetranscrystal.fpsmatch.common.capability;

import com.phasetranscrystal.fpsmatch.common.capability.team.CompensationCapability;
import com.phasetranscrystal.fpsmatch.common.capability.team.PauseDataCapability;
import com.phasetranscrystal.fpsmatch.common.capability.team.SpawnPointCapability;
import com.phasetranscrystal.fpsmatch.common.capability.team.TeamSwitchRestrictionCapability;
import com.phasetranscrystal.fpsmatch.core.event.team.FPSMTeamCapabilityRegisterEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber
public class FPSMCapabilityRegister {

    @SubscribeEvent
    public static void register(FPSMTeamCapabilityRegisterEvent event) {
        CompensationCapability.register();
        PauseDataCapability.register();
        SpawnPointCapability.register();
        TeamSwitchRestrictionCapability.register();
    }
}
