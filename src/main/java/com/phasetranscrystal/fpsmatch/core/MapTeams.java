package com.phasetranscrystal.fpsmatch.core;

import com.phasetranscrystal.fpsmatch.core.data.PlayerData;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointData;
import com.phasetranscrystal.fpsmatch.core.data.TabData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.commands.TeamCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Team;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class MapTeams {
    protected final ServerLevel level;
    private final Map<String,BaseTeam> teams = new HashMap<>();
    public MapTeams(ServerLevel level,Map<String,Integer> team, BaseMap map){
        this.level = level;
        team.forEach((name,limit)-> this.addTeam(name,limit,map));
    }

    @Nullable
    public List<SpawnPointData> getSpawnPointsByTeam(String team){
        BaseTeam t = this.teams.getOrDefault(team,null);
        if(t == null) return null;
        return t.getSpawnPointsData();
    }

    public Map<String,List<SpawnPointData>> getAllSpawnPoints(){
        Map<String,List<SpawnPointData>> data = new HashMap<>();
        this.teams.forEach((n,t)-> data.put(n,t.getSpawnPointsData()));
        return data;
    }

    public void putAllSpawnPoints(Map<String,List<SpawnPointData>> data){
        data.forEach((n,list)->{
            if (teams.containsKey(n)){
                teams.get(n).addAllSpawnPointData(list);
            }
        });
    }


    public void setTeamsSpawnPoints(){
            this.teams.forEach(((s, t) -> t.randomSpawnPoints()));
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
        this.teams.forEach((s,t)-> t.resetSpawnPointData());
    }

    public void addTeam(String teamName,int limit,BaseMap map){
        String fixedName = map.getGameType()+"_"+map.getMapName()+"_"+teamName;
        PlayerTeam playerteam = Objects.requireNonNullElseGet(this.level.getScoreboard().getPlayersTeam(fixedName), () -> this.level.getScoreboard().addPlayerTeam(fixedName));
        playerteam.setNameTagVisibility(Team.Visibility.NEVER);
        playerteam.setAllowFriendlyFire(false);
        playerteam.setSeeFriendlyInvisibles(false);
        playerteam.setDeathMessageVisibility(Team.Visibility.NEVER);
        this.teams.put(teamName, new BaseTeam(fixedName,limit,playerteam));
    }

    public void setTeamNameColor(BaseMap map, String teamName, ChatFormatting color){
        String fixedName = map.getGameType()+"_"+map.getMapName()+"_"+teamName;
        PlayerTeam playerteam = Objects.requireNonNullElseGet(this.level.getScoreboard().getPlayersTeam(fixedName), () -> this.level.getScoreboard().addPlayerTeam(fixedName));
        playerteam.setColor(color);
    }

    public void delTeam(PlayerTeam team){
        if(!checkTeam(team.getName())) return;
        this.teams.remove(team.getName());
        this.level.getScoreboard().removePlayerTeam(team);
    }

    @Nullable public BaseTeam getTeamByPlayer(Player player){
        PlayerTeam currentTeam = player.getScoreboard().getPlayersTeam(player.getScoreboardName());
        if(currentTeam != null && this.checkTeam(currentTeam.getName().split("_")[2])){
            return this.teams.getOrDefault(currentTeam.getName().split("_")[2],null);
        }
        return null;
    }

    public List<UUID> getJoinedPlayers(){
        List<UUID> uuids = new ArrayList<>();
        this.teams.values().forEach((t)-> uuids.addAll(t.getPlayers()));
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
        if (checkTeam(teamName) && !this.testTeamIsFull(teamName)) {
            this.playerJoin(player,teamName);
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
        BaseTeam team = teams.get(teamName);
        if (team == null) return false;
        return team.getPlayerLimit() < team.getPlayers().size();
    }


    public List<BaseTeam> getTeams(){
        return new ArrayList<>(teams.values().stream().toList());
    }

    public List<String> getTeamsName(){
        return teams.keySet().stream().toList();
    }

    @Nullable public BaseTeam getTeamByName(String teamName){
        if(checkTeam(teamName)) return this.teams.get(teamName);
        return null;
    }

    @Nullable public BaseTeam getTeamByComplexName(String teamName){
        AtomicReference<BaseTeam> team = new AtomicReference<>();
        teams.forEach((s,t)-> {
            if (t.getName().equals(teamName)){
                team.set(t);
            }
        });
        return team.get();
    }

    public void reset(){
        this.setDonePlayerStatsTemp();
        this.resetAllHurtData();
        this.resetLivingPlayers();
    }

    public void leaveTeam(ServerPlayer player){
        this.teams.values().forEach((t)-> t.leave(player));
    }

    public Map<String, List<UUID>> getTeamsLiving() {
        Map<String, List<UUID>> teamsLiving = new HashMap<>();
        teams.forEach((s,t)-> {
            List<UUID> list = t.getLivingPlayers();
            if(!list.isEmpty()){
                teamsLiving.put(s,list);
            }
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

        this.getLivingHurtData().forEach((attackerId, attackerDamageMap) -> attackerDamageMap.forEach((targetId, damage) -> damageMap.merge(attackerId, damage, Float::sum)));

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
        this.teams.forEach((s,t)-> t.getPlayersTabData().forEach((tab)-> data.put(tab.getOwner(),tab)));
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
        teams.values().forEach((team)-> flag.addAndGet(team.getScores()));
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
        teams.values().forEach((t)-> t.getPlayersTabData().forEach((data)-> hurtData.put(data.getOwner(),data.getDamageData())));
        return hurtData;
    }

    public void resetAllHurtData() {
        this.teams.values().forEach((t)-> t.getPlayersTabData().forEach(TabData::clearDamageData));
    }

    public void setDonePlayerStatsTemp(){
        this.teams.values().forEach((t)-> t.getPlayersTabData().forEach(tabData -> {
        }));
    }

}
