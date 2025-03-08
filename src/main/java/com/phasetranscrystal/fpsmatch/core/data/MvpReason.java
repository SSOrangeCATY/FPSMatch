package com.phasetranscrystal.fpsmatch.core.data;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class MvpReason{
    public final UUID uuid;
    private Component teamName;
    private Component playerName;
    private Component mvpReason;
    private Component extraInfo1;
    private Component extraInfo2;
    private MvpReason(Builder builder){
        this.uuid = builder.uuid;
        this.teamName = builder.teamName;
        this.playerName = builder.playerName == null ? Component.empty() : builder.playerName;
        this.mvpReason = builder.mvpReason == null ? Component.empty() : builder.mvpReason;
        this.extraInfo1 = builder.extraInfo1 == null ? Component.empty() : builder.extraInfo1;
        this.extraInfo2 = builder.extraInfo2 == null ? Component.empty() : builder.extraInfo2;
    }

    public Component getTeamName() {
        return teamName;
    }

    public void setTeamName(Component teamName) {
        this.teamName = teamName;
    }

    public Component getPlayerName() {
        return playerName;
    }

    public void setPlayerName(Component playerName) {
        this.playerName = playerName;
    }

    public Component getMvpReason() {
        return mvpReason;
    }

    public void setMvpReason(Component mvpReason) {
        this.mvpReason = mvpReason;
    }

    public Component getExtraInfo1() {
        return extraInfo1;
    }

    public void setExtraInfo1(Component extraInfo1) {
        this.extraInfo1 = extraInfo1;
    }

    public Component getExtraInfo2() {
        return extraInfo2;
    }

    public void setExtraInfo2(Component extraInfo2) {
        this.extraInfo2 = extraInfo2;
    }



    public static class Builder{
        public final UUID uuid;
        Component teamName;
        Component playerName;
        Component mvpReason;
        @Nullable Component extraInfo1;
        @Nullable Component extraInfo2;

        public Builder(UUID uuid) {
            this.uuid = uuid;
        }

        public Builder setTeamName(Component teamName){
            this.teamName = teamName;
            return this;
        }
        public Builder setPlayerName(Component playerName){
            this.playerName = playerName;
            return this;
        }
        public Builder setMvpReason(Component mvpReason){
            this.mvpReason = mvpReason;
            return this;
        }
        public Builder setExtraInfo1(@Nullable Component extraInfo1){
            this.extraInfo1 = extraInfo1;
            return this;
        }
        public Builder setExtraInfo2(@Nullable Component extraInfo2){
            this.extraInfo2 = extraInfo2;
            return this;
        }
        public MvpReason build(){
            return new MvpReason(this);
        }

    }
}
