package com.phasetranscrystal.fpsmatch.core;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.core.data.ShopData;
import com.phasetranscrystal.fpsmatch.net.ShopDataSlotS2CPacket;
import net.minecraft.server.commands.GiveCommand;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class FPSMShop {
    public final String name;
    private final ShopData defaultShopData;
    public final Map<UUID,ShopData> playersData = new HashMap<>();
    private static final Map<String,FPSMShop> gamesFPSMShop = new HashMap<>();

    public FPSMShop(String name){
        this.defaultShopData = new ShopData();
        this.name = name;
    }

    public FPSMShop(String name,ShopData data){
        this.defaultShopData = data;
        this.name = name;
    }

    @Nullable
    public static FPSMShop getShopByMapName(String map){
        return gamesFPSMShop.getOrDefault(map,null);
    }

    public static void putShopData(String map,FPSMShop shopData){
        gamesFPSMShop.put(map,shopData);
    }

    public static void putShopData(String map, ShopData.ShopSlot shopData){
        if(gamesFPSMShop.containsKey(map)){
            gamesFPSMShop.get(map).getDefaultShopData().addShopSlot(shopData);
        }
    }

    public static void syncShopData(String map, ServerPlayer player){
        if(!gamesFPSMShop.containsKey(map)) return;
        ShopData shopData = gamesFPSMShop.get(map).getPlayerShopData(player.getUUID());
        for (ShopData.ItemType type : ShopData.ItemType.values()) {
            List<ShopData.ShopSlot> slots = shopData.getShopSlotsByType(type);
            slots.forEach((shopSlot -> {
                FPSMatch.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new ShopDataSlotS2CPacket(shopSlot, map, shopData.getMoney()));
            }));
        }
    }

    public ShopData getPlayerShopData(UUID uuid){
        ShopData data = this.playersData.getOrDefault(uuid,null);
        if(data == null){
            this.playersData.put(uuid,this.defaultShopData);
            data = this.playersData.get(uuid);
        }
        return data;
    }

    public ShopData.ShopSlot getSlotData(UUID uuid, ShopData.ItemType type, int index) {
        return getPlayerShopData(uuid).getSlotData(type,index);
    }

    public int getNextRoundMinMoney(UUID uuid){
        return this.getPlayerShopData(uuid).getNextRoundMinMoney();
    }

    public void handleShopButton(UUID uuid,ShopData.ItemType type, int index) {
        ShopData data = this.getPlayerShopData(uuid);
        List<ShopData.ShopSlot> shopSlotList = data.getShopSlotsByType(type);
        if (index < 0 || index >= shopSlotList.size()) {
            return;
        }
        ShopData.ShopSlot currentSlot = shopSlotList.get(index);
        int cost = getCostOrBuy(uuid,type, index, false);
        if (!currentSlot.enable()) {
            return;
        }
        if (data.getMoney() < cost) {
            return;
        }
        data.takeMoney(cost);
        getCostOrBuy(uuid,type, index, true);
        ServerPlayer player = Objects.requireNonNull(FPSMCore.getMapByName(name)).getServerLevel().getServer().getPlayerList().getPlayer(uuid);
        if (player != null) {
            player.getInventory().add(currentSlot.itemStack());
            syncShopData(name, player);
        }
        ShopData.ShopSlot shopSlot = shopSlotList.get(index);
        System.out.println("bought : " + (shopSlot.itemStack() == null ? currentSlot.type().toString()+currentSlot.index() : shopSlot.itemStack().getDisplayName().getString()) + " cost->" + shopSlot.cost());
        System.out.println(data.getMoney() +"<-"+ cost);
    }

    private int getCostOrBuy(UUID uuid,ShopData.ItemType type, int index, boolean bought) {
        return switch (type) {
            case EQUIPMENT -> buyEquipment(uuid,type,index, bought);
            case PISTOL -> buyPistol(uuid,type,index, bought);
            case MID_RANK, RIFLE -> buyGuns(uuid,type, index, bought);
            case THROWABLE -> buyThrowable(uuid,type,index, bought);
        };
    }

    public void handleReturnButton(UUID uuid,ShopData.ItemType type, int index) {
        ShopData data = this.getPlayerShopData(uuid);
        List<ShopData.ShopSlot> shopSlotList = data.getShopSlotsByType(type);
        ShopData.ShopSlot currentSlot = shopSlotList.get(index);
        if (!currentSlot.canReturn()) {
            return; // 商品未购买
        }
        data.addMoney(returnTheGun(uuid,type, index));
        ServerPlayer player = Objects.requireNonNull(FPSMCore.getMapByName(name)).getServerLevel().getServer().getPlayerList().getPlayer(uuid);
        if (player != null) {
            player.getInventory().removeItem(currentSlot.itemStack());
            syncShopData(name, player);
        }
    }

    private int buyEquipment(UUID uuid, ShopData.ItemType type, int index, boolean bought) {
        ShopData data = this.getPlayerShopData(uuid);
        List<ShopData.ShopSlot> slotData = data.getShopSlotsByType(type);
        ShopData.ShopSlot currentSlot = slotData.get(index);
        AtomicInteger cost = new AtomicInteger(currentSlot.cost());
        slotData.forEach((shopSlot -> {
            if(index == 0){
                if (!shopSlot.canReturn() && shopSlot.index() == 1){
                    shopSlot.setCost(350);
                }
            }
        }));
        if(bought) currentSlot.bought();
        return cost.get();
    }

    private int buyPistol(UUID uuid, ShopData.ItemType type, int index, boolean bought) {
        ShopData data = this.getPlayerShopData(uuid);
        List<ShopData.ShopSlot> slotData = data.getShopSlotsByType(type);
        ShopData.ShopSlot currentSlot = slotData.get(index);

        AtomicInteger cost = new AtomicInteger(currentSlot.cost());
        slotData.forEach((shopSlot -> {
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

    private int buyGuns(UUID uuid, ShopData.ItemType type, int index, boolean bought) {
        ShopData data = this.getPlayerShopData(uuid);
        List<ShopData.ShopSlot> shopMidRankSlotList = data.getShopSlotsByType(ShopData.ItemType.MID_RANK);
        List<ShopData.ShopSlot> shopRifleSlotList = data.getShopSlotsByType(ShopData.ItemType.RIFLE);
        ShopData.ShopSlot currentSlot = data.getShopSlotsByType(type).get(index);
        AtomicInteger cost = new AtomicInteger(currentSlot.cost());
        if(type == ShopData.ItemType.MID_RANK){
            shopMidRankSlotList.forEach((shopSlot -> {
                if(shopSlot.boughtCount() > 0 && shopSlot.index() != index){
                    if(bought) {
                        shopSlot.returnGoods();
                    }
                    cost.addAndGet(-returnTheGun(uuid,ShopData.ItemType.MID_RANK,shopSlot.index()));
                }
            }));

            shopRifleSlotList.forEach((shopSlot -> {
                if(shopSlot.boughtCount() > 0){
                    if(bought) {
                        shopSlot.returnGoods();
                    }
                    cost.addAndGet(-returnTheGun(uuid,ShopData.ItemType.RIFLE,shopSlot.index()));
                }
            }));
        }else{
            shopRifleSlotList.forEach((shopSlot -> {
                if(shopSlot.boughtCount() > 0 && shopSlot.index() != index){
                    if(bought) {
                        shopSlot.returnGoods();
                    }
                    cost.addAndGet(-returnTheGun(uuid,ShopData.ItemType.RIFLE,shopSlot.index()));
                }
            }));

            shopMidRankSlotList.forEach((shopSlot -> {
                if(shopSlot.boughtCount() > 0){
                    if(bought) {
                        shopSlot.returnGoods();
                    }
                    cost.addAndGet(-returnTheGun(uuid,ShopData.ItemType.MID_RANK,shopSlot.index()));
                }
            }));
        }

        if(bought) currentSlot.bought();
        return cost.get();
    }

    private int buyThrowable(UUID uuid, ShopData.ItemType type, int index, boolean bought) {
        ShopData data = this.getPlayerShopData(uuid);
        List<ShopData.ShopSlot> shopThrowableSlotList = data.getShopSlotsByType(type);
        ShopData.ShopSlot currentSlot = shopThrowableSlotList.get(index);
        int cost = currentSlot.cost();
        int totalBought = data.getSlotListBoughtCount(shopThrowableSlotList);
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

    private int returnTheGun(UUID uuid, ShopData.ItemType type, int index){
        ShopData data = this.getPlayerShopData(uuid);
        List<ShopData.ShopSlot> slotList = data.getShopSlotsByType(type);
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
        List<ShopData.ShopSlot> shopSlotList = defaultShopData.getShopSlotsByType(type);
        if (index < 0 || index >= shopSlotList.size()) {
            return;
        }
        ShopData.ShopSlot currentSlot = shopSlotList.get(index);
        currentSlot.bought();
    }

    public ShopData getDefaultShopData() {
        return defaultShopData;
    }


}
