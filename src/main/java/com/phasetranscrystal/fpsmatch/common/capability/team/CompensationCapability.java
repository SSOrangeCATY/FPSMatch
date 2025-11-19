package com.phasetranscrystal.fpsmatch.common.capability.team;

import com.phasetranscrystal.fpsmatch.core.capability.FPSMCapability;
import com.phasetranscrystal.fpsmatch.core.team.BaseTeam;
import com.phasetranscrystal.fpsmatch.core.capability.team.TeamCapability;
import com.phasetranscrystal.fpsmatch.core.capability.FPSMCapabilityManager;
import net.minecraft.network.FriendlyByteBuf;

import java.util.function.Function;

public class CompensationCapability extends TeamCapability implements FPSMCapability.Synchronizable {
    private final BaseTeam team;
    private int compensationFactor = 0;
    private Function<Integer,Integer> function = (i)-> Math.max(0, Math.min(i, 4));

    private CompensationCapability(BaseTeam team) {
        this.team = team;
    }

    public static void register() {
        FPSMCapabilityManager.register(CompensationCapability.class, CompensationCapability::new);
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

    @Override
    public BaseTeam getHolder() {
        return team;
    }
}