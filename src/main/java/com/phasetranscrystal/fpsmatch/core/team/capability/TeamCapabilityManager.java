package com.phasetranscrystal.fpsmatch.core.team.capability;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.core.team.BaseTeam;
import net.minecraft.network.FriendlyByteBuf;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 队伍能力管理器，负责注册和提供所有TeamCapability的实例创建逻辑
 */
@SuppressWarnings("unchecked")
public class TeamCapabilityManager {
    private static final Map<Class<? extends TeamCapability>, TeamCapability.Factory<?>> CAPABILITY_FACTORIES = new HashMap<>();

    /**
     * 注册队伍能力工厂
     * @param capabilityClass 能力类型Class
     * @param factory 能力工厂实例
     * @param <T> 能力类型
     */
    public static <T extends TeamCapability> void register(Class<T> capabilityClass, TeamCapability.Factory<T> factory) {
        if (isRegistered(capabilityClass)) {
            throw new IllegalArgumentException("Capability " + capabilityClass.getSimpleName() + " already registered!");
        }
        CAPABILITY_FACTORIES.put(capabilityClass, factory);
    }

    public static <T extends TeamCapability> boolean isRegistered(Class<T> capabilityClass){
        return CAPABILITY_FACTORIES.containsKey(capabilityClass);
    }

    /**
     * 获取能力工厂
     * @param capabilityClass 能力类型Class
     * @param <T> 能力类型
     * @return 能力工厂（Optional）
     */
    public static <T extends TeamCapability> Optional<TeamCapability.Factory<T>> getFactory(Class<T> capabilityClass) {
        return Optional.ofNullable((TeamCapability.Factory<T>) CAPABILITY_FACTORIES.get(capabilityClass));
    }

    /**
     * 为队伍创建能力实例
     * @param team 队伍实例
     * @param capabilityClass 能力类型
     * @param <T> 能力类型
     * @return 能力实例（Optional）
     */
    public static <T extends TeamCapability> Optional<T> createInstance(BaseTeam team, Class<T> capabilityClass) {
        return getFactory(capabilityClass).map(factory -> factory.create(team));
    }

    public static Optional<Class<? extends TeamCapability>> getRegisteredCapabilityClass(String clazz){
        for (Class<? extends TeamCapability> capabilityClass : CAPABILITY_FACTORIES.keySet()) {
            if (capabilityClass.getSimpleName().equals(clazz)) {
                return Optional.of(capabilityClass);
            }
        }
        return Optional.empty();
    }

    /**
     * 从网络数据创建能力实例
     * @param team 队伍实例
     * @param className 能力类名
     * @param buf 网络缓冲区
     * @return 能力实例（Optional）
     */
    public static Optional<? extends TeamSyncedCapability> createFromNetwork(BaseTeam team, String className, FriendlyByteBuf buf) {
        Optional<Class<? extends TeamCapability>> clazz = getRegisteredCapabilityClass(className);
        if (clazz.isPresent()) {
            Optional<? extends TeamCapability.Factory<? extends TeamCapability>> factory = getFactory(clazz.get());
            if (factory.isPresent()) {
                try {
                    TeamCapability instance = factory.get().create(team);
                    if (instance instanceof TeamSyncedCapability syncedCapability) {
                        syncedCapability.readFromBuf(buf);
                        return Optional.of(syncedCapability);
                    }else{
                        return Optional.empty();
                    }
                } catch (Exception e) {
                    FPSMatch.debug("Failed to create capabilities from network: " + className + ": " + e.getMessage());
                }
            }
        }
        return Optional.empty();
    }

}