package com.phasetranscrystal.fpsmatch.core.event;

import com.phasetranscrystal.fpsmatch.core.data.MvpReason;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.Event;

public class PlayerGetMvpEvent extends Event {
    Player player;
    BaseMap map;
    MvpReason reason;

    public PlayerGetMvpEvent(Player player, BaseMap map, MvpReason reason) {
        this.player = player;
        this.map = map;
        this.reason = reason;
    }

    public MvpReason getReason() {
        return reason;
    }

    public BaseMap getMap() {
        return map;
    }

    public Player getPlayer() {
        return player;
    }

    public boolean isCancelable()
    {
        return false;
    }
}
