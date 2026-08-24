package com.ptcrys.fpsmatch.core.minimap.editor.command;

import java.util.ArrayList;
import java.util.List;

public record EditorDocumentState(List<EditorEdit> edits) {
    public EditorDocumentState {
        edits = List.copyOf(edits);
    }

    public List<EditorOperation> operations() {
        List<EditorOperation> operations = new ArrayList<>();
        for (EditorEdit edit : edits) {
            operations.addAll(edit.forward());
        }
        return List.copyOf(operations);
    }
}
