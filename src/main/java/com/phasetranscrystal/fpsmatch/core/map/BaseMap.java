package com.phasetranscrystal.fpsmatch.core.map;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.core.BaseTeam;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.MapTeams;
import com.phasetranscrystal.fpsmatch.core.data.AreaData;
import com.phasetranscrystal.fpsmatch.core.data.PlayerData;
import com.phasetranscrystal.fpsmatch.net.CSGameTabStatsS2CPacket;
import com.phasetranscrystal.fpsmatch.net.FPSMatchGameTypeS2CPacket;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Predicate;

@Mod.EventBusSubscriber(modid = FPSMatch.MODID,bus = Mod.EventBusSubscriber.Bus.FORGE)
public abstract class BaseMap{
    public final String mapName;
    public String gameType = "error";
    public boolean isStart = false;
    private boolean isDebug = false;
    private final ServerLevel serverLevel;
    private MapTeams mapTeams;
    public final AreaData mapArea;
    private final Map<String,Integer> teams = new HashMap<>();

    public BaseMap(ServerLevel serverLevel, String mapName, AreaData areaData) {
        this.serverLevel = serverLevel;
        this.mapName = mapName;
        this.mapArea = areaData;
    }

    public final Map<String,Integer> getTeams(){
        return teams;
    }
    public void addTeam(String teamName,int playerLimit){
        this.teams.put(teamName,playerLimit);
    }

    public final void setMapTeams(MapTeams teams){
        this.mapTeams = teams;
    }

    public final void mapTick(){
        checkForVictory();
        tick();
        syncToClient();
    }

   public abstract void syncToClient();

    public void tick(){
    }

    // 检查胜利条件
    public final void checkForVictory() {
        if (this.victoryGoal()) {
            this.victory();
        }
    }

    public abstract void startGame();

    public boolean checkGameHasPlayer(Player player){
        return this.getMapTeams().getJoinedPlayers().contains(player.getUUID());
    }
    public  void startNewRound(){}
    public abstract void victory();
    public abstract boolean victoryGoal();
    public void cleanupMap(){
    }
    public abstract void resetGame();

    public MapTeams getMapTeams() {
        return mapTeams;
    }

    public void joinTeam(String teamName, ServerPlayer player) {
        FPSMCore.checkAndLeaveTeam(player);
        FPSMatch.INSTANCE.send(PacketDistributor.PLAYER.with(()->player),new FPSMatchGameTypeS2CPacket(this.getMapName(),this.getGameType()));
        FPSMatch.INSTANCE.send(PacketDistributor.ALL.noArg(), new CSGameTabStatsS2CPacket(player.getUUID(), Objects.requireNonNull(Objects.requireNonNull(this.getMapTeams().getTeamByName(teamName)).getPlayerData(player.getUUID())).getTabData(),teamName));
        this.getMapTeams().joinTeam(teamName,player);
        if(this instanceof ShopMap<?> shopMap){
            shopMap.getShop(teamName).syncShopData(player);
        }
    }

    public ServerLevel getServerLevel() {
        return serverLevel;
    }

    public boolean isDebug() {
        return isDebug;
    }

    public boolean switchDebugMode(){
        this.isDebug = !this.isDebug;
        return this.isDebug;
    }

    public String getMapName(){
        return mapName;
    }

    public final void setGameType(String gameType) {
        this.gameType = gameType;
        this.setMapTeams(new MapTeams(this.getServerLevel(),this.getTeams(),this));
    }

    public String getGameType() {
        return gameType;
    }

    public boolean equals(Object object){
        if(object instanceof BaseMap map){
            return map.getMapName().equals(this.getMapName()) && map.getGameType().equals(this.getGameType());
        }else{
            return false;
        }
    }

    public AreaData getMapArea() {
        return mapArea;
    }

    public <MSG> void sendPacketToAllPlayer(MSG packet){
        this.getMapTeams().getJoinedPlayers().forEach(uuid -> {
            ServerPlayer player = (ServerPlayer) this.getServerLevel().getPlayerByUUID(uuid);
            if (player != null) {
                this.sendPacketToJoinedPlayer(player,packet,true);
            }else{
                FPSMatch.LOGGER.error(this.getMapTeams().playerName.get(uuid).getString() + " is not found in online world");
            }
        });
    }

    public <MSG> void sendPacketToJoinedPlayer(@NotNull ServerPlayer player, MSG packet, boolean noCheck){
        if(noCheck || this.checkGameHasPlayer(player)){
            if (packet instanceof Packet<?> vanillaPacket) {
                player.connection.send(vanillaPacket);
            } else {
                FPSMatch.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), packet);
            }
        }else{
            FPSMatch.LOGGER.error(player.getDisplayName().getString() + " is not join "+this.getGameType()+":"+ this.getMapName());
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedInEvent(PlayerEvent.PlayerLoggedInEvent event){
        if(event.getEntity() instanceof ServerPlayer player){
            BaseMap map = FPSMCore.getInstance().getMapByPlayer(player);
            if(map != null){
                MapTeams teams = map.getMapTeams();
                BaseTeam playerTeam = teams.getTeamByPlayer(player);
                if(playerTeam != null) {
                    PlayerData data = playerTeam.getPlayerData(player.getUUID());
                    if(data == null) return;
                    data.setOffline(false);
                }
            }else{
                if(!player.isCreative()){
                    player.heal(player.getMaxHealth());
                    player.setGameMode(GameType.ADVENTURE);
                }
            }
        }
    }
    @SubscribeEvent
    public static void onPlayerLoggedOutEvent(PlayerEvent.PlayerLoggedOutEvent event){
        if(event.getEntity() instanceof ServerPlayer player){
            BaseMap map = FPSMCore.getInstance().getMapByPlayer(player);
            if(map != null){
                MapTeams teams = map.getMapTeams();
                BaseTeam playerTeam = teams.getTeamByPlayer(player);
                if(playerTeam != null) {
                    playerTeam.leave(player);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onLivingHurtEvent(LivingHurtEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DamageSource damageSource = event.getSource();
            ServerPlayer from = null;
            if(damageSource.getEntity() instanceof ServerPlayer target){
                from = target;
            } else if (damageSource.getDirectEntity() instanceof ServerPlayer target) {
                from = target;
            }

            if(from != null){
                BaseMap map = FPSMCore.getInstance().getMapByPlayer(player);
                if (map != null && map.checkGameHasPlayer(player) && map.checkGameHasPlayer(from)) {
                    float damage = event.getAmount();
                    map.getMapTeams().addHurtData(from,player.getUUID(),damage);
                }
            }
        }
    }

}