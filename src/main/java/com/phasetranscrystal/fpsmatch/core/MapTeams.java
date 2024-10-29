package com.phasetranscrystal.fpsmatch.core;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.scores.PlayerTeam;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class MapTeams {
    protected final ServerLevel level;
    private final BlockPos defaultSpawnPoints;
    private final Map<String, List<BlockPos>> spawnPoints = new HashMap<>();
    private final Map<String, PlayerTeam> teams = new HashMap<>();
    private final Map<UUID, PlayerTeam> playerTeams = new HashMap<>();

    public MapTeams(ServerLevel level,int teamNum,BlockPos defaultSpawnPoints){
        this.level = level;
        this.defaultSpawnPoints = defaultSpawnPoints;
        this.teamInit(teamNum);
    }

    public boolean checkSpawnPoints(){
        AtomicBoolean check = new AtomicBoolean(true);
        this.teams.values().forEach((t)->{
            if(check.get()){
                List<BlockPos> spawnPoints = this.spawnPoints.getOrDefault(t.getName(),null);
                int p = t.getPlayers().size();
                if(spawnPoints == null) {
                    check.set(false);
                }else{
                    check.set(spawnPoints.size() >= p);
                };
            }
        });
        return check.get();
    }

    public List<BlockPos> getSpawnPointsByTeam(String team){
        return this.spawnPoints.getOrDefault(team,List.of(defaultSpawnPoints));
    }

    public void setTeamsSpawnPoints(){
        if(!checkSpawnPoints()) return;
        Random random = new Random();
        this.playerTeams.forEach(((uuid, playerTeam) -> {
            List<BlockPos> spawner = this.getSpawnPointsByTeam(playerTeam.getName());
            Player player = this.level.getPlayerByUUID(uuid);
            if (player != null){
                int rIndex = random.nextInt(0,spawner.size());
                BlockPos spawnPoint = spawner.get(rIndex);
                spawner.remove(rIndex);
                ((ServerPlayer) player).setRespawnPosition(Level.OVERWORLD,spawnPoint,0f,false,false);
            };
        }));
    }

    public void defineSpawnPoint(String teamName,BlockPos spawn){
        this.spawnPoints.get(teamName).add(spawn);
    }

    public void resetSpawnPoints(String teamName){
        this.spawnPoints.remove(teamName);
    }
    public void resetAllSpawnPoints(){
        this.spawnPoints.clear();
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
        this.spawnPoints.remove(team.getName());
        this.playerTeams.forEach(((uuid, playerTeam) -> {
            if(playerTeam.getName().equals(team.getName())){
                this.playerTeams.remove(uuid);
            }
        }));
    }

    public void joinTeam(String teamName, Player player) {
        leaveTeam(player);
        if(this.teams.containsKey(teamName)) {
            this.playerTeams.put(player.getUUID(),this.teams.get(teamName));
            player.getScoreboard().addPlayerToTeam(player.getScoreboardName(), this.teams.get(teamName));
        }else{
            player.sendSystemMessage(Component.literal("[FPSM] 未找到目标队伍,当前队伍已离队!"));
        }
    }

    public List<PlayerTeam> getTeams(){
        return (List<PlayerTeam>) teams.values();
    }

    public void leaveTeam(Player player){
        this.playerTeams.put(player.getUUID(),null);
        PlayerTeam currentTeam = player.getScoreboard().getPlayersTeam(player.getScoreboardName());
        if (currentTeam != null)  player.getScoreboard().removePlayerFromTeam(player.getScoreboardName(), currentTeam);
    }



}
