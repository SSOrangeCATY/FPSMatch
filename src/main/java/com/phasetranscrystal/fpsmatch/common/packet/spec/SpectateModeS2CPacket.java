package com.phasetranscrystal.fpsmatch.common.packet.spec;

import com.phasetranscrystal.fpsmatch.common.client.spectator.SpectateState;
import com.phasetranscrystal.fpsmatch.common.spectator.teammate.SpectateMode;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record SpectateModeS2CPacket(SpectateMode mode) {

    public static void encode(SpectateModeS2CPacket p, FriendlyByteBuf buf) {
        buf.writeEnum(p.mode);
    }

    public static SpectateModeS2CPacket decode(FriendlyByteBuf buf) {
        return new SpectateModeS2CPacket(buf.readEnum(SpectateMode.class));
    }

    public static void handle(SpectateModeS2CPacket p, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> {
            SpectateState.set(p.mode);
        });
        ctx.setPacketHandled(true);
    }
}