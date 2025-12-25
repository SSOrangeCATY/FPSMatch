package com.phasetranscrystal.fpsmatch.core.map;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.capability.CapabilityMap;
import com.phasetranscrystal.fpsmatch.core.capability.map.MapCapability;
import com.phasetranscrystal.fpsmatch.core.data.AreaData;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointData;
import com.phasetranscrystal.fpsmatch.common.packet.FPSMatchGameTypeS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.FPSMatchStatsResetS2CPacket;
import com.phasetranscrystal.fpsmatch.core.event.FPSMapEvent;
import com.phasetranscrystal.fpsmatch.core.team.BaseTeam;
import com.phasetranscrystal.fpsmatch.core.team.MapTeams;
import com.phasetranscrystal.fpsmatch.core.team.ServerTeam;
import com.phasetranscrystal.fpsmatch.core.team.TeamData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Predicate;

/**
 * BaseMap 抽象类，表示游戏中的基础地图。
 */
public abstract class BaseMap {
    // 地图名称
    public final String mapName;
    // 游戏是否开始
    protected boolean isStart = false;
    // 是否处于调试模式
    private boolean isDebug = false;
    // 服务器世界
    private final ServerLevel serverLevel;
    // 地图团队
    private final MapTeams mapTeams;

    private final CapabilityMap<BaseMap,MapCapability> capabilities;
    // 地图区域数据
    public final AreaData mapArea;
    /**
     * BaseMap 类的构造函数。
     *
     * @param serverLevel 地图所在世界。
     * @param mapName 地图名称。
     * @param areaData 地图的区域数据。
     */
    public BaseMap(ServerLevel serverLevel, String mapName, AreaData areaData) {
        this.serverLevel = serverLevel;
        this.mapName = mapName;
        this.mapArea = areaData;
        this.mapTeams = new MapTeams(serverLevel, this);
        this.capabilities = CapabilityMap.ofMapCapability(this);
    }

    public BaseMap(ServerLevel serverLevel, String mapName, AreaData areaData, List<Class<? extends MapCapability>> capabilities) {
        this(serverLevel, mapName, areaData);
        for (Class<? extends MapCapability> cap : capabilities) {
            if(!this.capabilities.add(cap)){
                FPSMatch.LOGGER.error("Failed to add capability {} to map {}", cap.getSimpleName(), this.mapName);
            }
        }
    }

    /**
     * 添加团队
     * @param data 团队数据
     */
    public ServerTeam addTeam(TeamData data) {
        return this.mapTeams.addTeam(data);
    }

    public ServerTeam getSpectatorTeam(){
        return this.mapTeams.getSpectatorTeam();
    }

    /**
     * 地图每个 tick 的操作
     */
    public final void mapTick() {
        checkForVictory();
        tick();
        capabilities.tick();
        getMapTeams().getTeamsWithSpectator().forEach(team->{
            team.getCapabilityMap().tick();
        });
        syncToClient();
    }

    /**
     * 同步数据到客户端
     */
    public abstract void syncToClient();

    /**
     * 每个 tick 的操作
     */
    public void tick() {
    }

    /**
     * 检查胜利条件
     */
    public final void checkForVictory() {
        if (this.victoryGoal()) {
            this.victory();
        }
    }

    /**
     * 开始游戏
     */
    public boolean start(){
        return !MinecraftForge.EVENT_BUS.post(new FPSMapEvent.StartEvent(this));
    };

    /**
     * 检查玩家是否在游戏中
     *
     * @param player 玩家对象
     * @return 是否在游戏中
     */
    public boolean checkGameHasPlayer(Player player) {
        return this.checkGameHasPlayer(player.getUUID());
    }

    /**
     * 检查玩家是否在游戏中
     *
     * @param player 玩家对象
     * @return 是否在游戏中
     */
    public boolean checkGameHasPlayer(UUID player) {
        return this.getMapTeams()
                .getJoinedUUID().contains(player);
    }

    public boolean checkSpecHasPlayer(Player player) {
        return this.getMapTeams().getSpecPlayers().contains(player.getUUID());
    }

    /**
     * 开始新一轮游戏
     */
    public void startNewRound() {
    }

    /**
     * 胜利操作
     */
    public void victory(){
        MinecraftForge.EVENT_BUS.post(new FPSMapEvent.VictoryEvent(this));
    };

    /**
     * 胜利条件
     *
     * @return 是否满足胜利条件
     */
    public abstract boolean victoryGoal();

    /**
     * 清理地图
     */
    public boolean cleanupMap(){
        return !MinecraftForge.EVENT_BUS.post(new FPSMapEvent.ClearEvent(this));
    }

    /**
     * 重置游戏
     */
    public void reset(){
        MinecraftForge.EVENT_BUS.post(new FPSMapEvent.ResetEvent(this));
    };

    /**
     * 获取地图团队
     * @return 地图团队对象
     */
    public MapTeams getMapTeams() {
        return mapTeams;
    }

    public void leave(ServerPlayer player) {
        this.sendPacketToJoinedPlayer(player,new FPSMatchStatsResetS2CPacket(),true);
        player.setGameMode(GameType.ADVENTURE);
        this.getMapTeams().leaveTeam(player);
    }


    public void join(ServerPlayer player) {
        MapTeams mapTeams = this.getMapTeams();
        List<ServerTeam> baseTeams = mapTeams.getNormalTeams();
        if(baseTeams.isEmpty()) return;
        ServerTeam team = baseTeams.stream().min(Comparator.comparingInt(BaseTeam::getPlayerCount)).orElse(baseTeams.stream().toList().get(new Random().nextInt(0,baseTeams.size())));
        this.join(team.name, player);
    }

    /**
     * 加入团队
     * @param teamName 团队名称
     * @param player 玩家对象
     */
    public void join(String teamName, ServerPlayer player) {
        FPSMCore.checkAndLeaveTeam(player);
        this.pullGameInfo(player);
        this.getMapTeams().joinTeam(teamName, player);
    }

    public void teleportPlayerToReSpawnPoint(ServerPlayer player){
        this.getMapTeams().getTeamByPlayer(player)
                .flatMap(t -> t.getPlayerData(player.getUUID()))
                .ifPresent(playerData -> {
                    SpawnPointData data = playerData.getSpawnPointsData();
                    player.setRespawnPosition(data.getDimension(),data.getBlockPos(),0,true,false);
                    teleportToPoint(player, data);
        });
    }

    public void teleportToPoint(ServerPlayer player, SpawnPointData data) {
        if(!Level.isInSpawnableBounds(data.getBlockPos())) return;
        Set<RelativeMovement> set = EnumSet.noneOf(RelativeMovement.class);
        set.add(RelativeMovement.X_ROT);
        set.add(RelativeMovement.Y_ROT);
        if (player.teleportTo(Objects.requireNonNull(this.getServerLevel().getServer().getLevel(data.getDimension())), data.getX(),data.getY(),data.getZ(), set, 0, 0)) {
            label23: {
                if (player.isFallFlying()) {
                    break label23;
                }

                player.setDeltaMovement(player.getDeltaMovement().multiply(1.0D, 0.0D, 1.0D));
                player.setOnGround(true);
            }
        }
    }

    public void clearInventory(UUID uuid, Predicate<ItemStack> inventoryPredicate){
        Player player = this.getServerLevel().getPlayerByUUID(uuid);
        if(player instanceof ServerPlayer serverPlayer){
            this.clearInventory(serverPlayer,inventoryPredicate);
        }
    }

    public void clearInventory(ServerPlayer player, Predicate<ItemStack> predicate){
        player.getInventory().clearOrCountMatchingItems(predicate, -1, player.inventoryMenu.getCraftSlots());
        player.containerMenu.broadcastChanges();
        player.inventoryMenu.slotsChanged(player.getInventory());
    }

    public void clearInventory(ServerPlayer player){
        player.getInventory().clearOrCountMatchingItems((p_180029_) -> true, -1, player.inventoryMenu.getCraftSlots());
        player.containerMenu.broadcastChanges();
        player.inventoryMenu.slotsChanged(player.getInventory());
    }

    public void syncInventory(ServerPlayer player) {
        player.inventoryMenu.slotsChanged(player.getInventory());
        player.inventoryMenu.broadcastChanges();
    }
    /**
     * 获取服务器世界
     * @return 服务器世界对象
     */
    public ServerLevel getServerLevel() {
        return serverLevel;
    }

    /**
     * 是否处于调试模式
     * @return 是否处于调试模式
     */
    public boolean isDebug() {
        return isDebug;
    }

    /**
     * 切换调试模式
     * @return 切换后的调试模式状态
     */
    public boolean switchDebugMode() {
        this.isDebug = !this.isDebug;
        return this.isDebug;
    }

    /**
     * 获取地图名称
     * @return 地图名称
     */
    public String getMapName() {
        return mapName;
    }

    /**
     * 获取游戏类型
     * @return 游戏类型
     */
    public abstract String getGameType();

    /**
     * 重新加载地图逻辑
     * */
    public boolean reload(){
        return !MinecraftForge.EVENT_BUS.post(new FPSMapEvent.ReloadEvent(this));
    }
    
    public boolean load(){
        if(MinecraftForge.EVENT_BUS.post(new FPSMapEvent.LoadEvent(this))) return false;
        FPSMCore.getInstance().registerMap(this.getGameType(),this);
        return true;
    }


    /**
     * 获取地图的所有能力
     *
     * @return 能力实例集合
     */
    public CapabilityMap<BaseMap,MapCapability> getCapabilityMap() {
        return capabilities;
    }

    /**
     * 比较两张地图是否相等
     * @param object 比较对象
     * @return 是否相等
     */
    public boolean equals(Object object) {
        if (object instanceof BaseMap map) {
            return map.getMapName().equals(this.getMapName()) && map.getGameType().equals(this.getGameType());
        } else {
            return false;
        }
    }

    /**
     * 获取地图区域数据
     * @return 地图区域数据对象
     */
    public AreaData getMapArea() {
        return mapArea;
    }

    /**
     * 发送数据包给所有玩家
     * @param packet 数据包对象
     * @param <MSG> 数据包类型
     */
    public <MSG> void sendPacketToAllPlayer(MSG packet) {
        this.getMapTeams().getJoinedPlayersWithSpec().forEach(uuid ->
            this.getPlayerByUUID(uuid).ifPresent(player ->
                this.sendPacketToJoinedPlayer(player, packet, true)
            )
        );
    }

    public <MSG> void sendPacketToSpecPlayer(MSG packet){
        this.getMapTeams().getSpecPlayers().forEach(uuid ->
            this.getPlayerByUUID(uuid).ifPresent(player ->
                this.sendPacketToJoinedPlayer(player, packet, true)
            )
        );
    }

    public <MSG> void sendPacketToTeamPlayer(ServerTeam team ,MSG packet,boolean living){
        team.getPlayersData().forEach(data ->
            data.getPlayer().ifPresent(player->{
                if (data.isLiving() == living) {
                    this.sendPacketToJoinedPlayer(player, packet, true);
                }
            })
        );
    }

    public <MSG> void sendPacketToTeamLivingPlayer(ServerTeam team ,MSG packet){
        this.sendPacketToTeamPlayer(team,packet,true);
    }

    /**
     * 发送数据包给加入游戏的玩家
     * @param player 玩家对象
     * @param packet 数据包对象
     * @param noCheck 是否跳过检查
     * @param <MSG> 数据包类型
     */
    public <MSG> void sendPacketToJoinedPlayer(@NotNull ServerPlayer player, MSG packet, boolean noCheck) {
        if (noCheck || this.checkGameHasPlayer(player)) {
            if (packet instanceof Packet<?> vanilla) {
                player.connection.send(vanilla);
            } else {
                FPSMatch.sendToPlayer(player, packet);
            }
        } else {
            FPSMatch.LOGGER.error("{} is not join {}:{}", player.getDisplayName().getString(), this.getGameType(), this.getMapName());
        }
    }

    public Optional<ServerPlayer> getPlayerByUUID(UUID uuid){
        return FPSMCore.getInstance().getPlayerByUUID(uuid);
    }
    /**
     * 玩家登录事件处理
     * @param event 玩家登录事件
     */
    @SubscribeEvent
    public static void onPlayerLoggedInEvent(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Optional<BaseMap> map = FPSMCore.getInstance().getMapByPlayer(player);
            if (map.isEmpty()) {
                if (!player.isCreative()) {
                    player.heal(player.getMaxHealth());
                    player.setGameMode(GameType.ADVENTURE);
                }
            }else{
                map.get().getMapTeams().getTeamByPlayer(player)
                        .flatMap(team -> team.getPlayerData(player.getUUID()))
                        .ifPresent(playerData -> {
                            playerData.setLiving(false);
                            player.setGameMode(GameType.SPECTATOR);
                        });
            }
        }
    }

    /**
     * 玩家登出事件处理
     * @param event 玩家登出事件
     */
    @SubscribeEvent
    public static void onPlayerLoggedOutEvent(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            FPSMCore.checkAndLeaveTeam(player);
        }
    }

    /**
     * 玩家受伤事件处理
     * @param event 玩家受伤事件
     */
    @SubscribeEvent
    public static void onPlayerHurt(LivingHurtEvent event){
        if(event.getEntity() instanceof ServerPlayer hurt){
            Optional<BaseMap> map = FPSMCore.getInstance().getMapByPlayer(hurt);
            if(map.isPresent() && map.get().isStart) {
                DamageSource source = event.getSource();
                ServerPlayer attacker;
                if (source.getEntity() instanceof ServerPlayer sourcePlayer) {
                    attacker = sourcePlayer;
                } else if (source.getDirectEntity() instanceof ServerPlayer sourcePlayer) {
                    attacker = sourcePlayer;
                } else {
                    attacker = null;
                }

                boolean flag = attacker != null && !attacker.isDeadOrDying();

                if (flag) {
                    if(!attacker.getUUID().equals(hurt.getUUID())) map.get().getMapTeams().addHurtData(attacker, hurt.getUUID(), Math.min(hurt.getHealth(), event.getAmount()));
                }else{
                    if (attacker == null) return;
                    event.setCanceled(true);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerDeathEvent(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Optional<BaseMap> map = FPSMCore.getInstance().getMapByPlayer(player);
            if (map.isPresent()) {
                ServerPlayer attacker = null;
                if (event.getSource().getEntity() instanceof ServerPlayer sourcePlayer) {
                    attacker = sourcePlayer;
                } else if (event.getSource().getDirectEntity() instanceof ServerPlayer sourcePlayer) {
                    attacker = sourcePlayer;
                }

                if (attacker != null) {
                    MinecraftForge.EVENT_BUS.post(new FPSMapEvent.PlayerDeathEvent(map.get(), player, attacker));
                }
            }
        }
    }

    public void pullGameInfo(ServerPlayer player){
        this.sendPacketToJoinedPlayer(player,new FPSMatchGameTypeS2CPacket(this.getMapName(), this.getGameType()),true);
    }

    public final boolean isStart(){
        return this.isStart;
    }
}