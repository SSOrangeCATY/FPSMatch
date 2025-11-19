package com.phasetranscrystal.fpsmatch.core.capability;


import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.core.capability.map.MapCapability;
import com.phasetranscrystal.fpsmatch.core.capability.team.TeamCapability;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import com.phasetranscrystal.fpsmatch.core.team.BaseTeam;
import net.minecraft.network.FriendlyByteBuf;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@SuppressWarnings("unchecked")
public class CapabilityMap<H, T extends FPSMCapability<H>> {

    public static CapabilityMap<BaseMap, MapCapability> ofMapCapability(BaseMap map){
        return new CapabilityMap<>(map, MapCapability.class);
    }

    public static CapabilityMap<BaseTeam, TeamCapability> ofTeamCapability(BaseTeam team){
        return new CapabilityMap<>(team, TeamCapability.class);
    }

    private final Map<Class<? extends T>, T> capabilities = new ConcurrentHashMap<>();

    private final H holder;

    private final Class<T> capabilityType;

    public CapabilityMap(final H holder , final Class<T> capabilityType) {
        this.holder = holder;
        this.capabilityType = capabilityType;
    }

    /**
     * 添加能力到队伍（通过管理器创建实例）
     * @param capabilityClass 能力类型
     * @return 是否添加成功
     */
    public final <C extends T> boolean addCapability(Class<C> capabilityClass) {
        if (hasCapability(capabilityClass)) {
            return false;
        }
        return FPSMCapabilityManager.createInstance(this.getHolder(), capabilityClass)
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
     */
    public final <C extends T> void removeCapability(Class<C> capabilityClass) {
        if(getCapability(capabilityClass).map(FPSMCapability::isImmutable).orElse(true)) return;

        getCapability(capabilityClass).ifPresent(FPSMCapability::destroy);
        capabilities.remove(capabilityClass);
    }

    /**
     * 获取队伍的能力
     * @param capabilityClass 能力类型
     * @return 能力实例（Optional）
     */
    @SuppressWarnings("unchecked")
    public final <C extends T> Optional<C> getCapability(Class<C> capabilityClass) {
        return Optional.ofNullable((C) capabilities.get(capabilityClass));
    }

    /**
     * 检查队伍是否拥有指定能力
     * @param capabilityClass 能力类型
     * @return 是否拥有
     */
    public final <C extends T> boolean hasCapability(Class<C> capabilityClass) {
        return capabilities.containsKey(capabilityClass);
    }

    /**
     * 重置所有能力状态
     */
    public final void resetAllCapabilities() {
        capabilities.values().forEach(FPSMCapability::reset);
    }

    public final <C extends T> void resetCapability(Class<C> capability) {
        getCapability(capability).ifPresent(FPSMCapability::reset);
    }

    public final List<T> getCapabilities(){
        return capabilities.values().stream().toList();
    }

    public final List<String> getCapabilitiesString(){
        return getCapabilities().stream().map(FPSMCapability::getName).collect(Collectors.toList());
    }
    public final List<String> getSynchronizableCapabilitiesString(){
        return getCapabilities().stream().filter(cap -> cap instanceof FPSMCapability.Synchronizable).map(FPSMCapability::getName).collect(Collectors.toList());
    }

    /**
     * 序列化指定能力到网络缓冲区
     * @param capabilityClass 要序列化的能力类
     * @param buf 网络缓冲区
     */
    public final <C extends FPSMCapability<H> & FPSMCapability.Synchronizable> void serializeCapability(Class<C> capabilityClass, FriendlyByteBuf buf) {
        getCapability((Class<T>) capabilityClass).ifPresent(capability -> {
            buf.writeUtf(capabilityClass.getName());
            ((FPSMCapability.Synchronizable) capability).writeToBuf(buf);
        });
    }

    /**
     * 从网络缓冲区反序列化能力数据
     * @param buf 网络缓冲区
     */
    public final void deserializeCapability(FriendlyByteBuf buf) {
        String className = buf.readUtf();
        FPSMCapabilityManager.getRegisteredCapabilityClassByFormated(className, getCapabilityType()).ifPresent(capabilityClass -> {
            getCapability(capabilityClass).ifPresentOrElse(
                    capability -> {
                        if (capability instanceof FPSMCapability.Synchronizable synced) {
                            synced.readFromBuf(buf);
                        }
                    },
                    () -> FPSMCapabilityManager.createFromNetwork(this, className, buf)
                            .ifPresent(capability -> {
                                if (getCapabilityType().isAssignableFrom(capability.getClass())) {
                                    this.addCapabilityInstanceDirectly((T) capability);
                                }
                            })
            );
        });
    }

    /**
     * 直接添加能力实例（用于网络同步）
     * @param capability 能力实例
     */
    private void addCapabilityInstanceDirectly(T capability) {
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
    public final <C extends FPSMCapability<H> & FPSMCapability.Synchronizable> List<Class<C>> getSynchronizableCapabilityClasses() {
        return getCapabilities().stream()
                .filter(capability -> capability instanceof FPSMCapability.Synchronizable)
                .map(cap -> (Class<C>) cap.getClass())
                .collect(Collectors.toList());
    }

    public H getHolder() {
        return holder;
    }

    public Class<T> getCapabilityType() {
        return capabilityType;
    }

    public void clear(){
        for (Map.Entry<Class<? extends T>, T> capability : capabilities.entrySet()) {
            if(!capability.getValue().isImmutable()){
                capability.getValue().destroy();
                capabilities.remove(capability.getKey());
            }
        }
    }

    public List<FPSMCapability.Savable<?>> getSaveData() {
        return capabilities.values().stream()
                .filter(FPSMCapability.Savable.class::isInstance)
                .map(cap -> (FPSMCapability.Savable<?>) cap)
                .collect(Collectors.toList());
    }

    public <D> void write(String className, D data) {
        FPSMCapabilityManager.getRegisteredCapabilityClassByFormated(className, getCapabilityType())
                .flatMap(this::getCapability).ifPresent(capability -> {
                    if (capability instanceof FPSMCapability.Savable<?> savable) {
                        try {
                            FPSMCapability.Savable<D> cap = (FPSMCapability.Savable<D>) savable;
                            cap.write(data);
                        } catch (Exception e) {
                            FPSMatch.LOGGER.error("Error while write capability", e);
                        }
                    }
                });
    }

    public <D, C extends FPSMCapability<H> & FPSMCapability.Savable<D>> void write(Class<C> clazz, D data) {
        getCapability((Class<T>) clazz).ifPresent(cap -> {
            ((FPSMCapability.Savable<D>) cap).write(data);
        });
    }

    public void write(Map<String,?> data){
        data.forEach(this::write);
    }
}
