package com.phasetranscrystal.fpsmatch.common.client.data;

import com.google.common.collect.Maps;
import com.mojang.datafixers.util.Pair;
import com.phasetranscrystal.fpsmatch.common.client.shop.ClientShopSlot;
import com.phasetranscrystal.fpsmatch.core.data.PlayerData;

import java.util.*;

public class FPSMClientGlobalData {
    private String currentMap = "fpsm_none";
    private String currentGameType = "none";
    private String currentTeam = "none";

    private final Map<String, List<ClientShopSlot>> clientShopData = Maps.newHashMap();
    public final Map<UUID, Pair<String, PlayerData>> tabData = new HashMap<>();
    private final Map<UUID,Integer> playersMoney = new HashMap<>();

    /**
     * 获取指定类型和索引的商店槽位数据
     * @param type 商店类型
     * @param index 槽位索引
     * @return 对应的商店槽位数据，如果索引无效返回空槽位
     */
    public ClientShopSlot getSlotData(String type, int index) {
        if (index < 0) {
            return ClientShopSlot.empty();
        }

        List<ClientShopSlot> shopSlots = clientShopData.computeIfAbsent(type, k -> new ArrayList<>());

        while (shopSlots.size() <= index) {
            shopSlots.add(ClientShopSlot.empty());
        }

        return shopSlots.get(index);
    }

    /**
     * 安全获取槽位数据，不会自动填充空槽位
     */
    public Optional<ClientShopSlot> getSlotDataIfPresent(String type, int index) {
        if (index < 0 || !clientShopData.containsKey(type)) {
            return Optional.empty();
        }
        List<ClientShopSlot> slots = clientShopData.get(type);
        return index < slots.size() ? Optional.of(slots.get(index)) : Optional.empty();
    }

    public void setTabData(UUID uuid ,String team ,PlayerData data){
        tabData.put(uuid, new Pair<>(team,data));
    }

    public Optional<Pair<String, PlayerData>> getFullTabPlayerData(UUID uuid){
        return Optional.ofNullable(tabData.get(uuid));
    }

    public Optional<String> getPlayerTeam(UUID uuid){
        return getFullTabPlayerData(uuid).map(Pair::getFirst);
    }

    public Optional<PlayerData> getPlayerTabData(UUID uuid){
        return getFullTabPlayerData(uuid).map(Pair::getSecond);
    }

    public void setPlayersMoney(UUID uuid, int money){
        playersMoney.put(uuid,money);
    }

    public int getPlayerMoney(UUID uuid){
        return playersMoney.getOrDefault(uuid,0);
    }

    public String getCurrentMap() {
        return currentMap;
    }

    public void setCurrentMap(String currentMap) {
        this.currentMap = currentMap;
    }

    public String getCurrentGameType() {
        return currentGameType;
    }

    public void setCurrentGameType(String currentGameType) {
        this.currentGameType = currentGameType;
    }

    public String getCurrentTeam() {
        return currentTeam;
    }

    public boolean isSpectator(){
        return currentTeam.equals("spectator");
    }

    public void setCurrentTeam(String currentTeam) {
        this.currentTeam = currentTeam;
    }

    public boolean equalsTeam(String team){
        return currentTeam.equals(team);
    }

    public boolean equalsMap(String map){
        return currentMap.equals(map);
    }

    public boolean equalsGame(String type){
        return currentGameType.equals(type);
    }

    public void removeTabData(UUID uuid){
        tabData.remove(uuid);
    }

    public void reset(){
        this.currentMap = "fpsm_none";
        this.currentGameType = "none";
        this.currentTeam = "none";
        this.playersMoney.clear();
        this.clientShopData.clear();
        this.tabData.clear();
    }
}
