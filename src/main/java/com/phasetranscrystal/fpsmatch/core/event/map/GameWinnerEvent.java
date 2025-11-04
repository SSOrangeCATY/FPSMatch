package com.phasetranscrystal.fpsmatch.core.event.map;

import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import com.phasetranscrystal.fpsmatch.core.team.BaseTeam;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.eventbus.api.Event;

public class GameWinnerEvent extends Event {
    BaseMap map;
    BaseTeam winner;
    BaseTeam loser;
    ServerLevel level;
    public GameWinnerEvent(BaseMap map, BaseTeam winner, BaseTeam loser, ServerLevel level) {
        this.map = map;
        this.winner = winner;
        this.loser = loser;
        this.level = level;
    }

    public BaseMap getMap() {
        return map;
    }

    public BaseTeam getLoser() {
        return loser;
    }

    public BaseTeam getWinner() {
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
