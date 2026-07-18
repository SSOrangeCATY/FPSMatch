package com.phasetranscrystal.fpsmatch.common.packet.minimap;

import com.phasetranscrystal.fpsmatch.core.minimap.wire.MinimapWireMessage;

import java.util.UUID;

@FunctionalInterface
public interface MinimapEditorRequestGate {
    boolean revalidatePermissionAndScope(UUID actorId, MinimapWireMessage message);
}
