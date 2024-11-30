package com.phasetranscrystal.fpsmatch.shopreconstruct;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.phasetranscrystal.fpsmatch.core.data.ShopData.ItemType;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class ShopData {
//    public static final ShopData defaultData = new ShopData();//TODO

    private final Map<ItemType, ImmutableList<ShopSlot>> data;
    public final Multimap<Integer, ShopSlot> grouped;

    public ShopData(Map<ItemType, List<ShopSlot>> shopData) {
        checkData(shopData);

        ImmutableMap.Builder<ItemType, ImmutableList<ShopSlot>> builder = ImmutableMap.builder();
        shopData.forEach((k, v) -> builder.put(k, ImmutableList.copyOf(v)));
        data = builder.build();

        data.values().forEach(shopSlots -> {
            AtomicInteger index = new AtomicInteger();
            shopSlots.forEach(slots -> slots.setIndex(index.getAndIncrement()));
        });

        ImmutableMultimap.Builder<Integer, ShopSlot> builder2 = ImmutableMultimap.builder();
        data.values().stream().flatMap(Collection::stream).filter(ShopSlot::haveGroup).forEach(slot -> builder2.put(slot.getGroupId(), slot));
        grouped = builder2.build();
    }

    public static void checkData(Map<ItemType, List<ShopSlot>> data) {
        for (ItemType type : ItemType.values()) {
            List<ShopSlot> slots = data.get(type);

            if (slots == null) throw new RuntimeException("No slots found for type " + type);
            else if (slots.size() != 5)
                throw new RuntimeException("Incorrect number of slots for type " + type + ". Expected 5 but found " + slots.size());
        }
    }
}