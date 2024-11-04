package com.phasetranscrystal.fpsmatch.core;

import com.phasetranscrystal.fpsmatch.core.data.ShopItemData;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class FPSMShop {
    private static FPSMShop INSTANCE;
    public int money = 10000;
    private final ShopItemData shopItemData = new ShopItemData();
    private final Map<ShopItemData.ItemType, Map<Integer,Boolean>> buttonsData = new HashMap<>();
    private final Map<ShopItemData.ItemType, Boolean> returnGunsData = new HashMap<>();
    private final Map<ShopItemData.ItemType,Map<Integer,Integer>> buyCountData = new HashMap<>();

    public static FPSMShop getInstance(){
        if(INSTANCE == null){
            INSTANCE = new FPSMShop();
        }
        return INSTANCE;
    }

    protected FPSMShop(){
    }

    public boolean handleShopButton(ShopItemData.ItemType type, int index) {
        List<ShopItemData.ShopSlot> shopSlotList = shopItemData.getShopSlotsByType(type);
        if (index < 0 || index >= shopSlotList.size()) {
            return false;
        }

        int cost = getCostOrBuy(type, index, false);
        Map<Integer, Boolean> data = buttonsData.get(type);
        if (!data.get(index)) {
            return false;
        }

        if (money < cost) {
            return false;
        }

        money -= cost;
        getCostOrBuy(type, index, true);
        ShopItemData.ShopSlot shopSlot = shopSlotList.get(index);
        System.out.println("bought : " + (shopSlot.itemStack() == null ? "debugItem" : shopSlot.itemStack().getDisplayName().getString()) + " cost->" + shopSlot.cost());
        return true;

    }

    private int getCostOrBuy(ShopItemData.ItemType type, int index, boolean bought) {
        return switch (type) {
            case EQUIPMENT -> buyEquipment(index, bought);
            case PISTOL -> buyPistol(index, bought);
            case MID_RANK, RIFLE -> buyGuns(type, index, bought);
            case THROWABLE -> buyThrowable(index, bought);
        };
    }

    public boolean handleReturnButton(ShopItemData.ItemType type, int index) {
        Map<Integer, Boolean> data = buttonsData.get(type);
        if (!data.get(index) && buyCountData.get(type).get(index) == 0) {
            return false; // 商品未购买
        }
        // 返回商品
        money += returnTheGun(type, index);
        return true;
    }

    private int buyEquipment(int index,boolean bought) {
        ShopItemData.ItemType type = ShopItemData.ItemType.EQUIPMENT;
        Map<Integer,Boolean> data = buttonsData.get(type);
        List<ShopItemData.ShopSlot> shopSlotList = shopItemData.getShopSlotsByType(type);
        ShopItemData.ShopSlot currentSlot = shopSlotList.get(index);
        int cost = currentSlot.cost();
        if(index == 1){
            boolean flag = data.get(0);
            if(bought) buttonsData.get(type).put(index,false);
            if (flag){
                return cost - shopSlotList.get(0).cost();
            }else{
                return cost + shopSlotList.get(0).cost();
            }
        }else{
            if(bought) buttonsData.get(type).put(index,false);
            return cost;
        }
    }

    private int buyPistol(int index,boolean bought) {
        ShopItemData.ItemType type = ShopItemData.ItemType.PISTOL;
        List<ShopItemData.ShopSlot> shopSlotList = shopItemData.getShopSlotsByType(type);
        ShopItemData.ShopSlot currentSlot = shopSlotList.get(index);
        AtomicInteger cost = new AtomicInteger(currentSlot.cost());
        shopSlotList.forEach((shopSlot -> {
            if(shopSlot.index() != index && buttonsData.get(type).getOrDefault(shopSlot.index(),false)){
                if(bought) buttonsData.get(type).put(shopSlot.index(),false);
                if(returnGunsData.getOrDefault(type,true)) cost.addAndGet(-shopSlot.cost());
            }
        }));
        if(bought) buttonsData.get(type).put(index,true);
        return cost.get();
    }

    private int buyGuns(ShopItemData.ItemType type,int index,boolean bought) {
        List<ShopItemData.ShopSlot> shopMidRankSlotList = shopItemData.getShopSlotsByType(ShopItemData.ItemType.MID_RANK);
        List<ShopItemData.ShopSlot> shopRifleSlotList = shopItemData.getShopSlotsByType(ShopItemData.ItemType.RIFLE);
        ShopItemData.ShopSlot currentSlot = shopMidRankSlotList.get(index);
        AtomicInteger cost = new AtomicInteger(currentSlot.cost());
        shopMidRankSlotList.forEach((shopSlot -> {
            if(buttonsData.get(ShopItemData.ItemType.MID_RANK).get(shopSlot.index())){
                if(shopSlot.index() != index || shopSlot.type() != type){
                    if(bought) buttonsData.get(ShopItemData.ItemType.MID_RANK).put(shopSlot.index(),false);
                    cost.addAndGet(-returnTheGun(ShopItemData.ItemType.MID_RANK,shopSlot.index()));
                }
            }
        }));
        shopRifleSlotList.forEach((shopSlot -> {
            if(buttonsData.get(ShopItemData.ItemType.RIFLE).get(shopSlot.index())){
                if(shopSlot.index() != index || shopSlot.type() != type){
                    if(bought) buttonsData.get(ShopItemData.ItemType.RIFLE).put(shopSlot.index(),false);
                    cost.addAndGet(-returnTheGun(ShopItemData.ItemType.RIFLE,shopSlot.index()));
                }
            }
        }));
        if(bought) buttonsData.get(type).put(index,true);
        return cost.get();
    }

    private int buyThrowable(int index, boolean bought) {
        ShopItemData.ItemType type = ShopItemData.ItemType.THROWABLE;
        List<ShopItemData.ShopSlot> shopThrowableSlotList = shopItemData.getShopSlotsByType(type);
        ShopItemData.ShopSlot currentSlot = shopThrowableSlotList.get(index);
        int cost = currentSlot.cost();
        Map<Integer, Integer> countData = buyCountData.getOrDefault(type, new HashMap<>());
        int totalBought = countData.values().stream().mapToInt(Integer::intValue).sum();

        if (totalBought > 4) {
            countData.forEach((indexId, count) -> {
                if(count == 0){
                    if(bought) buttonsData.get(ShopItemData.ItemType.THROWABLE).put(indexId,false);
                }
            });
            return 0;
        };

        boolean canBuyTwo = index == 0;
        int alreadyBoughtCount = countData.get(index);

        if(bought){
            if(canBuyTwo){
                int num = alreadyBoughtCount + 1;
                countData.put(index, num);
                buyCountData.put(type, countData);
                if(num == 1 && countData.values().stream().mapToInt(Integer::intValue).sum() < 4){
                    buttonsData.get(type).put(index,true);
                }else{
                    buttonsData.get(type).put(index,false);
                }
            }else{
                countData.put(index, alreadyBoughtCount + 1);
                buyCountData.put(type, countData);
                buttonsData.get(type).put(index,false);
            }

            if(countData.values().stream().mapToInt(Integer::intValue).sum() >= 4){
                countData.forEach((indexId, count) -> {
                    if(count == 0){
                        buttonsData.get(ShopItemData.ItemType.THROWABLE).put(indexId,false);
                    }
                });
            }
        }

        return cost;
    }
    private int returnTheGun(ShopItemData.ItemType type,int index){
        int cost = 0;
        if(returnGunsData.getOrDefault(type,true)){
            buttonsData.get(type).put(index,false);
            ShopItemData.ShopSlot shopSlot = shopItemData.getShopSlotsByType(type).get(index);
            cost = shopSlot.cost();
            System.out.println("return goods : " + (shopSlot.itemStack() == null ? "debugItem" : shopSlot.itemStack().getDisplayName().getString()) + " return cost->" + shopSlot.cost());
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

    public Map<ShopItemData.ItemType, Boolean> getReturnGunsData() {
        return returnGunsData;
    }

    public Map<ShopItemData.ItemType, Map<Integer, Boolean>> getButtonsData() {
        return buttonsData;
    }

    public Map<ShopItemData.ItemType, Map<Integer, Integer>> getBuyCountData() {
        return buyCountData;
    }
}
