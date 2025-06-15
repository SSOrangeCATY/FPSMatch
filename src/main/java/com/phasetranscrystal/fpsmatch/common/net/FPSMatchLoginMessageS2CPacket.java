package com.phasetranscrystal.fpsmatch.common.net;

import com.phasetranscrystal.fpsmatch.common.client.data.ClientData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class FPSMatchLoginMessageS2CPacket {
    public FPSMatchLoginMessageS2CPacket() {
    }
    public static void encode(FPSMatchLoginMessageS2CPacket packet, FriendlyByteBuf buf) {
    }

    public static FPSMatchLoginMessageS2CPacket decode(FriendlyByteBuf buf) {
        return new FPSMatchLoginMessageS2CPacket();
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(ClientData::handleLoginMessage);
        ctx.get().setPacketHandled(true);
    }
}
