package com.phasetranscrystal.fpsmatch.core.shop;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class ShopData {
//    public static final ShopData defaultData = new ShopData();//TODO

    // 存储数据
    private final Map<ItemType, ImmutableList<ShopSlot>> data;
    // 分组数据
    public final Multimap<Integer, ShopSlot> grouped;

    /**
     * 构造函数，初始化商店数据
     * @param shopData 商店数据
     */
    public ShopData(Map<ItemType, List<ShopSlot>> shopData) {
        // 检查数据是否合法
        checkData(shopData);

        // 创建一个不可变Map的构建器
        ImmutableMap.Builder<ItemType, ImmutableList<ShopSlot>> builder = ImmutableMap.builder();
        // 将传入的Map转换为不可变Map
        shopData.forEach((k, v) -> builder.put(k, ImmutableList.copyOf(v)));
        // 赋值给data字段
        data = builder.build();

        // 遍历data中的每个值，即每个类型的商店槽位列表
        data.values().forEach(shopSlots -> {
            // 创建一个原子整数，用于记录当前的索引值
            AtomicInteger index = new AtomicInteger();
            // 遍历每个商店槽位，并设置其索引值
            shopSlots.forEach(slots -> slots.setIndex(index.getAndIncrement()));
        });

        // 创建一个不可变Multimap的构建器
        ImmutableMultimap.Builder<Integer, ShopSlot> builder2 = ImmutableMultimap.builder();
        // 遍历data中的每个值，即每个类型的商店槽位列表
        data.values().stream().flatMap(Collection::stream).filter(ShopSlot::haveGroup).forEach(slot -> builder2.put(slot.getGroupId(), slot));
        // 赋值给grouped字段
        grouped = builder2.build();
    }

    /**
     * 检查数据是否合法
     * @param data 数据
     */
    public static void checkData(Map<ItemType, List<ShopSlot>> data) {
        // 遍历所有的物品类型
        for (ItemType type : ItemType.values()) {
            // 获取该类型的商店槽位列表
            List<ShopSlot> slots = data.get(type);

            // 如果没有找到该类型的商店槽位列表，则抛出异常
            if (slots == null) throw new RuntimeException("No slots found for type " + type);
                // 如果该类型的商店槽位列表数量不等于5，则抛出异常
            else if (slots.size()!= 5)
                throw new RuntimeException("Incorrect number of slots for type " + type + ". Expected 5 but found " + slots.size());
        }
    }

    public Map<ItemType, ImmutableList<ShopSlot>> getData() {
        return data;
    }

}
