package com.phasetranscrystal.fpsmatch.bukkit.event;

import com.phasetranscrystal.fpsmatch.core.BaseTeam;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import com.phasetranscrystal.fpsmatch.cs.CSGameMap;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class BukkitCSGameRoundEndEvent extends Event {
    private static final HandlerList handlers = new HandlerList();
    private final BaseMap map;
    private final BaseTeam winner;
    private final CSGameMap.WinnerReason reason;
    private int winnerMoney;

    public BukkitCSGameRoundEndEvent(BaseMap map, BaseTeam winner, CSGameMap.WinnerReason reason, int winnerMoney) {
        this.map = map;
        this.winner = winner;
        this.reason = reason;
        this.winnerMoney = winnerMoney;
    }

    public BaseMap getMap() { return map; }
    public BaseTeam getWinner() { return winner; }
    public CSGameMap.WinnerReason getReason() { return reason; }
    public int getWinnerMoney() { return winnerMoney; }
    public void setWinnerMoney(int money) { this.winnerMoney = money; }

    @Override
    public HandlerList getHandlers() { return handlers; }
    public static HandlerList getHandlerList() { return handlers; }
}