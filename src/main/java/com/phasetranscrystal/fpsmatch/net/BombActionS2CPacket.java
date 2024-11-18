package com.phasetranscrystal.fpsmatch.net;

import com.phasetranscrystal.fpsmatch.client.data.ClientData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public record BombActionS2CPacket(int action, UUID uuid) {
    public static void encode(BombActionS2CPacket packet, FriendlyByteBuf buf) {
        buf.writeInt(packet.action);
        buf.writeUUID(packet.uuid);
    }

    public static BombActionS2CPacket decode(FriendlyByteBuf buf) {
        return new BombActionS2CPacket(
                buf.readInt(),
                buf.readUUID());
    }


    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ClientData.dismantleBombStates = action;
            ClientData.bombUUID = action == 2 ? null:uuid;
        });
        ctx.get().setPacketHandled(true);
    }
}
