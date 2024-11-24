package com.phasetranscrystal.fpsmatch.net;

import com.phasetranscrystal.fpsmatch.client.data.ClientData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class FPSMatchStatsResetS2CPacket {
    public FPSMatchStatsResetS2CPacket() {
    }
    public static void encode(FPSMatchStatsResetS2CPacket packet, FriendlyByteBuf buf) {
    }

    public static FPSMatchStatsResetS2CPacket decode(FriendlyByteBuf buf) {
        return new FPSMatchStatsResetS2CPacket();
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(ClientData::reset);
        ctx.get().setPacketHandled(true);
    }
}
