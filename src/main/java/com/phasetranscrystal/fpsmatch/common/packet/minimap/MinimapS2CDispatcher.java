package com.phasetranscrystal.fpsmatch.common.packet.minimap;

import com.phasetranscrystal.fpsmatch.core.minimap.wire.MinimapWireMessage;

@FunctionalInterface
public interface MinimapS2CDispatcher {
    void dispatch(MinimapWireMessage message);
}
