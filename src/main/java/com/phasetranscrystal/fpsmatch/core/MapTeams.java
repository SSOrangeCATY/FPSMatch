package com.phasetranscrystal.fpsmatch.core;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
@Mod.EventBusSubscriber(modid = FPSMatch.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class MapTeams {
    protected final ServerLevel level;
    private final SpawnPointData defaultSpawnPoints;
    private final Map<String, List<SpawnPointData>> spawnPoints = new HashMap<>();
    private final Map<String, PlayerTeam> teams = new HashMap<>();
    private final List<UUID> joinedPlayers = new ArrayList<>();
    private final Map<UUID, PlayerTeam> playerTeams = new HashMap<>();
    private final Map<String, Integer> teamPlayerLimits = new HashMap<>();
    private final Map<String,List<UUID>> teamsLiving = new HashMap<>();

    public MapTeams(ServerLevel level,List<String> teamsName ,SpawnPointData defaultSpawnPoints){
        this.level = level;
        this.defaultSpawnPoints = defaultSpawnPoints;
        teamsName.forEach(this::addTeam);
    }

    public boolean checkSpawnPoints(){
        AtomicBoolean check = new AtomicBoolean(true);
        this.teams.values().forEach((t)->{
            if(check.get()){
                List<SpawnPointData> spawnPoints = this.spawnPoints.getOrDefault(t.getName(),null);
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

    public List<SpawnPointData> getSpawnPointsByTeam(String team){
        return this.spawnPoints.getOrDefault(team,List.of(defaultSpawnPoints));
    }

    public void setTeamsSpawnPoints(){
        if(!checkSpawnPoints()) return;
        Random random = new Random();
        this.playerTeams.forEach(((uuid, playerTeam) -> {
            List<SpawnPointData> spawner = this.getSpawnPointsByTeam(playerTeam.getName());
            Player player = this.level.getPlayerByUUID(uuid);
            if (player != null){
                int rIndex = random.nextInt(0,spawner.size());
                SpawnPointData data = spawner.get(rIndex);
                spawner.remove(rIndex);
                ((ServerPlayer) player).setRespawnPosition(data.getDimension(),data.getPosition(),data.getAngle(),data.isForced(),data.isSendMessage());
            };
        }));
    }

    public void defineSpawnPoint(String teamName,SpawnPointData spawn){
        this.spawnPoints.get(teamName).add(spawn);
    }

    public void resetSpawnPoints(String teamName){
        this.spawnPoints.remove(teamName);
    }
    public void resetAllSpawnPoints(){
        this.spawnPoints.clear();
    }

    public void addTeam(String teamName){
        PlayerTeam playerteam = this.level.getScoreboard().getPlayersTeam(teamName);
        this.teams.put(teamName, Objects.requireNonNullElseGet(playerteam, () -> this.level.getScoreboard().addPlayerTeam(teamName)));
    }

    public void delTeam(PlayerTeam team){
        if(!checkTeam(team.getName())) return;
        this.teams.remove(team.getName());
        this.level.getScoreboard().removePlayerTeam(team);
        this.spawnPoints.remove(team.getName());
        this.playerTeams.forEach(((uuid, playerTeam) -> {
            if(playerTeam.getName().equals(team.getName())){
                this.playerTeams.remove(uuid);
            }
        }));
    }

    @Nullable public String getTeamByPlayer(Player player){
        PlayerTeam currentTeam = player.getScoreboard().getPlayersTeam(player.getScoreboardName());
        if(currentTeam != null && this.checkTeam(currentTeam.getName())){
            return currentTeam.getName();
        }
        return null;
    }

    public List<UUID> getJoinedPlayers(){
        return this.joinedPlayers;
    }

    public void playerJoin(Player player){
        this.joinedPlayers.add(player.getUUID());
    }
    public void playerLeave(Player player){
        this.joinedPlayers.remove(player.getUUID());
    };

    public void joinTeam(String teamName, Player player) {
        leaveTeam(player);
        if (checkTeam(teamName) && this.testTeamIsFull(teamName)) {
            this.playerTeams.put(player.getUUID(), this.teams.get(teamName));
            player.getScoreboard().addPlayerToTeam(player.getScoreboardName(), this.teams.get(teamName));
        } else {
            player.sendSystemMessage(Component.literal("[FPSM] 队伍已满或未找到目标队伍，当前队伍已离队!"));
        }
    }

    public boolean checkTeam(String teamName){
        if(this.teams.containsKey(teamName)){
            return true;
        }else{
            this.level.getServer().sendSystemMessage(Component.literal("[FPSM] 不合法的队伍名 -?>" + teamName + " 检查队伍名是否在FPSM中被定义。"));
            return false;
        }
    }

    public void setTeamPlayerLimit(String teamName, int limit) {
        if(checkTeam(teamName)) this.teamPlayerLimits.put(teamName, limit);
    }

    public boolean testTeamIsFull(String teamName){
        if(checkTeam(teamName)) return this.teamPlayerLimits.getOrDefault(teamName, 99) > this.teams.get(teamName).getPlayers().size();
        return false;
    }

    public List<PlayerTeam> getTeams(){
        return (List<PlayerTeam>) teams.values();
    }

    public void leaveTeam(Player player){
        this.playerTeams.remove(player.getUUID());
        PlayerTeam currentTeam = player.getScoreboard().getPlayersTeam(player.getScoreboardName());
        if (currentTeam != null)  player.getScoreboard().removePlayerFromTeam(player.getScoreboardName(), currentTeam);
    }

    public Map<String, List<UUID>> getTeamsLiving() {
        return teamsLiving;
    }

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event){
        if(event.getEntity() instanceof Player player){
            BaseMap map = FPSMCore.getMapByPlayer(player);
            if(map != null){
                map.getMapTeams().getTeamsLiving().get(map.getMapTeams().getTeamByPlayer(player)).remove(player.getUUID());
            }
        }
    }


}
