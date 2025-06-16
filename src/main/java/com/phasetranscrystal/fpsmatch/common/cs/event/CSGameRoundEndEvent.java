package com.phasetranscrystal.fpsmatch.common.cs.event;

import com.phasetranscrystal.fpsmatch.common.cs.map.CSGameMap;
import com.phasetranscrystal.fpsmatch.core.map.BaseTeam;
import net.minecraftforge.eventbus.api.Event;

public class CSGameRoundEndEvent extends Event {
    private final CSGameMap map;
    private final BaseTeam winner;
    private final CSGameMap.WinnerReason reason;
    private int winnerMoney;

    public CSGameRoundEndEvent(CSGameMap map , BaseTeam winner, CSGameMap.WinnerReason reason) {
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

    public CSGameMap getMap() {
        return map;
    }

}
