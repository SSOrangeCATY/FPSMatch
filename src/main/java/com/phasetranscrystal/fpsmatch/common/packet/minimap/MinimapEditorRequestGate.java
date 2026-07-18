package com.phasetranscrystal.fpsmatch.common.packet.minimap;

import com.phasetranscrystal.fpsmatch.core.minimap.wire.MinimapWireMessage;

import java.util.UUID;

/**
 * Revalidates one editor request immediately before service dispatch.
 *
 * <p>Implementations parse the opcode payload and bind the current actor, map, dimension,
 * session, and generation/revision. They must fail closed when permission or scope cannot be
 * established.</p>
 */
@FunctionalInterface
public interface MinimapEditorRequestGate {
    boolean revalidatePermissionAndScope(UUID actorId, MinimapWireMessage message);
}
