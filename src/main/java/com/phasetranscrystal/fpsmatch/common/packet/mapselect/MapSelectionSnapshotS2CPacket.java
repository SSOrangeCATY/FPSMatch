package com.phasetranscrystal.fpsmatch.common.packet.mapselect;

import com.phasetranscrystal.fpsmatch.common.packet.ClientPacketExecutor;
import net.minecraft.network.FriendlyByteBuf;
import com.phasetranscrystal.fpsmatch.common.packet.register.NetworkPacketRegister;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public record MapSelectionSnapshotS2CPacket(List<MapRoomSummary> maps, boolean viewerOp, boolean nonOpButtonEnabled) {
    public static void encode(MapSelectionSnapshotS2CPacket packet, FriendlyByteBuf buf) {
        buf.writeCollection(packet.maps(), (buffer, summary) -> MapRoomSummary.encode(summary, buffer));
        buf.writeBoolean(packet.viewerOp());
        buf.writeBoolean(packet.nonOpButtonEnabled());
    }

    public static MapSelectionSnapshotS2CPacket decode(FriendlyByteBuf buf) {
        return new MapSelectionSnapshotS2CPacket(
                buf.readCollection(ArrayList::new, MapRoomSummary::decode),
                buf.readBoolean(),
                buf.readBoolean()
        );
    }

    public void handle(Supplier<NetworkPacketRegister.Context> ctx) {
        ClientPacketExecutor.execute(ctx, this);
    }
}
