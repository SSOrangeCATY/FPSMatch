package com.phasetranscrystal.fpsmatch.common.packet.minimap;

import com.phasetranscrystal.fpsmatch.common.packet.register.NetworkPacketRegister;
import com.phasetranscrystal.fpsmatch.common.packet.register.NetworkPacketRegisterTestHarness;
import net.minecraftforge.network.NetworkDirection;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FPSMatchMinimapPacketRegistrationTest {

    @Test
    void fpsMatchRegistersExactlyTwoConsecutiveDirectedMinimapEnvelopes() {
        List<NetworkPacketRegisterTestHarness.RegistrationCall> calls =
                new ArrayList<>();
        NetworkPacketRegister packets = NetworkPacketRegisterTestHarness.recording(
                calls
        );

        MinimapPacketRegistration.register(packets);

        assertEquals(List.of(
                new NetworkPacketRegisterTestHarness.RegistrationCall(
                        0,
                        MinimapC2SPacket.class,
                        NetworkDirection.PLAY_TO_SERVER
                ),
                new NetworkPacketRegisterTestHarness.RegistrationCall(
                        1,
                        MinimapS2CPacket.class,
                        NetworkDirection.PLAY_TO_CLIENT
                )
        ), calls);
    }
}
