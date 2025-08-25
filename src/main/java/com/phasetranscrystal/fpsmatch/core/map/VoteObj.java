package com.phasetranscrystal.fpsmatch.core.map;

import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;

public class VoteObj {
    private final long endVoteTimer;
    private final String voteTitle;
    private final Component message;
    private final float requiredPercent;
    private final Map<UUID, Boolean> voteResults = new HashMap<>();
    private final Set<UUID> eligiblePlayers = new HashSet<>();
    private final Runnable onSuccess;
    private final Runnable onFailure;
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
     * @param eligiblePlayers 有资格投票的玩家集合
     */
    public VoteObj(String voteTitle, Component message, int duration, float requiredPercent,
                   Runnable onSuccess, Runnable onFailure, Collection<UUID> eligiblePlayers) {
        this.endVoteTimer = System.currentTimeMillis() + duration * 1000L;
        this.voteTitle = voteTitle;
        this.message = message;
        this.requiredPercent = Math.min(Math.max(requiredPercent, 0f), 1f); // 确保在0-1范围内
        this.onSuccess = onSuccess;
        this.onFailure = onFailure;
        this.eligiblePlayers.addAll(eligiblePlayers);
    }

    /**
     * 处理玩家投票
     */
    public boolean processVote(ServerPlayer player, boolean agree) {
        if (status != VoteStatus.ONGOING) return false;

        UUID playerId = player.getUUID();

        // 检查玩家是否有资格投票
        if (!eligiblePlayers.contains(playerId)) {
            return false;
        }

        voteResults.put(playerId, agree);
        return true;
    }

    /**
     * 添加有资格投票的玩家
     */
    public void addEligiblePlayer(UUID playerId) {
        eligiblePlayers.add(playerId);
    }

    /**
     * 移除有资格投票的玩家
     */
    public void removeEligiblePlayer(UUID playerId) {
        eligiblePlayers.remove(playerId);
        voteResults.remove(playerId);
    }

    /**
     * 自动检查投票状态并执行相应操作
     * @return true 如果投票已结束，false 如果投票仍在进行中
     */
    public boolean tick() {
        if (status != VoteStatus.ONGOING || executed) {
            return true; // 投票已结束或已执行回调
        }

        // 检查是否超时
        if (System.currentTimeMillis() >= endVoteTimer) {
            status = VoteStatus.FAILED;
            executeCallback();
            return true;
        }

        int totalEligiblePlayers = getEligiblePlayerCount();
        if (totalEligiblePlayers == 0) {
            status = VoteStatus.FAILED;
            executeCallback();
            return true;
        }

        // 计算当前同意票比例
        long agreeCount = voteResults.values().stream().filter(Boolean::booleanValue).count();
        float currentRatio = (float) agreeCount / totalEligiblePlayers;

        // 检查是否所有有资格的玩家都已投票
        boolean allVoted = voteResults.size() >= totalEligiblePlayers;

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

        try {
            switch (status) {
                case SUCCESS:
                    if (onSuccess != null) {
                        onSuccess.run();
                    }
                    break;
                case FAILED:
                    if (onFailure != null) {
                        onFailure.run();
                    }
                    break;
            }
        } catch (Exception e) {
            // 记录回调执行异常，避免影响主线程
            System.err.println("Vote callback execution failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 获取未投票的玩家ID
     */
    public Set<UUID> getNonVotingPlayers() {
        Set<UUID> nonVoting = new HashSet<>(eligiblePlayers);
        nonVoting.removeAll(voteResults.keySet());
        return nonVoting;
    }

    /**
     * 获取有资格投票的玩家数量
     */
    public int getEligiblePlayerCount() {
        int count = 0;
        for (UUID player : eligiblePlayers) {
            if(FPSMCore.getInstance().getPlayerByUUID(player).isPresent()){
                count++;
            }
        }
        return count;
    }

    /**
     * 获取所有有资格投票的玩家
     */
    public Set<UUID> getEligiblePlayers() {
        return Collections.unmodifiableSet(eligiblePlayers);
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
        return voteResults.size();
    }

    public boolean hasExecuted() {
        return executed;
    }

    /**
     * 强制结束投票（用于特殊情况）
     */
    public void forceEnd(VoteStatus forcedStatus) {
        if (status == VoteStatus.ONGOING && !executed) {
            status = forcedStatus;
            executeCallback();
        }
    }
}