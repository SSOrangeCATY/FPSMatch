package com.ptcrys.fpsmatch.common.packet.minimap;

import com.ptcrys.fpsmatch.core.minimap.wire.MinimapWireMessage;

import java.util.UUID;

@FunctionalInterface
public interface MinimapC2SDispatcher {
    void dispatch(UUID actorId, MinimapWireMessage message);
}
