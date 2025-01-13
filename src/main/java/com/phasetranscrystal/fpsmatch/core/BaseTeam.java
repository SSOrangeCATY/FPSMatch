package com.phasetranscrystal.fpsmatch.core;

import com.phasetranscrystal.fpsmatch.core.data.PlayerData;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointData;
import com.phasetranscrystal.fpsmatch.core.data.TabData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.scores.PlayerTeam;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

public class BaseTeam {
    public final String name;
    public final String gameType;
    public final String mapName;
    private final int playerLimit;
    private final PlayerTeam playerTeam;
    private int scores = 0;
    private final Map<UUID, PlayerData> players = new HashMap<>();
    private final List<SpawnPointData> spawnPointsData = new ArrayList<>();
    public final List<UUID> teamUnableToSwitch = new ArrayList<>();
    private int loseStreak;
    private int compensationFactor;
    private int pauseTime = 0;
    private boolean needPause = false;


    public BaseTeam(String gameType,String mapName,String name, int playerLimit, PlayerTeam playerTeam) {
        this.gameType = gameType;
        this.mapName = mapName;
        this.name = name;
        this.playerLimit = playerLimit;
        this.playerTeam = playerTeam;
    }

    public void join(ServerPlayer player){
        player.getScoreboard().addPlayerToTeam(player.getScoreboardName(), playerTeam);
        this.createPlayerData(player);
    }

    public void leave(ServerPlayer player){
        if(this.hasPlayer(player.getUUID())){
            this.players.remove(player.getUUID());
            player.getScoreboard().removePlayerFromTeam(player.getScoreboardName());
        }
    }

    public void delPlayer(UUID uuid){
        this.players.remove(uuid);
    }

    public void createPlayerData(ServerPlayer player){
        this.players.put(player.getUUID(),new PlayerData(player.getUUID()));
    }

    public void handleOffline(ServerPlayer player){
        UUID uuid = player.getUUID();
        PlayerData playerData = players.get(uuid);
        playerData.setLiving(false);
        playerData.setOffline(true);
        playerData.getTabDataTemp().addDeaths();
        player.heal(player.getMaxHealth());
        player.setGameMode(GameType.SPECTATOR);
    }

    public void resetLiving(){
        this.players.values().forEach((data)->{
            if(!data.isOffline()){
                data.setLiving(true);
            }
        });
    }

    @Nullable
    public TabData getPlayerTabData(UUID uuid){
        if(this.players.containsKey(uuid)){
            return this.players.get(uuid).getTabData();
        }
        return null;
    }

    @Nullable
    public PlayerData getPlayerData(UUID uuid){
        if(this.players.containsKey(uuid)){
            return this.players.get(uuid);
        }
        return null;
    }

    public List<PlayerData> getPlayersData(){
        return this.players.values().stream().toList();
    }

    public List<TabData> getPlayersTabData(){
        List<TabData> tabDataList = new ArrayList<>();
        this.players.values().forEach((data)-> tabDataList.add(data.getTabData()));
        return tabDataList;
    }

    public List<TabData> getPlayersTabDataTemp(){
        List<TabData> tabDataList = new ArrayList<>();
        this.players.values().forEach((data)-> tabDataList.add(data.getTabDataTemp()));
        return tabDataList;
    }

    public List<UUID> getPlayerList(){
        return this.players.keySet().stream().toList();
    }

    public List<UUID> getOfflinePlayers() {
        List<UUID> offlinePlayers = new ArrayList<>();
        this.players.values().forEach((data)->{
            if(data.isOffline()){
                offlinePlayers.add(data.getOwner());
            }
        });
        return offlinePlayers;
    }

    public List<UUID> getLivingPlayers(){
        List<UUID> uuids = new ArrayList<>();
        this.players.values().forEach((data)->{
            if(data.getTabData().isLiving()){
                uuids.add(data.getOwner());
            }
        });
        return uuids;
    }

    public boolean hasPlayer(UUID uuid){
        return this.players.containsKey(uuid);
    }

    public void randomSpawnPoints() {
        Random random = new Random();

        if (this.spawnPointsData.isEmpty()) {
            throw new RuntimeException(new IllegalStateException("No spawn points available."));
        }

        if (this.spawnPointsData.size() < this.players.size()) {
            throw new RuntimeException(new IllegalStateException("Not enough spawn points for all players."));
        }

        List<UUID> playerUUIDs = new ArrayList<>(this.players.keySet());
        List<SpawnPointData> list = new ArrayList<>(this.spawnPointsData);
        for (UUID playerUUID : playerUUIDs) {
            if(list.isEmpty()){
                // 出生点不够多就会这样
                list.addAll(this.spawnPointsData);
            }
            if(this.spawnPointsData.isEmpty()){
                return;
            }
            int randomIndex = random.nextInt(list.size());
            SpawnPointData spawnPoint = list.get(randomIndex);
            list.remove(randomIndex);
            this.players.get(playerUUID).setSpawnPointsData(spawnPoint);
        }
    }

    public void addSpawnPointData(@Nonnull SpawnPointData data){
        this.spawnPointsData.add(data);
    }

    public void addAllSpawnPointData(@Nonnull List<SpawnPointData> data){
        this.spawnPointsData.addAll(data);
    }

    public void resetSpawnPointData(){
        this.spawnPointsData.clear();
    }

    public List<SpawnPointData> getSpawnPointsData(){
        return spawnPointsData;
    }
    public int getPlayerLimit() {
        return playerLimit;
    }

    public int getRemainingLimit(){
        return playerLimit - this.players.size();
    }

    public PlayerTeam getPlayerTeam() {
        return playerTeam;
    }

    public int getScores() {
        return scores;
    }

    public void setScores(int scores) {
        this.scores = scores;
    }


    public String getFixedName() {
        return this.gameType+"_"+this.mapName+"_"+this.name;
    }

    // 获取连败次数
    public int getLoseStreak() {
        return loseStreak;
    }

    // 设置连败次数
    public void setLoseStreak(int loseStreak) {
        this.loseStreak = loseStreak;
    }

    // 获取战败补偿因数
    public int getCompensationFactor() {
        return compensationFactor;
    }

    // 设置战败补偿因数
    public void setCompensationFactor(int compensationFactor) {
        this.compensationFactor = Math.max(0, Math.min(compensationFactor, 4));
    }

    public void setAllSpawnPointData(List<SpawnPointData> spawnPointsData) {
        this.spawnPointsData.clear();
        this.spawnPointsData.addAll(spawnPointsData);
    }

    public Map<UUID, PlayerData> getPlayers(){
        return this.players;
    }

    public int getPlayerCount(){
        return this.players.size();
    }

    public void resetAllPlayers(ServerLevel serverLevel, Map<UUID, PlayerData> players){
        this.players.clear();
        this.players.putAll(players);

        players.keySet().forEach(uuid -> {
            ServerPlayer serverPlayer = (ServerPlayer) serverLevel.getPlayerByUUID(uuid);
            if(serverPlayer != null){
                serverPlayer.getScoreboard().addPlayerToTeam(serverPlayer.getScoreboardName(), this.getPlayerTeam());
            }else{
                teamUnableToSwitch.add(uuid);
            }
        });
    }

    public void addPause(){
        if(pauseTime < 2 && !needPause){
            needPause = true;
            pauseTime++;
        }
    }

    public boolean canPause(){
        return pauseTime < 2 && !needPause;
    }

    public void setPauseTime(int t){
        this.pauseTime = t;
    }

    public void resetPauseIfNeed(){
        if(this.needPause){
            this.needPause = false;
            this.pauseTime--;
        }
    }

    public void setNeedPause(boolean needPause){
        this.needPause = needPause;
    }

    public boolean needPause(){
        return needPause;
    }

    public int getPauseTime(){
        return pauseTime;
    }

}
