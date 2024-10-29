package com.phasetranscrystal.fpsmatch.cs;

import com.phasetranscrystal.fpsmatch.core.BaseMap;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;

import java.util.List;

public class GameMap extends BaseMap {
    private int waittingTime = 20;
    private  int roundTimeLimit = 115; // 回合时间限制，单位为秒
    private int currentRoundTime = 0; // 当前回合已过时间
    private boolean isPause = false;
    private boolean isWaiting = false;

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
        // 开始新的回合
        currentRoundTime = 0;
        this.getMapTeams().setTeamsSpawnPoints();
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
