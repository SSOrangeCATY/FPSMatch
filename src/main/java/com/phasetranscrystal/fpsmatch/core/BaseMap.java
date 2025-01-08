package com.phasetranscrystal.fpsmatch.core;

import com.phasetranscrystal.fpsmatch.core.data.AreaData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.*;

public abstract class BaseMap {
    public final String mapName;
    public String gameType = "error";
    public boolean isStart = false;
    private boolean isDebug = false;
    private final ServerLevel serverLevel;
    private MapTeams mapTeams;
    public final AreaData mapArea;

    public BaseMap(ServerLevel serverLevel, String mapName, AreaData areaData) {
        this.serverLevel = serverLevel;
        this.mapName = mapName;
        this.mapArea = areaData;
    }

    public Map<String,Integer> getTeams(){
        Map<String,Integer> teams = new HashMap<>();
        teams.put("teamA",5);
        teams.put("teamB",5);
        return teams;
    }

    public final void setMapTeams(MapTeams teams){
        this.mapTeams = teams;
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
        return this.getMapTeams().getJoinedPlayers().contains(player.getUUID());
    }
    public  void startNewRound(){}
    public abstract void victory();
    public abstract boolean victoryGoal();
    public void cleanupMap(){
    }
    public abstract void resetGame();

    public MapTeams getMapTeams() {
        return mapTeams;
    }

    public void joinTeam(String teamName, ServerPlayer player) {
        this.getMapTeams().joinTeam(teamName,player);
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

    public String getMapName(){
        return mapName;
    }

    public void setGameType(String gameType) {
        this.gameType = gameType;
        this.setMapTeams(new MapTeams(this.getServerLevel(),this.getTeams(),this));
    }

    public String getGameType() {
        return gameType;
    }

    public boolean isStart() {
        return isStart;
    }
    public boolean equals(Object object){
        if(object instanceof BaseMap map){
            return map.getMapName().equals(this.getMapName()) && map.getGameType().equals(this.getGameType());
        }else{
            return false;
        }
    }

    public AreaData getMapArea() {
        return mapArea;
    }

}