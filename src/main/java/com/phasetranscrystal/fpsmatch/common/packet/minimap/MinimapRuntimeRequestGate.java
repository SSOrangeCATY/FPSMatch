package com.phasetranscrystal.fpsmatch.common.packet.minimap;

import com.phasetranscrystal.fpsmatch.core.minimap.wire.MinimapWireMessage;

import java.util.UUID;

@FunctionalInterface
public interface MinimapRuntimeRequestGate {
    boolean allow(UUID actorId, MinimapWireMessage message);
}
