package com.phasetranscrystal.fpsmatch.core.data;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class MvpReason{
    public final UUID uuid;
    public final Component teamName;
    public final Component playerName;
    public final Component mvpReason;
    public final Component extraInfo1;
    public final Component extraInfo2;
    private MvpReason(Builder builder){
        this.uuid = builder.uuid;
        this.teamName = builder.teamName;
        this.playerName = builder.playerName == null ? Component.empty() : builder.playerName;
        this.mvpReason = builder.mvpReason == null ? Component.empty() : builder.mvpReason;
        this.extraInfo1 = builder.extraInfo1 == null ? Component.empty() : builder.extraInfo1;
        this.extraInfo2 = builder.extraInfo2 == null ? Component.empty() : builder.extraInfo2;
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
