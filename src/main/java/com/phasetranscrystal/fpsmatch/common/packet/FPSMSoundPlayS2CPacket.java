package com.phasetranscrystal.fpsmatch.common.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import com.phasetranscrystal.fpsmatch.common.packet.register.NetworkPacketRegister;

import java.util.function.Supplier;

public class FPSMSoundPlayS2CPacket {
    Identifier location;
    public FPSMSoundPlayS2CPacket(Identifier location) {
        this.location = location;
    }
    public static void encode(FPSMSoundPlayS2CPacket packet, FriendlyByteBuf buf) {
        buf.writeIdentifier(packet.location);
    }

    public static FPSMSoundPlayS2CPacket decode(FriendlyByteBuf buf) {
        return new FPSMSoundPlayS2CPacket(buf.readIdentifier());
    }

    public void handle(Supplier<NetworkPacketRegister.Context> ctx) {
        ClientPacketExecutor.execute(ctx, this);
    }

    public Identifier getLocation() {
        return location;
    }
}
