package com.phasetranscrystal.fpsmatch.common.team.capabilities;

import com.phasetranscrystal.fpsmatch.core.team.BaseTeam;
import com.phasetranscrystal.fpsmatch.core.team.capability.TeamCapabilityManager;
import com.phasetranscrystal.fpsmatch.core.team.capability.TeamSyncedCapability;
import net.minecraft.network.FriendlyByteBuf;

import java.util.function.Function;

public class CompensationCapability implements TeamSyncedCapability {
    private final BaseTeam team;
    private int compensationFactor = 0;
    private Function<Integer,Integer> function = (i)-> Math.max(0, Math.min(i, 4));

    private CompensationCapability(BaseTeam team) {
        this.team = team;
    }

    public static void register() {
        TeamCapabilityManager.register(CompensationCapability.class, CompensationCapability::new);
    }

    public void setFunction(Function<Integer,Integer> function) {
        if(this.team.isClientSide()) return;
        this.function = function;
    }

    public int getCompensationFactor() {
        return compensationFactor;
    }

    public void setCompensationFactor(int factor) {
        this.compensationFactor = function.apply(factor);
    }

    @Override
    public void reset() {
        compensationFactor = 0;
    }

    @Override
    public void readFromBuf(FriendlyByteBuf buf) {
        compensationFactor = buf.readInt();
    }

    @Override
    public void writeToBuf(FriendlyByteBuf buf) {
        buf.writeInt(compensationFactor);
    }
}