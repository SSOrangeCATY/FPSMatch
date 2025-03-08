package com.phasetranscrystal.fpsmatch.core.event;

import com.phasetranscrystal.fpsmatch.core.BaseTeam;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import com.phasetranscrystal.fpsmatch.cs.CSGameMap;
import net.minecraftforge.eventbus.api.Event;

public class CSGameRoundEndEvent extends Event {
    private final BaseMap map;
    private final BaseTeam winner;
    private final CSGameMap.WinnerReason reason;
    private int winnerMoney;

    public CSGameRoundEndEvent(BaseMap map ,BaseTeam winner, CSGameMap.WinnerReason reason) {
        this.map = map;
        this.winner = winner;
        this.reason = reason;
        this.winnerMoney = reason.winMoney;
    }

    public BaseTeam getWinner() {
        return winner;
    }

    public CSGameMap.WinnerReason getReason() {
        return reason;
    }

    public int getWinnerMoney() {
        return winnerMoney;
    }

    public void setWinnerMoney(int winnerMoney) {
        this.winnerMoney = winnerMoney;
    }

    public boolean isCancelable()
    {
        return false;
    }

    public BaseMap getMap() {
        return map;
    }

}
