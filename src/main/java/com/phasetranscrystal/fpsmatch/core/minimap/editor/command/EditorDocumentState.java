package com.phasetranscrystal.fpsmatch.core.minimap.editor.command;

import java.util.List;

public record EditorDocumentState(List<EditorOperation> operations) {
    public EditorDocumentState {
        operations = List.copyOf(operations);
    }
}
