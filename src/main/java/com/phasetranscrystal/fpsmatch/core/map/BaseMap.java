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
 * BaseMap 鎶借薄绫伙紝琛ㄧず娓告垙涓殑鍩虹鍦板浘�? */
@net.neoforged.fml.common.EventBusSubscriber(modid = FPSMatch.MODID)
public abstract class BaseMap {
    public final String mapName;
    protected boolean isStart = false;
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
    // 鍦板浘鍖哄煙鏁版�?
    public final AreaData mapArea;

    /**
     * BaseMap 绫荤殑鏋勯€犲嚱鏁般�?     *
     * @param serverLevel 鍦板浘鎵€鍦ㄤ笘鐣屻�?     * @param mapName     鍦板浘鍚嶇О銆?     * @param areaData    鍦板浘鐨勫尯鍩熸暟鎹€?     */
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
     * 鑾峰彇褰撳墠鍦板浘鐨勬墍鏈夐厤缃」闆嗗悎�?     *
     * @return 閰嶇疆椤归泦鍚堛�?     */
    public Collection<Setting<?>> settings() {
        return settings;
    }

    ;

    /**
     * 娣诲姞鍥㈤槦
     *
     * @param data 鍥㈤槦鏁版嵁
     */
    public ServerTeam addTeam(TeamData data) {
        return this.mapTeams.addTeam(data);
    }

    public ServerTeam getSpectatorTeam() {
        return this.mapTeams.getSpectatorTeam();
    }

    /**
     * 鍦板浘姣忎釜 tick 鐨勬搷浣?     */
    public final void mapTick() {
        checkForVictory();
        tick();
        applyTeammateGlow();
        getMapTeams().tick();
        capabilities.tick();
        syncToClient();
    }

    /**
     * 涓洪槦鍙嬪簲鐢ㄩ€忚鍙戝厜鏁堟灉锛屼娇鐜╁鍙€忚繃澧欏鐪嬪埌闃熷弸浣嶇疆�?     * <p>
     * 宸叉敼涓哄鎴风鎸夐槦浼嶅垽鏂紝涓嶅啀閫氳繃鏈嶅姟�?{@link MobEffects#GLOWING} 瀹炵幇銆?     * 鏈嶅姟绔?GLOWING 鏁堟灉瀵规墍鏈夊鎴风鍙锛屼細瀵艰嚧鏁屾柟涔熻兘鐪嬪埌鍙戝厜銆?     * 闃熷弸閫忚鍙戝厜閫昏緫�?{@code MixinEntityUnified#onIsCurrentlyGlowing}�?     */
    protected void applyTeammateGlow() {
    }

    public void syncToClient() {
    }

    ;

    /**
     * 姣忎�?tick 鐨勬搷浣?     */
    public void tick() {
    }

    /**
     * 妫€鏌ヨ儨鍒╂潯�?     */
    public final void checkForVictory() {
        if (this.victoryGoal()) {
            this.victory();
        }
    }

    /**
     * 寮€濮嬫父鎴?     */
    public boolean start() {
        return !NeoForge.EVENT_BUS.post(new FPSMapEvent.StartEvent(this)).isCanceled();
    }

    ;

    /**
     * 妫€鏌ョ帺瀹舵槸鍚﹀湪娓告垙�?     *
     * @param player 鐜╁瀵硅�?
     * @return 鏄惁鍦ㄦ父鎴忎�?
     */
    public boolean checkGameHasPlayer(Player player) {
        return this.checkGameHasPlayer(player.getUUID());
    }

    /**
     * 妫€鏌ョ帺瀹舵槸鍚﹀湪娓告垙�?     *
     * @param player 鐜╁瀵硅�?
     * @return 鏄惁鍦ㄦ父鎴忎�?
     */
    public boolean checkGameHasPlayer(UUID player) {
        return this.getMapTeams()
                .getJoinedUUID().contains(player);
    }

    public boolean checkSpecHasPlayer(Player player) {
        return this.getMapTeams().getSpecPlayers().contains(player.getUUID());
    }

    /**
     * 寮€濮嬫柊涓€杞父�?     */
    public void startNewRound() {
    }


    /**
     * 褰撳灞€鍐呯帺瀹舵浜?     *
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
     * 瑙ｆ瀽姝讳骸姝﹀櫒锛堝熀纭€瀹炵幇锛氫富鎵嬬墿鍝侊級
     */
    public ItemStack resolveDeathItem(@Nullable ServerPlayer attacker, DamageSource source) {
        return attacker == null ? ItemStack.EMPTY : attacker.getMainHandItem();
    }

    /**
     * 鑳滃埄鎿嶄綔
     */
    public void victory() {
        NeoForge.EVENT_BUS.post(new FPSMapEvent.VictoryEvent(this));
    }

    ;

    /**
     * 鑳滃埄鏉′欢
     *
     * @return 鏄惁婊¤冻鑳滃埄鏉′欢
     */
    public abstract boolean victoryGoal();

    /**
     * 娓呯悊鍦板浘
     */
    public boolean cleanupMap() {
        return !NeoForge.EVENT_BUS.post(new FPSMapEvent.ClearEvent(this)).isCanceled();
    }

    /**
     * 閲嶇疆娓告垙
     */
    public void reset() {
        NeoForge.EVENT_BUS.post(new FPSMapEvent.ResetEvent(this));
    }

    ;

    /**
     * 鑾峰彇鍦板浘鍥㈤�?
     *
     * @return 鍦板浘鍥㈤槦瀵硅�?
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
     * 鍔犲叆鍥㈤槦
     *
     * @param teamName 鍥㈤槦鍚嶇�?
     * @param player   鐜╁瀵硅�?
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
     * 鑾峰彇鏈嶅姟鍣ㄤ笘鐣?     *
     * @return 鏈嶅姟鍣ㄤ笘鐣屽璞?     */
    public ServerLevel getServerLevel() {
        return serverLevel;
    }

    /**
     * 鏄惁澶勪簬璋冭瘯妯″紡
     *
     * @return 鏄惁澶勪簬璋冭瘯妯″紡
     */
    public boolean isDebug() {
        return isDebug;
    }

    /**
     * 鍒囨崲璋冭瘯妯″紡
     *
     * @return 鍒囨崲鍚庣殑璋冭瘯妯″紡鐘舵�?     */
    public boolean switchDebugMode() {
        this.isDebug = !this.isDebug;
        return this.isDebug;
    }

    /**
     * 鑾峰彇鍦板浘鍚嶇О
     *
     * @return 鍦板浘鍚嶇�?
     */
    public String getMapName() {
        return mapName;
    }

    /**
     * 鑾峰彇娓告垙绫诲�?
     *
     * @return 娓告垙绫诲�?
     */
    public abstract String getGameType();

    /**
     * 閲嶆柊鍔犺浇鍦板浘閫昏緫
     *
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
     * 鑾峰彇鍦板浘鐨勬墍鏈夎兘�?     *
     * @return 鑳藉姏瀹炰緥闆嗗悎
     */
    public CapabilityMap<BaseMap, MapCapability> getCapabilityMap() {
        return capabilities;
    }

    /**
     * 姣旇緝涓ゅ紶鍦板浘鏄惁鐩哥�?
     *
     * @param object 姣旇緝瀵硅�?
     * @return 鏄惁鐩哥瓑
     */
    public boolean equals(Object object) {
        if (object instanceof BaseMap map) {
            return map.getMapName().equals(this.getMapName()) && map.getGameType().equals(this.getGameType());
        } else {
            return false;
        }
    }

    /**
     * 鑾峰彇鍦板浘鍖哄煙鏁版嵁
     *
     * @return 鍦板浘鍖哄煙鏁版嵁瀵硅�?
     */
    public AreaData getMapArea() {
        return mapArea;
    }

    /**
     * 鍙戦€佹暟鎹寘缁欐墍鏈夌帺�?     *
     * @param packet 鏁版嵁鍖呭�?     * @param <MSG>  鏁版嵁鍖呯被�?     */
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
     * 鍙戦€佹暟鎹寘缁欏姞鍏ユ父鎴忕殑鐜╁
     *
     * @param player  鐜╁瀵硅�?
     * @param packet  鏁版嵁鍖呭�?     * @param noCheck 鏄惁璺宠繃妫€�?     * @param <MSG>   鏁版嵁鍖呯被�?     */
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
     * 楠岃瘉鏀诲嚮鏄惁鏈夋晥
     */
    public boolean isValidAttack(ServerPlayer attacker, ServerPlayer hurt) {
        return attacker != null &&
                !attacker.isDeadOrDying() &&
                !attacker.getUUID().equals(hurt.getUUID());
    }

    /**
     * 璁板綍鏈夋晥鐨勪激瀹虫潵婧愶紝鐢ㄤ簬鍚庣画鍔╂敾璁＄畻�?     */
    public void recordHurtData(ServerPlayer hurt, DamageSource source, float amount) {
        getAttackerFromDamageSource(source).ifPresent(attacker -> {
            if (!isValidAttack(attacker, hurt)) return;
            if (!getMapTeams().isSameTeam(attacker, hurt)) {
                getMapTeams().addHurtData(attacker, hurt, amount);
            }
        });
    }

    /**
     * 浠庝激瀹虫簮涓彁鍙栨湇鍔″櫒鐜╁鏀诲嚮�?     */
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
     * 灏嗘墍鏈夐厤缃」搴忓垪鍖栦�?JSON 鏍煎紡銆?     * <p>
     * 閬嶅巻鎵€鏈夐厤缃」锛屽苟璋冪敤姣忎釜閰嶇疆椤圭殑 {@link Setting#toJson()} 鏂规硶锛?     * 灏嗗叾鍊肩紪鐮佷�?JSON 鍏冪礌骞舵坊鍔犲埌涓€涓?JSON 瀵硅薄涓€?     *
     * @return 鍖呭惈鎵€鏈夐厤缃」鐨?JSON 瀵硅薄銆?     */
    public JsonElement configToJson() {
        JsonElement json = new JsonObject();
        for (Setting<?> setting : settings()) {
            json.getAsJsonObject().add(setting.getConfigName(), setting.toJson());
        }
        return json;
    }

    /**
     * �?JSON 鏍煎紡鍙嶅簭鍒楀寲閰嶇疆椤广�?     * <p>
     * 閬嶅�?JSON 瀵硅薄涓殑姣忎釜閿€煎锛屽苟鏍规嵁閰嶇疆椤圭殑鍚嶇О鏌ユ壘瀵瑰簲鐨勯厤缃」銆?     * 濡傛灉鎵惧埌鍖归厤鐨勯厤缃」锛屽垯璋冪敤鍏?{@link Setting#fromJson(JsonElement)} 鏂规硶杩涜鍙嶅簭鍒楀寲銆?     * 濡傛灉鏈壘鍒板尮閰嶇殑閰嶇疆椤癸紝鍒欒褰曡鍛婃棩蹇椼€?     *
     * @param json 鍖呭惈閰嶇疆椤圭�?JSON 瀵硅薄銆?     */
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
     * 鑾峰彇褰撳墠鍦板浘鐨勯厤缃枃浠惰矾寰勩�?     * <p>
     * 濡傛灉鍦板浘瀹炵幇浜?{@link ISavePort} 鎺ュ彛锛屽垯鏍规嵁鍦板浘鍚嶇О鐢熸垚閰嶇疆鏂囦欢璺緞�?     * 濡傛灉鍦板浘鏈疄鐜拌鎺ュ彛锛屽垯璁板綍閿欒鏃ュ織骞惰繑�?null�?     *
     * @return 閰嶇疆鏂囦欢璺緞锛屾垨 null锛堝鏋滃湴鍥炬湭瀹炵�?ISavedData 鎺ュ彛锛夈€?     */
    public File getConfigFile() {
        File file = FPSMCore.getInstance().getFPSMDataManager().getSaveFolder(this);
        if (file == null) {
            FPSMatch.LOGGER.error("Failed to get config file for map {} because 锛歁ap is not implement ISavedData interface.", this.getMapName());
            return null;
        } else {
            return new File(file, this.getMapName() + ".cfg");
        }

    }

    /**
     * 鍔犺浇鍦板浘閰嶇疆鏂囦欢�?     * <p>
     * 浠庨厤缃枃浠惰矾寰勮�?JSON 鏁版嵁锛屽苟璋冪�?{@link #configFromJson(JsonElement)} 鏂规硶鍙嶅簭鍒楀寲閰嶇疆椤广�?     * 濡傛灉閰嶇疆鏂囦欢涓嶅瓨鍦ㄦ垨璇诲彇澶辫触锛屽垯璁板綍閿欒鏃ュ織銆?     */
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
     * 淇濆瓨鍦板浘閰嶇疆鏂囦欢�?     * <p>
     * 灏嗘墍鏈夐厤缃」搴忓垪鍖栦�?JSON 鏍煎紡锛屽苟鍐欏叆鍒伴厤缃枃浠惰矾寰勩�?     * 濡傛灉閰嶇疆鏂囦欢涓嶅瓨鍦紝鍒欏垱寤烘柊鏂囦欢�?     * 濡傛灉淇濆瓨澶辫触锛屽垯璁板綍閿欒鏃ュ織銆?     */
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
     * 娣诲姞涓€涓暣鍨嬮厤缃」銆?     *
     * @param configName   閰嶇疆椤瑰悕绉般�?     * @param defaultValue 榛樿鍊笺€?     * @return 娣诲姞鐨勯厤缃」銆?     */
    public Setting<Integer> addSetting(String configName, int defaultValue) {
        return addSetting(Setting.of(configName, defaultValue));
    }

    /**
     * 娣诲姞涓€涓暱鏁村瀷閰嶇疆椤广�?     *
     * @param configName   閰嶇疆椤瑰悕绉般�?     * @param defaultValue 榛樿鍊笺€?     * @return 娣诲姞鐨勯厤缃」銆?     */
    public Setting<Long> addSetting(String configName, long defaultValue) {
        return addSetting(Setting.of(configName, defaultValue));
    }

    /**
     * 娣诲姞涓€涓诞鐐瑰瀷閰嶇疆椤广�?     *
     * @param configName   閰嶇疆椤瑰悕绉般�?     * @param defaultValue 榛樿鍊笺€?     * @return 娣诲姞鐨勯厤缃」銆?     */
    public Setting<Float> addSetting(String configName, float defaultValue) {
        return addSetting(Setting.of(configName, defaultValue));
    }

    /**
     * 娣诲姞涓€涓弻绮惧害娴偣鍨嬮厤缃」銆?     *
     * @param configName   閰嶇疆椤瑰悕绉般�?     * @param defaultValue 榛樿鍊笺€?     * @return 娣诲姞鐨勯厤缃」銆?     */
    public Setting<Double> addSetting(String configName, double defaultValue) {
        return addSetting(Setting.of(configName, defaultValue));
    }

    /**
     * 娣诲姞涓€涓瓧鑺傚瀷閰嶇疆椤广�?     *
     * @param configName   閰嶇疆椤瑰悕绉般�?     * @param defaultValue 榛樿鍊笺€?     * @return 娣诲姞鐨勯厤缃」銆?     */
    public Setting<Byte> addSetting(String configName, byte defaultValue) {
        return addSetting(Setting.of(configName, defaultValue));
    }

    /**
     * 娣诲姞涓€涓竷灏斿瀷閰嶇疆椤广�?     *
     * @param configName   閰嶇疆椤瑰悕绉般�?     * @param defaultValue 榛樿鍊笺€?     * @return 娣诲姞鐨勯厤缃」銆?     */
    public Setting<Boolean> addSetting(String configName, boolean defaultValue) {
        return addSetting(Setting.of(configName, defaultValue));
    }

    /**
     * 娣诲姞涓€涓瓧绗︿覆閰嶇疆椤广�?     *
     * @param configName   閰嶇疆椤瑰悕绉般�?     * @param defaultValue 榛樿鍊笺€?     * @return 娣诲姞鐨勯厤缃」銆?     */
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
