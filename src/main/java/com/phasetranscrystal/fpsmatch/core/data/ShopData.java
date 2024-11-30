package com.phasetranscrystal.fpsmatch.core.data;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.cs.CSGameMap;
import com.phasetranscrystal.fpsmatch.net.ShopActionS2CPacket;
import com.phasetranscrystal.fpsmatch.util.FPSMUtil;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
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
                    FPSMUtil.fixGunItem(itemStack, iGun);
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
                    //WTF IS THIS???
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

    //AND WTF IS THIS?????
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

    public void setShopSlot(ServerPlayer serverPlayer) {
        this.data.forEach((itemType, shopSlots) -> {
            shopSlots.forEach((shopSlot) -> {
                int i = serverPlayer.getInventory().clearOrCountMatchingItems((itemStack -> {
                    // 检查物品是否是相同的
                    if(shopSlot.itemStack().getItem() instanceof IGun iGun){
                        return iGun.getGunId(itemStack).equals(iGun.getGunId(shopSlot.itemStack()));
                    }else {
                        return itemStack.is(shopSlot.itemStack().getItem());
                    }
                }), 0,serverPlayer.inventoryMenu.getCraftSlots());
                // 如果i为0，说明没有找到相同的物品
                boolean canBuyTwo = shopSlot.type == ItemType.THROWABLE && shopSlot.index == 0;
                int k = canBuyTwo ? Math.min(2,i): 1;
                if(i > 0 && shopSlot.boughtCount != k){
                    shopSlot.boughtCount = k;
                    shopSlot.canReturn = true;
                    shopSlot.enable = canBuyTwo && shopSlot.boughtCount < 2;
                    FPSMatch.INSTANCE.send(PacketDistributor.PLAYER.with(() -> serverPlayer), new ShopActionS2CPacket("null",shopSlot,3, shopSlot.boughtCount));
                }else if(i == 0 && shopSlot.boughtCount != 0){
                    while (shopSlot.canReturn()){
                        shopSlot.returnGoods();
                        if(!shopSlot.canReturn()){
                            FPSMatch.INSTANCE.send(PacketDistributor.PLAYER.with(() -> serverPlayer), new ShopActionS2CPacket("null",shopSlot,2,money));
                        }
                    }
                }
            });
        });
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
            if(boughtCount < 0) boughtCount = 0; // 防止负数
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

        public void reset(int money){
            this.boughtCount = 0;
            this.canReturn = false;
            this.enable = money >= defaultCost;
            this.cost = defaultCost;
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
