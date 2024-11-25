package com.phasetranscrystal.fpsmatch.core.data;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class ShopData {
    private static final Map<ItemType, ArrayList<ShopSlot>> defaultData = getDefaultShopItemData(false);
    private final Map<ItemType, List<ShopSlot>> data = new HashMap<>();
    public int money = 800;
    private int nextRoundMinMoney = 1000;
    public ShopData(){
        checkData(defaultData);
        data.putAll(defaultData);
    }

    public ShopData(Map<ItemType, ArrayList<ShopSlot>> data){
        checkData(data);
        this.data.putAll(data);
    }

    public ShopData(int startMoney){
        checkData(defaultData);
        this.data.putAll(defaultData);
        this.money = startMoney;
    }
    public void setData(Map<ItemType, ArrayList<ShopSlot>> shopData){
        shopData.forEach((itemType, shopSlots) -> {
            shopSlots.forEach((shopSlot) -> {
                ItemStack itemStack = shopSlot.itemStack();
                if(itemStack.getItem() instanceof IGun iGun){
                    Optional<CommonGunIndex> gunIndexOptional = TimelessAPI.getCommonGunIndex(iGun.getGunId(itemStack));
                    if(gunIndexOptional.isPresent()){
                        GunData gunData = gunIndexOptional.get().getGunData();
                        iGun.setCurrentAmmoCount(itemStack,gunData.getAmmoAmount());
                    }
                    int maxAmmo = iGun.getMaxDummyAmmoAmount(itemStack);
                    if(maxAmmo > 0){
                        iGun.useDummyAmmo(itemStack);
                        iGun.setDummyAmmoAmount(itemStack,maxAmmo);
                    }
                    shopSlot.setItemStack(itemStack);
                }
            });
        });
        this.data.clear();
        this.data.putAll(shopData);
    }

    public Map<ItemType, List<ShopSlot>> getData(){
        return data;
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
    public static void checkData(Map<ItemType, ArrayList<ShopSlot>> data) {
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

    public static boolean checkShopData(Map<ItemType, ArrayList<ShopSlot>> data) {
        for (ItemType type : ItemType.values()) {
            List<ShopSlot> slots = data.getOrDefault(type, null);
            if (slots != null && slots.size() == 5) {
                for (int i = 0; i <= 4; i++) {
                    ShopSlot slot = slots.get(i);
                    if (slot.index != i) {
                        return false;
                    }
                }
            } else {
                return false;
            }
        }
        return true;
    }

    public ShopSlot checkItemStackIsInData(ItemStack itemStack){
        AtomicReference<ShopSlot> flag = new AtomicReference<>();
        if(itemStack.getItem() instanceof IGun iGun){
            ResourceLocation gunId = iGun.getGunId(itemStack);
            data.forEach(((itemType, shopSlots) -> {
                shopSlots.forEach(shopSlot -> {
                    if(shopSlot.itemStack.getItem() instanceof IGun shopGun && gunId.equals(shopGun.getGunId(shopSlot.itemStack))){
                        flag.set(shopSlot);
                    }
                });
            }));
        }else {
            data.forEach(((itemType, shopSlots) -> {
                shopSlots.forEach(shopSlot -> {
                    if(itemStack.getDisplayName().getString().equals(shopSlot.itemStack().getDisplayName().getString())){
                        flag.set(shopSlot);
                    }
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

    public static Map<ItemType, ArrayList<ShopSlot>> getDefaultShopItemData(boolean debug){
        ItemStack itemStack = debug ? null : new ItemStack(Items.APPLE);
        int[][] d = new int[][]{
                {650,1000,200,200,200},
                {200,700,600,500,300},
                {1500,1050,1700,2350,1050},
                {1800,2700,3000,1700,4750},
                {200,300,300,400,50}
        };
        Map<ItemType, ArrayList<ShopSlot>> data = new HashMap<>();
        for(ItemType c : ItemType.values()) {
            ArrayList<ShopSlot> shopSlots = new ArrayList<>();
            for (int i = 0;i <= 4 ; i++){
                ShopSlot shopSlot = new ShopSlot(i,c,itemStack == null ? null : itemStack.copy(),d[c.typeIndex][i]);
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

    // TODO 后面会拓展Money
    public int getMoney() {
        return money;
    }

    public void setMoney(int money) {
        this.money = Math.min(money, 99999);
        if(this.money < 0) this.money = 0;
        System.out.println("Money: " + this.money);
    }

    public void addMoney(int money){
        this.money += money;
        if(this.money > 16000) this.money = 16000;
        System.out.println("Money: " + this.money);
    }

    public void takeMoney(int money){
        this.money -= money;
        if(this.money < 0) this.money = 0;
        System.out.println("Money: " + this.money);
    }

    public void reset() {
        money = 800;
        data.clear();
        data.putAll(defaultData);
    }

    public ShopData copy() {
        ShopData newData = new ShopData();
        newData.money = this.money;
        newData.data.clear();
        newData.data.putAll(this.data);
        newData.nextRoundMinMoney = this.nextRoundMinMoney;
        return newData;
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

        public String typeStr(){
            return type.toString();
        }
        public ItemStack itemStack(){
            if(itemStack == null){
                return ItemStack.EMPTY;
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

        public int defaultCost() {
            return defaultCost;
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
