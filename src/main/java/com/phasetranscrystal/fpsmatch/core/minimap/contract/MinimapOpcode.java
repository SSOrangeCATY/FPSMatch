package com.phasetranscrystal.fpsmatch.core.minimap.contract;

public enum MinimapOpcode {
    C2S_SUBSCRIBE(MinimapMessageDirection.C2S, 0x01),
    C2S_UNSUBSCRIBE(MinimapMessageDirection.C2S, 0x02),
    C2S_REQUEST_ENTRIES(MinimapMessageDirection.C2S, 0x03),
    C2S_REQUEST_MARKER_RESET(MinimapMessageDirection.C2S, 0x04),
    C2S_EDITOR_OPEN(MinimapMessageDirection.C2S, 0x10),
    C2S_EDITOR_RESUME(MinimapMessageDirection.C2S, 0x11),
    C2S_EDITOR_REQUEST_SOURCE_ENTRIES(MinimapMessageDirection.C2S, 0x12),
    C2S_EDITOR_OPERATION(MinimapMessageDirection.C2S, 0x13),
    C2S_EDITOR_UPLOAD_FRAGMENT(MinimapMessageDirection.C2S, 0x14),
    C2S_EDITOR_SAVE_DRAFT(MinimapMessageDirection.C2S, 0x15),
    C2S_EDITOR_REBASE(MinimapMessageDirection.C2S, 0x16),
    C2S_EDITOR_REQUEST_WORLD_SNAPSHOT(MinimapMessageDirection.C2S, 0x17),
    C2S_EDITOR_REQUEST_DIRTY_SECTIONS(MinimapMessageDirection.C2S, 0x18),
    C2S_EDITOR_RESERVE_PUBLISH(MinimapMessageDirection.C2S, 0x19),
    C2S_EDITOR_COMMIT_PUBLISH(MinimapMessageDirection.C2S, 0x1a),
    C2S_EDITOR_CLOSE(MinimapMessageDirection.C2S, 0x1b),
    C2S_EDITOR_QUERY_PUBLISH_STATUS(MinimapMessageDirection.C2S, 0x1c),
    S2C_SCOPE_ACK(MinimapMessageDirection.S2C, 0x41),
    S2C_MANIFEST(MinimapMessageDirection.S2C, 0x42),
    S2C_ENTRY_FRAGMENT(MinimapMessageDirection.S2C, 0x43),
    S2C_MARKER_RESET(MinimapMessageDirection.S2C, 0x44),
    S2C_MARKER_DELTA(MinimapMessageDirection.S2C, 0x45),
    S2C_EDITOR_SESSION(MinimapMessageDirection.S2C, 0x50),
    S2C_EDITOR_SOURCE_MANIFEST(MinimapMessageDirection.S2C, 0x51),
    S2C_EDITOR_SOURCE_FRAGMENT(MinimapMessageDirection.S2C, 0x52),
    S2C_EDITOR_ACK(MinimapMessageDirection.S2C, 0x53),
    S2C_EDITOR_REBASE_RESULT(MinimapMessageDirection.S2C, 0x54),
    S2C_WORLD_SNAPSHOT_MANIFEST(MinimapMessageDirection.S2C, 0x55),
    S2C_WORLD_SNAPSHOT_FRAGMENT(MinimapMessageDirection.S2C, 0x56),
    S2C_DIRTY_SECTIONS(MinimapMessageDirection.S2C, 0x57),
    S2C_PUBLISH_RESERVATION(MinimapMessageDirection.S2C, 0x58),
    S2C_PUBLISH_RESULT(MinimapMessageDirection.S2C, 0x59),
    S2C_PUBLISH_STATUS(MinimapMessageDirection.S2C, 0x5a),
    S2C_ERROR(MinimapMessageDirection.S2C, 0x7f);

    private final MinimapMessageDirection direction;
    private final int code;

    MinimapOpcode(MinimapMessageDirection direction, int code) {
        this.direction = direction;
        this.code = code;
    }

    public MinimapMessageDirection direction() {
        return direction;
    }

    public int code() {
        return code;
    }
}
