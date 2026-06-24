package com.phasetranscrystal.fpsmatch.common.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import com.phasetranscrystal.fpsmatch.common.packet.register.NetworkPacketRegister;

import java.util.function.Supplier;

public class FPSMusicPlayS2CPacket {
    Identifier location;
    public FPSMusicPlayS2CPacket(Identifier location) {
        this.location = location;
    }
    public static void encode(FPSMusicPlayS2CPacket packet, FriendlyByteBuf buf) {
        buf.writeIdentifier(packet.location);
    }

    public static FPSMusicPlayS2CPacket decode(FriendlyByteBuf buf) {
        return new FPSMusicPlayS2CPacket(buf.readIdentifier());
    }

    public void handle(Supplier<NetworkPacketRegister.Context> ctx) {
        ClientPacketExecutor.execute(ctx, this);
    }

    public Identifier getLocation() {
        return location;
    }
}
