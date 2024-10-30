package com.phasetranscrystal.fpsmatch.cs;

import com.phasetranscrystal.fpsmatch.core.BaseMap;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointData;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.commands.ClearInventoryCommands;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GameMap extends BaseMap {
    public static final int WINNER_ROUND = 13;
    private int waittingTime = 20;
    private  int roundTimeLimit = 115; // 回合时间限制，单位为秒
    private int currentRoundTime = 0; // 当前回合已过时间
    private boolean isPause = false;
    private boolean isWaiting = false;
    private final Map<String,Integer> teamScores = new HashMap<>();

    public GameMap(ServerLevel serverLevel, List<String> teams, SpawnPointData spawnPoint) {
        super(serverLevel, teams, spawnPoint);
    }


    @Override
    public void tick() {
        super.tick(); // 调用父类的tick方法

        if (!isPause) {
            // 每刻增加时间计数
            if(!isRoundTimeEnd() && !this.isWaiting){
                currentRoundTime++;
            }

            if(isWaiting && !isWaitingTimeEnd()){
                currentRoundTime++;
            }else{
                currentRoundTime = 0;
                this.isWaiting = false;
            }

            // 检查回合是否超时
            if (isRoundTimeEnd()) {
                endCurrentRound();
                startNewRound();
            }

            // 检查是否有队伍胜利
            checkForVictory();
        } else {
            // 如果当前没有活跃的回合，检查是否有玩家在线，如果有，则开始新的回合
           /* if (!serverLevel.getPlayerList().isEmpty()) {
                startNewRound();
            }*/
        }
    }

    public boolean isRoundTimeEnd(){
        return this.currentRoundTime >= this.roundTimeLimit*20;
    }

    public boolean isWaitingTimeEnd(){
        return this.currentRoundTime >= this.roundTimeLimit*20;
    }

    private void endCurrentRound() {
        // 结束当前回合
        currentRoundTime = 0;
        // 这里可以添加逻辑来处理回合结束时的得分和状态保存

        // 可以在这里添加更多的逻辑，比如保存游戏状态、显示得分板等
    }

    private void startNewRound() {
        currentRoundTime = 0;
        this.isWaiting = true;
        // 可以在这里添加更多的逻辑，比如重置得分板、装备武器等
    }

    private void respawnPlayers() {
        // 重生玩家

       // SpawnPointData spawnPoint = this.getMapTeams()
    }

    @Override
    public void victory() {
        // 显示胜利信息

        // 重置游戏状态
        resetGame();
    }

    @Override
    public boolean victoryGoal() {
        // 检查胜利条件，例如一个队伍消灭了另一个队伍的所有成员
        return false;
    }

    @Override
    public void initializeMap() {
        // 初始化地图，设置重生点，准备武器等

    }

    @Override
    public void cleanupMap() {
        // 清理地图，重置重生点，清除武器等
        this.getMapTeams().setTeamsSpawnPoints();
        this.getMapTeams().getJoinedPlayers().forEach((uuid -> {
            ServerPlayer player =  this.getServerLevel().getServer().getPlayerList().getPlayer(uuid);
            if(player != null){
                float angle = player.getRespawnAngle();
                ResourceKey<Level> dimension = player.getRespawnDimension();
                ServerLevel serverLevel = this.getServerLevel().getServer().getLevel(dimension);
                BlockPos pos =  player.getRespawnPosition();
                player.heal(player.getMaxHealth());
                if (serverLevel != null && pos != null) {
                    player.teleportTo(serverLevel, pos.getX(), pos.getY(), pos.getZ(), angle, 0F);
                }
                this.clearPlayerInventory(player);
            }
        }));

    }

    public void clearPlayerInventory(ServerPlayer player){
        player.getInventory().clearOrCountMatchingItems((p_180029_) -> true, -1, player.inventoryMenu.getCraftSlots());
        player.containerMenu.broadcastChanges();
        player.inventoryMenu.slotsChanged(player.getInventory());
    }

    private String getWinningTeamName() {
        // 根据游戏逻辑确定获胜队伍的名称
        // 这里需要具体的逻辑来确定哪个队伍获胜
        return null; // 需要具体实现
    }

    private void resetGame() {
        // 重置游戏状态，准备下一轮
        currentRoundTime = 0;
    }

}
