package com.phasetranscrystal.fpsmatch.common.packet.register;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NetworkPacketRegisterDirectionTest {

    @Test
    void directedRegistrationForwardsConsecutiveIdAndExactDirection() {
        List<NetworkPacketRegisterTestHarness.RegistrationCall> calls =
                new ArrayList<>();
        NetworkPacketRegister packets = NetworkPacketRegisterTestHarness.recording(
                calls
        );

        packets.registerPacket(TestPacket.class, NetworkDirection.PLAY_TO_SERVER);
        packets.registerPacket(TestPacket.class, NetworkDirection.PLAY_TO_CLIENT);

        assertEquals(List.of(
                new NetworkPacketRegisterTestHarness.RegistrationCall(
                        0, TestPacket.class, NetworkDirection.PLAY_TO_SERVER
                ),
                new NetworkPacketRegisterTestHarness.RegistrationCall(
                        1, TestPacket.class, NetworkDirection.PLAY_TO_CLIENT
                )
        ), calls);
    }

    @Test
    void legacyRegistrationRemainsExplicitlyUndirectedAtTheSink() {
        List<NetworkPacketRegisterTestHarness.RegistrationCall> calls =
                new ArrayList<>();
        NetworkPacketRegister packets = NetworkPacketRegisterTestHarness.recording(
                calls
        );

        packets.registerPacket(TestPacket.class);

        assertEquals(List.of(
                new NetworkPacketRegisterTestHarness.RegistrationCall(
                        0, TestPacket.class, null
                )
        ), calls);
        assertThrows(
                RuntimeException.class,
                () -> NetworkPacketRegister.getChannelFromCache(TestPacket.class)
        );
    }

    public static final class TestPacket {
        public static void encode(TestPacket packet, FriendlyByteBuf buffer) {
        }

        public static TestPacket decode(FriendlyByteBuf buffer) {
            return new TestPacket();
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
        }
    }
}
