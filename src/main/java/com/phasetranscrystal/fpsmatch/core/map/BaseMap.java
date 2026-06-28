package com.phasetranscrystal.fpsmatch.core.map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.common.capability.team.SpawnPointCapability;
import com.phasetranscrystal.fpsmatch.common.packet.AddAreaDataS2CPacket;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.capability.CapabilityMap;
import com.phasetranscrystal.fpsmatch.core.capability.map.MapCapability;
import com.phasetranscrystal.fpsmatch.core.data.AreaData;
import com.phasetranscrystal.fpsmatch.core.data.PlayerData;
import com.phasetranscrystal.fpsmatch.core.data.Setting;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointData;
import com.phasetranscrystal.fpsmatch.common.packet.FPSMatchGameTypeS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.FPSMatchStatsResetS2CPacket;
import com.phasetranscrystal.fpsmatch.common.event.FPSMapEvent;
import com.phasetranscrystal.fpsmatch.core.persistence.ISavePort;
import com.phasetranscrystal.fpsmatch.core.team.BaseTeam;
import com.phasetranscrystal.fpsmatch.core.team.MapTeams;
import com.phasetranscrystal.fpsmatch.core.team.ServerTeam;
import com.phasetranscrystal.fpsmatch.core.team.TeamData;
import com.phasetranscrystal.fpsmatch.util.FPSMUtil;
import com.phasetranscrystal.fpsmatch.util.PreviewColorUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelData;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.*;
import java.util.function.Predicate;

/**
 * BaseMap 抽象类，表示游戏中的基础地图。
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = FPSMatch.MODID)
public abstract class BaseMap {
    public final String mapName;
    protected boolean isStart = false;
    private final MatchLifecycleState lifecycleState = new MatchLifecycleState();
    private boolean isDebug = false;
    private final ServerLevel serverLevel;
    private final MapTeams mapTeams;

    private final List<Setting<?>> settings = new LinkedList<>();

    protected final Setting<Float> minAssistDamageRatio = this.addSetting("minAssistDamageRatio", 0.25f);
    protected final Setting<Boolean> allowJoinInProgress = this.addSetting("allowJoinInProgress", true);
    protected final Setting<Boolean> teammateGlow = this.addSetting("teammateGlow", false);
    protected final Setting<Boolean> hideEnemyNameTag = this.addSetting("hideEnemyNameTag", true);
    protected final Setting<String> displayName = this.addSetting("displayName", "");

    private final CapabilityMap<BaseMap, MapCapability> capabilities;
    // 地图区域数据
    public final AreaData mapArea;

    /**
     * BaseMap 类的构造函数。
     *
     * @param serverLevel 地图所在世界。
     * @param mapName     地图名称。
     * @param areaData    地图的区域数据。
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
            if (!this.capabilities.add(cap)) {
                FPSMatch.LOGGER.error("Failed to add capability {} to map {}", cap.getSimpleName(), this.mapName);
            }
        }
    }

    /**
     * 获取当前地图的所有配置项集合。
     *
     * @return 配置项集合。
     */
    public Collection<Setting<?>> settings() {
        return settings;
    }

    ;

    /**
     * 添加团队
     *
     * @param data 团队数据
     */
    public ServerTeam addTeam(TeamData data) {
        return this.mapTeams.addTeam(data);
    }

    public ServerTeam getSpectatorTeam() {
        return this.mapTeams.getSpectatorTeam();
    }

    /**
     * 地图每个 tick 的操作
     */
    public final void mapTick() {
        checkForVictory();
        tick();
        applyTeammateGlow();
        getMapTeams().tick();
        capabilities.tick();
        syncToClient();
    }

    /**
     * 为队友应用透视发光效果，使玩家可透过墙壁看到队友位置。
     * <p>
     * 已改为客户端按队伍判断，不再通过服务端 {@link MobEffects#GLOWING} 实现。
     * 服务端 GLOWING 效果对所有客户端可见，会导致敌方也能看到发光。
     * 队友透视发光逻辑见 {@code MixinEntityUnified#onIsCurrentlyGlowing}。
     */
    protected void applyTeammateGlow() {
    }

    public void syncToClient() {
    }

    ;

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
    public boolean start() {
        if (this.isStart) {
            return true;
        }
        boolean eventAccepted = !NeoForge.EVENT_BUS.post(new FPSMapEvent.StartEvent(this)).isCanceled();
        boolean prepared = eventAccepted && this.prepareStart();
        boolean started = this.lifecycleState.acceptStart(eventAccepted, prepared);
        this.isStart = this.lifecycleState.isStarted();
        return started;
    }

    protected boolean prepareStart() {
        return true;
    }

    ;

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
     * @param player 玩家 UUID
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
     * 当对局内玩家死亡
     */
    public void handleDeath(DeathContext context) {
        ServerPlayer player = context.getDeadPlayer();
        MapTeams mapTeams = this.getMapTeams();
        mapTeams.getPlayerData(player).ifPresent(data -> {
            data.setLiving(false);
            data.addDeath();
        });
    }

    ;

    /**
     * 解析死亡武器（基础实现：主手物品）
     */
    public ItemStack resolveDeathItem(@Nullable ServerPlayer attacker, DamageSource source) {
        return attacker == null ? ItemStack.EMPTY : attacker.getMainHandItem();
    }

    /**
     * 胜利操作
     */
    public void victory() {
        NeoForge.EVENT_BUS.post(new FPSMapEvent.VictoryEvent(this));
    }

    ;

    /**
     * 胜利条件
     *
     * @return 是否满足胜利条件
     */
    public abstract boolean victoryGoal();

    /**
     * 清理地图
     */
    public boolean cleanupMap() {
        return !NeoForge.EVENT_BUS.post(new FPSMapEvent.ClearEvent(this)).isCanceled();
    }

    /**
     * 重置游戏
     */
    public void reset() {
        NeoForge.EVENT_BUS.post(new FPSMapEvent.ResetEvent(this));
        this.lifecycleState.reset();
        this.isStart = false;
    }

    ;

    /**
     * 获取地图团队
     *
     * @return 地图团队对象
     */
    public MapTeams getMapTeams() {
        return mapTeams;
    }

    public RandomSource getRandom() {
        return getServerLevel().getRandom();
    }

    public void leave(ServerPlayer player) {
        if (NeoForge.EVENT_BUS.post(new FPSMapEvent.PlayerEvent.LeaveEvent(this, player)).isCanceled()) return;
        this.sendPacketToJoinedPlayer(player, new FPSMatchStatsResetS2CPacket(), true);
        player.setGameMode(GameType.ADVENTURE);
        this.getMapTeams().leaveTeam(player);
    }


    public MapTeams.JoinTeamResult join(ServerPlayer player) {
        MapTeams mapTeams = this.getMapTeams();
        Optional<String> reservedTeam = mapTeams.getReservedTeamName(player.getUUID());
        if (reservedTeam.isPresent()) {
            return this.join(reservedTeam.get(), player);
        }
        List<ServerTeam> baseTeams = mapTeams.getNormalTeams();
        if (baseTeams.isEmpty()) return MapTeams.JoinTeamResult.of(MapTeams.JoinTeamResult.Status.NO_AVAILABLE_TEAM);

        List<ServerTeam> teams = new ArrayList<>();
        int minPlayerCount = 0;
        boolean firstFlag = true;
        for (ServerTeam t : baseTeams) {
            if (firstFlag || t.getPlayerCount() < minPlayerCount) {
                minPlayerCount = t.getPlayerCount();
                teams.clear();
                teams.add(t);
                firstFlag = false;
            } else if (t.getPlayerCount() == minPlayerCount) {
                teams.add(t);
            }
        }
        ServerTeam team = teams.size() == 1
                ? teams.get(0)
                : teams.get(new Random().nextInt(0, teams.size()));

        return this.join(team.name, player);
    }

    /**
     * 加入团队
     *
     * @param teamName 团队名称
     * @param player   玩家对象
     */
    public MapTeams.JoinTeamResult join(String teamName, ServerPlayer player) {
        if (this.isStart() && !this.checkGameHasPlayer(player) && !this.allowJoinInProgress.get()) {
            return MapTeams.JoinTeamResult.of(MapTeams.JoinTeamResult.Status.MID_MATCH_JOIN_DISABLED);
        }

        if (NeoForge.EVENT_BUS.post(new FPSMapEvent.PlayerEvent.JoinEvent(this, player)).isCanceled()) {
            return MapTeams.JoinTeamResult.of(MapTeams.JoinTeamResult.Status.CANCELLED);
        }

        FPSMCore.checkAndLeaveTeam(player);
        this.pullGameInfo(player);
        return this.getMapTeams().joinTeam(teamName, player);
    }

    public boolean allowJoinInProgress() {
        return this.allowJoinInProgress.get();
    }

    public void teleportPlayerToReSpawnPoint(ServerPlayer player) {
        this.getMapTeams().getTeamByPlayer(player)
                .ifPresent(team -> team.getPlayerData(player.getUUID()).ifPresent(playerData -> {
                    SpawnPointData currentPoint = playerData.getSpawnPointsData();
                    if (currentPoint == null) {
                        currentPoint = team.getCapabilityMap().get(SpawnPointCapability.class)
                                .flatMap(cap -> cap.assignNextSpawnPoint(player.getUUID()))
                                .orElse(null);
                    }
                    if (currentPoint == null) {
                        player.sendSystemMessage(Component.translatable("message.fpsmatch.error.no_spawn_points")
                                .withStyle(ChatFormatting.RED), false);
                        return;
                    }

                    player.setRespawnPosition(new ServerPlayer.RespawnConfig(LevelData.RespawnData.of(currentPoint.getDimension(), currentPoint.getBlockPos(), currentPoint.getYaw(), currentPoint.getPitch()), true), false);
                    if (teleportToPoint(player, currentPoint)) {
                        team.getCapabilityMap().get(SpawnPointCapability.class)
                                .ifPresent(cap -> cap.assignNextSpawnPoint(player.getUUID()));
                    }
                }));
    }

    public boolean teleportToPoint(ServerPlayer player, SpawnPointData data) {
        if (!Level.isInSpawnableBounds(data.getBlockPos())) return false;
        ServerLevel targetLevel = this.getServerLevel().getServer().getLevel(data.getDimension());
        if (targetLevel == null) {
            return false;
        }

        player.setCamera(player);
        Set<Relative> set = EnumSet.noneOf(Relative.class);
        if (player.teleportTo(targetLevel, data.getX(), data.getY(), data.getZ(), set, data.getYaw(), data.getPitch(), false)) {
            label23:
            {
                if (player.isFallFlying()) {
                    break label23;
                }

                player.setDeltaMovement(player.getDeltaMovement().multiply(1.0D, 0.0D, 1.0D));
                player.setOnGround(true);
            }
            return true;
        }
        return false;
    }

    public void clearInventory(UUID uuid, Predicate<ItemStack> inventoryPredicate) {
        Player player = this.getServerLevel().getPlayerByUUID(uuid);
        if (player instanceof ServerPlayer serverPlayer) {
            this.clearInventory(serverPlayer, inventoryPredicate);
        }
    }

    public void clearInventory(ServerPlayer player, Predicate<ItemStack> predicate) {
        player.getInventory().clearOrCountMatchingItems(predicate, -1, player.inventoryMenu.getCraftSlots());
        player.containerMenu.broadcastChanges();
        player.inventoryMenu.slotsChanged(player.getInventory());
    }

    public void clearInventory(ServerPlayer player) {
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
     *
     * @return 服务器世界对象
     */
    public ServerLevel getServerLevel() {
        return serverLevel;
    }

    /**
     * 是否处于调试模式
     *
     * @return 是否处于调试模式
     */
    public boolean isDebug() {
        return isDebug;
    }

    /**
     * 切换调试模式
     *
     * @return 切换后的调试模式状态
     */
    public boolean switchDebugMode() {
        this.isDebug = !this.isDebug;
        return this.isDebug;
    }

    /**
     * 获取地图名称
     *
     * @return 地图名称
     */
    public String getMapName() {
        return mapName;
    }

    /**
     * 获取游戏类型
     *
     * @return 游戏类型
     */
    public abstract String getGameType();

    /**
     * 重新加载地图逻辑
     */
    public boolean reload() {
        boolean flag = !NeoForge.EVENT_BUS.post(new FPSMapEvent.ReloadEvent(this)).isCanceled();
        if (flag) {
            loadConfig();
        }
        return flag;
    }

    public final void load() {
        if (FPSMCore.getInstance().isRegistered(this)) return;

        NeoForge.EVENT_BUS.post(new FPSMapEvent.LoadEvent(this));
        FPSMCore.getInstance().registerMap(this.getGameType(), this);
    }


    /**
     * 获取地图的所有能力
     *
     * @return 能力实例集合
     */
    public CapabilityMap<BaseMap, MapCapability> getCapabilityMap() {
        return capabilities;
    }

    /**
     * 比较两张地图是否相等
     *
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
     *
     * @return 地图区域数据对象
     */
    public AreaData getMapArea() {
        return mapArea;
    }

    /**
     * 发送数据包给所有玩家
     *
     * @param packet 数据包对象
     * @param <MSG>  数据包类型
     */
    public <MSG> void sendPacketToAllPlayer(MSG packet) {
        this.getMapTeams().getJoinedPlayersWithSpec().forEach(uuid ->
                this.getPlayerByUUID(uuid).ifPresent(player ->
                        this.sendPacketToJoinedPlayer(player, packet, true)
                )
        );
    }

    public <MSG> void sendPacketToSpecPlayer(MSG packet) {
        this.getMapTeams().getSpecPlayers().forEach(uuid ->
                this.getPlayerByUUID(uuid).ifPresent(player ->
                        this.sendPacketToJoinedPlayer(player, packet, true)
                )
        );
    }

    public <MSG> void sendPacketToTeamPlayer(ServerTeam team, MSG packet, boolean living) {
        team.getPlayersData().forEach(data ->
                data.getPlayer().ifPresent(player -> {
                    if (data.isLiving() || !living) {
                        this.sendPacketToJoinedPlayer(player, packet, true);
                    }
                })
        );
    }

    public <MSG> void sendPacketToTeamLivingPlayer(ServerTeam team, MSG packet) {
        this.sendPacketToTeamPlayer(team, packet, true);
    }

    /**
     * 发送数据包给加入游戏的玩家
     *
     * @param player  玩家对象
     * @param packet  数据包对象
     * @param noCheck 是否跳过检查
     * @param <MSG>   数据包类型
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

    public Optional<ServerPlayer> getPlayerByUUID(UUID uuid) {
        return FPSMCore.getInstance().getPlayerByUUID(uuid);
    }

    public void pullGameInfo(ServerPlayer player) {
        this.sendPacketToJoinedPlayer(player, new FPSMatchGameTypeS2CPacket(this.getMapName(), this.getGameType()), true);
    }

    public final boolean isStart() {
        return this.isStart;
    }

    /**
     * 验证攻击是否有效
     */
    public boolean isValidAttack(ServerPlayer attacker, ServerPlayer hurt) {
        return attacker != null &&
                !attacker.isDeadOrDying() &&
                !attacker.getUUID().equals(hurt.getUUID());
    }

    /**
     * 记录有效的伤害来源，用于后续助攻计算。
     */
    public void recordHurtData(ServerPlayer hurt, DamageSource source, float amount) {
        getAttackerFromDamageSource(source).ifPresent(attacker -> {
            if (!isValidAttack(attacker, hurt)) return;
            if (!getMapTeams().isSameTeam(attacker, hurt)) {
                getMapTeams().addHurtData(attacker, hurt, amount);
            }
        });
    }

    /**
     * 从伤害源中提取服务器玩家攻击者
     */
    public Optional<ServerPlayer> getAttackerFromDamageSource(DamageSource source) {
        if (source.getEntity() instanceof ServerPlayer serverPlayer) {
            return Optional.of(serverPlayer);
        }

        if (source.getDirectEntity() instanceof ServerPlayer serverPlayer) {
            return Optional.of(serverPlayer);
        }

        return Optional.ofNullable(FPSMUtil.getOwnerIfTraceable(source.getEntity(), source.getDirectEntity()));
    }

    /**
     * 将所有配置项序列化为 JSON 格式。
     * <p>
     * 遍历所有配置项，并调用每个配置项的 {@link Setting#toJson()} 方法，
     * 将其值编码为 JSON 元素并添加到一个 JSON 对象中。
     *
     * @return 包含所有配置项的 JSON 对象。
     */
    public JsonElement configToJson() {
        JsonElement json = new JsonObject();
        for (Setting<?> setting : settings()) {
            json.getAsJsonObject().add(setting.getConfigName(), setting.toJson());
        }
        return json;
    }

    /**
     * 从 JSON 格式反序列化配置项。
     * <p>
     * 遍历 JSON 对象中的每个键值对，并根据配置项的名称查找对应的配置项。
     * 如果找到匹配的配置项，则调用其 {@link Setting#fromJson(JsonElement)} 方法进行反序列化。
     * 如果未找到匹配的配置项，则记录警告日志。
     *
     * @param json 包含配置项的 JSON 对象。
     */
    public void configFromJson(JsonElement json) {
        JsonObject jsonObject = json.getAsJsonObject();
        for (Setting<?> setting : settings()) {
            if (jsonObject.has(setting.getConfigName())) {
                setting.fromJson(jsonObject.get(setting.getConfigName()));
            } else {
                FPSMatch.LOGGER.warn("Setting {} not found in config file.", setting.getConfigName());
            }
        }
    }

    /**
     * 获取当前地图的配置文件路径。
     * <p>
     * 如果地图实现了 {@link ISavePort} 接口，则根据地图名称生成配置文件路径。
     * 如果地图未实现该接口，则记录错误日志并返回 null。
     *
     * @return 配置文件路径，或 null（如果地图未实现 ISavePort 接口）。
     */
    public File getConfigFile() {
        File file = FPSMCore.getInstance().getFPSMDataManager().getSaveFolder(this);
        if (file == null) {
            FPSMatch.LOGGER.error("Failed to get config file for map {} because map does not implement ISavePort.", this.getMapName());
            return null;
        } else {
            return new File(file, this.getMapName() + ".cfg");
        }

    }

    /**
     * 加载地图配置文件。
     * <p>
     * 从配置文件路径读取 JSON 数据，并调用 {@link #configFromJson(JsonElement)} 方法反序列化配置项。
     * 如果配置文件不存在或读取失败，则记录错误日志。
     */
    public void loadConfig() {
        File dataFile = getConfigFile();
        if (dataFile == null) return;
        try {
            if (dataFile.exists()) {
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                FileReader reader = new FileReader(dataFile);
                this.configFromJson(gson.fromJson(reader, JsonElement.class));
                reader.close();
            }
        } catch (Exception e) {
            e.fillInStackTrace();
        }
    }

    /**
     * 保存地图配置文件。
     * <p>
     * 将所有配置项序列化为 JSON 格式，并写入到配置文件路径。
     * 如果配置文件不存在，则创建新文件。
     * 如果保存失败，则记录错误日志。
     */
    public void saveConfig() {
        File dataFile = getConfigFile();
        if (dataFile == null) return;
        try {
            if (!dataFile.exists() && !dataFile.createNewFile()) {
                return;
            }
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            FileWriter writer = new FileWriter(dataFile);
            gson.toJson(this.configToJson(), writer);
            writer.close();
        } catch (Exception e) {
            e.fillInStackTrace();
        }
    }

    public <I> Setting<I> addSetting(Setting<I> setting) {
        settings.add(setting);
        return setting;
    }

    /**
     * 添加一个整型配置项。
     *
     * @param configName   配置项名称。
     * @param defaultValue 默认值。
     * @return 添加的配置项。
     */
    public Setting<Integer> addSetting(String configName, int defaultValue) {
        return addSetting(Setting.of(configName, defaultValue));
    }

    /**
     * 添加一个长整型配置项。
     *
     * @param configName   配置项名称。
     * @param defaultValue 默认值。
     * @return 添加的配置项。
     */
    public Setting<Long> addSetting(String configName, long defaultValue) {
        return addSetting(Setting.of(configName, defaultValue));
    }

    /**
     * 添加一个浮点型配置项。
     *
     * @param configName   配置项名称。
     * @param defaultValue 默认值。
     * @return 添加的配置项。
     */
    public Setting<Float> addSetting(String configName, float defaultValue) {
        return addSetting(Setting.of(configName, defaultValue));
    }

    /**
     * 添加一个双精度浮点型配置项。
     *
     * @param configName   配置项名称。
     * @param defaultValue 默认值。
     * @return 添加的配置项。
     */
    public Setting<Double> addSetting(String configName, double defaultValue) {
        return addSetting(Setting.of(configName, defaultValue));
    }

    /**
     * 添加一个字节型配置项。
     *
     * @param configName   配置项名称。
     * @param defaultValue 默认值。
     * @return 添加的配置项。
     */
    public Setting<Byte> addSetting(String configName, byte defaultValue) {
        return addSetting(Setting.of(configName, defaultValue));
    }

    /**
     * 添加一个布尔型配置项。
     *
     * @param configName   配置项名称。
     * @param defaultValue 默认值。
     * @return 添加的配置项。
     */
    public Setting<Boolean> addSetting(String configName, boolean defaultValue) {
        return addSetting(Setting.of(configName, defaultValue));
    }

    /**
     * 添加一个字符串配置项。
     *
     * @param configName   配置项名称。
     * @param defaultValue 默认值。
     * @return 添加的配置项。
     */
    public Setting<String> addSetting(String configName, String defaultValue) {
        return addSetting(Setting.of(configName, defaultValue));
    }

    public Optional<Setting<?>> findSetting(String settingName) {
        return settings().stream()
                .filter(setting -> setting.getConfigName().equals(settingName))
                .findFirst();
    }

    public float getMinAssistDamageRatio() {
        return minAssistDamageRatio.get();
    }

    public String getDisplayName() {
        String name = displayName.get();
        return name.isEmpty() ? mapName : name;
    }

    public void displayAreas(ServerPlayer player) {
        FPSMatch.sendToPlayer(player, new AddAreaDataS2CPacket(
                "map_preview:" + this.getGameType() + ":" + this.getMapName(),
                Component.literal(this.getMapName()),
                PreviewColorUtil.getMapPreviewColor(this.getGameType()),
                this.mapArea
        ));
    }
}
