package com.phasetranscrystal.fpsmatch.core.minimap.wire;

import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapOpcode;

/** Stable v1 wire message root. */
public sealed interface MinimapWireMessage permits RuntimeWireMessage, MarkerWireMessage,
        EditorWireMessage, SnapshotWireMessage, PublishWireMessage {
    MinimapOpcode opcode();
}
