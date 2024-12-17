package com.phasetranscrystal.fpsmatch.core.shop;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.phasetranscrystal.fpsmatch.core.shop.slot.ShopSlot;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ShopData {
//    public static final ShopData defaultData = new ShopData();//TODO
    private int money;
    private int willBeAddMoney;
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

    public void handleShopButton(ServerPlayer player, ItemType type, int index) {
        List<ShopSlot> shopSlotList = data.get(type);
        if (index < 0 || index >= shopSlotList.size()) {
            return;
        }
        ShopSlot currentSlot = shopSlotList.get(index);
        List<ShopSlot> groupSlot = currentSlot.haveGroup() ? new ArrayList<>() : this.grouped.get(currentSlot.getGroupId()).stream().filter((slot)-> slot != currentSlot).toList();

        groupSlot.forEach(slot -> {
                ShopSlotChangeEvent event = new ShopSlotChangeEvent(slot, player,this.money,1);
                slot.onGroupSlotChanged(event);
                this.money = event.getMoney();
        });

        int cost = currentSlot.getCost();

        if (!currentSlot.canBuy(this.money)) {
            return;
        }
        this.money -= cost;
        player.getInventory().add(currentSlot.process());
    }

    public static ShopData getDefaultData() {
        Map<ItemType, List<ShopSlot>> data = new HashMap<>();
        int cost = 0;
        ItemStack empty = ItemStack.EMPTY;
        for (ItemType type : ItemType.values()) {
            List<ShopSlot> list = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                list.add(new ShopSlot(empty, cost));
            }
            data.put(type, list);
        }
        return new ShopData(data);
    }

}
