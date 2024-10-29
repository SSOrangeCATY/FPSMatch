package com.phasetranscrystal.fpsmatch.core;

import com.phasetranscrystal.fpsmatch.core.data.SpawnPointData;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public abstract class BaseMap {
    private final ServerLevel serverLevel;
    private final SpawnPointData spawnPoint;
    private final Round round;
    private final MapTeams mapTeams;
    private final List<UUID> joinedPlayers = new ArrayList<>();
    private final ResourceKey<Level> levelResourceKey = Level.OVERWORLD;

    public BaseMap(ServerLevel serverLevel, int teamNum, SpawnPointData spawnPoint) {
        this.serverLevel = serverLevel;
        this.spawnPoint = spawnPoint;
        this.mapTeams = new MapTeams(serverLevel,teamNum,spawnPoint);
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

    public abstract void victory();

    public abstract boolean victoryGoal();

    public void playerJoin(ServerPlayer player){
        this.joinedPlayers.add(player.getUUID());
    };

    public void playerLeave(ServerPlayer player){
        this.joinedPlayers.remove(player.getUUID());
    };

    public boolean checkGameHasPlayer(ServerPlayer player){
        return this.joinedPlayers.contains(player.getUUID());
    }

    public abstract void initializeMap();

    public abstract void cleanupMap();
}