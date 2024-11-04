package com.phasetranscrystal.fpsmatch.core.data;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class ShopItemData {
    private static final Map<ItemType, List<ShopSlot>> defaultData = getDefaultShopItemData(true);
    private final Map<ItemType, List<ShopSlot>> data = new HashMap<>();
    public ShopItemData(){
        checkData(defaultData);
        data.putAll(defaultData);
    }

    public ShopItemData(Map<ItemType, List<ShopSlot>> data){
        checkData(data);
        this.data.putAll(data);
    }

    public ShopSlot getSlotData(ItemType type,int index) {
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
        data.computeIfAbsent(shopSlot.type, k -> {
            data.get(k).remove(shopSlot.index);
            return data.get(k);
        }).add(shopSlot);
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
        Map<ItemType, List<ShopSlot>> data = new HashMap<>();
        for(ItemType c : ItemType.values()) {
            List<ShopSlot> shopSlots = new ArrayList<>();
            for (int i = 0;i <= 4 ; i++){
                ShopSlot shopSlot = new ShopSlot(i,c,itemStack,4750);
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
        private final int index;
        private final ItemType type;
        private final ItemStack itemStack;
        private final int cost;
        private int boughtCount = 0;
        private boolean enable = true;
        private boolean canReturn = false;

        public ShopSlot(int index,ItemType type, ItemStack itemStack, int cost) {
            this.type = type;
            if (index < 0 || index > 4) {
                throw new IllegalArgumentException("Index must be between 0 and 4 inclusive.");
            }
            this.index = index;
            this.itemStack = itemStack;
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

        public int index(){
            return index;
        }

        public boolean enable(){
            return enable;
        }

        public int boughtCount(){
            return boughtCount;
        }

        public boolean canReturn(){
            return canReturn;
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
