package com.phasetranscrystal.fpsmatch.core.map;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;
import java.util.function.Consumer;

public class VoteObj {
    private final long endVoteTimer;
    private final String voteTitle;
    private final Component message;
    private final float requiredPercent;
    private final Map<UUID, Boolean> voteResults = new HashMap<>();
    private final Set<UUID> votedPlayers = new HashSet<>();
    private final Consumer<VoteObj> onSuccess;
    private final Consumer<VoteObj> onFailure;
    private VoteStatus status = VoteStatus.ONGOING;
    private boolean executed = false;

    // 投票状态枚举
    public enum VoteStatus {
        ONGOING, SUCCESS, FAILED
    }

    /**
     * @param voteTitle 投票标题
     * @param message 投票消息
     * @param duration 投票持续时间（秒）
     * @param requiredPercent 通过所需的玩家比例 (0.0 到 1.0)
     * @param onSuccess 投票成功时的回调
     * @param onFailure 投票失败时的回调
     */
    public VoteObj(String voteTitle, Component message, int duration, float requiredPercent,
                   Consumer<VoteObj> onSuccess, Consumer<VoteObj> onFailure) {
        this.endVoteTimer = System.currentTimeMillis() + duration * 1000L;
        this.voteTitle = voteTitle;
        this.message = message;
        this.requiredPercent = Math.min(requiredPercent, 1f);
        this.onSuccess = onSuccess;
        this.onFailure = onFailure;
    }

    /**
     * 处理玩家投票
     */
    public void processVote(ServerPlayer player, boolean agree) {
        if (status != VoteStatus.ONGOING) return;

        UUID playerId = player.getUUID();
        voteResults.put(playerId, agree);
        votedPlayers.add(playerId);
    }

    /**
     * 自动检查投票状态并执行相应操作
     * @param totalPlayers 当前总玩家数
     * @return true 如果投票已结束，false 如果投票仍在进行中
     */
    public boolean tick(int totalPlayers) {
        if (status != VoteStatus.ONGOING || executed) {
            return true; // 投票已结束或已执行回调
        }

        // 检查是否超时
        if (System.currentTimeMillis() >= endVoteTimer) {
            status = VoteStatus.FAILED;
            executeCallback();
            return true;
        }

        // 检查是否所有玩家都已投票
        boolean allVoted = votedPlayers.size() >= totalPlayers;

        // 计算当前同意票比例
        long agreeCount = voteResults.values().stream().filter(Boolean::booleanValue).count();
        float currentRatio = totalPlayers > 0 ? (float) agreeCount / totalPlayers : 0f;

        // 检查是否已达到通过比例
        boolean passed = currentRatio >= requiredPercent;

        // 如果所有玩家已投票或已达到通过比例，则结束投票
        if (allVoted || passed) {
            status = passed ? VoteStatus.SUCCESS : VoteStatus.FAILED;
            executeCallback();
            return true;
        }

        return false; // 投票仍在进行中
    }

    /**
     * 执行相应的回调函数
     */
    private void executeCallback() {
        if (executed) return;

        executed = true;

        switch (status) {
            case SUCCESS:
                if (onSuccess != null) {
                    onSuccess.accept(this);
                }
                break;
            case FAILED:
                if (onFailure != null) {
                    onFailure.accept(this);
                }
                break;
        }
    }

    /**
     * 获取未投票的玩家ID
     */
    public Set<UUID> getNonVotingPlayers(Collection<UUID> allPlayers) {
        Set<UUID> nonVoting = new HashSet<>(allPlayers);
        nonVoting.removeAll(votedPlayers);
        return nonVoting;
    }

    // Getter 方法
    public Component getMessage() {
        return message;
    }

    public String getVoteTitle() {
        return voteTitle;
    }

    public float getRequiredPercent() {
        return requiredPercent;
    }

    public boolean isOvertime() {
        return "overtime".equals(voteTitle);
    }

    public VoteStatus getStatus() {
        return status;
    }

    public long getRemainingTime() {
        return Math.max(0, (endVoteTimer - System.currentTimeMillis()) / 1000);
    }

    public int getAgreeCount() {
        return (int) voteResults.values().stream().filter(Boolean::booleanValue).count();
    }

    public int getDisagreeCount() {
        return (int) voteResults.values().stream().filter(v -> !v).count();
    }

    public int getVotedCount() {
        return votedPlayers.size();
    }

    public boolean hasExecuted() {
        return executed;
    }
}