package com.phasetranscrystal.fpsmatch.core;

import com.phasetranscrystal.fpsmatch.core.data.PlayerData;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointData;
import com.phasetranscrystal.fpsmatch.core.data.TabData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Team;
import org.checkerframework.checker.units.qual.A;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class MapTeams {
    protected final ServerLevel level;
    private final Map<String,BaseTeam> teams = new HashMap<>();
    private final Map<String,List<UUID>> unableToSwitch = new HashMap<>();
    public final Map<UUID,Component> playerName = new HashMap<>();
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

    public void switchAttackAndDefend(ServerLevel serverLevel, String attackTeamName, String defendTeamName) {
        BaseTeam attackTeam = this.getTeamByName(attackTeamName);
        BaseTeam defendTeam = this.getTeamByName(defendTeamName);
        if(attackTeam == null || defendTeam == null) return;

        //交换玩家
        Map<UUID, PlayerData> tempPlayers = new HashMap<>(attackTeam.getPlayers());
        attackTeam.resetAllPlayers(serverLevel, defendTeam.getPlayers());
        defendTeam.resetAllPlayers(serverLevel, tempPlayers);

        // 交换得分
        int tempScore = attackTeam.getScores();
        attackTeam.setScores(defendTeam.getScores());
        defendTeam.setScores(tempScore);

        attackTeam.setLoseStreak(0);
        attackTeam.setCompensationFactor(0);
        defendTeam.setCompensationFactor(0);
        defendTeam.setLoseStreak(0);

        // 交换暂停次数
        int tempP = attackTeam.getPauseTime();
        boolean tempN = attackTeam.needPause();
        attackTeam.setPauseTime(defendTeam.getPauseTime());
        attackTeam.setNeedPause(defendTeam.needPause());
        defendTeam.setPauseTime(tempP);
        defendTeam.setNeedPause(tempN);
    }

    public Map<String, List<UUID>> getUnableToSwitch() {
        return unableToSwitch;
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
        playerteam.setNameTagVisibility(Team.Visibility.HIDE_FOR_OTHER_TEAMS);
        playerteam.setAllowFriendlyFire(false);
        playerteam.setSeeFriendlyInvisibles(false);
        playerteam.setDeathMessageVisibility(Team.Visibility.NEVER);
        this.teams.put(teamName, new BaseTeam(map.getGameType(),map.getMapName(),teamName,limit,playerteam));
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
        AtomicReference<BaseTeam> baseTeamAtomicReference = new AtomicReference<>();
        this.teams.forEach(((s, team) -> {
            if(team.hasPlayer(player.getUUID())){
                baseTeamAtomicReference.set(team);
            };
        }));
        return baseTeamAtomicReference.get();
    }
    @Nullable public BaseTeam getTeamByPlayer(UUID player){
        AtomicReference<BaseTeam> baseTeamAtomicReference = new AtomicReference<>();
        this.teams.forEach(((s, team) -> {
            if(team.hasPlayer(player)){
                baseTeamAtomicReference.set(team);
            };
        }));
        return baseTeamAtomicReference.get();
    }

    public List<UUID> getJoinedPlayers(){
        List<UUID> uuids = new ArrayList<>();
        this.teams.values().forEach((t)-> uuids.addAll(t.getPlayerList()));
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
            this.playerName.put(player.getUUID(),player.getDisplayName());
            player.displayClientMessage(Component.translatable("fpsm.map.cs.join.team",teamName).withStyle(ChatFormatting.GREEN),false);
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
        return team.getPlayerLimit() < team.getPlayerList().size();
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
            if (t.getFixedName().equals(teamName)){
                team.set(t);
            }
        });
        return team.get();
    }

    public void reset(){
        this.resetAllHurtData();
        this.resetLivingPlayers();
        this.teams.forEach((name,team)->{
            team.setScores(0);
            team.getPlayers().clear();
            team.setLoseStreak(0);
            team.setCompensationFactor(0);
            team.setPauseTime(0);
        });
        this.unableToSwitch.clear();
        this.playerName.clear();
    }

    public void leaveTeam(ServerPlayer player){
        this.teams.values().forEach((t)-> t.leave(player));
        this.playerName.remove(player.getUUID());
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
                uuids.addAll(team.getPlayerList());
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
