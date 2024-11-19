package com.phasetranscrystal.fpsmatch.net;

import com.phasetranscrystal.fpsmatch.client.data.ClientData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class BombDemolitionProgressS2CPacket {
    public final float progress;
    public BombDemolitionProgressS2CPacket(float progress){
        this.progress = progress;
    }

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
