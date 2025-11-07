package com.phasetranscrystal.fpsmatch.core.team;

import com.phasetranscrystal.fpsmatch.core.data.PlayerData;
import com.phasetranscrystal.fpsmatch.core.entity.FPSMPlayer;
import com.phasetranscrystal.fpsmatch.core.team.capability.TeamCapability;
import com.phasetranscrystal.fpsmatch.core.team.capability.TeamCapabilityManager;
import com.phasetranscrystal.fpsmatch.core.team.capability.TeamSyncedCapability;
import com.phasetranscrystal.fpsmatch.util.RenderUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.PlayerTeam;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.*;
import java.util.stream.Collectors;

@SuppressWarnings("unchecked")
public abstract class BaseTeam {
    public final String name;
    public final String gameType;
    public final String mapName;
    private final int playerLimit;
    private final PlayerTeam playerTeam;
    private int scores = 0;
    private Vector3f color = new Vector3f(1, 1, 1);
    private final Map<Class<? extends TeamCapability>, TeamCapability> capabilities = new HashMap<>();

    public BaseTeam(String gameType, String mapName, String name, int playerLimit, PlayerTeam playerTeam) {
        this.gameType = gameType;
        this.mapName = mapName;
        this.name = name;
        this.playerLimit = playerLimit;
        this.playerTeam = playerTeam;
    }

    public abstract void join(FPSMPlayer player);
    public abstract void leave(FPSMPlayer player);
    public abstract void delPlayer(UUID player);
    public abstract void resetLiving();
    public abstract Optional<PlayerData> getPlayerData(UUID player);
    public abstract List<PlayerData> getPlayersData();
    public abstract List<UUID> getPlayerList();
    public abstract boolean hasPlayer(UUID uuid);
    public abstract int getPlayerCount();
    public abstract boolean isEmpty();
    public abstract Map<UUID, PlayerData> getPlayers();
    public abstract void clearAndPutPlayers(Map<UUID, PlayerData> players);

    /**
     * 添加能力到队伍（通过管理器创建实例）
     * @param capabilityClass 能力类型
     * @param <T> 能力类型
     * @return 是否添加成功
     */
    public final <T extends TeamCapability> boolean addCapability(Class<T> capabilityClass) {
        if (hasCapability(capabilityClass)) {
            return false;
        }
        return TeamCapabilityManager.createInstance(this, capabilityClass)
                .map(capability -> {
                    capabilities.put(capabilityClass, capability);
                    capability.init();
                    return true;
                })
                .orElse(false);
    }

    /**
     * 移除队伍的能力
     * @param capabilityClass 能力类型
     * @param <T> 能力类型
     */
    public final <T extends TeamCapability> void removeCapability(Class<T> capabilityClass) {
        getCapability(capabilityClass).ifPresent(TeamCapability::destroy);
        capabilities.remove(capabilityClass);
    }

    /**
     * 获取队伍的能力
     * @param capabilityClass 能力类型
     * @param <T> 能力类型
     * @return 能力实例（Optional）
     */
    @SuppressWarnings("unchecked")
    public final <T extends TeamCapability> Optional<T> getCapability(Class<T> capabilityClass) {
        return Optional.ofNullable((T) capabilities.get(capabilityClass));
    }

    /**
     * 检查队伍是否拥有指定能力
     * @param capabilityClass 能力类型
     * @param <T> 能力类型
     * @return 是否拥有
     */
    public final <T extends TeamCapability> boolean hasCapability(Class<T> capabilityClass) {
        return capabilities.containsKey(capabilityClass);
    }

    /**
     * 重置所有能力状态
     */
    public final void resetAllCapabilities() {
        capabilities.values().forEach(TeamCapability::reset);
    }

    public final <T extends TeamCapability> void resetCapability(Class<T> capability) {
        getCapability(capability).ifPresent(TeamCapability::reset);
    }

    public final List<TeamCapability> getCapabilities(){
        return capabilities.values().stream().toList();
    }

    public final List<String> getCapabilitiesString(){
        return getCapabilities().stream().map(TeamCapability::getName).collect(Collectors.toList());
    }
    public final List<String> getSynchronizableCapabilitiesString(){
        return getCapabilities().stream().filter(cap -> cap instanceof TeamSyncedCapability).map(TeamCapability::getName).collect(Collectors.toList());
    }


    /**
     * 序列化指定能力到网络缓冲区
     * @param capabilityClass 要序列化的能力类
     * @param buf 网络缓冲区
     * @param <T> 能力类型
     */
    public final <T extends TeamSyncedCapability> void serializeCapability(Class<T> capabilityClass, FriendlyByteBuf buf) {
        getCapability(capabilityClass).ifPresent(capability -> {
            buf.writeUtf(capabilityClass.getName());
            capability.writeToBuf(buf);
        });
    }

    /**
     * 从网络缓冲区反序列化能力数据
     * @param buf 网络缓冲区
     */
    public final void deserializeCapability(FriendlyByteBuf buf) {
        String className = buf.readUtf();
        TeamCapabilityManager.getRegisteredCapabilityClass(className).ifPresent(capabilityClass -> {
            getCapability(capabilityClass).ifPresentOrElse(
                    capability -> {
                        if(capability instanceof TeamSyncedCapability syncedCapability){
                            syncedCapability.readFromBuf(buf);
                        }
                    },
                    () -> TeamCapabilityManager.createFromNetwork(this, className, buf)
                            .ifPresent(this::addCapabilityInstanceDirectly)
            );
        });
    }

    /**
     * 直接添加能力实例（用于网络同步）
     * @param capability 能力实例
     * @param <T> 能力类型
     */
    private <T extends TeamCapability> void addCapabilityInstanceDirectly(T capability) {
        Class<T> capabilityClass = (Class<T>) capability.getClass();
        if (!hasCapability(capabilityClass)) {
            capabilities.put(capabilityClass, capability);
            capability.init();
        }
    }

    /**
     * 获取需要同步的能力列表
     *
     * @return 需要同步的能力列表
     */
    public final List<Class<? extends TeamSyncedCapability>> getSynchronizableCapabilities() {
        return getCapabilities().stream()
                .filter(capability -> capability instanceof TeamSyncedCapability)
                .map(cap -> ((TeamSyncedCapability) cap).getClass())
                .collect(Collectors.toList());
    }


    public abstract void sendMessage(Component message, boolean onlyLiving);

    public abstract boolean isClientSide();

    // 公共方法实现
    public int getPlayerLimit() {
        return playerLimit;
    }

    public int getRemainingLimit() {
        return playerLimit - getPlayerCount();
    }

    /**
     * @apiNote  只在服务端返回不为null
     * */
    public @Nullable PlayerTeam getPlayerTeam() {
        return playerTeam;
    }

    public int getScores() {
        return scores;
    }

    public void setScores(int scores) {
        this.scores = scores;
    }

    public String getFixedName() {
        return this.gameType + "_" + this.mapName + "_" + this.name;
    }

    public Vector3f getColorVec3f() {
        return color;
    }

    public int getColor(){
        return RenderUtil.color(color);
    }

    public void setColor(Vector3f color) {
        this.color = color;
    }

    public void resetCapabilities(){
        this.capabilities.values().forEach(TeamCapability::reset);
    }

    public void reset(){
        this.resetCapabilities();
        this.setScores(0);
    }

    public void clean(){
        this.reset();
        this.getPlayers().clear();
    }

    @Override
    public boolean equals(Object obj) {
        if(obj instanceof BaseTeam team){
            return this.gameType.equals(team.gameType) && this.mapName.equals(team.mapName) && this.name.equals(team.name);
        }
        return false;
    }
}