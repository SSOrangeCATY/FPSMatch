package com.phasetranscrystal.fpsmatch.core.map;

import com.phasetranscrystal.fpsmatch.core.BaseTeam;
import com.phasetranscrystal.fpsmatch.core.data.AreaData;
import net.minecraft.world.entity.player.Player;

import java.util.List;
/**
 * 爆破模式地图接口，扩展了基础地图功能。
 * <p>
 * 该接口为爆破模式的地图提供了特定的方法，包括炸弹区域的管理、爆破状态的设置和检查，
 * 以及与玩家相关的炸弹区域检测功能。
 */
public interface BlastModeMap<T extends BaseMap> extends IMap<T> {
    /**
     * 添加一个炸弹区域到地图。
     * <p>
     * 炸弹区域用于定义可以放置炸弹的区域范围。
     *
     * @param area 炸弹区域数据
     */
    void addBombArea(AreaData area);

    /**
     * 获取所有炸弹区域的数据。
     * <p>
     * 返回一个包含所有炸弹区域的列表。
     *
     * @return 炸弹区域数据列表
     */
    List<AreaData> getBombAreaData();

    /**
     * 设置爆破方的队伍名称。
     * <p>
     * 爆破方是指在游戏中负责放置炸弹的队伍。
     *
     * @param team 爆破方队伍名称
     */
    void setBlastTeam(BaseTeam team);

    /**
     * 设置当前的爆破状态。
     * <p>
     * 爆破状态用于表示炸弹是否正在爆破过程中。
     *
     * @param blasting 爆破状态（通常为时间倒计时）
     */
    void setBlasting(int blasting);

    /**
     * 设置炸弹是否已经爆炸。
     * <p>
     * 该方法用于标记炸弹是否已经爆炸，以便在游戏中进行状态检查。
     *
     * @param exploded 是否已经爆炸
     */
    void setExploded(boolean exploded);

    /**
     * 获取当前的爆破状态。
     * <p>
     * 返回爆破状态的时间倒计时，如果爆破未开始则返回 0。
     *
     * @return 爆破状态
     */
    int isBlasting();

    /**
     * 检查炸弹是否已经爆炸。
     * <p>
     * 返回炸弹是否已经爆炸的状态。
     *
     * @return 如果炸弹已经爆炸，返回 true；否则返回 false
     */
    boolean isExploded();

    /**
     * 检查指定队伍是否可以放置炸弹。
     * <p>
     * 该方法用于判断当前队伍是否有权限放置炸弹。
     *
     * @param fixedTeamName 队伍完整名称
     * @return 如果可以放置炸弹，返回 true；否则返回 false
     */
    boolean checkCanPlacingBombs(String fixedTeamName);

    /**
     * 检查玩家是否处于炸弹区域内。
     * <p>
     * 该方法用于判断玩家是否处于炸弹区域范围内。
     *
     * @param player 玩家对象
     * @return 如果玩家处于炸弹区域，返回 true；否则返回 false
     */
    boolean checkPlayerIsInBombArea(Player player);
}