package com.phasetranscrystal.fpsmatch.common.capability.team;

import com.phasetranscrystal.fpsmatch.core.team.BaseTeam;

import com.phasetranscrystal.fpsmatch.core.capability.team.TeamCapability;
import com.phasetranscrystal.fpsmatch.core.capability.FPSMCapabilityManager;
import net.minecraft.network.FriendlyByteBuf;

public class PauseDataCapability extends TeamCapability implements Synchronizable {
    private final BaseTeam team;

    private int pauseTime = 0;
    private boolean needPause = false;

    private PauseDataCapability(BaseTeam team) {
        this.team = team;
    }

    public static void register() {
        FPSMCapabilityManager.register(PauseDataCapability.class, PauseDataCapability::new);
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

    @Override
    public BaseTeam getHolder() {
        return team;
    }
}