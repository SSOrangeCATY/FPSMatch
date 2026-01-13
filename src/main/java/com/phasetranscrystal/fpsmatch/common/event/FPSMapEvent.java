package com.phasetranscrystal.fpsmatch.common.event;

import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import com.phasetranscrystal.fpsmatch.util.FPSMUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.Event;

import java.util.Optional;

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
    }

    /**
     * 你不能直接监听这个Event!!!!
     * 未在游戏中的地图不会发布这个事件
     * */
    public static class PlayerEvent extends FPSMapEvent {

        private final ServerPlayer player;

        PlayerEvent(BaseMap map, ServerPlayer player) {
            super(map);
            this.player = player;
        }

        public ServerPlayer getPlayer() {
            return player;
        }

        public static class JoinEvent extends PlayerEvent {

            public JoinEvent(BaseMap map, ServerPlayer player) {
                super(map, player);
            }

            @Override
            public boolean isCancelable() {
                return true;
            }
        }

        public static class LeaveEvent extends PlayerEvent {

            public LeaveEvent(BaseMap map, ServerPlayer player) {
                super(map, player);
            }

            @Override
            public boolean isCancelable() {
                return true;
            }
        }

        public static class HurtEvent extends PlayerEvent{
            private final DamageSource source;
            private float amount;

            public HurtEvent(BaseMap map, ServerPlayer player, DamageSource source, float amount) {
                super(map, player);
                this.source = source;
                this.amount = amount;
            }

            public DamageSource getSource() {
                return source;
            }

            public float getAmount() {
                return amount;
            }

            public void setAmount(float amount) {
                this.amount = amount;
            }

            @Override
            public boolean isCancelable() {
                return true;
            }
        }

        public static class DeathEvent extends PlayerEvent {
            private final DamageSource source;

            public DeathEvent(BaseMap map, ServerPlayer dead, DamageSource source) {
                super(map, dead);
                this.source = source;
            }

            public DamageSource getSource() {
                return source;
            }

            public Optional<ServerPlayer> getKiller() {
                return FPSMUtil.getAttackerFromDamageSource(source);
            }

            @Override
            public boolean isCancelable() {
                return true;
            }
        }

        public static class LoggedInEvent extends PlayerEvent {
            public LoggedInEvent(BaseMap map, ServerPlayer player) {
                super(map, player);
            }
        }

        /*
        * 可以被取消，取消后不会退出队伍，需要额外处理一些逻辑来应对这个情况
        * */
        public static class LoggedOutEvent extends PlayerEvent {
            public LoggedOutEvent(BaseMap map, ServerPlayer player) {
                super(map, player);
            }

            @Override
            public boolean isCancelable() {
                return true;
            }
        }

        public static class PickupItemEvent extends PlayerEvent {
            private final ItemEntity itemEntity;
            private final ItemStack stack;

            public PickupItemEvent(BaseMap map, ServerPlayer player, ItemEntity originalEntity, ItemStack stack) {
                super(map, player);
                this.itemEntity = originalEntity;
                this.stack = stack;
            }

            public ItemEntity getItemEntity() {
                return itemEntity;
            }

            public ItemStack getStack() {
                return stack;
            }

            @Override
            public boolean isCancelable() {
                return true;
            }
        }

        public static class TossItemEvent extends PlayerEvent {
            private final ItemEntity item;
            public TossItemEvent(BaseMap map, ServerPlayer player, ItemEntity item) {
                super(map, player);
                this.item = item;
            }

            public ItemEntity getItemEntity() {
                return item;
            }

            @Override
            public boolean isCancelable() {
                return true;
            }
        }

        public static class ChatEvent extends PlayerEvent {
            private final String message;

            public ChatEvent(BaseMap map, ServerPlayer player,String message) {
                super(map, player);
                this.message = message;
            }

            public String getMessage() {
                return message;
            }

            @Override
            public boolean isCancelable() {
                return true;
            }
        }
    }
}
