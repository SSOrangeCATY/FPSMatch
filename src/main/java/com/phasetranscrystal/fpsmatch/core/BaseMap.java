package com.phasetranscrystal.fpsmatch.core;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public abstract class BaseMap<T extends BaseGame> {
    private final ServerLevel serverLevel;
    private final Round round;
    private final MapTeams mapTeams;
    private final List<UUID> joinedPlayers = new ArrayList<>();

    public BaseMap(ServerLevel serverLevel, int teamNum) {
        this.serverLevel = serverLevel;
        this.mapTeams = new MapTeams(serverLevel,teamNum);
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

    public BaseMap(ServerLevel serverLevel, MapTeams teams, Round round) {
        this.serverLevel = serverLevel;
        this.mapTeams = teams;
        this.round = round;
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