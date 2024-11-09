package com.phasetranscrystal.fpsmatch.core;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.core.data.PlayerData;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointData;
import com.phasetranscrystal.fpsmatch.core.data.TabData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Mod.EventBusSubscriber(modid = FPSMatch.MODID)
public class MapTeams {
    protected final ServerLevel level;
    private final Map<String,BaseTeam> teams = new HashMap<>();
    public MapTeams(ServerLevel level,Map<String,Integer> team){
        this.level = level;
        team.forEach(this::addTeam);
    }

    @Nullable
    public List<SpawnPointData> getSpawnPointsByTeam(String team){
        BaseTeam t = this.teams.getOrDefault(team,null);
        if(t == null) return null;
        return t.getSpawnPointsData();
    }

    public void setTeamsSpawnPoints(){
            this.teams.forEach(((s, t) -> {
                t.randomSpawnPoints();
            }));
    }

    public void defineSpawnPoint(String teamName, SpawnPointData data) {
        BaseTeam team = this.teams.getOrDefault(teamName, null);
        if (team == null) return;
        team.addSpawnPointData(data);
    }

    public void resetSpawnPoints(String teamName){
        BaseTeam team = this.teams.getOrDefault(teamName, null);
        if (team == null) return;
        team.resetSpawnPointData();
    }

    public void resetAllSpawnPoints(){
        this.teams.forEach((s,t)->{
            t.resetSpawnPointData();
        });
    }

    public void addTeam(String teamName,int limit){
        PlayerTeam playerteam = Objects.requireNonNullElseGet(this.level.getScoreboard().getPlayersTeam(teamName), () -> this.level.getScoreboard().addPlayerTeam(teamName));
        this.teams.put(teamName, new BaseTeam(teamName,limit,playerteam));
    }

    public void delTeam(PlayerTeam team){
        if(!checkTeam(team.getName())) return;
        this.teams.remove(team.getName());
        this.level.getScoreboard().removePlayerTeam(team);
    }

    @Nullable public BaseTeam getTeamByPlayer(Player player){
        PlayerTeam currentTeam = player.getScoreboard().getPlayersTeam(player.getScoreboardName());
        if(currentTeam != null && this.checkTeam(currentTeam.getName())){
            return this.teams.getOrDefault(currentTeam.getName(),null);
        }
        return null;
    }

    public List<UUID> getJoinedPlayers(){
        List<UUID> uuids = new ArrayList<>();
        this.teams.values().forEach((t)->{
            uuids.addAll(t.getPlayers());
        });
        return uuids;
    }

    public void resetLivingPlayers(){
        this.teams.values().forEach(BaseTeam::resetLiving);
    }

    public void playerJoin(ServerPlayer player,String teamName){
        BaseTeam team = this.teams.getOrDefault(teamName,null);
        if(team == null) return;
        team.join(player);
    }

    public void joinTeam(String teamName, ServerPlayer player) {
        leaveTeam(player);
        if (checkTeam(teamName) && this.testTeamIsFull(teamName)) {
            if(!this.getJoinedPlayers().contains(player.getUUID())) this.playerJoin(player,teamName);
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

    public boolean testTeamIsFull(String teamName){

        return false;
    }
    public List<BaseTeam> getTeams(){
        return (List<BaseTeam>) teams.values();
    }

    public List<String> getTeamsName(){
        return teams.keySet().stream().toList();
    }

    @Nullable public BaseTeam getTeamByName(String teamName){
        if(checkTeam(teamName)) return this.teams.get(teamName);
        return null;
    }

    public void reset(){
        this.setDonePlayerStatsTemp();
        this.resetAllHurtData();
        this.resetLivingPlayers();
    }

    public void leaveTeam(ServerPlayer player){
        this.teams.values().forEach((t)->{
            t.leave(player);
        });
    }


    public Map<String, List<UUID>> getTeamsLiving() {
        Map<String, List<UUID>> teamsLiving = new HashMap<>();
        teams.forEach((s,t)->{
            teamsLiving.put(s,t.getLivingPlayers());
        });
        return teamsLiving;
    }

    @Nullable
    public TabData getTabData(UUID player) {
        AtomicReference<TabData> data = new AtomicReference<>();
        teams.values().forEach((team)->{
            if (team.hasPlayer(player)){
                data.set(team.getPlayerTabData(player));
            }
        });
        return data.get();
    }


    @Nullable
    public TabData getTabData(Player player) {
        BaseTeam team = getTeamByPlayer(player);
        if(team != null){
            if (team.hasPlayer(player.getUUID())){
                return team.getPlayerTabData(player.getUUID());
            }
        }
        return null;
    }

    public List<UUID> getSameTeamPlayerUUIDs(Player player){
        BaseTeam team = getTeamByPlayer(player);
        List<UUID> uuids = new ArrayList<>();
        if(team != null){
            if (team.hasPlayer(player.getUUID())){
                uuids.addAll(team.getPlayers());
            }
        }
        return uuids;
    }


    public void addHurtData(Player attackerId ,UUID targetId, float damage) {
        BaseTeam team = getTeamByPlayer(attackerId);
        if(team != null) {
           TabData data = team.getPlayerTabData(attackerId.getUUID());
            if (data != null) {
                data.addDamageData(targetId,damage);
            }
        }
    }

    @Nullable public UUID getDamageMvp() {
        Map<UUID, Float> damageMap = new HashMap<>();


        this.getLivingHurtData().forEach((attackerId, attackerDamageMap) -> {
            attackerDamageMap.forEach((targetId, damage) -> {
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


    public Map<UUID,TabData> getAllTabData(){
        Map<UUID,TabData> data = new HashMap<>();
        this.teams.forEach((s,t)->{
            t.getPlayersTabData().forEach((tab)->{
                data.put(tab.getOwner(),tab);
            });
        });
        return data;
    }

    public UUID getGameMvp(){
        UUID mvpId = null;
        int highestScore = 0;
        UUID damageMvpId = this.getDamageMvp();
        for (Map.Entry<UUID, TabData> entry : this.getAllTabData().entrySet()) {
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

    public boolean isFirstRound(){
        AtomicInteger flag = new AtomicInteger();
        teams.values().forEach((team)->{
            flag.addAndGet(team.getScores());
        });
        return flag.get() == 0;
    }

    public UUID getRoundMvpPlayer(String winnerTeam) {
        UUID mvpId = null;
        int highestScore = 0;
        UUID damageMvpId = this.getDamageMvp();
        BaseTeam team = teams.getOrDefault(winnerTeam,null);
        if (team == null) return null;

        if (isFirstRound()) {
            mvpId = this.getGameMvp();
        }else{
            for (PlayerData data : team.getPlayersData()) {
                int kills = data.getTabData().getKills() - data.getTabDataTemp().getKills();
                int assists = data.getTabData().getAssists() - data.getTabDataTemp().getAssists();
                int score = kills * 2 + assists;
                if (data.getOwner().equals(damageMvpId)){
                    score += 2;
                }

                if (mvpId == null || score > highestScore) {
                    mvpId = data.getOwner();
                    highestScore = score;
                }
            }
        }

        if(mvpId != null){
            Objects.requireNonNull(team.getPlayerData(mvpId)).getTabData().addMvpCount(1);
        }

        return mvpId;
    }
    public Map<UUID, Map<UUID, Float>> getLivingHurtData() {
        Map<UUID,Map<UUID,Float>> hurtData = new HashMap<>();
        teams.values().forEach((t)->{
            t.getPlayersTabData().forEach((data)->{
                hurtData.put(data.getOwner(),data.getDamageData());
            });
        });
        return hurtData;
    }

    public void resetAllHurtData() {
        this.teams.values().forEach((t)->{
            t.getPlayersTabData().forEach(TabData::clearDamageData);
        });
    }

    public void setDonePlayerStatsTemp(){
        this.teams.values().forEach((t)->{
            t.getPlayersTabData().forEach(tabData -> {
            });
        });
    }


    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event){
         if(event.getEntity() instanceof ServerPlayer player){
            BaseMap map = FPSMCore.getMapByPlayer(player);
             if(map != null && map.isStart){
                MapTeams teams = map.getMapTeams();
                BaseTeam deadPlayerTeam = teams.getTeamByPlayer(player);
                if(deadPlayerTeam != null){
                    PlayerData data = deadPlayerTeam.getPlayerData(player.getUUID());
                    if(data == null) return;
                    data.getTabData().addDeaths();
                    data.setLiving(false);
                    player.heal(player.getMaxHealth());
                    player.setGameMode(GameType.SPECTATOR);
                    List<UUID> uuids = teams.getSameTeamPlayerUUIDs(player);
                    Entity entity = null;
                    if(uuids.size() > 1){
                        Random random = new Random();
                        entity = map.getServerLevel().getEntity(uuids.get(random.nextInt(0,uuids.size())));
                    }else if(!uuids.isEmpty()){
                         entity = map.getServerLevel().getEntity(uuids.get(0));
                    }
                    if(entity != null) player.setCamera(entity);
                    player.setRespawnPosition(player.level().dimension(),player.getOnPos().above(),0f,true,false);
                    event.setCanceled(true);
                }

                if(event.getSource().getEntity() instanceof ServerPlayer killer){
                    BaseTeam killerPlayerTeam = teams.getTeamByPlayer(killer);
                    if(killerPlayerTeam != null){
                        PlayerData data = killerPlayerTeam.getPlayerData(player.getUUID());
                        if(data == null) return;
                        data.getTabData().addKills();

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
                                if (assist != null && teams.getJoinedPlayers().contains(assistId)) {;
                                    BaseTeam assistPlayerTeam = teams.getTeamByPlayer(killer);
                                    if(assistPlayerTeam != null){
                                        PlayerData assistData = assistPlayerTeam.getPlayerData(assistId);
                                        if(assistData == null) return;
                                        assistData.getTabData().addAssist();
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerOffline(PlayerEvent.PlayerLoggedInEvent event){
        if(event.getEntity() instanceof ServerPlayer player){
            BaseMap map = FPSMCore.getMapByPlayer(player);
            if(map != null && map.isStart){
                MapTeams teams = map.getMapTeams();
                BaseTeam playerTeam = teams.getTeamByPlayer(player);
                if(playerTeam != null) {
                    PlayerData data = playerTeam.getPlayerData(player.getUUID());
                    if(data == null) return;
                    data.setOffline(false);
                    //TODO
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerOffline(PlayerEvent.PlayerLoggedOutEvent event){
        if(event.getEntity() instanceof ServerPlayer player){
            BaseMap map = FPSMCore.getMapByPlayer(player);
            if(map != null && map.isStart){
                MapTeams teams = map.getMapTeams();
                BaseTeam playerTeam = teams.getTeamByPlayer(player);
                if(playerTeam != null) {
                    PlayerData data = playerTeam.getPlayerData(player.getUUID());
                    if(data == null) return;
                    data.setOffline(true);
                    data.getTabDataTemp().addDeaths();
                    player.heal(player.getMaxHealth());
                    player.setGameMode(GameType.SPECTATOR);
                }
            }
        }
    }

    @SubscribeEvent
    public void onLivingHurtEvent(LivingHurtEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DamageSource damageSource = event.getSource();
            if(damageSource.getEntity() instanceof ServerPlayer from){
                BaseMap map = FPSMCore.getMapByPlayer(player);
                if (map != null && map.checkGameHasPlayer(player) && map.checkGameHasPlayer(from)) {
                    float damage = event.getAmount();
                    map.getMapTeams().addHurtData(player,from.getUUID(),damage);
                }
            }
        }
    }
}
