package com.phasetranscrystal.fpsmatch.common.packet.minimap;

import com.phasetranscrystal.fpsmatch.common.packet.register.NetworkPacketRegister;
import net.minecraftforge.network.NetworkDirection;

import java.util.Objects;

public final class MinimapPacketRegistration {
    private MinimapPacketRegistration() {
    }

    public static void register(NetworkPacketRegister packets) {
        Objects.requireNonNull(packets, "packets");
        packets.registerPacket(
                MinimapC2SPacket.class,
                NetworkDirection.PLAY_TO_SERVER
        );
        packets.registerPacket(
                MinimapS2CPacket.class,
                NetworkDirection.PLAY_TO_CLIENT
        );
    }
}
