package com.phasetranscrystal.fpsmatch.core;

import com.phasetranscrystal.fpsmatch.core.data.ShopItemData;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class FPSMShop {
    private static FPSMShop INSTANCE;
    public int money = 10000;
    private final ShopItemData shopItemData = new ShopItemData();

    public static FPSMShop getInstance(){
        if(INSTANCE == null){
            INSTANCE = new FPSMShop();
        }
        return INSTANCE;
    }

    protected FPSMShop(){
    }

    public ShopItemData.ShopSlot getSlotData(ShopItemData.ItemType type, int index) {
        return shopItemData.getSlotData(type,index);
    }

    public void handleShopButton(ShopItemData.ItemType type, int index) {
        List<ShopItemData.ShopSlot> shopSlotList = shopItemData.getShopSlotsByType(type);
        if (index < 0 || index >= shopSlotList.size()) {
            return;
        }
        ShopItemData.ShopSlot currentSlot = shopSlotList.get(index);
        int cost = getCostOrBuy(type, index, false);
        if (!currentSlot.enable()) {
            return;
        }
        if (money < cost) {
            return;
        }
        money -= cost;
        getCostOrBuy(type, index, true);
        ShopItemData.ShopSlot shopSlot = shopSlotList.get(index);
        System.out.println("bought : " + (shopSlot.itemStack() == null ? "debugItem" : shopSlot.itemStack().getDisplayName().getString()) + " cost->" + shopSlot.cost());
    }

    private int getCostOrBuy(ShopItemData.ItemType type, int index, boolean bought) {
        return switch (type) {
            case EQUIPMENT -> buyEquipment(type,index, bought);
            case PISTOL -> buyPistol(type,index, bought);
            case MID_RANK, RIFLE -> buyGuns(type, index, bought);
            case THROWABLE -> buyThrowable(type,index, bought);
        };
    }

    public void handleReturnButton(ShopItemData.ItemType type, int index) {
        List<ShopItemData.ShopSlot> data = this.shopItemData.getShopSlotsByType(type);
        if (!data.get(index).canReturn() && data.get(index).boughtCount() != 0) {
            return; // 商品未购买
        }
        money += returnTheGun(type, index);
    }

    private int buyEquipment(ShopItemData.ItemType type, int index,boolean bought) {
        List<ShopItemData.ShopSlot> data = this.shopItemData.getShopSlotsByType(type);
        ShopItemData.ShopSlot currentSlot = data.get(index);
        int cost = currentSlot.cost();
        if(index == 1){
            boolean flag = data.get(0).boughtCount() > 0;
            if(bought) {
                currentSlot.bought();
            }
            if (flag){
                return cost - data.get(0).cost();
            }else{
                return cost + data.get(0).cost();
            }
        }else{
            if(bought) {
                currentSlot.bought();
            }
            return cost;
        }
    }

    private int buyPistol(ShopItemData.ItemType type,int index,boolean bought) {
        List<ShopItemData.ShopSlot> data = this.shopItemData.getShopSlotsByType(type);
        ShopItemData.ShopSlot currentSlot = data.get(index);

        AtomicInteger cost = new AtomicInteger(currentSlot.cost());
        data.forEach((shopSlot -> {
            if(shopSlot.index() != index && shopSlot.boughtCount() > 0){
                if(bought) {
                    //shopSlot.returnGoods();
                   // shopSlot.setEnable(false); // aaaaaaaaaaaaaaaaaaaaaa
                };
                //if(shopSlot.canReturn()) cost.addAndGet(-shopSlot.cost());
            }
        }));
        if(bought) currentSlot.bought();
        return cost.get();
    }

    private int buyGuns(ShopItemData.ItemType type,int index,boolean bought) {
        List<ShopItemData.ShopSlot> shopMidRankSlotList = shopItemData.getShopSlotsByType(ShopItemData.ItemType.MID_RANK);
        List<ShopItemData.ShopSlot> shopRifleSlotList = shopItemData.getShopSlotsByType(ShopItemData.ItemType.RIFLE);
        ShopItemData.ShopSlot currentSlot = shopMidRankSlotList.get(index);
        AtomicInteger cost = new AtomicInteger(currentSlot.cost());
        shopMidRankSlotList.forEach((shopSlot -> {
            if(shopSlot.canReturn()){
                if(shopSlot.index() != index || shopSlot.type() != type){
                    if(bought) {
                        // shopSlot.returnGoods();
                    }
                   // cost.addAndGet(-returnTheGun(ShopItemData.ItemType.MID_RANK,shopSlot.index()));
                }
            }
        }));
        shopRifleSlotList.forEach((shopSlot -> {
            if(shopSlot.canReturn()){
                if(shopSlot.index() != index || shopSlot.type() != type){
                    if(bought) {
                       // shopSlot.returnGoods();
                    }
                    // cost.addAndGet(-returnTheGun(ShopItemData.ItemType.RIFLE,shopSlot.index()));
                }
            }
        }));
        if(bought) currentSlot.bought();
        return cost.get();
    }

    private int buyThrowable(ShopItemData.ItemType type,int index, boolean bought) {
        List<ShopItemData.ShopSlot> shopThrowableSlotList = shopItemData.getShopSlotsByType(type);
        ShopItemData.ShopSlot currentSlot = shopThrowableSlotList.get(index);
        int cost = currentSlot.cost();
        int totalBought = shopItemData.getSlotListBoughtCount(shopThrowableSlotList);

        if (totalBought > 4) {
            shopThrowableSlotList.forEach((shopSlot -> {
                if(shopSlot.boughtCount() == 0){
                    shopSlot.setEnable(false);
                }

                if(index == 0 && currentSlot.boughtCount() ==1){
                    shopSlot.setEnable(false);
                }
            }));
            return 0;
        };
        boolean canBuyTwo = index == 0;
        if(bought){
            if(canBuyTwo){
                if(currentSlot.boughtCount() < 2 && shopItemData.getSlotListBoughtCount(shopThrowableSlotList) < 4){
                    currentSlot.bought(true);
                }else{
                    currentSlot.bought();
                }
            }else{
                currentSlot.bought();
            }
            if(shopItemData.getSlotListBoughtCount(shopThrowableSlotList) >= 4){
                shopThrowableSlotList.forEach((shopSlot -> {
                    if(shopSlot.boughtCount() == 0){
                        shopSlot.setEnable(false);
                    }

                    if(index == 0 && currentSlot.boughtCount() ==1){
                        shopSlot.setEnable(false);
                    }
                }));
            }
        }
        return cost;
    }
    private int returnTheGun(ShopItemData.ItemType type, int index){
        List<ShopItemData.ShopSlot> slotList = shopItemData.getShopSlotsByType(type);
        ShopItemData.ShopSlot currentSlot = slotList.get(index);
        int cost = 0;
        if(currentSlot.canReturn()){
            currentSlot.returnGoods();
            cost = currentSlot.cost();
            System.out.println("return goods : " + (currentSlot.itemStack() == null ? "debugItem" : currentSlot.itemStack().getDisplayName().getString()) + " return cost->" + currentSlot.cost());
        }
        return cost;
    }

    public ShopItemData getShopItemData() {
        return shopItemData;
    }

    public int getMoney() {
        return money;
    }

    public void setMoney(int money) {
        this.money = money;
    }

}
