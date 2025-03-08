package com.phasetranscrystal.fpsmatch.bukkit.event;

import com.phasetranscrystal.fpsmatch.core.data.MvpReason;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class BukkitPlayerGetMvpEvent extends Event {
    private static final HandlerList handlers = new HandlerList();
    private final UUID player;
    private final BaseMap map;
    private final MvpReason reason;

    public BukkitPlayerGetMvpEvent(UUID player, BaseMap map, MvpReason reason) {
        this.player = player;
        this.map = map;
        this.reason = reason;
    }

    public @Nullable Player getPlayer() { return Bukkit.getPlayer(player); }
    public @NotNull UUID getPlayerUUID() { return player; }
    public BaseMap getMap() { return map; }
    public MvpReason getReason() { return reason; }

    @Override
    public HandlerList getHandlers() { return handlers; }
    public static HandlerList getHandlerList() { return handlers; }
}