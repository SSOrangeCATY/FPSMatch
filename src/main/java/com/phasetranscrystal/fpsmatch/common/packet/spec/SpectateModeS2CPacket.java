package com.phasetranscrystal.fpsmatch.common.packet.spec;

import com.phasetranscrystal.fpsmatch.common.client.spec.SpectateMode;
import com.phasetranscrystal.fpsmatch.common.client.spec.SpectateState;
import net.minecraft.network.FriendlyByteBuf;
import com.phasetranscrystal.fpsmatch.common.packet.register.NetworkPacketRegister;

import java.util.function.Supplier;

public record SpectateModeS2CPacket(SpectateMode mode) {

    public static void encode(SpectateModeS2CPacket p, FriendlyByteBuf buf) {
        buf.writeEnum(p.mode);
    }

    public static SpectateModeS2CPacket decode(FriendlyByteBuf buf) {
        return new SpectateModeS2CPacket(buf.readEnum(SpectateMode.class));
    }

    public void handle(Supplier<NetworkPacketRegister.Context> ctxSup) {
        NetworkPacketRegister.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> {
            SpectateState.set(mode);
        });
        ctx.setPacketHandled(true);
    }
}