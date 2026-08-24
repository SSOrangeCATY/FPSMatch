package com.ptcrys.fpsmatch.common.client.minimap.editor;

import com.ptcrys.fpsmatch.core.minimap.contract.MinimapOpcode;

enum EditorRequestKind {
    OPEN(MinimapOpcode.C2S_EDITOR_OPEN),
    RESUME(MinimapOpcode.C2S_EDITOR_RESUME),
    SOURCE(MinimapOpcode.C2S_EDITOR_REQUEST_SOURCE_ENTRIES),
    OPERATION(MinimapOpcode.C2S_EDITOR_OPERATION),
    UPLOAD_BEGIN(MinimapOpcode.C2S_EDITOR_UPLOAD_FRAGMENT),
    UPLOAD_DATA(MinimapOpcode.C2S_EDITOR_UPLOAD_FRAGMENT),
    UPLOAD_FINISH(MinimapOpcode.C2S_EDITOR_UPLOAD_FRAGMENT),
    SAVE(MinimapOpcode.C2S_EDITOR_SAVE_DRAFT),
    REBASE(MinimapOpcode.C2S_EDITOR_REBASE),
    PUBLISH(MinimapOpcode.C2S_EDITOR_RESERVE_PUBLISH),
    PUBLISH_STATUS(MinimapOpcode.C2S_EDITOR_QUERY_PUBLISH_STATUS),
    CLOSE(MinimapOpcode.C2S_EDITOR_CLOSE);

    private final MinimapOpcode opcode;

    EditorRequestKind(MinimapOpcode opcode) {
        this.opcode = opcode;
    }

    MinimapOpcode opcode() {
        return opcode;
    }
}
