package com.phasetranscrystal.fpsmatch.common.net.cs.bomb;

import com.phasetranscrystal.fpsmatch.common.client.data.ClientData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record BombDemolitionProgressS2CPacket(float progress) {

    public static void encode(BombDemolitionProgressS2CPacket packet, FriendlyByteBuf buf) {
        buf.writeFloat(packet.progress);
    }

    public static BombDemolitionProgressS2CPacket decode(FriendlyByteBuf buf) {
        return new BombDemolitionProgressS2CPacket(
                buf.readFloat());
    }


    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ClientData.dismantleBombProgress = progress;
        });
        ctx.get().setPacketHandled(true);
    }
}
