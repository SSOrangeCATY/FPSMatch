package com.phasetranscrystal.fpsmatch.common.capability.team;

import com.phasetranscrystal.fpsmatch.core.capability.FPSMCapability;
import com.phasetranscrystal.fpsmatch.core.team.BaseTeam;
import com.phasetranscrystal.fpsmatch.core.capability.team.TeamCapability;
import com.phasetranscrystal.fpsmatch.core.capability.FPSMCapabilityManager;
import net.minecraft.network.FriendlyByteBuf;

import java.util.function.Function;

public class CompensationCapability extends TeamCapability implements FPSMCapability.CapabilitySynchronizable {
    private boolean dirty = false;
    private int compensationFactor = 0;
    private Function<Integer,Integer> setter = (i)-> Math.max(1, Math.min(i, 4));

    public CompensationCapability(BaseTeam team) {
        super(team);
    }

    public static void register() {
        FPSMCapabilityManager.register(FPSMCapabilityManager.CapabilityType.TEAM, CompensationCapability.class, CompensationCapability::new);
    }

    public void withSetter( Function<Integer,Integer> setter) {
        this.setter = setter;
    }

    public Function<Integer, Integer> getSetter() {
        return setter;
    }

    public void add(int factor){
        setFactor(compensationFactor + factor);
    }

    public void reduce(int factor){
        setFactor(compensationFactor - factor);
    }

    public int getFactor() {
        return compensationFactor;
    }

    public void setFactor(int factor) {
        this.compensationFactor = setter == null ? factor : setter.apply(factor);
        dirty = true;
    }

    @Override
    public void reset() {
        compensationFactor = 1;
        dirty = true;
    }

    @Override
    public boolean isDirty() {
        return dirty;
    }

    @Override
    public void readFromBuf(FriendlyByteBuf buf) {
        compensationFactor = buf.readInt();
    }

    @Override
    public void writeToBuf(FriendlyByteBuf buf) {
        buf.writeInt(compensationFactor);
        dirty = false;
    }
}