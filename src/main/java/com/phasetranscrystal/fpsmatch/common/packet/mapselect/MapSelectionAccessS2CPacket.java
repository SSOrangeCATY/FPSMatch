package com.phasetranscrystal.fpsmatch.common.packet.mapselect;

import com.phasetranscrystal.fpsmatch.common.packet.ClientPacketExecutor;
import net.minecraft.network.FriendlyByteBuf;
import com.phasetranscrystal.fpsmatch.common.packet.register.NetworkPacketRegister;

import java.util.function.Supplier;

public record MapSelectionAccessS2CPacket(boolean visible) {
    public static void encode(MapSelectionAccessS2CPacket packet, FriendlyByteBuf buf) {
        buf.writeBoolean(packet.visible());
    }

    public static MapSelectionAccessS2CPacket decode(FriendlyByteBuf buf) {
        return new MapSelectionAccessS2CPacket(buf.readBoolean());
    }

    public void handle(Supplier<NetworkPacketRegister.Context> ctx) {
        ClientPacketExecutor.execute(ctx, this);
    }
}
