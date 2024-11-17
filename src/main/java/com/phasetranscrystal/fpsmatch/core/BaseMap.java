package com.phasetranscrystal.fpsmatch.core;

import com.phasetranscrystal.fpsmatch.core.data.BombAreaData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class BaseMap{
    public final String mapName;
    public String gameType = "error";
    public boolean isStart = false;
    private boolean isDebug = false;
    private final ServerLevel serverLevel;
    private MapTeams mapTeams;
    private int isBlasting = 0; // 是否放置炸弹 0 = 未放置 | 1 = 已放置 | 2 = 已拆除
    private boolean isExploded = false; // 炸弹是否爆炸
    private final List<BombAreaData> bombAreaData = new ArrayList<>();
    private String blastTeam;
    private int demolitionStates = 0;


    public BaseMap(ServerLevel serverLevel, String mapName) {
        this.serverLevel = serverLevel;
        this.mapName = mapName;
        this.setMapTeams(new MapTeams(this.getServerLevel(),this.getTeams()));
        this.setShopData();
    }

    public Map<String,Integer> getTeams(){
        Map<String,Integer> teams = new HashMap<>();
        teams.put("teamA",5);
        teams.put("teamB",5);
        setBlastTeam("teamB");
        return teams;
    }

    public final void setMapTeams(MapTeams teams){
        this.mapTeams = teams;
    }

    public final void setBlastTeam(String team){
        this.blastTeam = team;
    }

    public boolean checkCanPlacingBombs(String team){
        if(this.blastTeam == null) this.blastTeam = this.getTeams().keySet().stream().findFirst().get();
        return this.blastTeam.equals(team);
    }


    public boolean checkPlayerIsInBombArea(Player player){
        AtomicBoolean a = new AtomicBoolean(false);
        this.bombAreaData.forEach(area->{
            if(!a.get()) a.set(area.isPlayerInArea(player));
        });
        return a.get();
    }

    public List<BombAreaData> getBombAreaData() {
        return bombAreaData;
    }

    public final void mapTick(){
        checkForVictory();
        tick();
        syncToClient();
    }

   public abstract void syncToClient();

    public void tick(){
    }

    // 检查胜利条件
    public final void checkForVictory() {
        if (this.victoryGoal()) {
            this.victory();
        }
    }

    public abstract void startGame();

    public boolean checkGameHasPlayer(Player player){
        boolean flag = false;
        if(!this.getMapTeams().getJoinedPlayers().contains(player.getUUID()) || getMapTeams().getTeamByPlayer(player) != null){
            flag = true;
        }else if(this.getMapTeams().getJoinedPlayers().contains(player.getUUID())){
            flag = true;
        }
        return flag;
    }
    public  void startNewRound(){}
    public abstract void victory();
    public abstract boolean victoryGoal();
    public abstract void cleanupMap();
    public abstract void resetGame();

    public MapTeams getMapTeams() {
        return mapTeams;
    }

    public ServerLevel getServerLevel() {
        return serverLevel;
    }

    public boolean isDebug() {
        return isDebug;
    }

    public boolean switchDebugMode(){
        this.isDebug = !this.isDebug;
        return this.isDebug;
    }

    public void setShopData(){
    }

    public String getMapName(){
        return mapName;
    }

    public static void syncShopDataToClient(String mapName, ServerPlayer player){
        FPSMShop.syncShopData(mapName,player);
    }

    public void setGameType(String gameType) {
        this.gameType = gameType;
    }

    public String getGameType() {
        return gameType;
    }

    public void putBombAreaData(BombAreaData data){
        this.bombAreaData.add(data);
    }

    public void setBlasting(int blasting) {
        isBlasting = blasting;
    }

    public void setExploded(boolean exploded) {
        isExploded = exploded;
    }

    public int isBlasting() {
        return isBlasting;
    }

    public boolean isExploded() {
        return isExploded;
    }

    public boolean isStart() {
        return isStart;
    }

    public int demolitionStates() {
        return demolitionStates;
    }

    public void setDemolitionStates(int demolitionStates) {
        this.demolitionStates = demolitionStates;
    }
}