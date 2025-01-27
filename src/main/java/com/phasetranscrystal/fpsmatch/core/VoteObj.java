package com.phasetranscrystal.fpsmatch.core;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;

public class VoteObj {
    private final long endVoteTimer;
    private final String voteTitle;
    private final Component message;
    private final float playerPercent;
    public final Map<UUID,Boolean> voteResult = new HashMap<>();

    /**
     * @param duration second
     * @param message vote message
     * @param playerPercent player percent
     * */
    public VoteObj(String voteTitle, Component message, int duration, float playerPercent) {
        this.endVoteTimer =  System.currentTimeMillis() + duration * 1000L;
        this.voteTitle = voteTitle;
        this.message = message;
        this.playerPercent = Math.min(playerPercent,1f);
    }

    public Component getMessage() {
        return message;
    }

    public String getVoteTitle() {
        return voteTitle;
    }

    public float getPlayerPercent() {
        return playerPercent;
    }

    public boolean checkVoteIsOverTime(){
        return System.currentTimeMillis() >= endVoteTimer;
    }

    public void addAgree(ServerPlayer serverPlayer) {
        voteResult.put(serverPlayer.getUUID(),true);
    }
    public void addDisagree(ServerPlayer serverPlayer) {
        voteResult.put(serverPlayer.getUUID(),false);
    }

    public long getEndVoteTimer() {
        return endVoteTimer;
    }
}
