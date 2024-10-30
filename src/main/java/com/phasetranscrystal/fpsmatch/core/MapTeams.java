package com.phasetranscrystal.fpsmatch.core;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointData;
import com.phasetranscrystal.fpsmatch.core.data.TabData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
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
    private final Map<UUID, TabData> playerStats = new HashMap<>();
    private final Map<UUID, Map<UUID,Float>> livingHurtData = new HashMap<>();

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
        this.leaveTeam(player);
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
        this.teamsLiving.forEach(((s, uuids) -> {
            if(uuids.contains(player.getUUID())){
                this.teamsLiving.get(s).remove(player.getUUID());
            }
        }));
        PlayerTeam currentTeam = player.getScoreboard().getPlayersTeam(player.getScoreboardName());
        if (currentTeam != null)  player.getScoreboard().removePlayerFromTeam(player.getScoreboardName(), currentTeam);
    }

    public Map<String, List<UUID>> getTeamsLiving() {
        return teamsLiving;
    }

    public TabData getTabData(Player player) {
        return playerStats.getOrDefault(player.getUUID(), new TabData());
    }

    public void updateTabData(Player player, TabData tabData) {
        playerStats.put(player.getUUID(), tabData);
    }

    public void addHurtData(UUID attackerId, UUID targetId, float damage) {
        Map<UUID, Float> hurtDataMap = livingHurtData.getOrDefault(attackerId, new HashMap<>());
        hurtDataMap.merge(targetId, damage, Float::sum);
        livingHurtData.put(attackerId, hurtDataMap);
    }

    public float getTotalHurtData(UUID attackerId) {
        Map<UUID, Float> hurtDataMap = livingHurtData.getOrDefault(attackerId, new HashMap<>());
        return (float) hurtDataMap.values().stream().mapToDouble(Float::valueOf).sum();
    }

    public Map<UUID, Map<UUID, Float>> getLivingHurtData() {
        return livingHurtData;
    }

    public void resetAllHurtData() {
        livingHurtData.clear();
    }

    public float getHurtData(UUID attackerId, UUID targetId) {
        Map<UUID, Float> hurtDataMap = livingHurtData.getOrDefault(attackerId, new HashMap<>());
        return hurtDataMap.getOrDefault(targetId, 0.0f);
    }

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event){
        if(event.getEntity() instanceof Player player){
            BaseMap map = FPSMCore.getMapByPlayer(player);
            if(map != null){
                MapTeams teams = map.getMapTeams();
                String playerTeam = teams.getTeamByPlayer(player);
                if(playerTeam != null){
                    teams.getTeamsLiving().get(playerTeam).remove(player.getUUID());
                    teams.updateTabData(player, teams.getTabData(player).addDeaths());
                }

                if(event.getSource().getEntity() instanceof Player killer){
                    if(teams.getTeamByPlayer(killer) != null){ // && !teams.getTeamByPlayer(killer).equals(playerTeam)
                        teams.updateTabData(killer, teams.getTabData(killer).addKills());
                        Map<UUID, Float> hurtDataMap = teams.getLivingHurtData().get(player.getUUID());
                        if(hurtDataMap != null && !hurtDataMap.isEmpty()){

                            List<Map.Entry<UUID, Float>> sortedDamageEntries = hurtDataMap.entrySet().stream()
                                    .filter(entry -> !entry.getKey().equals(killer.getUUID()) && entry.getValue() > 4)
                                    .sorted(Map.Entry.<UUID, Float>comparingByValue().reversed())
                                    .limit(2)
                                    .toList();

                            for (Map.Entry<UUID, Float> sortedDamageEntry : sortedDamageEntries) {
                                UUID assistId = sortedDamageEntry.getKey();
                                Player assist = map.getServerLevel().getPlayerByUUID(assistId);
                                if (assist != null) {
                                    teams.updateTabData(assist, teams.getTabData(assist).addAssist());
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    @SubscribeEvent
    public void onLivingHurtEvent(LivingHurtEvent event) {
        if (event.getEntity() instanceof Player player) {
            DamageSource damageSource = event.getSource();
            if(damageSource.getEntity() instanceof Player from){
                BaseMap map = FPSMCore.getMapByPlayer(player);
                if (map != null && map.checkGameHasPlayer(player) && map.checkGameHasPlayer(from)) {
                    float damage = event.getAmount();
                    map.getMapTeams().addHurtData(player.getUUID(),from.getUUID(),damage);
                }
            }
        }
    }
}
