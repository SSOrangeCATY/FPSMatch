package com.phasetranscrystal.fpsmatch.common.packet.mapselect;

import com.phasetranscrystal.fpsmatch.common.packet.ClientPacketExecutor;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import com.phasetranscrystal.fpsmatch.common.packet.register.NetworkPacketRegister;

import java.util.function.Supplier;

public record MapRoomInvitationS2CPacket(String gameType, String mapName, Component message) {
    private static final int ID_MAX_LENGTH = 128;

    public static void encode(MapRoomInvitationS2CPacket packet, RegistryFriendlyByteBuf buf) {
        buf.writeUtf(packet.gameType(), ID_MAX_LENGTH);
        buf.writeUtf(packet.mapName(), ID_MAX_LENGTH);
        ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, packet.message());
    }

    public static MapRoomInvitationS2CPacket decode(RegistryFriendlyByteBuf buf) {
        return new MapRoomInvitationS2CPacket(buf.readUtf(ID_MAX_LENGTH), buf.readUtf(ID_MAX_LENGTH), ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf));
    }

    public void handle(Supplier<NetworkPacketRegister.Context> ctx) {
        ClientPacketExecutor.execute(ctx, this);
    }
}
