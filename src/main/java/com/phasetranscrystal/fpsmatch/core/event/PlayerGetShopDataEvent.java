package com.phasetranscrystal.fpsmatch.core.event;

import com.phasetranscrystal.fpsmatch.core.shop.FPSMShop;
import com.phasetranscrystal.fpsmatch.core.shop.ItemType;
import com.phasetranscrystal.fpsmatch.core.shop.skin.PlayerSkinData;
import com.phasetranscrystal.fpsmatch.core.shop.slot.ShopSlot;
import net.minecraftforge.eventbus.api.Event;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PlayerGetShopDataEvent extends Event {
    FPSMShop shop;
    Map<ItemType, List<ShopSlot>> data;
    UUID player;

    public PlayerGetShopDataEvent(UUID player, FPSMShop shop, Map<ItemType, List<ShopSlot>> data) {
        this.data = new HashMap<>(data);
        this.player = player;
        this.shop = shop;
    }

    public Map<ItemType, List<ShopSlot>> getData() {
        return data;
    }

    public FPSMShop getShop() {
        return shop;
    }

    public void setData(Map<ItemType, List<ShopSlot>> data) {
        this.data = data;
    }

    public UUID getPlayer() {
        return player;
    }
}
