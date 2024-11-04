package com.phasetranscrystal.fpsmatch.core;

import com.phasetranscrystal.fpsmatch.core.data.SpawnPointData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

import java.util.List;

public abstract class BaseMap {
    private boolean isDebug = false;
    private final ServerLevel serverLevel;
    private final String gameType;
    private final MapTeams mapTeams;

    public BaseMap(ServerLevel serverLevel, List<String> teams, String gameType) {
        this.serverLevel = serverLevel;
        this.gameType = gameType;
        this.mapTeams = new MapTeams(serverLevel,teams);
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
            this.getMapTeams().playerJoin(player);
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

    public String getType(){
        return this.gameType;
    };

    public boolean isDebug() {
        return isDebug;
    }

    public boolean switchDebugMode(){
        this.isDebug = !this.isDebug;
        return this.isDebug;
    }
}