package com.ptcrys.fpsmatch.common.packet.minimap;

import java.time.Clock;

final class MinimapPacketEndpointRuntime {
    static final MinimapFrameReassembler REASSEMBLER =
            new MinimapFrameReassembler(Clock.systemUTC());
    static final MinimapPacketEndpointRegistry REGISTRY =
            new MinimapPacketEndpointRegistry(REASSEMBLER);
    static final MinimapPacketEndpointLifecycle LIFECYCLE =
            new MinimapPacketEndpointLifecycle(REGISTRY);

    private MinimapPacketEndpointRuntime() {
    }
}
