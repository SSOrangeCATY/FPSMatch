package com.phasetranscrystal.fpsmatch.core.map;

import com.phasetranscrystal.fpsmatch.core.BaseTeam;
import com.phasetranscrystal.fpsmatch.core.FPSMShop;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * 提供商店功能的地图接口。
 * <p>
 * 该接口扩展了基础地图接口（IMap），为地图提供了商店管理功能。
 * 支持获取商店、处理玩家经济奖励/惩罚、同步商店数据等功能。
 */
public interface ShopMap<T extends BaseMap> extends IMap<T> {
    /**
     * 根据商店名称获取商店实例。
     *
     * @param shopName 商店名称
     * @return 商店实例
     */
    @Nullable FPSMShop getShop(String shopName);

    /**
     * 获取地图中所有商店的列表。
     *
     * @return 商店列表
     */
    List<FPSMShop> getShops();

    /**
     * 获取地图中所有商店的名称列表。
     *
     * @return 商店名称列表
     */
    List<String> getShopNames();

    /**
     * 处理玩家击杀经济奖励/惩罚。
     * <p>
     * 根据玩家所属队伍，为其商店数据中的金钱进行增加或减少操作，并同步到客户端。
     *
     * @param uuid 玩家 UUID
     * @param money 金额变化量（可正负）
     */
    default void addPlayerMoney(UUID uuid, int money) {
        BaseTeam team = this.getMap().getMapTeams().getTeamByPlayer(uuid);
        if (team != null) {
            this.getShop(team.name).getPlayerShopData(uuid).addMoney(money);
            this.getShop(team.name).syncShopMoneyData(uuid);
        }
    }

    /**
     * 减少玩家的金钱。
     * <p>
     * 根据玩家所属队伍，为其商店数据中的金钱进行减少操作，并同步到客户端。
     *
     * @param uuid 玩家 UUID
     * @param money 减少的金额
     */
    default void removePlayerMoney(UUID uuid, int money) {
        BaseTeam team = this.getMap().getMapTeams().getTeamByPlayer(uuid);
        if (team != null) {
            this.getShop(team.name).getPlayerShopData(uuid).reduceMoney(money);
            this.getShop(team.name).syncShopMoneyData(uuid);
        }
    }

    /**
     * 设置玩家的金钱数量。
     * <p>
     * 根据玩家所属队伍，直接设置其商店数据中的金钱数量，并同步到客户端。
     *
     * @param uuid 玩家 UUID
     * @param money 设置的金钱数量
     */
    default void setPlayerMoney(UUID uuid, int money) {
        BaseTeam team = this.getMap().getMapTeams().getTeamByPlayer(uuid);
        if (team != null) {
            this.getShop(team.name).getPlayerShopData(uuid).setMoney(money);
            this.getShop(team.name).syncShopMoneyData(uuid);
        }
    }

    /**
     * 同步所有商店的数据和金钱信息。
     * <p>
     * 清空所有玩家的商店数据，并重新同步商店数据和金钱信息到客户端。
     */
    default void syncShopData() {
        this.getShops().forEach(shop -> {
            shop.syncShopData();
            shop.syncShopMoneyData();
        });
    }

    default void clearAndSyncShopData() {
        this.getShops().forEach(shop -> {
            shop.resetPlayerData();
            shop.syncShopData();
            shop.syncShopMoneyData();
        });
    }

}