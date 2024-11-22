package com.phasetranscrystal.fpsmatch.core;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.core.data.save.FileHelper;
import com.phasetranscrystal.fpsmatch.core.data.ShopData;
import com.phasetranscrystal.fpsmatch.net.ShopActionS2CPacket;
import com.phasetranscrystal.fpsmatch.net.ShopDataSlotS2CPacket;
import com.phasetranscrystal.fpsmatch.net.ShopMoneyS2CPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class FPSMShop {
    public final String name;
    private final ShopData defaultShopData;
    public final Map<UUID,ShopData> playersData = new HashMap<>();

    public FPSMShop(String name, int startMoney){
        this.defaultShopData = new ShopData(startMoney);
        this.name = name;
    }

    public FPSMShop(String name){
        this.defaultShopData = new ShopData();
        this.name = name;
    }

    public FPSMShop(String name,ShopData data){
        this.defaultShopData = data;
        this.name = name;
    }
    public FPSMShop(String name,ShopData data,int startMoney){
        this.defaultShopData = data;
        this.defaultShopData.setMoney(startMoney);
        this.name = name;
    }

    public void syncShopData() {
        BaseMap map = FPSMCore.getInstance().getMapByName(name);
        if(map != null){
            for (UUID uuid : map.getMapTeams().getJoinedPlayers()) {
                ServerPlayer player = (ServerPlayer) map.getServerLevel().getPlayerByUUID(uuid);
                if (player != null){
                    ShopData shopData = this.getPlayerShopData(uuid);
                    for (ShopData.ItemType type : ShopData.ItemType.values()) {
                        List<ShopData.ShopSlot> slots = shopData.getShopSlotsByType(type);
                        slots.forEach((shopSlot -> {
                            FPSMatch.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new ShopDataSlotS2CPacket(shopSlot, name));
                        }));
                    }
                }
            }
        }
    }
    public void syncShopMoneyData() {
        BaseMap map = FPSMCore.getInstance().getMapByName(name);
        if(map != null){
            for (UUID uuid : map.getMapTeams().getJoinedPlayers()) {
                ServerPlayer player = (ServerPlayer) map.getServerLevel().getPlayerByUUID(uuid);
                if (player != null){
                    ShopData shopData = this.getPlayerShopData(uuid);
                    FPSMatch.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new ShopMoneyS2CPacket(uuid, shopData.getMoney()));
                }
            }
        }
    }

    public void syncShopMoneyData(UUID uuid) {
        BaseMap map = FPSMCore.getInstance().getMapByName(name);
        if(map != null){
            ServerPlayer player = (ServerPlayer) map.getServerLevel().getPlayerByUUID(uuid);
            if (player != null){
                ShopData shopData = this.getPlayerShopData(uuid);
                FPSMatch.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new ShopMoneyS2CPacket(uuid, shopData.getMoney()));
            }
        }
    }

    public void syncShopData(List<ServerPlayer> players){
        players.forEach(this::syncShopData);
    }

    public void syncShopData(ServerPlayer player){
        ShopData shopData = this.getPlayerShopData(player.getUUID());
        for (ShopData.ItemType type : ShopData.ItemType.values()) {
            List<ShopData.ShopSlot> slots = shopData.getShopSlotsByType(type);
            slots.forEach((shopSlot -> {
                FPSMatch.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new ShopDataSlotS2CPacket(shopSlot, name));
            }));
        }

    }

    public void syncShopData(ServerPlayer player, ShopData.ShopSlot slot){
        FPSMatch.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new ShopDataSlotS2CPacket(slot, name));
    }

    public void syncShopAction(ServerPlayer player, ShopData.ItemType type, int index, int action){
        int money = this.getPlayerShopData(player.getUUID()).getMoney();
        FPSMatch.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new ShopActionS2CPacket(name,type,index,action,money));
    }

    public void syncShopAction(ServerPlayer player, ShopData.ShopSlot shopSlot, int action){
        int money = this.getPlayerShopData(player.getUUID()).getMoney();
        FPSMatch.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new ShopActionS2CPacket(name,shopSlot,action,money));
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

    public void handleShopButton(ServerPlayer player, ShopData.ItemType type, int index) {
        ShopData data = this.getPlayerShopData(player.getUUID());
        List<ShopData.ShopSlot> shopSlotList = data.getShopSlotsByType(type);
        if (index < 0 || index >= shopSlotList.size()) {
            return;
        }
        ShopData.ShopSlot currentSlot = shopSlotList.get(index);
        int cost = getCostOrBuy(player,type, index, false);
        if (!currentSlot.enable()) {
            return;
        }
        if (data.getMoney() < cost) {
            return;
        }

        data.takeMoney(cost);
        getCostOrBuy(player,type, index, true);
        player.getInventory().add(currentSlot.itemStack());
        syncShopData(player);
        syncShopAction(player,currentSlot,1);
        ShopData.ShopSlot shopSlot = shopSlotList.get(index);
        System.out.println("bought : " + (shopSlot.itemStack() == null ? currentSlot.type().toString()+currentSlot.index() : shopSlot.itemStack().getDisplayName().getString()) + " cost->" + shopSlot.cost());
        System.out.println(data.getMoney() +"<-"+ cost);
    }

    private int getCostOrBuy(ServerPlayer player,ShopData.ItemType type, int index, boolean bought) {
        return switch (type) {
            case EQUIPMENT -> buyEquipment(player,type,index, bought);
            case PISTOL -> buyPistol(player,type,index, bought);
            case MID_RANK, RIFLE -> buyGuns(player,type, index, bought);
            case THROWABLE -> buyThrowable(player,type,index, bought);
        };
    }

    public void handleReturnButton(ServerPlayer player,ShopData.ItemType type, int index) {
        ShopData data = this.getPlayerShopData(player.getUUID());
        List<ShopData.ShopSlot> shopSlotList = data.getShopSlotsByType(type);
        ShopData.ShopSlot currentSlot = shopSlotList.get(index);
        if (!currentSlot.canReturn()) {
            return; // 商品未购买
        }
        data.addMoney(returnTheGun(player,currentSlot));
        syncShopData(player, currentSlot);
        syncShopAction(player, currentSlot,0);
    }

    private int buyEquipment(ServerPlayer serverPlayer, ShopData.ItemType type, int index, boolean bought) {
        ShopData data = this.getPlayerShopData(serverPlayer.getUUID());
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

    private int buyPistol(ServerPlayer serverPlayer, ShopData.ItemType type, int index, boolean bought) {
        ShopData data = this.getPlayerShopData(serverPlayer.getUUID());
        List<ShopData.ShopSlot> slotData = data.getShopSlotsByType(type);
        ShopData.ShopSlot currentSlot = slotData.get(index);
        AtomicInteger cost = new AtomicInteger(currentSlot.cost());
        slotData.forEach((shopSlot -> {
            if(shopSlot.index() != index && shopSlot.boughtCount() > 0){
                if(bought) {
                    returnTheGun(serverPlayer, shopSlot);
                    syncShopAction(serverPlayer,shopSlot,0);
                }
                cost.addAndGet(-shopSlot.cost());
            }
        }));
        if(bought) currentSlot.bought();
        return cost.get();
    }

    private int buyGuns(ServerPlayer serverPlayer, ShopData.ItemType type, int index, boolean bought) {
        ShopData data = this.getPlayerShopData(serverPlayer.getUUID());
        List<ShopData.ShopSlot> shopMidRankSlotList = data.getShopSlotsByType(ShopData.ItemType.MID_RANK);
        List<ShopData.ShopSlot> shopRifleSlotList = data.getShopSlotsByType(ShopData.ItemType.RIFLE);
        ShopData.ShopSlot currentSlot = data.getShopSlotsByType(type).get(index);
        AtomicInteger cost = new AtomicInteger(currentSlot.cost());
        if(type == ShopData.ItemType.MID_RANK){
            shopMidRankSlotList.forEach((shopSlot -> {
                if(shopSlot.boughtCount() > 0 && shopSlot.index() != index){
                    if(bought) {
                        returnTheGun(serverPlayer,shopSlot);
                        syncShopAction(serverPlayer,shopSlot,0);
                    }
                    cost.addAndGet(-shopSlot.cost());
                }
            }));

            shopRifleSlotList.forEach((shopSlot -> {
                if(shopSlot.boughtCount() > 0){
                    if(bought) {
                        returnTheGun(serverPlayer,shopSlot);
                        syncShopAction(serverPlayer,shopSlot,0);
                    }
                    cost.addAndGet(-shopSlot.cost());
                }
            }));
        }else{
            shopRifleSlotList.forEach((shopSlot -> {
                if(shopSlot.boughtCount() > 0 && shopSlot.index() != index){
                    if(bought) {
                        returnTheGun(serverPlayer,shopSlot);
                        syncShopAction(serverPlayer,shopSlot,0);
                    }
                    cost.addAndGet(-shopSlot.cost());
                }
            }));

            shopMidRankSlotList.forEach((shopSlot -> {
                if(shopSlot.boughtCount() > 0){
                    if(bought) {
                        returnTheGun(serverPlayer,shopSlot);
                        syncShopAction(serverPlayer,shopSlot,0);
                    }
                    cost.addAndGet(-shopSlot.cost());
                }
            }));
        }

        if(bought) currentSlot.bought();
        return cost.get();
    }

    private int buyThrowable(ServerPlayer serverPlayer, ShopData.ItemType type, int index, boolean bought) {
        ShopData data = this.getPlayerShopData(serverPlayer.getUUID());
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

    private int returnTheGun(ServerPlayer serverPlayer, ShopData.ShopSlot shopSlot){
        ShopData data = this.getPlayerShopData(serverPlayer.getUUID());
        List<ShopData.ShopSlot> slotList = data.getShopSlotsByType(shopSlot.type());
        ShopData.ShopSlot currentSlot = slotList.get(shopSlot.index());
        if(currentSlot.canReturn()){
            if(shopSlot.type() == ShopData.ItemType.EQUIPMENT && shopSlot.index() == 0){
                if(!slotList.get(1).canReturn()){
                    slotList.get(1).setCost(1000);
                }
            }
            currentSlot.returnGoods();
            int im = serverPlayer.getInventory().clearOrCountMatchingItems((i)-> i.getDisplayName().getString().equals(currentSlot.itemStack().getDisplayName().getString()), 1, serverPlayer.inventoryMenu.getCraftSlots());
            System.out.println("return goods : " + (currentSlot.itemStack() == null ? currentSlot.type().toString()+currentSlot.index() : currentSlot.itemStack().getDisplayName().getString()) + " return cost->" + currentSlot.cost());
            if (im == 0) return 0;
        }
        return currentSlot.cost();
    }

    public void resetSlot(ServerPlayer serverPlayer, ShopData.ItemType type, int index){
        ShopData data = this.getPlayerShopData(serverPlayer.getUUID());
        List<ShopData.ShopSlot> slotList = data.getShopSlotsByType(type);
        ShopData.ShopSlot currentSlot = slotList.get(index);
        while (currentSlot.canReturn()) currentSlot.returnGoods();
        syncShopAction(serverPlayer,currentSlot,2);
    }

    public ShopData getDefaultShopData() {
        return defaultShopData;
    }

}
