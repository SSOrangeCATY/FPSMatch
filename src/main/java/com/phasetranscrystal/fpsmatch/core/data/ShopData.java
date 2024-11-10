package com.phasetranscrystal.fpsmatch.core.data;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.resource.index.ClientGunIndex;
import com.tacz.guns.resource.pojo.GunIndexPOJO;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class ShopData extends SavedData {
    private static final Map<ItemType, List<ShopSlot>> defaultData = getDefaultShopItemData(true);
    private final Map<ItemType, List<ShopSlot>> data = new HashMap<>();
    public int money = 10000;
    private int nextRoundMinMoney = 1000;
    public ShopData(){
        checkData(defaultData);
        data.putAll(defaultData);
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag pCompoundTag) {
        // 创建一个列表来存储所有的ShopSlot数据
        ListTag shopSlotsTag = new ListTag();

        // 遍历所有的ItemType和对应的ShopSlot列表
        for (Map.Entry<ItemType, List<ShopSlot>> entry : data.entrySet()) {
            ItemType type = entry.getKey();
            List<ShopSlot> slots = entry.getValue();

            // 为每个ItemType创建一个列表标签
            ListTag typeTag = new ListTag();

            // 遍历每个ShopSlot并将其数据添加到列表标签中
            for (ShopSlot slot : slots) {
                CompoundTag slotTag = new CompoundTag();
                slotTag.putInt("index", slot.index());
                slotTag.putString("itemName", slot.name());
                slotTag.putInt("type", type.typeIndex);
                slot.itemStack().save(slotTag);
                slotTag.putInt("defaultCost", slot.defaultCost);
                slotTag.putInt("cost", slot.cost());
                slotTag.putInt("boughtCount", slot.boughtCount());
                slotTag.putBoolean("enable", slot.enable());
                slotTag.putBoolean("canReturn", slot.canReturn());

                // 将ShopSlot的NBT标签添加到ItemType的列表标签中
                typeTag.add(slotTag);
            }

            // 将ItemType的列表标签添加到总的列表标签中
            shopSlotsTag.add(typeTag);
        }

        // 将商店的金钱和下一轮最小金钱也保存起来
        pCompoundTag.putInt("money", this.money);
        pCompoundTag.putInt("nextRoundMinMoney", this.nextRoundMinMoney);
        pCompoundTag.put("shopSlots", shopSlotsTag);
        return pCompoundTag;
    }

    public void load(CompoundTag pCompoundTag) {
        // 首先，读取金钱和下一轮最小金钱
        this.money = pCompoundTag.getInt("money");
        this.nextRoundMinMoney = pCompoundTag.getInt("nextRoundMinMoney");

        // 读取商店槽位数据
        ListTag shopSlotsTag = pCompoundTag.getList("shopSlots", Tag.TAG_COMPOUND);
        for (int i = 0; i < shopSlotsTag.size(); i++) {
            ListTag typeTag = shopSlotsTag.getList(i);
            ItemType type = ItemType.values()[typeTag.getInt(2)];
            List<ShopSlot> slots = new ArrayList<>();

            for (int j = 0; j < typeTag.size(); j++) {
                CompoundTag slotTag = typeTag.getCompound(j);
                ShopSlot slot = new ShopSlot(
                        slotTag.getInt("index"),
                        type,
                        ItemStack.of(slotTag),
                        slotTag.getInt("defaultCost")
                );
                slot.setCost(slotTag.getInt("cost"));
                slot.setBoughtCount(slotTag.getInt("boughtCount"));
                slot.setEnable(slotTag.getBoolean("enable"));
                slot.setCanReturn(slotTag.getBoolean("canReturn"));
                slots.add(slot);
            }

            // 将重建的ShopSlot列表添加到data映射中
            this.data.put(type, slots);
        }
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

    public ShopSlot checkItemStackIsInData(ItemStack itemStack){
        AtomicReference<ShopSlot> flag = new AtomicReference<>();
        if(itemStack.getItem() instanceof IGun iGun){
            ResourceLocation gunId = iGun.getGunId(itemStack);
            data.forEach(((itemType, shopSlots) -> {
                shopSlots.forEach(shopSlot -> {
                    if(shopSlot.itemStack.getItem() instanceof IGun shopGun && gunId.equals(shopGun.getGunId(shopSlot.itemStack))){
                        flag.set(shopSlot);
                    };
                });
            }));
        }else {
            data.forEach(((itemType, shopSlots) -> {
                shopSlots.forEach(shopSlot -> {
                    if(itemStack.getDisplayName().getString().equals(shopSlot.itemStack().getDisplayName().getString())){
                        flag.set(shopSlot);
                    };
                });
            }));
        }
        return flag.get();
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

    public int getMoney() {
        return money;
    }

    public void setMoney(int money) {
        this.money = Math.min(money, 16000);
        if(this.money < 0) this.money = 0;
    }

    public void addMoney(int money){
        this.money += money;
        if(this.money > 16000) this.money = 16000;
    }

    public void takeMoney(int money){
        this.money -= money;
    }

    public static class ShopSlot{
        private ResourceLocation texture = new ResourceLocation(FPSMatch.MODID,"gun/hud/ai_awp");
        private String itemName;
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
            this.itemName = itemStack == null ? "DebugItem": itemStack.getDisplayName().getString();
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

        public void setItemStack(ItemStack itemStack) {
            this.itemName = itemStack == null ? "DebugItem": itemStack.getDisplayName().getString();
            this.itemStack = itemStack;
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

    public enum ItemType{
        EQUIPMENT(0),PISTOL(1),MID_RANK(2),RIFLE(3),THROWABLE(4);
        public final int typeIndex;

        ItemType(int typeIndex) {
            this.typeIndex = typeIndex;
        }

    }
}
