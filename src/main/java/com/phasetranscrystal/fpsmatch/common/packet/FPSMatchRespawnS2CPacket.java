package com.phasetranscrystal.fpsmatch.common.packet;

import net.minecraft.network.FriendlyByteBuf;
import com.phasetranscrystal.fpsmatch.common.packet.register.NetworkPacketRegister;

import java.util.function.Supplier;

public class FPSMatchRespawnS2CPacket {
    public static void encode(FPSMatchRespawnS2CPacket packet, FriendlyByteBuf buf) {
    }

    public static FPSMatchRespawnS2CPacket decode(FriendlyByteBuf buf) {
        return new FPSMatchRespawnS2CPacket();
    }

    public void handle(Supplier<NetworkPacketRegister.Context> ctx) {
        ClientPacketExecutor.execute(ctx, this);
    }
}
