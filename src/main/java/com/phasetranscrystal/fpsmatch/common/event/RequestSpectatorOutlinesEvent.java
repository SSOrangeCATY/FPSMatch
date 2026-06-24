package com.phasetranscrystal.fpsmatch.common.event;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

public class RequestSpectatorOutlinesEvent extends Event implements ICancellableEvent {
    private final LocalPlayer player;
    private final MultiPlayerGameMode gameMode;

    public RequestSpectatorOutlinesEvent(LocalPlayer player, MultiPlayerGameMode gameMode) {
        this.player = player;
        this.gameMode = gameMode;
    }

    public LocalPlayer getPlayer() {
        return player;
    }

    public MultiPlayerGameMode getGameMode(){
        return gameMode;
    }

}
