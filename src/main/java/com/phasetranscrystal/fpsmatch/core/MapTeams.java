package com.phasetranscrystal.fpsmatch.core;

import net.minecraft.client.sounds.AudioStream;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.PlayerTeam;

import java.util.*;

public class MapTeams {
    protected final ServerLevel level;
    private BlockPos defaultSpawnPoints;
    private final Map<String, List<BlockPos>> spawnPoints = new HashMap<>();
    private final Map<String, PlayerTeam> teams = new HashMap<>();

    public MapTeams(ServerLevel level,int teamNum){
        this.level = level;
        this.teamInit(teamNum);
    }

    public void defineSpawnPoint(String teamName,BlockPos spawn){
        this.spawnPoints.get(teamName).add(spawn);
    }

    public List<BlockPos> getSpawnPointsByTeam(PlayerTeam team){
        return this.spawnPoints.getOrDefault(team.getName(),List.of(defaultSpawnPoints));
    }

    public void resetSpawnPoints(String teamName){
        this.spawnPoints.remove(teamName);
    }

    public List<String> renameTeams(int teamNum){
        //TODO rename Teams
        List<String> teams = new ArrayList<>();
        for (int i = 1;i<=teamNum;i++) {
            teams.add("GameTeam_"+ i);
        }
        return teams;
    }

    public void teamInit(int teamNum){
        this.renameTeams(teamNum).forEach(this::addTeam);
    }

    public void addTeam(String teamName){
        PlayerTeam playerteam = this.level.getScoreboard().getPlayersTeam(teamName);
        this.teams.put(teamName, Objects.requireNonNullElseGet(playerteam, () -> this.level.getScoreboard().addPlayerTeam(teamName)));
    }

    public void delTeam(PlayerTeam team){
        this.teams.remove(team.getName());
        this.level.getScoreboard().removePlayerTeam(team);
    }

    public void joinTeam(String teamName, Player player) {
        leaveTeam(player);
        if(this.teams.containsKey(teamName)) {
            player.getScoreboard().addPlayerToTeam(player.getScoreboardName(), this.teams.get(teamName));
        }else{
            player.sendSystemMessage(Component.literal("[FPSM] 未找到目标队伍,当前队伍已离队!"));
        }
    }

    public List<PlayerTeam> getTeams(){
        return (List<PlayerTeam>) teams.values();
    }

    public void leaveTeam(Player player){
        PlayerTeam currentTeam = player.getScoreboard().getPlayersTeam(player.getScoreboardName());
        if (currentTeam != null)  player.getScoreboard().removePlayerFromTeam(player.getScoreboardName(), currentTeam);
    }



}
