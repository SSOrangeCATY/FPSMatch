package com.phasetranscrystal.fpsmatch.core.event;

import com.phasetranscrystal.fpsmatch.core.shop.ItemType;
import com.phasetranscrystal.fpsmatch.core.shop.slot.ShopSlot;
import net.minecraftforge.eventbus.api.Event;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PlayerGetShopDataEvent extends Event {
    Map<ItemType, List<ShopSlot>> data;
    UUID player;
    public PlayerGetShopDataEvent(Map<ItemType, List<ShopSlot>> data, UUID player) {
        this.data = new HashMap<>(data); // 防御性拷贝
        this.player = player;
    }

    public Map<ItemType, List<ShopSlot>> getData() {
        return data;
    }

    public UUID getPlayer() {
        return player;
    }
}
