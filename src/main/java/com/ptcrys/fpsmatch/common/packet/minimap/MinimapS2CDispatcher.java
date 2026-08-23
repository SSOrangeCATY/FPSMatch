package com.ptcrys.fpsmatch.common.packet.minimap;

import com.ptcrys.fpsmatch.core.minimap.wire.MinimapWireMessage;

@FunctionalInterface
public interface MinimapS2CDispatcher {
    void dispatch(MinimapWireMessage message);
}
