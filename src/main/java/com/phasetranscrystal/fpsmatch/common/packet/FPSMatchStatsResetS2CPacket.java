package com.phasetranscrystal.fpsmatch.common.packet;

import net.minecraft.network.FriendlyByteBuf;
import com.phasetranscrystal.fpsmatch.common.packet.register.NetworkPacketRegister;

import java.util.function.Supplier;

public class FPSMatchStatsResetS2CPacket {
    public FPSMatchStatsResetS2CPacket() {
    }
    public static void encode(FPSMatchStatsResetS2CPacket packet, FriendlyByteBuf buf) {
    }

    public static FPSMatchStatsResetS2CPacket decode(FriendlyByteBuf buf) {
        return new FPSMatchStatsResetS2CPacket();
    }

    public void handle(Supplier<NetworkPacketRegister.Context> ctx) {
        ClientPacketExecutor.execute(ctx, this);
    }
}
