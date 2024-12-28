package com.phasetranscrystal.fpsmatch.core;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.core.shop.ItemType;
import com.phasetranscrystal.fpsmatch.core.shop.ShopAction;
import com.phasetranscrystal.fpsmatch.core.shop.ShopData;
import com.phasetranscrystal.fpsmatch.core.shop.functional.ListenerModule;
import com.phasetranscrystal.fpsmatch.core.shop.slot.ShopSlot;
import com.phasetranscrystal.fpsmatch.net.ShopDataSlotS2CPacket;
import com.phasetranscrystal.fpsmatch.net.ShopMoneyS2CPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.PacketDistributor;

import java.util.*;

public class FPSMShop {
    public final String name;
    private final Map<ItemType, ArrayList<ShopSlot>> defaultShopData;
    private final int startMoney;
    public final Map<UUID,ShopData> playersData = new HashMap<>();

    public FPSMShop(String name, int startMoney){
        this.defaultShopData = ShopData.getRawData();
        this.startMoney = startMoney;
        this.name = name;
    }

    public FPSMShop(String name){
        this.defaultShopData = ShopData.getRawData();
        this.startMoney = 0;
        this.name = name;
    }

    public FPSMShop(String name,Map<ItemType, ArrayList<ShopSlot>> data){
        this.defaultShopData = data;
        this.startMoney = 0;
        this.name = name;
    }

    public FPSMShop(String name, Map<ItemType, ArrayList<ShopSlot>> data, int startMoney){
        this.defaultShopData = data;
        this.startMoney = startMoney;
        this.name = name;
    }

    public void syncShopData() {
        BaseMap map = FPSMCore.getInstance().getMapByName(name);
        if(map != null){
            for (UUID uuid : map.getMapTeams().getJoinedPlayers()) {
                ServerPlayer player = (ServerPlayer) map.getServerLevel().getPlayerByUUID(uuid);
                if (player != null){
                    ShopData shopData = this.getPlayerShopData(uuid);
                    for (ItemType type : ItemType.values()) {
                        List<ShopSlot> slots = shopData.getShopSlotsByType(type);
                        slots.forEach((shopSlot -> FPSMatch.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new ShopDataSlotS2CPacket(type, shopSlot, name))));
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

    public void syncShopMoneyData(ServerPlayer player) {
        BaseMap map = FPSMCore.getInstance().getMapByName(name);
        if(map != null){
            if (player != null){
                ShopData shopData = this.getPlayerShopData(player.getUUID());
                FPSMatch.INSTANCE.send(PacketDistributor.ALL.noArg(), new ShopMoneyS2CPacket(player.getUUID(), shopData.getMoney()));
            }
        }
    }

    public void syncShopData(List<ServerPlayer> players){
        players.forEach(this::syncShopData);
    }

    public void syncShopData(ServerPlayer player){
        ShopData shopData = this.getPlayerShopData(player.getUUID());
        for (ItemType type : ItemType.values()) {
            List<ShopSlot> slots = shopData.getShopSlotsByType(type);
            slots.forEach((shopSlot -> FPSMatch.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new ShopDataSlotS2CPacket(type, shopSlot, name))));
        }

    }

    public void syncShopData(ServerPlayer player,ItemType type, ShopSlot slot){
        FPSMatch.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new ShopDataSlotS2CPacket(type, slot, name));
    }

    public void syncShopData(ServerPlayer player,ItemType type, int index){
        ShopSlot shopSlot = this.getPlayerShopData(player.getUUID()).getShopSlotsByType(type).get(index);
        FPSMatch.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new ShopDataSlotS2CPacket(type, shopSlot, name));
    }

    public ShopData getPlayerShopData(UUID uuid){
        ShopData data = this.playersData.getOrDefault(uuid,null);
        if(data == null){
            this.playersData.put(uuid,this.getDefaultShopData());
            data = this.playersData.get(uuid);
        }
        return data;
    }

    public void clearPlayerShopData(){
        this.playersData.clear();
    }

    public void clearPlayerShopData(UUID uuid){
        this.playersData.put(uuid,this.getDefaultShopData());
    }

    public void setDefaultShopData(Map<ItemType, ArrayList<ShopSlot>> data){
        this.defaultShopData.clear();
        this.defaultShopData.putAll(data);
    }

    public void replaceDefaultShopData(ItemType type, int index,ShopSlot shopSlot){
        this.defaultShopData.get(type).set(index,shopSlot);
    }

    public void setDefaultShopDataGroupId(ItemType type, int index,int groupId){
        this.defaultShopData.get(type).get(index).setGroupId(groupId);
    }

    public void addDefaultShopDataListenerModule(ItemType type, int index, ListenerModule listenerModule){
        this.defaultShopData.get(type).get(index).addListener(listenerModule);
    }

    public void removeDefaultShopDataListenerModule(ItemType type, int index, String listenerModule){
        this.defaultShopData.get(type).get(index).removeListenerModule(listenerModule);
    }

    public void setDefaultShopDataItemStack(ItemType type, int index, ItemStack itemStack){
        this.defaultShopData.get(type).get(index).itemSupplier = itemStack::copy;
    }

    public void setDefaultShopDataCost(ItemType type, int index, int cost){
        this.defaultShopData.get(type).get(index).setDefaultCost(cost);
    }

    public ShopData getDefaultShopData() {
        Map<ItemType, ArrayList<ShopSlot>> map = new HashMap<>(this.defaultShopData);
        return new ShopData(map,this.startMoney);
    }

    public Map<ItemType, ArrayList<ShopSlot>> getDefaultShopDataMap() {
        return this.defaultShopData;
    }

    public void handleButton(ServerPlayer serverPlayer, ItemType type, int index, ShopAction action) {
        this.getPlayerShopData(serverPlayer.getUUID()).handleButton(serverPlayer,type,index,action);
        this.syncShopData(serverPlayer);
        this.syncShopMoneyData(serverPlayer);
    }
}
