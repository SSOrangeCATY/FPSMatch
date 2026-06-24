package com.phasetranscrystal.fpsmatch.common.packet.team;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import com.phasetranscrystal.fpsmatch.common.packet.register.NetworkPacketRegister;

import java.util.function.Supplier;

/**
 * 队伍管理操作结果数据包，服务端发送给客户端
 */
public record TeamManageResultS2CPacket(boolean success, Component message) {

    public static void encode(TeamManageResultS2CPacket packet, RegistryFriendlyByteBuf buf) {
        buf.writeBoolean(packet.success());
        ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, packet.message());
    }

    public static TeamManageResultS2CPacket decode(RegistryFriendlyByteBuf buf) {
        return new TeamManageResultS2CPacket(buf.readBoolean(), ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf));
    }

    public void handle(Supplier<NetworkPacketRegister.Context> ctx) {
        ctx.get().enqueueWork(() -> {});
        ctx.get().setPacketHandled(true);
    }
}
