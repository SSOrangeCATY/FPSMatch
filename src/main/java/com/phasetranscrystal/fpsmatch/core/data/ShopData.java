package com.phasetranscrystal.fpsmatch.core.data;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ShopData {
    private static final Map<ItemType, List<ShopSlot>> defaultData = getDefaultShopItemData(true);
    private final Map<ItemType, List<ShopSlot>> data = new HashMap<>();
    private int nextRoundMinMoney = 1000;
    public ShopData(){
        checkData(defaultData);
        data.putAll(defaultData);
    }

    public ShopData(Map<ItemType, List<ShopSlot>> data){
        checkData(data);
        this.data.putAll(data);
    }

    public int getNextRoundMinMoney() {
        return nextRoundMinMoney;
    }

    public void setNextRoundMinMoney(int money){
        this.nextRoundMinMoney = money;
    }

    public ShopSlot getSlotData(ItemType type, int index) {
        return data.get(type).get(index);
    }

    public static void checkData(Map<ItemType, List<ShopSlot>> data) {
        for (ItemType type : ItemType.values()) {
            List<ShopSlot> slots = data.getOrDefault(type, null);
            if (slots != null && slots.size() == 5) {
                for (int i = 0; i <= 4; i++) {
                    ShopSlot slot = slots.get(i);
                    if (slot.index != i) {
                        throw new RuntimeException("Index mismatch for " + type + " at position " + i + ". Expected index " + i + " but found " + slot.index);
                    }
                }
            } else {
                if (slots == null) {
                    throw new RuntimeException("No slots found for type " + type);
                } else {
                    throw new RuntimeException("Incorrect number of slots for type " + type + ". Expected 5 but found " + slots.size());
                }
            }
        }
    }

    // 添加一个ShopSlot到对应的ItemType列表中
    public void addShopSlot(ShopSlot shopSlot) {
        data.get(shopSlot.type).remove(shopSlot.index);
        data.get(shopSlot.type).add(shopSlot.index,shopSlot);
    }

    // 获取特定ItemType的所有ShopSlot
    public List<ShopSlot> getShopSlotsByType(ItemType type) {
        return data.get(type);
    }

    // 移除特定ItemType和index的ShopSlot
    public boolean removeShopSlot(ItemType type, int index) {
        List<ShopSlot> slots = data.get(type);
        if (slots != null) {
            slots.set(index,defaultData.get(type).get(index));
            return true;
        }
        return false;
    }

    public static Map<ItemType, List<ShopSlot>> getDefaultShopItemData(boolean debug){
        ItemStack itemStack = debug ? null : ItemStack.EMPTY;
        int[][] d = new int[][]{
                {650,1000,200,200,200},
                {200,700,600,500,300},
                {1500,1050,1700,2350,1050},
                {1800,2700,3000,1700,4750},
                {200,300,300,400,50}
        };

        Map<ItemType, List<ShopSlot>> data = new HashMap<>();
        for(ItemType c : ItemType.values()) {
            List<ShopSlot> shopSlots = new ArrayList<>();
            for (int i = 0;i <= 4 ; i++){
                ShopSlot shopSlot = new ShopSlot(i,c,itemStack,d[c.typeIndex][i]);
                shopSlots.add(shopSlot);
            }
            data.put(c,shopSlots);
        }
        return data;
    }

    public int getSlotListBoughtCount(List<ShopSlot> slotList){
        AtomicInteger totalBought = new AtomicInteger(0);
        slotList.forEach((shopSlot -> {
            totalBought.addAndGet(shopSlot.boughtCount());
        }));
        return totalBought.get();
    }

    public int getThrowableTypeBoughtCount(){
        AtomicInteger totalBought = new AtomicInteger(0);
        data.get(ItemType.THROWABLE).forEach((shopSlot -> {
            totalBought.addAndGet(shopSlot.boughtCount());
        }));
        return totalBought.get();
    }

    public static class ShopSlot{
        private ResourceLocation texture = new ResourceLocation(FPSMatch.MODID,"gun/hud/ai_awp");
        private final String itemName;
        private final int index;
        private final ItemType type;
        private ItemStack itemStack;
        private int defaultCost;
        private int cost;
        private int boughtCount = 0;
        private boolean enable = true;
        private boolean canReturn = false;

        public ShopSlot(int index, ItemType type,ItemStack itemStack, int cost) {
            this.type = type;
            if (index < 0 || index > 4) {
                throw new IllegalArgumentException("Index must be between 0 and 4 inclusive.");
            }
            this.itemName = itemStack== null ? "DebugItem": itemStack.getDisplayName().getString();
            this.index = index;
            this.itemStack = itemStack;
            this.defaultCost = cost;
            this.cost = cost;
        }


        public int cost(){
            return cost;
        }
        public ItemType type(){
            return type;
        }
        public ItemStack itemStack(){
            if(itemStack == null){
                return null;
            }
            return itemStack.copy();
        }

        public ResourceLocation getTexture() {
            return texture;
        }

        public void setTexture(ResourceLocation texture){
            this.texture = texture;
        }

        public int index(){
            return index;
        }
        public String name(){
            return this.itemName;
        }

        public boolean enable(){
            return enable;
        }

        public int boughtCount(){
            return boughtCount;
        }

        public boolean canReturn(){
            return canReturn || boughtCount > 0;
        }

        public int setDefaultCost(){
            this.cost = defaultCost;
            return this.cost;
        }

        public void setDefaultCost(int cost){
            this.defaultCost = cost;
        }

        public void setBoughtCount(int boughtCount) {
            this.boughtCount = boughtCount;
        }

        public void setCanReturn(boolean canReturn) {
            this.canReturn = canReturn;
        }

        public void setEnable(boolean enable) {
            this.enable = enable;
        }

        public void setCost(int cost) {
            this.cost = cost;
        }

        public void bought(boolean enable) {
            this.boughtCount++;
            this.canReturn = true;
            this.enable = enable;
        }
        public void bought() {
            this.boughtCount++;
            this.canReturn = true;
            this.enable = false;
        }
        public void returnGoods() {
            this.boughtCount--;
            this.canReturn = boughtCount >= 1;
            this.enable = true;
        }

        @Override
        public boolean equals(Object anObject) {
            if(anObject instanceof ShopSlot other){
                return other.index == this.index && other.type == this.type;
            }else{
                return false;
            }
        }
    }

    public  enum ItemType{
        EQUIPMENT(0),PISTOL(1),MID_RANK(2),RIFLE(3),THROWABLE(4);
        public final int typeIndex;

        ItemType(int typeIndex) {
            this.typeIndex = typeIndex;
        }

    }
}
