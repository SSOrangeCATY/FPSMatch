package com.phasetranscrystal.fpsmatch.core.event;

import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.Event;

public class FPSMapEvent extends Event {
    private final BaseMap map;

    public FPSMapEvent(BaseMap map) {
        this.map = map;
    }

    public BaseMap getMap() {
        return map;
    }

    public static class VictoryEvent extends FPSMapEvent {
        public VictoryEvent(BaseMap map) {
            super(map);
        }

        @Override
        public boolean isCancelable() {
            return false;
        }
    }

    public static class ClearEvent extends FPSMapEvent {
        public ClearEvent(BaseMap map) {
            super(map);
        }

        @Override
        public boolean isCancelable() {
            return true;
        }
    }

    public static class ResetEvent extends FPSMapEvent {
        public ResetEvent(BaseMap map) {
            super(map);
        }

        @Override
        public boolean isCancelable() {
            return false;
        }
    }

    public static class StartEvent extends FPSMapEvent {
        public StartEvent(BaseMap map) {
            super(map);
        }

        @Override
        public boolean isCancelable() {
            return true;
        }
    }

    public static class ReloadEvent extends FPSMapEvent {
        public ReloadEvent(BaseMap map) {
            super(map);
        }

        @Override
        public boolean isCancelable() {
            return true;
        }
    }

    public static class LoadEvent extends FPSMapEvent {
        public LoadEvent(BaseMap map) {
            super(map);
        }

        @Override
        public boolean isCancelable() {
            return true;
        }
    }

    public static class PlayerDeathEvent extends FPSMapEvent {
        private final Player dead;
        private final Player killer;

        public PlayerDeathEvent(BaseMap map, Player dead, Player killer) {
            super(map);
            this.dead = dead;
            this.killer = killer;
        }

        public Player getDead() {
            return dead;
        }

        public Player getKiller() {
            return killer;
        }

        @Override
        public boolean isCancelable() {
            return false;
        }
    }
}
