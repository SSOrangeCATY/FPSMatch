package com.phasetranscrystal.fpsmatch.core.entity;

import com.phasetranscrystal.fpsmatch.common.client.FPSMClient;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import com.phasetranscrystal.fpsmatch.core.team.BaseTeam;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.UUID;

public record FPSMPlayer(@NotNull Player get) {

    public boolean isClientSide(){
        return get.level().isClientSide();
    }

    public UUID uuid(){
        return get.getUUID();
    }

    public int id(){
        return get.getId();
    }

    public Optional<BaseTeam> getTeam(){
        if(FPSMCore.initialized()){
            Optional<BaseMap> opt = FPSMCore.getInstance().getMapByPlayer(get);
            if(opt.isPresent()){
                return opt.get().getMapTeams().getTeamByPlayer(get);
            }
            return Optional.empty();
        }else{
            return Optional.ofNullable(FPSMClient.getGlobalData().getTeamByUUID(uuid()).orElse(null));
        }
    }
}
