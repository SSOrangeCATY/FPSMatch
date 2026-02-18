package com.phasetranscrystal.fpsmatch.common.packet;

import com.phasetranscrystal.fpsmatch.common.client.FPSMClient;
import com.phasetranscrystal.fpsmatch.common.client.data.RenderableArea;
import com.phasetranscrystal.fpsmatch.core.data.AreaData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record AddAreaDataS2CPacket(Component name, AreaData areaData) {
    public static void encode(AddAreaDataS2CPacket packet, FriendlyByteBuf buf) {
        buf.writeComponent(packet.name());
        buf.writeJsonWithCodec(AreaData.CODEC, packet.areaData());
    }

    public static AddAreaDataS2CPacket decode(FriendlyByteBuf buf) {
        return new AddAreaDataS2CPacket(buf.readComponent(),buf.readJsonWithCodec(AreaData.CODEC));
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            FPSMClient.getGlobalData().getDebugData().addRenderableArea(new RenderableArea(name,areaData));
        });
        ctx.get().setPacketHandled(true);
    }
}