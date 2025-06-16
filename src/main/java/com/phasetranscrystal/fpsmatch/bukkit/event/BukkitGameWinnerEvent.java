package com.phasetranscrystal.fpsmatch.bukkit.event;

import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import com.phasetranscrystal.fpsmatch.core.map.BaseTeam;
import org.bukkit.World;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class BukkitGameWinnerEvent extends Event {
    private static final HandlerList handlers = new HandlerList();
    private final BaseMap map;
    private final BaseTeam winners;
    private final BaseTeam losers;
    private final World world;

    public BukkitGameWinnerEvent(BaseMap map, BaseTeam winners, BaseTeam losers, World world) {
        this.map = map;
        this.winners = winners;
        this.losers = losers;
        this.world = world;
    }

    public BaseMap getMap() { return map; }
    public BaseTeam getWinners() { return winners; }
    public BaseTeam getLosers() { return losers; }
    public World getWorld() { return world; }

    @Override
    public HandlerList getHandlers() { return handlers; }
    public static HandlerList getHandlerList() { return handlers; }
}