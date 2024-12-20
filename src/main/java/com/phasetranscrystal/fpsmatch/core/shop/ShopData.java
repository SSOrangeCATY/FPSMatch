package com.phasetranscrystal.fpsmatch.core.shop;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.mojang.datafixers.util.Pair;
import com.phasetranscrystal.fpsmatch.core.shop.event.CheckCostEvent;
import com.phasetranscrystal.fpsmatch.core.shop.event.ShopSlotChangeEvent;
import com.phasetranscrystal.fpsmatch.core.shop.slot.ShopSlot;
import com.tacz.guns.api.item.IGun;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;


/* *
 * server side
 * */
public class ShopData {
//    public static final ShopData defaultData = new ShopData();//TODO
    private int money = 800;
    private int willBeAddMoney = 0;
    // 存储数据
    private final Map<ItemType, ImmutableList<ShopSlot>> data;
    // 分组数据
    public final Multimap<Integer, ShopSlot> grouped;

    /**
     * 构造函数，初始化商店数据
     * @param shopData 商店数据
     */
    public <T extends List<ShopSlot>> ShopData(Map<ItemType, T> shopData) {
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

    public <T extends List<ShopSlot>> ShopData(Map<ItemType, T> shopData,int money) {
        this(shopData);
        this.money = money;
    }

    /**
     * 检查数据是否合法
     * @param data 数据
     */
    public static  <T extends List<ShopSlot>> void checkData(Map<ItemType, T> data) {
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

    public static Map<ItemType, ArrayList<ShopSlot>> getRawData(){
        Map<ItemType, ArrayList<ShopSlot>> data = new HashMap<>();
        int cost = 0;
        ItemStack empty = ItemStack.EMPTY;
        for (ItemType type : ItemType.values()) {
            ArrayList<ShopSlot> list = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                list.add(new ShopSlot(empty, cost));
            }
            data.put(type, list);
        }
        return data;
    }

    public void setMoney(int money) {
        this.money = Math.max(0,money);
    }

    public void reduceMoney(int money){
        this.money -= Math.max(0,money);
    }

    public void addMoney(int money){
        this.money += Math.max(0,money);
    }

    public List<ShopSlot> getShopSlotsByType(ItemType type) {
        return this.data.get(type);
    }

    public void reset() {

    }

    public int getMoney() {
        return this.money;
    }

    public void handleButton(ServerPlayer player, ItemType type, int index, ShopAction action){
        List<ShopSlot> shopSlotList = data.get(type);
        if (index < 0 || index >= shopSlotList.size()) {
            return;
        }
        ShopSlot currentSlot = shopSlotList.get(index);

        switch (action){
            case BUY -> this.handleBuy(player,currentSlot);
            case RETURN -> this.handleReturn(player,currentSlot);
        }
    }


    protected void handleBuy(ServerPlayer player, ShopSlot currentSlot) {
        boolean check = this.broadcastCostCheckEvent(player,currentSlot);
        if(check || this.money >= currentSlot.getCost()){
            this.broadcastGroupChangeEvent(player,currentSlot,1);

            if (!currentSlot.canBuy(this.money)) {
                return;
            }

            this.money = currentSlot.buy(player,this.money);
        }
    }


    protected void handleReturn(ServerPlayer player, ShopSlot currentSlot) {
        AtomicBoolean checkFlag = new AtomicBoolean(true);
        List<ShopSlot> groupSlot = currentSlot.haveGroup() ? this.grouped.get(currentSlot.getGroupId()).stream().filter((slot)-> slot != currentSlot).toList() : new ArrayList<>();
        for (ShopSlot slot : groupSlot) {
            slot.getListenerNames().forEach(name->{
                if(name.contains("changeItem") && slot.getBoughtCount() > 0 && !slot.canReturn(player)){
                    checkFlag.set(false);
                }
            });
        }

        if(currentSlot.canReturn(player) && checkFlag.get()){
            this.broadcastGroupChangeEvent(player,currentSlot,-1);
            this.addMoney(currentSlot.getCost());
            currentSlot.returnItem(player);
        }
    }

    public void lockShopSlots(ServerPlayer player){
        List<NonNullList<ItemStack>> items = ImmutableList.of(player.getInventory().items,player.getInventory().armor,player.getInventory().offhand);

        data.forEach(((itemType, shopSlots) -> {
            shopSlots.forEach(shopSlot -> {
                items.forEach(list -> {
                    list.forEach(itemStack -> {
                        if (shopSlot.returningChecker.test(itemStack)) {
                            shopSlot.lock();
                        }else{
                            shopSlot.unlock();
                        }
                    });
                });
            });
        }));
    }


    protected boolean broadcastCostCheckEvent(ServerPlayer player ,ShopSlot currentSlot){
        List<ShopSlot> groupSlot = currentSlot.haveGroup() ? new ArrayList<>() : this.grouped.get(currentSlot.getGroupId()).stream().filter((slot)-> slot != currentSlot).toList();
        CheckCostEvent event = new CheckCostEvent(player,currentSlot.getCost());
        groupSlot.forEach(slot -> {
            slot.handleCheckCostEvent(event);
        });

        return event.success();

    }
    protected void broadcastGroupChangeEvent(ServerPlayer player ,ShopSlot currentSlot, int flag){
        List<ShopSlot> groupSlot = currentSlot.haveGroup() ? this.grouped.get(currentSlot.getGroupId()).stream().filter((slot)-> slot != currentSlot).toList() : new ArrayList<>();

        groupSlot.forEach(slot -> {
            ShopSlotChangeEvent event = new ShopSlotChangeEvent(slot, player,this.money,flag);
            slot.onGroupSlotChanged(event);
            this.money = event.getMoney();
        });
    }

    @Nullable
    public Pair<ItemType, ShopSlot> checkItemStackIsInData(ItemStack itemStack){
        AtomicReference<Pair<ItemType, ShopSlot>> flag = new AtomicReference<>();
        if(itemStack.getItem() instanceof IGun iGun){
            ResourceLocation gunId = iGun.getGunId(itemStack);
            data.forEach(((itemType, shopSlots) -> {
                shopSlots.forEach(shopSlot -> {
                    ItemStack itemStack1 = shopSlot.process();
                    if(itemStack1.getItem() instanceof IGun shopGun && gunId.equals(shopGun.getGunId(itemStack1)) && !itemStack1.isEmpty()){
                        flag.set(new Pair<>(itemType,shopSlot));
                    }
                });
            }));
        }else {
            data.forEach(((itemType, shopSlots) -> {
                shopSlots.forEach(shopSlot -> {
                    ItemStack itemStack1 = shopSlot.process();
                    if(itemStack.getDisplayName().getString().equals(itemStack1.getDisplayName().getString()) && !itemStack1.isEmpty()){
                        flag.set(new Pair<>(itemType,shopSlot));
                    }
                });
            }));
        }
        return flag.get();
    }

    public ShopData copy(){
        return new ShopData(this.data,this.money);
    }


}
