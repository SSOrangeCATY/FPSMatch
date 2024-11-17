package com.phasetranscrystal.fpsmatch.net;

import com.phasetranscrystal.fpsmatch.client.data.ClientData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record BombActionS2CPacket(int action) {
    public static void encode(BombActionS2CPacket packet, FriendlyByteBuf buf) {
        buf.writeInt(packet.action);
    }

    public static BombActionS2CPacket decode(FriendlyByteBuf buf) {
        return new BombActionS2CPacket(
                buf.readInt());
    }


    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ClientData.dismantleBombStates = action;
        });
        ctx.get().setPacketHandled(true);
    }
}
