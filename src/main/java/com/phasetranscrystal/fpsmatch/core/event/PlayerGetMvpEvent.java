package com.phasetranscrystal.fpsmatch.core.event;

import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.Event;

public class PlayerGetMvpEvent extends Event {
    Player player;
    BaseMap map;

    public PlayerGetMvpEvent(Player player, BaseMap map) {
        this.player = player;
        this.map = map;
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
