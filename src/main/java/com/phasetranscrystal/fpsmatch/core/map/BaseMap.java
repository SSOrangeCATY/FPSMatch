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
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * BaseMap 抽象类，表示游戏中的基础地图。
 */
public abstract class BaseMap {
    // 地图名称
    public final String mapName;
    // 游戏是否开始
    public boolean isStart = false;
    // 是否处于调试模式
    private boolean isDebug = false;
    // 服务器级别
    private final ServerLevel serverLevel;
    // 地图团队
    private final MapTeams mapTeams;
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
    }

    /**
     * 添加团队
     * @param teamName 团队名称
     * @param playerLimit 玩家限制
     */
    public BaseTeam addTeam(String teamName, int playerLimit) {
        return this.mapTeams.addTeam(teamName, playerLimit);
    }

    public BaseTeam getSpectatorTeam(){
        return this.mapTeams.getSpectatorTeam();
    }

    /**
     * 地图每个 tick 的操作
     */
    public final void mapTick() {
        checkForVictory();
        tick();
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
    public abstract void startGame();

    /**
     * 检查玩家是否在游戏中
     *
     * @param player 玩家对象
     * @return 是否在游戏中
     */
    public boolean checkGameHasPlayer(Player player) {
        return this.getMapTeams().getJoinedPlayers().contains(player.getUUID());
    }

    /**
     * 开始新一轮游戏
     */
    public void startNewRound() {
    }

    /**
     * 胜利操作
     */
    public abstract void victory();

    /**
     * 胜利条件
     *
     * @return 是否满足胜利条件
     */
    public abstract boolean victoryGoal();

    /**
     * 清理地图
     */
    public void cleanupMap() {
    }

    /**
     * 重置游戏
     */
    public abstract void resetGame();

    /**
     * 获取地图团队
     * @return 地图团队对象
     */
    public MapTeams getMapTeams() {
        return mapTeams;
    }

    /**
     * 加入团队
     * @param teamName 团队名称
     * @param player 玩家对象
     */
    public void joinTeam(String teamName, ServerPlayer player) {
        FPSMCore.checkAndLeaveTeam(player);
        FPSMatch.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new FPSMatchGameTypeS2CPacket(this.getMapName(), this.getGameType()));
        FPSMatch.INSTANCE.send(PacketDistributor.ALL.noArg(), new CSGameTabStatsS2CPacket(player.getUUID(), Objects.requireNonNull(Objects.requireNonNull(this.getMapTeams().getTeamByName(teamName)).getPlayerData(player.getUUID())).getTabData(), teamName));
        this.getMapTeams().joinTeam(teamName, player);
        if (this instanceof ShopMap<?> shopMap && !teamName.equals("spectator")) {
            shopMap.getShop(teamName).syncShopData(player);
        }
    }

    public void joinSpecTeam(ServerPlayer player){
        FPSMCore.checkAndLeaveTeam(player);
        player.setGameMode(GameType.SPECTATOR);
        this.sendPacketToJoinedPlayer(player,new FPSMatchGameTypeS2CPacket(this.getMapName(), this.getGameType()),true);
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
    //TODO WIP
    public void reload(){}

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
        this.getMapTeams().getJoinedPlayers().forEach(uuid -> {
            ServerPlayer player = (ServerPlayer) this.getServerLevel().getPlayerByUUID(uuid);
            if (player != null) {
                this.sendPacketToJoinedPlayer(player, packet, true);
            } else {
                FPSMatch.LOGGER.error(this.getMapTeams().playerName.get(uuid).getString() + " is not found in online world");
            }
        });

        this.getMapTeams().getSpecPlayers().forEach(uuid -> {
            ServerPlayer player = (ServerPlayer) this.getServerLevel().getPlayerByUUID(uuid);
            if (player != null) {
                this.sendPacketToJoinedPlayer(player, packet, true);
            } else {
                FPSMatch.LOGGER.error(this.getMapTeams().playerName.get(uuid).getString() + " is not found in online world");
            }
        });
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
            if (packet instanceof Packet<?> vanillaPacket) {
                player.connection.send(vanillaPacket);
            } else {
                FPSMatch.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), packet);
            }
        } else {
            FPSMatch.LOGGER.error(player.getDisplayName().getString() + " is not join " + this.getGameType() + ":" + this.getMapName());
        }
    }

    /**
     * 玩家登录事件处理
     * @param event 玩家登录事件
     */
    @SubscribeEvent
    public static void onPlayerLoggedInEvent(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            BaseMap map = FPSMCore.getInstance().getMapByPlayer(player);
            if (map != null) {
                MapTeams teams = map.getMapTeams();
                BaseTeam playerTeam = teams.getTeamByPlayer(player);
                if (playerTeam != null) {
                    PlayerData data = playerTeam.getPlayerData(player.getUUID());
                    if (data == null) return;
                    data.setOffline(false);
                }
            } else {
                if (!player.isCreative()) {
                    player.heal(player.getMaxHealth());
                    player.setGameMode(GameType.ADVENTURE);
                }
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
    public static void onLivingHurtEvent(LivingHurtEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DamageSource damageSource = event.getSource();
            ServerPlayer from = null;
            if (damageSource.getEntity() instanceof ServerPlayer target) {
                from = target;
            } else if (damageSource.getDirectEntity() instanceof ServerPlayer target) {
                from = target;
            }

            if (from != null) {
                BaseMap map = FPSMCore.getInstance().getMapByPlayer(player);
                if (map != null && map.checkGameHasPlayer(player) && map.checkGameHasPlayer(from)) {
                    float damage = event.getAmount();
                    map.getMapTeams().addHurtData(from, player.getUUID(), damage);
                }
            }
        }
    }
}