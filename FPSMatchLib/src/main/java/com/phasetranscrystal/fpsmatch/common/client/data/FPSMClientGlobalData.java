package com.phasetranscrystal.fpsmatch.common.client.data;

import com.google.common.collect.Maps;
import com.mojang.datafixers.util.Pair;
import com.phasetranscrystal.fpsmatch.common.client.shop.ClientShopSlot;
import com.phasetranscrystal.fpsmatch.core.data.PlayerData;
import com.phasetranscrystal.fpsmatch.core.team.ClientTeam;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import org.checkerframework.checker.units.qual.C;

import java.util.*;

public class FPSMClientGlobalData {
    private String currentMap = "fpsm_none";
    private String currentGameType = "none";
    private String currentTeam = "none";

    private final Map<String, List<ClientShopSlot>> clientShopData = Maps.newHashMap();
    private final Map<UUID,Integer> playersMoney = new HashMap<>();
    public final Map<String, ClientTeam> clientTeamData = Maps.newHashMap();

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

    public Optional<ClientTeam> getTeamByName(String teamName) {
        return Optional.ofNullable(clientTeamData.getOrDefault(teamName,null));
    }

    public Optional<ClientTeam> getTeamByUUID(UUID uuid){
        for (ClientTeam team : clientTeamData.values()) {
            if(team.hasPlayer(uuid)) return Optional.of(team);
        }
        return Optional.empty();
    }

    public void addTeam(ClientTeam team) {
        clientTeamData.put(team.name, team);
    }

    public void setTabData(String teamName, UUID uuid,PlayerData data){
        if(clientTeamData.containsKey(teamName)) {
            clientTeamData.get(teamName).setPlayerData(uuid,data);
        }else{
            throw new IllegalArgumentException("Team " + teamName + " does not exist");
        }
    }

    public Optional<Pair<String, PlayerData>> getFullTabPlayerData(UUID uuid){
        for (Map.Entry<String, ClientTeam> teamEntry : clientTeamData.entrySet()) {
            String teamName = teamEntry.getKey();
            ClientTeam team = teamEntry.getValue();
            Optional<PlayerData> playerData = team.getPlayerData(uuid);
            if (playerData.isPresent()) {
                return Optional.of(Pair.of(teamName, playerData.get()));
            }
        }
        return Optional.empty();
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
        LocalPlayer player = Minecraft.getInstance().player;
        if(player == null) return false;

        return currentTeam.equals("spectator") || player.isSpectator();
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

    public void removePlayer(UUID uuid){
        clientTeamData.values().forEach(team -> team.delPlayer(uuid));
        playersMoney.remove(uuid);
    }

    public void reset(){
        this.currentMap = "fpsm_none";
        this.currentGameType = "none";
        this.currentTeam = "none";
        this.playersMoney.clear();
        this.clientShopData.clear();
        this.clientTeamData.clear();
    }

    public int getKills(UUID uuid) {
        return this.getPlayerTabData(uuid)
                .map(PlayerData::_kills)
                .orElse(0);
    }

    public int getHeadshots(UUID uuid) {
        return this.getPlayerTabData(uuid)
                .map(PlayerData::getHeadshotKills)
                .orElse(0);
    }

    public boolean isLiving(UUID uuid){
        return this.getPlayerTabData(uuid)
                .map(PlayerData::isLivingNoOnlineCheck)
                .orElse(false);
    }

    public int getMvpCount(UUID uuid){
        return this.getPlayerTabData(uuid)
                .map(PlayerData::getMvpCount)
                .orElse(0);
    }

    public int getDeaths(UUID uuid){
        return this.getPlayerTabData(uuid)
                .map(PlayerData::getDeaths)
                .orElse(0);
    }

    public float getDamages(UUID uuid){
        return this.getPlayerTabData(uuid)
                .map(PlayerData::getDamage)
                .orElse(0F);
    }

    public int getAssists(UUID uuid){
        return this.getPlayerTabData(uuid)
                .map(PlayerData::getAssists)
                .orElse(0);
    }

    public boolean isSameTeam(Player p1, Player p2) {
        String team1 = this.getPlayerTeam(p1.getUUID()).orElse(null);
        String team2 = this.getPlayerTeam(p2.getUUID()).orElse(null);
        return team1 != null && team1.equals(team2);
    }

}
