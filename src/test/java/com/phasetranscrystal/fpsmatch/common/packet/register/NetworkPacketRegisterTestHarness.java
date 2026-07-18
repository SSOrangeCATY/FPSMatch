package com.phasetranscrystal.fpsmatch.common.packet.register;

import net.minecraftforge.network.NetworkDirection;

import java.util.List;

public final class NetworkPacketRegisterTestHarness {
    private NetworkPacketRegisterTestHarness() {
    }

    public static NetworkPacketRegister recording(
            List<RegistrationCall> calls
    ) {
        return new NetworkPacketRegister((
                discriminator,
                packetClass,
                direction,
                encoder,
                decoder,
                consumer
        ) -> calls.add(new RegistrationCall(
                discriminator, packetClass, direction
        )));
    }

    public record RegistrationCall(
            int discriminator,
            Class<?> packetClass,
            NetworkDirection direction
    ) {
    }
}
