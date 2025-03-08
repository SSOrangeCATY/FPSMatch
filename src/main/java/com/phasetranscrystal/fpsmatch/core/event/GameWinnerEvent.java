package com.phasetranscrystal.fpsmatch.core.event;

import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventListenerHelper;

import java.util.List;
import java.util.UUID;

public class GameWinnerEvent extends Event {
    BaseMap map;
    List<UUID> winner;
    List<UUID> loser;
    ServerLevel level;
    public GameWinnerEvent(BaseMap map, List<UUID> winner, List<UUID> loser, ServerLevel level) {
        this.map = map;
        this.winner = winner;
        this.loser = loser;
        this.level = level;
    }

    public BaseMap getMap() {
        return map;
    }

    public List<UUID> getLoser() {
        return loser;
    }

    public List<UUID> getWinner() {
        return winner;
    }

    public ServerLevel getLevel() {
        return level;
    }

    public boolean isCancelable()
    {
        return false;
    }

}
