package com.phasetranscrystal.fpsmatch.core.shop;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.tacz.guns.api.item.IGun;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

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

    public static ShopData getDefaultData(){
        Map<ItemType, List<ShopSlot>> map = new HashMap<>();
        int[][] c = new int[][]{
                {650,1000,200,200,200},
                {200,700,600,500,300},
                {1500,1050,1700,2350,1050},
                {1800,2700,3000,1700,4750},
                {200,300,300,400,50}
        };

        Item[][] i = new Item[][]{
                {Items.APPLE,Items.STONE,Items.ACACIA_WOOD,Items.OAK_WOOD,Items.BIRCH_WOOD},
                {Items.ENDER_PEARL,Items.DIAMOND,Items.DIAMOND_AXE,Items.DIAMOND_PICKAXE,Items.IRON_AXE},
                {Items.EMERALD,Items.IRON_BLOCK,Items.DIAMOND_BLOCK,Items.EGG,Items.MAP},
                {Items.WARPED_HYPHAE,Items.ENDER_CHEST,Items.HOPPER,Items.KELP,Items.DEEPSLATE},
                {Items.ACACIA_FENCE,Items.CAMEL_SPAWN_EGG,Items.BEE_SPAWN_EGG,Items.GLOW_INK_SAC,Items.MAGENTA_STAINED_GLASS_PANE}
        };

        for (int j = 0; j < 5; j++) {
            map.put(ItemType.values()[j], new ArrayList<>());
            for (int k = 0; k < 5; k++) {
                ItemStack itemStack = new ItemStack(i[j][k]);
                Supplier<ItemStack> supplier = itemStack::copy;
                ShopSlot slot = new ShopSlot(supplier,c[j][k],
                        1,
                        j,
                        stack -> { ItemStack itemStack1 = supplier.get();
                    if(stack.getItem() instanceof IGun iGun && itemStack1.getItem() instanceof IGun iGun1){
                        return iGun.getGunId(stack).equals(iGun1.getGunId(itemStack1));
                    }else{
                        return stack.is(itemStack1.getItem());
                    }
                });
                slot.setIndex(k);
                map.get(ItemType.values()[j]).add(new ShopSlot(new ItemStack(i[j][k]),c[j][k]));
            }
        }

        return new ShopData(map);
    }

}
