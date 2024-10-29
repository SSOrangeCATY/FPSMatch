package com.phasetranscrystal.fpsmatch.core;

import com.phasetranscrystal.fpsmatch.core.data.SpawnPointData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import java.util.List;

public abstract class BaseMap {
    private final ServerLevel serverLevel;
    private final SpawnPointData spawnPoint;
    private final Round round;
    private final MapTeams mapTeams;

    public BaseMap(ServerLevel serverLevel, List<String> teams, SpawnPointData spawnPoint) {
        this.serverLevel = serverLevel;
        this.spawnPoint = spawnPoint;
        this.mapTeams = new MapTeams(serverLevel,teams,spawnPoint);
        this.round = new Round(false,-1) {
            @Override
            public boolean shouldEndCurrentRound() {
                return false;
            }

            @Override
            public boolean isVictory() {
                return false;
            }

            @Override
            public void endMatch() {

            }
        };
    }

    public BaseMap(ServerLevel serverLevel, MapTeams teams, Round round, SpawnPointData spawnPoint) {
        this.serverLevel = serverLevel;
        this.mapTeams = teams;
        this.round = round;
        this.spawnPoint = spawnPoint;
    }

    public final void mapTick(){
        checkForVictory();
        tick();
    }

    public void tick(){
        if (this.round.isHasRound() && this.round.shouldEndCurrentRound()) {
            this.round.endCurrentRound();
        }
    }

    // 检查胜利条件
    public final void checkForVictory() {
        if (this.victoryGoal()) {
            this.victory();
        }
    }

    public boolean checkGameHasPlayer(Player player){
        return this.mapTeams.getJoinedPlayers().contains(player.getUUID());
    }

    public abstract void victory();

    public abstract boolean victoryGoal();

    public abstract void initializeMap();

    public abstract void cleanupMap();

    public MapTeams getMapTeams() {
        return mapTeams;
    }

    public Round getRound() {
        return round;
    }

    public ServerLevel getServerLevel() {
        return serverLevel;
    }

    public SpawnPointData getSpawnPoint() {
        return spawnPoint;
    }
}