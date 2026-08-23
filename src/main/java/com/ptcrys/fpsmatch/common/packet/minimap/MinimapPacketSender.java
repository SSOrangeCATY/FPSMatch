package com.ptcrys.fpsmatch.common.packet.minimap;

import com.ptcrys.fpsmatch.core.minimap.wire.MinimapWireMessage;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

public final class MinimapPacketSender {
    private MinimapPacketSender() {
    }

    public static int sendC2S(
            UUID frameId,
            MinimapWireMessage message,
            Consumer<? super MinimapC2SPacket> transport
    ) {
        Objects.requireNonNull(transport, "transport");
        List<MinimapC2SPacket> packets = MinimapC2SPacket.fromMessage(
                frameId, message
        );
        packets.forEach(transport);
        return packets.size();
    }
}
