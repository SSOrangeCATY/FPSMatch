package com.phasetranscrystal.fpsmatch.core;

import com.phasetranscrystal.fpsmatch.core.data.SpawnPointData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import java.util.List;

public abstract class BaseMap {
    private final ServerLevel serverLevel;
    private final SpawnPointData spawnPoint;
    private final MapTeams mapTeams;

    public BaseMap(ServerLevel serverLevel, List<String> teams, SpawnPointData spawnPoint) {
        this.serverLevel = serverLevel;
        this.spawnPoint = spawnPoint;
        this.mapTeams = new MapTeams(serverLevel,teams,spawnPoint);
    }

    public BaseMap(ServerLevel serverLevel, MapTeams teams, SpawnPointData spawnPoint) {
        this.serverLevel = serverLevel;
        this.mapTeams = teams;
        this.spawnPoint = spawnPoint;
    }

    public final void mapTick(){
        checkForVictory();
        tick();
    }

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
        if(!this.getMapTeams().getJoinedPlayers().contains(player.getUUID()) && getMapTeams().getTeamByPlayer(player) != null){
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

    public SpawnPointData getSpawnPoint() {
        return spawnPoint;
    }
}