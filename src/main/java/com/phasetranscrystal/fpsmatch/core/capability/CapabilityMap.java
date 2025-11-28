package com.phasetranscrystal.fpsmatch.core.capability;


import com.mojang.serialization.Codec;
import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.core.capability.map.MapCapability;
import com.phasetranscrystal.fpsmatch.core.capability.team.TeamCapability;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import com.phasetranscrystal.fpsmatch.core.team.BaseTeam;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.common.MinecraftForge;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SuppressWarnings("unchecked")
public class CapabilityMap<H, T extends FPSMCapability<H>> {
    Codec<CapabilityMap<H, T>> codec;

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
    public final <C extends T> boolean add(Class<C> capabilityClass) {
        return FPSMCapabilityManager.createInstance(this.getHolder(), capabilityClass).map(this::addDirectly).orElse(false);
    }

    public final boolean add(T capability){
        if(!FPSMCapabilityManager.isRegistered(capability.getClass())) return false;
        this.addDirectly(capability);
        return true;
    }

    /**
     * 批量添加多种能力。
     * @param capabilityClasses 能力类型列表
     * @return 添加成功的能力类型列表
     */
    public final <C extends T> List<Class<C>> addAll(Collection<Class<C>> capabilityClasses) {
        List<Class<C>> added = new ArrayList<>();
        for (Class<C> clazz : capabilityClasses) {
            if (add(clazz)) {
                added.add(clazz);
            }
        }
        return added;
    }

    /**
     * 直接添加能力实例（用于网络同步）
     * @param capability 能力实例
     */
    private boolean addDirectly(T capability) {
        Class<T> capabilityClass = (Class<T>) capability.getClass();
        if (!contains(capabilityClass)) {
            capabilities.put(capabilityClass, capability);
            MinecraftForge.EVENT_BUS.register(capability);
            capability.init();
            return true;
        }
        return false;
    }

    /**
     * 移除队伍的能力
     * @param capabilityClass 能力类型
     */
    public final <C extends T> void remove(Class<C> capabilityClass) {
        get(capabilityClass).ifPresent(cap->{
            if(cap.isImmutable()) return;
            cap.destroy();
            capabilities.remove(capabilityClass);
            MinecraftForge.EVENT_BUS.unregister(cap);
        });
    }

    /**
     * 获取队伍的能力
     * @param capabilityClass 能力类型
     * @return 能力实例（Optional）
     */
    public final <C extends T> Optional<C> get(Class<C> capabilityClass) {
        return Optional.ofNullable((C) capabilities.get(capabilityClass));
    }

    /**
     * 获取指定类型的能力，如果不存在，则尝试创建并添加它。
     *
     * @param capabilityClass 能力类型
     * @return 存在或新创建的能力实例 (Optional)
     */
    public final <C extends T> Optional<C> getOrCreate(Class<C> capabilityClass) {
        Optional<C> existing = get(capabilityClass);
        if (existing.isPresent()) {
            return existing;
        }

        if (add(capabilityClass)) {
            return get(capabilityClass);
        }

        return Optional.empty();
    }

    /**
     * 如果指定类型的能力存在，则对其执行提供的操作。
     * 无论能力是否存在，此方法都会返回 CapabilityMap 本身，以支持链式调用。
     *
     * @param capabilityClass 要检查的能力类型
     * @param action 如果能力存在，则执行的操作
     * @return 调用此方法的 CapabilityMap 实例
     */
    public final <C extends T> CapabilityMap<H, T> ifPresent(Class<C> capabilityClass, Consumer<C> action) {
        this.get(capabilityClass).ifPresent(action);
        return this;
    }


    /**
     * 检查队伍是否拥有指定能力
     * @param capabilityClass 能力类型
     * @return 是否拥有
     */
    public final <C extends T> boolean contains(Class<C> capabilityClass) {
        return capabilities.containsKey(capabilityClass);
    }

    /**
     * 重置所有能力状态
     */
    public final void resetAll() {
        capabilities.values().forEach(FPSMCapability::reset);
    }

    public final <C extends T> void reset(Class<C> capability) {
        get(capability).ifPresent(FPSMCapability::reset);
    }

    public final List<T> values(){
        return capabilities.values().stream().toList();
    }

    public final List<String> capabilitiesString(){
        return values().stream().map(FPSMCapability::getName).toList();
    }

    public final List<String> synchronizableCapabilitiesString(){
        return values().stream().filter(cap -> cap instanceof FPSMCapability.Synchronizable).map(FPSMCapability::getName).collect(Collectors.toList());
    }
    /**
     * 序列化指定能力到网络缓冲区
     * @param capabilityClass 要序列化的能力类
     * @param buf 网络缓冲区
     */
    public final <C extends FPSMCapability<H> & FPSMCapability.Synchronizable> void serializeCapability(Class<C> capabilityClass, FriendlyByteBuf buf) {
        get((Class<T>) capabilityClass).ifPresent(capability -> {
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
            get(capabilityClass).ifPresentOrElse(
                    capability -> {
                        if (capability instanceof FPSMCapability.Synchronizable synced) {
                            synced.readFromBuf(buf);
                        }
                    },
                    () -> FPSMCapabilityManager.createFromNetwork(this, className, buf)
                            .ifPresent(capability -> {
                                if (getCapabilityType().isAssignableFrom(capability.getClass())) {
                                    this.addDirectly((T) capability);
                                }
                            })
            );
        });
    }


    /**
     * 获取所有能力的流(Stream)，便于进行过滤、映射等操作。
     * @return 包含所有能力的 Stream
     */
    public final Stream<T> stream() {
        return capabilities.values().stream();
    }

    /**
     * 获取需要同步的能力列表
     *
     * @return 需要同步的能力列表
     */
    public final <C extends FPSMCapability<H> & FPSMCapability.Synchronizable> List<Class<C>> getSynchronizableCapabilityClasses() {
        return values().stream()
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
        capabilities.keySet().forEach(this::remove);
    }

    public List<FPSMCapability.Savable<?>> getSaveData() {
        return capabilities.values().stream()
                .filter(FPSMCapability.Savable.class::isInstance)
                .map(cap -> (FPSMCapability.Savable<?>) cap)
                .collect(Collectors.toList());
    }

    public <D> void write(String className, D data) {
        FPSMCapabilityManager.getRegisteredCapabilityClassByFormated(className, getCapabilityType())
                .flatMap(this::get).ifPresent(capability -> {
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
        get((Class<T>) clazz).ifPresent(cap -> {
            ((FPSMCapability.Savable<D>) cap).write(data);
        });
    }

    public void write(Map<String,?> data){
        data.forEach(this::write);
    }
}
