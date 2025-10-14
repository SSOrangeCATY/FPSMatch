package com.phasetranscrystal.fpsmatch.core.event;

import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Event;

import java.util.Optional;

public class PlayerKillOnMapEvent extends Event {
    private final BaseMap map;
    private final ServerPlayer dead;
    private final ServerPlayer killer;
    public PlayerKillOnMapEvent(BaseMap map, ServerPlayer dead, ServerPlayer killer){
        this.map = map;
        this.dead = dead;
        this.killer = killer;
    }
    @Override
    public boolean isCancelable()
    {
        return false;
    }
    public BaseMap getBaseMap() {
        return map;
    }
    public ServerPlayer getDead(){
        return dead;
    }

    public ServerPlayer getKiller() {
        return killer;
    }
}
