package com.phasetranscrystal.fpsmatch.bukkit.event;

import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import org.bukkit.World;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import java.util.List;
import java.util.UUID;

public class BukkitGameWinnerEvent extends Event {
    private static final HandlerList handlers = new HandlerList();
    private final BaseMap map;
    private final List<UUID> winners;
    private final List<UUID> losers;
    private final World world;

    public BukkitGameWinnerEvent(BaseMap map, List<UUID> winners, List<UUID> losers, World world) {
        this.map = map;
        this.winners = winners;
        this.losers = losers;
        this.world = world;
    }

    public BaseMap getMap() { return map; }
    public List<UUID> getWinners() { return winners; }
    public List<UUID> getLosers() { return losers; }
    public World getWorld() { return world; }

    @Override
    public HandlerList getHandlers() { return handlers; }
    public static HandlerList getHandlerList() { return handlers; }
}