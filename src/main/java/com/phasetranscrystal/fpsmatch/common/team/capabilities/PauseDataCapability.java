package com.phasetranscrystal.fpsmatch.common.team.capabilities;

import com.phasetranscrystal.fpsmatch.core.team.BaseTeam;

import com.phasetranscrystal.fpsmatch.core.team.capability.TeamCapabilityManager;
import com.phasetranscrystal.fpsmatch.core.team.capability.TeamSyncedCapability;
import net.minecraft.network.FriendlyByteBuf;

public class PauseDataCapability implements TeamSyncedCapability {
    private final BaseTeam team;

    private int pauseTime = 0;
    private boolean needPause = false;

    private PauseDataCapability(BaseTeam team) {
        this.team = team;
    }

    public static void register() {
        TeamCapabilityManager.register(PauseDataCapability.class, PauseDataCapability::new);
    }

    public void addPause() {
        if (pauseTime < 2 && !needPause) {
            needPause = true;
            pauseTime++;
        }
    }

    public boolean canPause() {
        return pauseTime < 2 && !needPause;
    }

    public void setPauseTime(int t) {
        this.pauseTime = t;
    }

    public void resetPauseIfNeed() {
        if (this.needPause) {
            this.needPause = false;
            this.pauseTime--;
        }
    }

    public void setNeedPause(boolean needPause) {
        this.needPause = needPause;
    }

    public boolean needPause() {
        return needPause;
    }

    public int getPauseTime() {
        return pauseTime;
    }

    @Override
    public void init() {
        this.pauseTime = 0;
        this.needPause = false;
    }

    @Override
    public void reset() {
        this.pauseTime = 0;
        this.needPause = false;
    }

    @Override
    public void destroy() {
        reset();
    }

    @Override
    public void readFromBuf(FriendlyByteBuf buf) {
        this.pauseTime = buf.readInt();
        this.needPause = buf.readBoolean();
    }

    @Override
    public void writeToBuf(FriendlyByteBuf buf) {
        buf.writeInt(this.pauseTime);
        buf.writeBoolean(this.needPause);
    }
}