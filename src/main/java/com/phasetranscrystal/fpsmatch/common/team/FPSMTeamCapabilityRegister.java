package com.phasetranscrystal.fpsmatch.common.team;

import com.phasetranscrystal.fpsmatch.common.team.capabilities.CompensationCapability;
import com.phasetranscrystal.fpsmatch.common.team.capabilities.PauseDataCapability;
import com.phasetranscrystal.fpsmatch.common.team.capabilities.SpawnPointCapability;
import com.phasetranscrystal.fpsmatch.common.team.capabilities.TeamSwitchRestrictionCapability;
import com.phasetranscrystal.fpsmatch.core.event.team.FPSMTeamCapabilityRegisterEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber
public class FPSMTeamCapabilityRegister {

    @SubscribeEvent
    public static void register(FPSMTeamCapabilityRegisterEvent event) {
        CompensationCapability.register();
        PauseDataCapability.register();
        SpawnPointCapability.register();
        TeamSwitchRestrictionCapability.register();
    }
}
