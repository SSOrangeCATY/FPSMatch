package com.phasetranscrystal.fpsmatch.core.shop;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.event.PlayerGetShopDataEvent;
import com.phasetranscrystal.fpsmatch.core.shop.functional.ListenerModule;
import com.phasetranscrystal.fpsmatch.core.shop.slot.ShopSlot;
import com.phasetranscrystal.fpsmatch.common.net.cs.shop.ShopDataSlotS2CPacket;
import com.phasetranscrystal.fpsmatch.common.net.cs.shop.ShopMoneyS2CPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * FPSMatch 商店系统的核心类，用于管理玩家的商店数据和默认商店配置。
 * <p>
 * 该类提供了玩家商店数据的同步、默认商店配置的管理以及商店操作的处理。
 * 支持通过网络包同步商店数据和金钱信息。
 */
public class FPSMShop {
    /**
     * 商店的名称，通常与队伍名称相关联。
     */
    public final String name;

    /**
     * 默认商店数据，存储了商店中所有物品类型及其对应的商店槽位列表。
     */
    private final Map<ItemType, ArrayList<ShopSlot>> defaultShopData;

    /**
     * 玩家初始金钱。
     */
    private int startMoney;

    /**
     * 存储所有玩家的商店数据，键为玩家 UUID，值为对应的 ShopData。
     */
    public final Map<UUID, ShopData> playersData = new HashMap<>();

    /**
     * FPSMShop 的编解码器，用于序列化和反序列化商店配置。
     */
    public static final Codec<FPSMShop> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("mapName").forGetter(FPSMShop::getName),
            Codec.INT.fieldOf("defaultMoney").forGetter(FPSMShop::getDefaultMoney),
            Codec.unboundedMap(
                    Codec.STRING,
                    ShopSlot.CODEC.listOf()
            ).fieldOf("shopData").forGetter(FPSMShop::getDefaultShopDataMapString)
    ).apply(instance, (name, defaultMoney, shopData) -> {
        FPSMShop shop = new FPSMShop(name, defaultMoney);

        Map<ItemType, ArrayList<ShopSlot>> data = new HashMap<>();
        shopData.forEach((t, l) -> {
            ArrayList<ShopSlot> list = new ArrayList<>(l);
            data.put(ItemType.valueOf(t), list);
        });

        shop.setDefaultShopData(data);
        return shop;
    }));

    /**
     * 获取默认金钱。
     *
     * @return 默认金钱数量
     */
    private int getDefaultMoney() {
        return startMoney;
    }

    /**
     * 获取商店名称。
     *
     * @return 商店名称
     */
    public String getName() {
        return name;
    }

    /**
     * 构造函数，用于创建一个新的 FPSMShop 实例。
     *
     * @param name 商店名称
     * @param startMoney 玩家初始金钱
     */
    public FPSMShop(String name, int startMoney) {
        this.defaultShopData = ShopData.getRawData();
        this.startMoney = startMoney;
        this.name = name;
    }

    /**
     * 构造函数，用于创建一个新的 FPSMShop 实例（无初始金钱）。
     *
     * @param name 商店名称
     */
    public FPSMShop(String name) {
        this.defaultShopData = ShopData.getRawData();
        this.startMoney = 0;
        this.name = name;
    }

    /**
     * 构造函数，用于创建一个新的 FPSMShop 实例（自定义默认商店数据）。
     *
     * @param name 商店名称
     * @param data 默认商店数据
     */
    public FPSMShop(String name, Map<ItemType, ArrayList<ShopSlot>> data) {
        this.defaultShopData = data;
        this.startMoney = 0;
        this.name = name;
    }

    /**
     * 构造函数，用于创建一个新的 FPSMShop 实例（自定义默认商店数据和初始金钱）。
     *
     * @param name 商店名称
     * @param data 默认商店数据
     * @param startMoney 玩家初始金钱
     */
    public FPSMShop(String name, Map<ItemType, ArrayList<ShopSlot>> data, int startMoney) {
        this.defaultShopData = data;
        this.startMoney = startMoney;
        this.name = name;
    }

    /**
     * 同步所有玩家的商店数据到客户端。
     * <p>
     * 遍历所有玩家的商店数据，并通过网络包发送给对应的玩家。
     */
    public void syncShopData() {
        for (UUID uuid : playersData.keySet()) {
            FPSMCore.getInstance().getPlayerByUUID(uuid).ifPresent(player->{
                ShopData shopData = this.getPlayerShopData(uuid);
                for (ItemType type : ItemType.values()) {
                    List<ShopSlot> slots = shopData.getShopSlotsByType(type);
                    slots.forEach((shopSlot -> FPSMatch.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new ShopDataSlotS2CPacket(type, shopSlot))));
                }
            });

        }
    }

    /**
     * 同步所有玩家的金钱数据到客户端。
     * <p>
     * 遍历所有玩家的商店数据，并通过网络包发送金钱信息。
     */
    public void syncShopMoneyData() {
        for (UUID uuid : playersData.keySet()) {
            FPSMCore.getInstance().getPlayerByUUID(uuid).ifPresent(player->{
                ShopData shopData = this.getPlayerShopData(uuid);
                FPSMatch.INSTANCE.send(PacketDistributor.ALL.noArg(), new ShopMoneyS2CPacket(uuid, shopData.getMoney()));
            });
        }
    }

    /**
     * 同步指定玩家的金钱数据到客户端。
     *
     * @param uuid 玩家的 UUID
     */
    public void syncShopMoneyData(UUID uuid) {
        if (playersData.containsKey(uuid)) {
            FPSMCore.getInstance().getPlayerByUUID(uuid).ifPresent(player->{
                ShopData shopData = this.getPlayerShopData(uuid);
                FPSMatch.INSTANCE.send(PacketDistributor.ALL.noArg(), new ShopMoneyS2CPacket(uuid, shopData.getMoney()));
            });
        }
    }

    /**
     * 同步指定玩家的金钱数据到客户端。
     *
     * @param player 玩家对象
     */
    public void syncShopMoneyData(@NotNull ServerPlayer player) {
        if (playersData.containsKey(player.getUUID())) {
            ShopData shopData = this.getPlayerShopData(player.getUUID());
            FPSMatch.INSTANCE.send(PacketDistributor.ALL.noArg(), new ShopMoneyS2CPacket(player.getUUID(), shopData.getMoney()));
        }
    }

    /**
     * 同步指定玩家列表的商店数据到客户端。
     *
     * @param players 玩家列表
     */
    public void syncShopData(List<ServerPlayer> players) {
        players.forEach(this::syncShopData);
    }

    /**
     * 同步指定玩家的商店数据到客户端。
     *
     * @param player 玩家对象
     */
    public void syncShopData(ServerPlayer player) {
        ShopData shopData = this.getPlayerShopData(player.getUUID());
        for (ItemType type : ItemType.values()) {
            List<ShopSlot> slots = shopData.getShopSlotsByType(type);
            slots.forEach((shopSlot -> FPSMatch.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new ShopDataSlotS2CPacket(type, shopSlot))));
        }
    }

    /**
     * 同步指定玩家的商店槽位数据到客户端。
     *
     * @param player 玩家对象
     * @param type 物品类型
     * @param slot 商店槽位
     */
    public void syncShopData(ServerPlayer player, ItemType type, ShopSlot slot) {
        FPSMatch.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new ShopDataSlotS2CPacket(type, slot));
    }

    /**
     * 同步指定玩家的商店槽位数据到客户端。
     *
     * @param player 玩家对象
     * @param type 物品类型
     * @param index 槽位索引
     */
    public void syncShopData(ServerPlayer player, ItemType type, int index) {
        ShopSlot shopSlot = this.getPlayerShopData(player.getUUID()).getShopSlotsByType(type).get(index);
        FPSMatch.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new ShopDataSlotS2CPacket(type, shopSlot));
    }

    /**
     * 获取玩家的商店数据。
     * <p>
     * 如果玩家的商店数据不存在，则会创建一个新的默认商店数据。
     *
     * @param uuid 玩家的 UUID
     * @return 玩家的商店数据
     */
    public ShopData getPlayerShopData(UUID uuid) {
        if (this.playersData.containsKey(uuid)) {
            return this.playersData.get(uuid);
        }else{
            return this.getDefaultAndPutData(uuid);
        }
    }

    /**
     * 获取玩家的商店数据。
     * <p>
     * 如果玩家的商店数据不存在，则会创建一个新的默认商店数据。
     *
     * @param player 玩家
     * @return 玩家的商店数据
     */
    public ShopData getPlayerShopData(Player player) {
        return this.getPlayerShopData(player.getUUID());
    }

    /**
     * 清空所有玩家的商店数据。
     */
    public void clearPlayerShopData() {
        this.playersData.clear();
    }
    public void clearPlayerShopData(UUID uuid) {
        this.playersData.remove(uuid);
    }

    public void resetPlayerData(List<UUID> uuids){
        this.clearPlayerShopData();
        uuids.forEach(this::getDefaultAndPutData);
        this.syncShopData();
        this.syncShopMoneyData();
    }

    public void resetPlayerData(){
        this.playersData.keySet().forEach(this::getDefaultAndPutData);
        this.syncShopData();
        this.syncShopMoneyData();
    }

    /**
     * 获取所有玩家的商店数据。
     *
     * @return 玩家商店数据的 Map
     */
    public Map<UUID, ShopData> getPlayersData() {
        return playersData;
    }

    /**
     * 设置默认商店数据。
     *
     * @param data 默认商店数据
     */
    public void setDefaultShopData(Map<ItemType, ArrayList<ShopSlot>> data) {
        this.defaultShopData.clear();
        this.defaultShopData.putAll(data);
        this.resetPlayerData();
    }

    /**
     * 替换默认商店数据中的某个槽位。
     *
     * @param type 物品类型
     * @param index 槽位索引
     * @param shopSlot 新的商店槽位
     */
    public void replaceDefaultShopData(ItemType type, int index, ShopSlot shopSlot) {
        this.defaultShopData.get(type).set(index, shopSlot);
        this.resetPlayerData();
    }

    /**
     * 设置默认商店数据的分组 ID。
     *
     * @param type 物品类型
     * @param index 槽位索引
     * @param groupId 分组 ID
     */
    public void setDefaultShopDataGroupId(ItemType type, int index, int groupId) {
        this.defaultShopData.get(type).get(index).setGroupId(groupId);
        this.resetPlayerData();
    }

    /**
     * 添加默认商店数据的监听模块。
     *
     * @param type 物品类型
     * @param index 槽位索引
     * @param listenerModule 监听模块
     */
    public void addDefaultShopDataListenerModule(ItemType type, int index, ListenerModule listenerModule) {
        this.defaultShopData.get(type).get(index).addListener(listenerModule);
        this.resetPlayerData();
    }

    /**
     * 移除默认商店数据的监听模块。
     *
     * @param type 物品类型
     * @param index 槽位索引
     * @param listenerModule 监听模块名称
     */
    public void removeDefaultShopDataListenerModule(ItemType type, int index, String listenerModule) {
        this.defaultShopData.get(type).get(index).removeListenerModule(listenerModule);
        this.resetPlayerData();
    }

    /**
     * 设置默认商店数据的物品堆。
     *
     * @param type 物品类型
     * @param index 槽位索引
     * @param itemStack 物品堆
     */
    public void setDefaultShopDataItemStack(ItemType type, int index, ItemStack itemStack) {
        this.defaultShopData.get(type).get(index).itemSupplier = itemStack::copy;
        this.resetPlayerData();
    }

    /**
     * 设置默认商店数据的成本。
     *
     * @param type 物品类型
     * @param index 槽位索引
     * @param cost 成本
     */
    public void setDefaultShopDataCost(ItemType type, int index, int cost) {
        this.defaultShopData.get(type).get(index).setDefaultCost(cost);
        this.resetPlayerData();
    }

    /**
     * 获取默认商店数据。
     *
     * @return 默认商店数据
     */
    public ShopData getDefaultShopData() {
        Map<ItemType, ArrayList<ShopSlot>> map = new HashMap<>(this.defaultShopData);
        return new ShopData(map, this.startMoney);
    }

    public ShopData getDefaultAndPutData(UUID uuid) {
        Map<ItemType, List<ShopSlot>> modifiableMap = new HashMap<>(this.defaultShopData);

        Map<ItemType, List<ShopSlot>> protectedMap = new HashMap<>();
        for (Map.Entry<ItemType, List<ShopSlot>> entry : modifiableMap.entrySet()) {
            protectedMap.put(entry.getKey(), List.copyOf(entry.getValue()));
        }

        PlayerGetShopDataEvent event = new PlayerGetShopDataEvent(uuid,this,protectedMap);
        MinecraftForge.EVENT_BUS.post(event);
        ShopData finalData;
        if(this.playersData.containsKey(uuid)){
            finalData = new ShopData(event.getData(), this.playersData.get(uuid).getMoney());
        }else{
            finalData = new ShopData(event.getData(), this.startMoney);
        }

        this.playersData.put(uuid, finalData);
        return finalData;
    }

    /**
     * 获取默认商店数据。
     *
     * @return 默认商店数据的 Map
     */
    public Map<ItemType, ArrayList<ShopSlot>> getDefaultShopDataMap() {
        return this.defaultShopData;
    }

    /**
     * 获取默认商店数据（字符串键）。
     *
     * @return 默认商店数据的 Map（字符串键）
     */
    public Map<String, List<ShopSlot>> getDefaultShopDataMapString() {
        Map<String, List<ShopSlot>> map = new HashMap<>();
        this.defaultShopData.forEach((k, v) -> {
            map.put(k.name(), v);
        });
        return map;
    }

    public void setStartMoney(int money){
        this.startMoney = money;
    }

    /**
     * 处理商店按钮操作。
     * <p>
     * 根据玩家的操作类型，更新玩家的商店数据并同步到客户端。
     *
     * @param serverPlayer 玩家对象
     * @param type 物品类型
     * @param index 槽位索引
     * @param action 操作类型
     */
    public void handleButton(ServerPlayer serverPlayer, ItemType type, int index, ShopAction action) {
        this.getPlayerShopData(serverPlayer.getUUID()).handleButton(serverPlayer, type, index, action);
        this.syncShopData(serverPlayer);
        this.syncShopMoneyData(serverPlayer);
    }
}