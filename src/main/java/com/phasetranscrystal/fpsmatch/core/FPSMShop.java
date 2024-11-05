package com.phasetranscrystal.fpsmatch.core;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.core.data.ShopData;
import com.phasetranscrystal.fpsmatch.net.ShopDataSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class FPSMShop {
    private static FPSMShop INSTANCE;
    public int money = 10000;
    private final ShopData shopItemData = new ShopData();
    private static final Map<String,ShopData> gamesShopData = new HashMap<>();

    public static FPSMShop getInstance(){
        if(INSTANCE == null){
            INSTANCE = new FPSMShop();
        }
        return INSTANCE;
    }

    protected FPSMShop(){
    }

    public static void putShopData(String map,ShopData shopData){
        gamesShopData.put(map,shopData);
    }

    public static void putShopData(String map, ShopData.ShopSlot shopData){
        if(gamesShopData.containsKey(map)){
            gamesShopData.get(map).addShopSlot(shopData);
        }
    }

    public static void syncShopData(String map, ServerPlayer player){
        if(!gamesShopData.containsKey(map)) return;
        for (ShopData.ItemType type : ShopData.ItemType.values()){
            List<ShopData.ShopSlot> slots = gamesShopData.get(map).getShopSlotsByType(type);
            slots.forEach((shopSlot -> {
                FPSMatch.INSTANCE.send(PacketDistributor.PLAYER.with(()-> player), new ShopDataSlotPacket(shopSlot));
            }));
        }
    }


    public ShopData.ShopSlot getSlotData(ShopData.ItemType type, int index) {
        return shopItemData.getSlotData(type,index);
    }

    public int getNextRoundMinMoney(){
        return this.shopItemData.getNextRoundMinMoney();
    }
    public void handleShopButton(ShopData.ItemType type, int index) {
        List<ShopData.ShopSlot> shopSlotList = shopItemData.getShopSlotsByType(type);
        if (index < 0 || index >= shopSlotList.size()) {
            return;
        }
        ShopData.ShopSlot currentSlot = shopSlotList.get(index);
        int cost = getCostOrBuy(type, index, false);
        if (!currentSlot.enable()) {
            return;
        }
        if (money < cost) {
            return;
        }
        money -= cost;
        getCostOrBuy(type, index, true);
        ShopData.ShopSlot shopSlot = shopSlotList.get(index);
        System.out.println("bought : " + (shopSlot.itemStack() == null ? currentSlot.type().toString()+currentSlot.index() : shopSlot.itemStack().getDisplayName().getString()) + " cost->" + shopSlot.cost());
        System.out.println(this.money +"<-"+ cost);
    }

    private int getCostOrBuy(ShopData.ItemType type, int index, boolean bought) {
        return switch (type) {
            case EQUIPMENT -> buyEquipment(type,index, bought);
            case PISTOL -> buyPistol(type,index, bought);
            case MID_RANK, RIFLE -> buyGuns(type, index, bought);
            case THROWABLE -> buyThrowable(type,index, bought);
        };
    }

    public void handleReturnButton(ShopData.ItemType type, int index) {
        List<ShopData.ShopSlot> shopSlotList = this.shopItemData.getShopSlotsByType(type);
        ShopData.ShopSlot currentSlot = shopSlotList.get(index);
        if (!currentSlot.canReturn()) {
            return; // 商品未购买
        }
        money += returnTheGun(type, index);
    }

    private int buyEquipment(ShopData.ItemType type, int index, boolean bought) {
        List<ShopData.ShopSlot> data = this.shopItemData.getShopSlotsByType(type);
        ShopData.ShopSlot currentSlot = data.get(index);
        AtomicInteger cost = new AtomicInteger(currentSlot.cost());
        data.forEach((shopSlot -> {
            if(index == 0){
                if (!shopSlot.canReturn() && shopSlot.index() == 1){
                    shopSlot.setCost(350);
                }
            }
        }));
        if(bought) currentSlot.bought();
        return cost.get();
    }

    private int buyPistol(ShopData.ItemType type, int index, boolean bought) {
        List<ShopData.ShopSlot> data = this.shopItemData.getShopSlotsByType(type);
        ShopData.ShopSlot currentSlot = data.get(index);

        AtomicInteger cost = new AtomicInteger(currentSlot.cost());
        data.forEach((shopSlot -> {
            if(shopSlot.index() != index && shopSlot.boughtCount() > 0){
                if(bought) {
                    shopSlot.returnGoods();
                };
                cost.addAndGet(-shopSlot.cost());
            }
        }));
        if(bought) currentSlot.bought();
        return cost.get();
    }

    private int buyGuns(ShopData.ItemType type, int index, boolean bought) {
        List<ShopData.ShopSlot> shopMidRankSlotList = shopItemData.getShopSlotsByType(ShopData.ItemType.MID_RANK);
        List<ShopData.ShopSlot> shopRifleSlotList = shopItemData.getShopSlotsByType(ShopData.ItemType.RIFLE);
        ShopData.ShopSlot currentSlot = shopItemData.getShopSlotsByType(type).get(index);
        AtomicInteger cost = new AtomicInteger(currentSlot.cost());
        if(type == ShopData.ItemType.MID_RANK){
            shopMidRankSlotList.forEach((shopSlot -> {
                if(shopSlot.boughtCount() > 0 && shopSlot.index() != index){
                    if(bought) {
                        shopSlot.returnGoods();
                    }
                    cost.addAndGet(-returnTheGun(ShopData.ItemType.MID_RANK,shopSlot.index()));
                }
            }));

            shopRifleSlotList.forEach((shopSlot -> {
                if(shopSlot.boughtCount() > 0){
                    if(bought) {
                        shopSlot.returnGoods();
                    }
                    cost.addAndGet(-returnTheGun(ShopData.ItemType.RIFLE,shopSlot.index()));
                }
            }));
        }else{
            shopRifleSlotList.forEach((shopSlot -> {
                if(shopSlot.boughtCount() > 0 && shopSlot.index() != index){
                    if(bought) {
                        shopSlot.returnGoods();
                    }
                    cost.addAndGet(-returnTheGun(ShopData.ItemType.RIFLE,shopSlot.index()));
                }
            }));

            shopMidRankSlotList.forEach((shopSlot -> {
                if(shopSlot.boughtCount() > 0){
                    if(bought) {
                        shopSlot.returnGoods();
                    }
                    cost.addAndGet(-returnTheGun(ShopData.ItemType.MID_RANK,shopSlot.index()));
                }
            }));
        }

        if(bought) currentSlot.bought();
        return cost.get();
    }

    private int buyThrowable(ShopData.ItemType type, int index, boolean bought) {
        List<ShopData.ShopSlot> shopThrowableSlotList = shopItemData.getShopSlotsByType(type);
        ShopData.ShopSlot currentSlot = shopThrowableSlotList.get(index);
        int cost = currentSlot.cost();
        int totalBought = shopItemData.getSlotListBoughtCount(shopThrowableSlotList);
        boolean canBuyTwo = index == 0;
        if(totalBought + 1 <= 4 && bought){
            if(canBuyTwo){
                if(currentSlot.boughtCount() == 1){
                    currentSlot.bought();
                }else{
                    currentSlot.bought(true);
                }
            }else{
                currentSlot.bought();
            }
        }
        return cost;
    }

    private int returnTheGun(ShopData.ItemType type, int index){
        List<ShopData.ShopSlot> slotList = shopItemData.getShopSlotsByType(type);
        ShopData.ShopSlot currentSlot = slotList.get(index);
        int cost = 0;
        if(currentSlot.canReturn()){
            if(type == ShopData.ItemType.EQUIPMENT && index == 0){
                if(!slotList.get(1).canReturn()){
                    slotList.get(1).setCost(1000);
                }
            }
            currentSlot.returnGoods();
            cost = currentSlot.cost();
            System.out.println("return goods : " + (currentSlot.itemStack() == null ? currentSlot.type().toString()+currentSlot.index() : currentSlot.itemStack().getDisplayName().getString()) + " return cost->" + currentSlot.cost());
        }
        return cost;
    }

    public void forceBuyItem(ShopData.ItemType type, int index){
        List<ShopData.ShopSlot> shopSlotList = shopItemData.getShopSlotsByType(type);
        if (index < 0 || index >= shopSlotList.size()) {
            return;
        }
        ShopData.ShopSlot currentSlot = shopSlotList.get(index);
        currentSlot.bought();
    }
    public ShopData getShopItemData() {
        return shopItemData;
    }

    public int getMoney() {
        return money;
    }

    public void setMoney(int money) {
        this.money = money;
    }

}
