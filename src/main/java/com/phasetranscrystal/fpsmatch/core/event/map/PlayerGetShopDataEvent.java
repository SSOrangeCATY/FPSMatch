package com.phasetranscrystal.fpsmatch.core.event.map;

import com.phasetranscrystal.fpsmatch.core.shop.FPSMShop;
import com.phasetranscrystal.fpsmatch.core.shop.INamedType;
import com.phasetranscrystal.fpsmatch.core.shop.slot.ShopSlot;
import net.minecraftforge.eventbus.api.Event;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PlayerGetShopDataEvent<T extends Enum<T> & INamedType> extends Event {
    FPSMShop<T> shop;
    Map<T, List<ShopSlot>> data;
    UUID player;

    public PlayerGetShopDataEvent(UUID player, FPSMShop<T> shop, Map<T, List<ShopSlot>> data) {
        this.data = data;
        this.player = player;
        this.shop = shop;
    }

    public Map<T, List<ShopSlot>> getData() {
        return data;
    }

    public FPSMShop<T> getShop() {
        return shop;
    }

    public void setData(Map<T, List<ShopSlot>> data) {
        this.data = data;
    }

    public UUID getPlayer() {
        return player;
    }
}
