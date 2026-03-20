package com.phasetranscrystal.fpsmatch.common.packet;

import com.phasetranscrystal.fpsmatch.common.client.FPSMClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record RemoveDebugDataByPrefixS2CPacket(String prefix) {
    public static void encode(RemoveDebugDataByPrefixS2CPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.prefix());
    }

    public static RemoveDebugDataByPrefixS2CPacket decode(FriendlyByteBuf buf) {
        return new RemoveDebugDataByPrefixS2CPacket(buf.readUtf());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> FPSMClient.getGlobalData().getDebugData().removeByPrefix(prefix));
        ctx.get().setPacketHandled(true);
    }
}
