package com.phasetranscrystal.fpsmatch.common.packet.mapselect;

import com.phasetranscrystal.fpsmatch.common.packet.ClientPacketExecutor;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import com.phasetranscrystal.fpsmatch.common.packet.register.NetworkPacketRegister;

import java.util.function.Supplier;

public record MapRoomToastS2CPacket(Component message, boolean error) {
    public static void encode(MapRoomToastS2CPacket packet, RegistryFriendlyByteBuf buf) {
        ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, packet.message());
        buf.writeBoolean(packet.error());
    }

    public static MapRoomToastS2CPacket decode(RegistryFriendlyByteBuf buf) {
        return new MapRoomToastS2CPacket(ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf), buf.readBoolean());
    }

    public void handle(Supplier<NetworkPacketRegister.Context> ctx) {
        ClientPacketExecutor.execute(ctx, this);
    }
}
