package com.phasetranscrystal.fpsmatch.core;

import com.phasetranscrystal.fpsmatch.core.data.PlayerData;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointData;
import com.phasetranscrystal.fpsmatch.core.data.TabData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.scores.PlayerTeam;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

/**
 * 基础队伍类，用于管理游戏中的队伍信息和玩家数据。
 * <p>
 * 该类提供了队伍的基本功能，包括玩家加入/离开、玩家数据管理、出生点管理、得分统计等。
 * 同时支持队伍的暂停功能和连败补偿机制。
 */
public class BaseTeam {
    /**
     * 队伍名称。
     */
    public final String name;

    /**
     * 游戏类型。
     */
    public final String gameType;

    /**
     * 地图名称。
     */
    public final String mapName;

    /**
     * 队伍人数上限。
     */
    private final int playerLimit;

    /**
     * 队伍的 Scoreboard 对象。
     */
    private final PlayerTeam playerTeam;

    /**
     * 队伍当前得分。
     */
    private int scores = 0;

    /**
     * 队伍玩家数据，键为玩家 UUID，值为对应的 PlayerData。
     */
    private final Map<UUID, PlayerData> players = new HashMap<>();

    /**
     * 队伍玩家的临时数据，用于保存玩家的 TabData。
     */
    private final Map<UUID, TabData> playerDataTemp = new HashMap<>();

    /**
     * 队伍的出生点数据列表。
     */
    private final List<SpawnPointData> spawnPointsData = new ArrayList<>();

    /**
     * 无法切换队伍的玩家 UUID 列表。
     */
    public final List<UUID> teamUnableToSwitch = new ArrayList<>();

    /**
     * 队伍的连败次数。
     */
    private int loseStreak;

    /**
     * 队伍的连败补偿因数。
     */
    private int compensationFactor;

    /**
     * 队伍的暂停时间。
     */
    private int pauseTime = 0;

    /**
     * 队伍是否需要暂停。
     */
    private boolean needPause = false;

    /**
     * 构造函数，初始化队伍的基本信息。
     *
     * @param gameType 游戏类型
     * @param mapName 地图名称
     * @param name 队伍名称
     * @param playerLimit 队伍人数上限
     * @param playerTeam 队伍的 Scoreboard 对象
     */
    public BaseTeam(String gameType, String mapName, String name, int playerLimit, PlayerTeam playerTeam) {
        this.gameType = gameType;
        this.mapName = mapName;
        this.name = name;
        this.playerLimit = playerLimit;
        this.playerTeam = playerTeam;
    }

    /**
     * 让玩家加入队伍。
     * @param player 玩家对象
     */
    public void join(ServerPlayer player) {
        player.getScoreboard().addPlayerToTeam(player.getScoreboardName(), playerTeam);
        this.createPlayerData(player);
    }

    /**
     * 让玩家离开队伍。
     * @param player 玩家对象
     */
    public void leave(ServerPlayer player) {
        if (this.hasPlayer(player.getUUID())) {
            this.players.remove(player.getUUID());
            player.getScoreboard().removePlayerFromTeam(player.getScoreboardName());
        }
    }

    /**
     * 删除队伍中的玩家。
     * @param uuid 玩家 UUID
     */
    public void delPlayer(UUID uuid) {
        this.players.remove(uuid);
    }

    /**
     * 创建玩家数据。
     * @param player 玩家对象
     */
    public void createPlayerData(ServerPlayer player) {
        this.players.put(player.getUUID(), new PlayerData(player.getUUID()));
    }

    /**
     * 处理玩家离线逻辑。
     * @param player 玩家对象
     */
    public void handleOffline(ServerPlayer player) {
        UUID uuid = player.getUUID();
        PlayerData playerData = players.get(uuid);
        playerData.setLiving(false);
        playerData.setOffline(true);
        player.heal(player.getMaxHealth());
        player.setGameMode(GameType.SPECTATOR);
    }

    /**
     * 重置队伍中所有玩家的存活状态。
     */
    public void resetLiving() {
        this.players.values().forEach(data -> {
            if (!data.isOffline()) {
                data.setLiving(true);
            }
        });
    }

    /**
     * 获取玩家的 TabData。
     * @param uuid 玩家 UUID
     * @return 玩家的 TabData，如果未找到则返回 null
     */
    @Nullable
    public TabData getPlayerTabData(UUID uuid) {
        if (this.players.containsKey(uuid)) {
            return this.players.get(uuid).getTabData();
        }
        return null;
    }

    /**
     * 获取玩家的 PlayerData。
     * @param uuid 玩家 UUID
     * @return 玩家的 PlayerData，如果未找到则返回 null
     */
    @Nullable
    public PlayerData getPlayerData(UUID uuid) {
        if (this.players.containsKey(uuid)) {
            return this.players.get(uuid);
        }
        return null;
    }

    /**
     * 获取队伍中所有玩家的 PlayerData 列表。
     * @return 玩家数据列表
     */
    public List<PlayerData> getPlayersData() {
        return this.players.values().stream().toList();
    }

    /**
     * 获取队伍中所有玩家的 TabData 列表。
     * @return 玩家 TabData 列表
     */
    public List<TabData> getPlayersTabData() {
        List<TabData> tabDataList = new ArrayList<>();
        this.players.values().forEach(data -> tabDataList.add(data.getTabData()));
        return tabDataList;
    }

    /**
     * 保存队伍中所有玩家的临时数据。
     */
    public void saveTemp() {
        this.playerDataTemp.clear();
        for (Map.Entry<UUID, PlayerData> entry : this.players.entrySet()) {
            this.playerDataTemp.put(entry.getKey(), entry.getValue().getTabData().copy());
        }
    }

    /**
     * 获取队伍中所有玩家的临时 TabData 列表。
     * @return 临时 TabData 列表
     */
    public List<TabData> getPlayerDataTemp() {
        return this.playerDataTemp.values().stream().toList();
    }

    /**
     * 获取玩家的临时 TabData。
     * @param uuid 玩家 UUID
     * @return 玩家的临时 TabData，如果未找到则返回一个新的 TabData
     */
    public TabData getPlayerTabTemp(UUID uuid) {
        return this.playerDataTemp.getOrDefault(uuid, new TabData(uuid));
    }

    /**
     * 获取队伍中所有玩家的 UUID 列表。
     * @return 玩家 UUID 列表
     */
    public List<UUID> getPlayerList() {
        return this.players.keySet().stream().toList();
    }

    /**
     * 获取队伍中所有离线玩家的 UUID 列表。
     * @return 离线玩家 UUID 列表
     */
    public List<UUID> getOfflinePlayers() {
        List<UUID> offlinePlayers = new ArrayList<>();
        this.players.values().forEach(data -> {
            if (data.isOffline()) {
                offlinePlayers.add(data.getOwner());
            }
        });
        return offlinePlayers;
    }

    /**
     * 获取队伍中所有存活玩家的 UUID 列表。
     * @return 存活玩家 UUID 列表
     */
    public List<UUID> getLivingPlayers() {
        List<UUID> uuids = new ArrayList<>();
        this.players.values().forEach(data -> {
            if (data.getTabData().isLiving()) {
                uuids.add(data.getOwner());
            }
        });
        return uuids;
    }

    /**
     * 检查队伍中是否包含指定玩家。
     * @param uuid 玩家 UUID
     * @return 如果队伍中包含该玩家，返回 true；否则返回 false
     */
    public boolean hasPlayer(UUID uuid) {
        return this.players.containsKey(uuid);
    }

    /**
     * 随机分配队伍的出生点。
     */
    public boolean randomSpawnPoints() {
        Random random = new Random();

        if (this.spawnPointsData.isEmpty()) {
            FPSMCore.getInstance().getServer().sendSystemMessage(Component.translatable("message.fpsmatch.error.no_spawn_points").append(Component.literal("error from -> " + this.name)).withStyle(ChatFormatting.RED));
            return false;
        }

        if (this.spawnPointsData.size() < this.players.size()) {
            FPSMCore.getInstance().getServer().sendSystemMessage(Component.translatable("message.fpsmatch.error.not_enough_spawn_points").append(Component.literal("error from -> " + this.name)).withStyle(ChatFormatting.RED));
            return false;
        }

        List<UUID> playerUUIDs = new ArrayList<>(this.players.keySet());
        List<SpawnPointData> list = new ArrayList<>(this.spawnPointsData);
        for (UUID playerUUID : playerUUIDs) {
            if (list.isEmpty()) {
                // 出生点不够多就会这样
                list.addAll(this.spawnPointsData);
            }
            if (this.spawnPointsData.isEmpty()) {
                return false;
            }
            int randomIndex = random.nextInt(list.size());
            SpawnPointData spawnPoint = list.get(randomIndex);
            list.remove(randomIndex);
            this.players.get(playerUUID).setSpawnPointsData(spawnPoint);
        }
        return true;
    }

    /**
     * 添加一个出生点到队伍。
     * @param data 出生点数据
     */
    public void addSpawnPointData(@Nonnull SpawnPointData data) {
        this.spawnPointsData.add(data);
    }

    /**
     * 添加多个出生点到队伍。
     * @param data 出生点数据列表
     */
    public void addAllSpawnPointData(@Nonnull List<SpawnPointData> data) {
        this.spawnPointsData.addAll(data);
    }

    /**
     * 重置队伍的出生点数据。
     */
    public void resetSpawnPointData() {
        this.spawnPointsData.clear();
    }

    /**
     * 获取队伍的出生点数据列表。
     * @return 出生点数据列表
     */
    public List<SpawnPointData> getSpawnPointsData() {
        return spawnPointsData;
    }

    /**
     * 获取队伍的人数上限。
     * @return 队伍人数上限
     */
    public int getPlayerLimit() {
        return playerLimit;
    }

    /**
     * 获取队伍剩余的人数上限。
     * @return 剩余人数上限
     */
    public int getRemainingLimit() {
        return playerLimit - this.players.size();
    }

    /**
     * 获取队伍的 Scoreboard 对象。
     * @return Scoreboard 对象
     */
    public PlayerTeam getPlayerTeam() {
        return playerTeam;
    }

    /**
     * 获取队伍的当前得分。
     * @return 队伍得分
     */
    public int getScores() {
        return scores;
    }

    /**
     * 设置队伍的得分。
     * @param scores 新得分
     */
    public void setScores(int scores) {
        this.scores = scores;
    }

    /**
     * 获取队伍的固定名称（游戏类型_地图名称_队伍名称）。
     * @return 固定名称
     */
    public String getFixedName() {
        return this.gameType + "_" + this.mapName + "_" + this.name;
    }

    /**
     * 获取队伍的连败次数。
     * @return 连败次数
     */
    public int getLoseStreak() {
        return loseStreak;
    }

    /**
     * 设置队伍的连败次数。
     * @param loseStreak 新连败次数
     */
    public void setLoseStreak(int loseStreak) {
        this.loseStreak = loseStreak;
    }

    /**
     * 获取队伍的连败补偿因数。
     * @return 连败补偿因数
     */
    public int getCompensationFactor() {
        return compensationFactor;
    }

    /**
     * 设置队伍的连败补偿因数。
     * @param compensationFactor 新补偿因数
     */
    public void setCompensationFactor(int compensationFactor) {
        this.compensationFactor = Math.max(0, Math.min(compensationFactor, 4));
    }

    /**
     * 设置队伍的所有出生点数据。
     * @param spawnPointsData 新出生点数据列表
     */
    public void setAllSpawnPointData(List<SpawnPointData> spawnPointsData) {
        this.spawnPointsData.clear();
        this.spawnPointsData.addAll(spawnPointsData);
    }

    /**
     * 获取队伍的所有玩家数据。
     * @return 玩家数据 Map
     */
    public Map<UUID, PlayerData> getPlayers() {
        return this.players;
    }

    /**
     * 获取队伍的玩家数量。
     * @return 玩家数量
     */
    public int getPlayerCount() {
        return this.players.size();
    }

    /**
     * 重置队伍的所有玩家数据。
     * @param players 新的玩家数据 Map
     */
    public void resetAllPlayers(Map<UUID, PlayerData> players) {
        this.players.clear();
        this.players.putAll(players);
        players.keySet().forEach(uuid -> {
            ServerPlayer serverPlayer = FPSMCore.getInstance().getPlayerByUUID(uuid);
            if (serverPlayer != null) {
                serverPlayer.getScoreboard().addPlayerToTeam(serverPlayer.getScoreboardName(), this.getPlayerTeam());
            } else {
                teamUnableToSwitch.add(uuid);
            }
        });
    }

    /**
     * 增加队伍的暂停时间。
     */
    public void addPause() {
        if (pauseTime < 2 && !needPause) {
            needPause = true;
            pauseTime++;
        }
    }

    /**
     * 检查队伍是否可以暂停。
     * @return 如果可以暂停，返回 true；否则返回 false
     */
    public boolean canPause() {
        return pauseTime < 2 && !needPause;
    }

    /**
     * 设置队伍的暂停时间。
     * @param t 新暂停时间
     */
    public void setPauseTime(int t) {
        this.pauseTime = t;
    }

    /**
     * 如果需要，重置队伍的暂停时间。
     */
    public void resetPauseIfNeed() {
        if (this.needPause) {
            this.needPause = false;
            this.pauseTime--;
        }
    }

    /**
     * 设置队伍是否需要暂停。
     * @param needPause 是否需要暂停
     */
    public void setNeedPause(boolean needPause) {
        this.needPause = needPause;
    }

    /**
     * 检查队伍是否需要暂停。
     * @return 如果需要暂停，返回 true；否则返回 false
     */
    public boolean needPause() {
        return needPause;
    }

    /**
     * 获取队伍的暂停时间。
     * @return 暂停时间
     */
    public int getPauseTime() {
        return pauseTime;
    }
}