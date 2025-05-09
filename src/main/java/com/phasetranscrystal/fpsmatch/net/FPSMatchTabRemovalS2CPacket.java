package com.phasetranscrystal.fpsmatch.net;

import com.phasetranscrystal.fpsmatch.client.data.ClientData;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public record FPSMatchTabRemovalS2CPacket(UUID uuid) {
    public static void encode(FPSMatchTabRemovalS2CPacket packet, FriendlyByteBuf buf) {
        buf.writeUUID(packet.uuid);
    }

    public static FPSMatchTabRemovalS2CPacket decode(FriendlyByteBuf buf) {
        return new FPSMatchTabRemovalS2CPacket(buf.readUUID());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ClientData.removePlayerFromTab(this.uuid);
        });
        ctx.get().setPacketHandled(true);
    }
}
