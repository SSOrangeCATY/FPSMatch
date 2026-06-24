package com.phasetranscrystal.fpsmatch.common.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.world.phys.Vec3;
import com.phasetranscrystal.fpsmatch.common.packet.register.NetworkPacketRegister;

import java.util.function.Supplier;

public record AddPointDataS2CPacket(String key, Component name, int color, Vec3 position) {
    public static void encode(AddPointDataS2CPacket packet, RegistryFriendlyByteBuf buf) {
        buf.writeUtf(packet.key());
        ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, packet.name());
        buf.writeInt(packet.color());
        buf.writeDouble(packet.position().x);
        buf.writeDouble(packet.position().y);
        buf.writeDouble(packet.position().z);
    }

    public static AddPointDataS2CPacket decode(RegistryFriendlyByteBuf buf) {
        return new AddPointDataS2CPacket(
                buf.readUtf(),
                ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf),
                buf.readInt(),
                new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble())
        );
    }

    public void handle(Supplier<NetworkPacketRegister.Context> ctx) {
        ClientPacketExecutor.execute(ctx, this);
    }
}
