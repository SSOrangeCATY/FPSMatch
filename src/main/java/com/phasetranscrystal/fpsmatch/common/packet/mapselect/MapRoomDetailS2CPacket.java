package com.phasetranscrystal.fpsmatch.common.packet.mapselect;

import com.phasetranscrystal.fpsmatch.common.packet.ClientPacketExecutor;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record MapRoomDetailS2CPacket(MapRoomDetail detail) {
    public static void encode(MapRoomDetailS2CPacket packet, FriendlyByteBuf buf) {
        MapRoomDetail.encode(packet.detail(), buf);
    }

    public static MapRoomDetailS2CPacket decode(FriendlyByteBuf buf) {
        return new MapRoomDetailS2CPacket(MapRoomDetail.decode(buf));
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ClientPacketExecutor.execute(ctx, this);
    }
}
