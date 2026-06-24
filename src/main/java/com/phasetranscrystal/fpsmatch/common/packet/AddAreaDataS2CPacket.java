package com.phasetranscrystal.fpsmatch.common.packet;

import com.phasetranscrystal.fpsmatch.core.data.AreaData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import com.phasetranscrystal.fpsmatch.common.packet.register.NetworkPacketRegister;

import java.util.function.Supplier;

public record AddAreaDataS2CPacket(String key, Component name, int color, AreaData areaData) {
    public AddAreaDataS2CPacket(Component name, AreaData areaData) {
        this(name.getString(), name, 0xFFFFFF00, areaData);
    }

    public static void encode(AddAreaDataS2CPacket packet, RegistryFriendlyByteBuf buf) {
        buf.writeUtf(packet.key());
        ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, packet.name());
        buf.writeInt(packet.color());
        buf.writeJsonWithCodec(AreaData.CODEC, packet.areaData());
    }

    public static AddAreaDataS2CPacket decode(RegistryFriendlyByteBuf buf) {
        return new AddAreaDataS2CPacket(
                buf.readUtf(),
                ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf),
                buf.readInt(),
                buf.readLenientJsonWithCodec(AreaData.CODEC)
        );
    }

    public void handle(Supplier<NetworkPacketRegister.Context> ctx) {
        ClientPacketExecutor.execute(ctx, this);
    }
}
