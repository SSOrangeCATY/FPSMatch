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
    private final Map<UUID, String> playerTeams = new HashMap<>();
    private final Map<String, Integer> teamPlayerLimits = new HashMap<>();
    private final Map<String,List<UUID>> teamsLiving = new HashMap<>();
    private final Map<UUID, TabData> playerStats = new HashMap<>();
    private final Map<UUID, TabData> playerStatsTemp = new HashMap<>();
    private final Map<UUID, Map<UUID,Float>> livingHurtData = new HashMap<>();
    private final Map<UUID, Integer> mvpData = new HashMap<>();

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
        if(checkSpawnPoints()) {
            Random random = new Random();
            this.playerTeams.forEach(((uuid, playerTeam) -> {
                List<SpawnPointData> spawner = this.getSpawnPointsByTeam(playerTeam);
                Player player = this.level.getPlayerByUUID(uuid);
                if (player != null){
                    int rIndex = random.nextInt(0,spawner.size());
                    SpawnPointData data = spawner.get(rIndex);
                    spawner.remove(rIndex);
                    ((ServerPlayer) player).setRespawnPosition(data.getDimension(),data.getPosition(),data.getAngle(),data.isForced(),data.isSendMessage());
                };
            }));
        }
    }

    public void defineSpawnPoint(String teamName, SpawnPointData spawn){
        List<SpawnPointData> data = this.spawnPoints.getOrDefault(teamName,new ArrayList<>());
        data.add(spawn);
        this.spawnPoints.put(teamName,data);
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
            if(playerTeam.equals(team.getName())){
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
    }

    public void playerLeave(UUID player){
        this.playerTeams.remove(player);
        this.teamsLiving.forEach(((s, uuids) -> {
            if(uuids.contains(player)){
                this.teamsLiving.get(s).remove(player);
            }
        }));
        this.joinedPlayers.remove(player);
    }


    public void joinTeam(String teamName, Player player) {
        leaveTeam(player);
        if (checkTeam(teamName) && this.testTeamIsFull(teamName)) {
            this.playerTeams.put(player.getUUID(), this.teams.get(teamName).toString());
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

    public List<String> getTeamsName(){
        return teams.keySet().stream().toList();
    }

    @Nullable public PlayerTeam getTeamByName(String teamName){
        if(checkTeam(teamName)) return this.teams.get(teamName);
        return null;
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



    public void addHurtData(UUID targetId, UUID attackerId, float damage) {
        Map<UUID, Float> hurtDataMap = livingHurtData.getOrDefault(attackerId, new HashMap<>());
        hurtDataMap.merge(attackerId, damage, Float::sum);
        livingHurtData.put(targetId, hurtDataMap);
    }

    @Nullable public UUID getDamageMvp() {
        Map<UUID, Float> damageMap = new HashMap<>();
        this.livingHurtData.forEach((targetId, attackerDamageMap) -> {
            attackerDamageMap.forEach((attackerId, damage) -> {
                damageMap.merge(attackerId, damage, Float::sum);
            });
        });

        UUID mvpId = null;
        float highestDamage = 0;

        for (Map.Entry<UUID, Float> entry : damageMap.entrySet()) {
            if (mvpId == null || entry.getValue() > highestDamage) {
                mvpId = entry.getKey();
                highestDamage = entry.getValue();
            }
        }

        return mvpId;
    }

    public UUID getGameMvp(){
        UUID mvpId = null;
        int highestScore = 0;
        UUID damageMvpId = this.getDamageMvp();
        for (Map.Entry<UUID, TabData> entry : playerStats.entrySet()) {
            TabData tabData = entry.getValue();
            int kills = tabData.getKills() * 2;
            int assists = tabData.getAssists();
            int score = kills + assists;
            if (entry.getKey().equals(damageMvpId)){
                score += 2;
            }

            if (mvpId == null || score > highestScore) {
                mvpId = entry.getKey();
                highestScore = score;
            }
        }

        return mvpId;
    }

    public void startNewRound() {
        this.resetAllHurtData();
        this.setDonePlayerStatsTemp();
    }

    public UUID getRoundMvpPlayer() {
        UUID mvpId = null;
        int highestScore = 0;
        UUID damageMvpId = this.getDamageMvp();
        TabData Empty = new TabData();

        if (playerStatsTemp.isEmpty()) {
            mvpId = this.getGameMvp();
        }else{
            for (Map.Entry<UUID, TabData> entry : playerStats.entrySet()) {
                TabData tabData = entry.getValue();
                int kills = tabData.getKills() - playerStatsTemp.getOrDefault(entry.getKey(),Empty).getKills();
                int assists = tabData.getAssists() - playerStatsTemp.getOrDefault(entry.getKey(),Empty).getAssists();
                int score = kills * 2 + assists;
                if (entry.getKey().equals(damageMvpId)){
                    score += 2;
                }

                if (mvpId == null || score > highestScore) {
                    mvpId = entry.getKey();
                    highestScore = score;
                }
            }
        }

        if(mvpId != null){
            this.mvpData.put(mvpId,this.mvpData.getOrDefault(mvpId,0) + 1);
        }

        return mvpId;
    }
    public Map<UUID, Map<UUID, Float>> getLivingHurtData() {
        return livingHurtData;
    }

    public void resetAllHurtData() {
        livingHurtData.clear();
    }

    public void setDonePlayerStatsTemp(){
        this.playerStatsTemp.clear();
        this.playerStatsTemp.putAll(this.playerStats);
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
                    map.getMapTeams().updateTabData(from,map.getMapTeams().getTabData(from).addDamage(damage));
                    map.getMapTeams().addHurtData(player.getUUID(),from.getUUID(),damage);
                }
            }
        }
    }
}
