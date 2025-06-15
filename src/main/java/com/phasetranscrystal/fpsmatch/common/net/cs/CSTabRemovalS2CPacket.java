package com.phasetranscrystal.fpsmatch.common.net.cs;

import com.phasetranscrystal.fpsmatch.common.client.data.ClientData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public record CSTabRemovalS2CPacket(UUID uuid) {
    public static void encode(CSTabRemovalS2CPacket packet, FriendlyByteBuf buf) {
        buf.writeUUID(packet.uuid);
    }

    public static CSTabRemovalS2CPacket decode(FriendlyByteBuf buf) {
        return new CSTabRemovalS2CPacket(buf.readUUID());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ClientData.removePlayerFromTab(this.uuid);
        });
        ctx.get().setPacketHandled(true);
    }
}
