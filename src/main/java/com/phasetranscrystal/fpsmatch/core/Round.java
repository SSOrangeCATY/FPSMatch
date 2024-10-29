package com.phasetranscrystal.fpsmatch.core;

public abstract class Round {
    private boolean hasRound;  // 是否有回合制
    private int roundNum = 1;  // 当前回合数
    private int winnerRoundNum; // 获胜所需的回合数

    // 构造方法，允许设置是否有回合制和获胜所需的回合数
    public Round(boolean hasRound, int winnerRoundNum) {
        this.hasRound = hasRound;
        this.winnerRoundNum = winnerRoundNum;
        if (!hasRound) {
            this.roundNum = 0; // 如果没有回合制，则回合数不增加
        }
    }
    // 开始新的回合
    public void startNewRound() {
        roundNum++;
    }

    // 结束当前回合
    public void endCurrentRound() {
        if (hasRound && roundNum > 0) {
            if (checkForVictory()){
                this.startNewRound();
            }
        }
    }

    public abstract boolean shouldEndCurrentRound();
    // 检查胜利条件
    private boolean checkForVictory() {
        if (isVictory()) {
            endMatch();
            return true;
        }else return false;
    }
    public abstract boolean isVictory();
    public abstract void endMatch();

    // Getters and Setters
    public boolean isHasRound() {
        return hasRound;
    }

    public void setHasRound(boolean hasRound) {
        this.hasRound = hasRound;
    }

    public int getRoundNum() {
        return roundNum;
    }

    public void setRoundNum(int roundNum) {
        this.roundNum = roundNum;
    }

    public int getWinnerRoundNum() {
        return winnerRoundNum;
    }

    public void setWinnerRoundNum(int winnerRoundNum) {
        this.winnerRoundNum = winnerRoundNum;
    }
}