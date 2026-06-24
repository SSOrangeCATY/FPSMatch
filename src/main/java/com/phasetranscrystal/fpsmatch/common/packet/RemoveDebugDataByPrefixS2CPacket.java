package com.phasetranscrystal.fpsmatch.common.packet;

import net.minecraft.network.FriendlyByteBuf;
import com.phasetranscrystal.fpsmatch.common.packet.register.NetworkPacketRegister;

import java.util.function.Supplier;

public record RemoveDebugDataByPrefixS2CPacket(String prefix) {
    public static void encode(RemoveDebugDataByPrefixS2CPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.prefix());
    }

    public static RemoveDebugDataByPrefixS2CPacket decode(FriendlyByteBuf buf) {
        return new RemoveDebugDataByPrefixS2CPacket(buf.readUtf());
    }

    public void handle(Supplier<NetworkPacketRegister.Context> ctx) {
        ClientPacketExecutor.execute(ctx, this);
    }
}
