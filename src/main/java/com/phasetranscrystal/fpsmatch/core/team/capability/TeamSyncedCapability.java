package com.phasetranscrystal.fpsmatch.core.team.capability;

import net.minecraft.network.FriendlyByteBuf;

public interface TeamSyncedCapability extends TeamCapability {
    void readFromBuf(FriendlyByteBuf buf);

    void writeToBuf(FriendlyByteBuf buf);
}
