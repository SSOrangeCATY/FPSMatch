package com.phasetranscrystal.fpsmatch.core.minimap.editor.command;

import com.phasetranscrystal.fpsmatch.core.minimap.format.Sha256Digest;
import com.phasetranscrystal.fpsmatch.core.minimap.model.Sha256;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class EditorCommandLog {
    private final Sha256 baseRootHash;
    private final List<EditorCommand> history = new ArrayList<>();
    private int cursor;
    private long nextSequence = 1L;

    private EditorCommandLog(Sha256 baseRootHash) {
        this.baseRootHash = Objects.requireNonNull(baseRootHash, "baseRootHash");
        this.cursor = 0;
    }

    public static EditorCommandLog empty(Sha256 baseRootHash) {
        return new EditorCommandLog(baseRootHash);
    }

    public static EditorCommandLog restore(DraftSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        EditorCommandLog log = empty(snapshot.baseSourceHash());
        for (EditorOperation operation : snapshot.operations()) {
            log.append(operation);
        }
        return log;
    }

    public EditorCommand append(EditorOperation operation) {
        Objects.requireNonNull(operation, "operation");
        if (cursor < history.size()) {
            history.subList(cursor, history.size()).clear();
        }
        Sha256 previous = rootHash();
        List<EditorOperation> nextOps = new ArrayList<>(currentState().operations());
        nextOps.add(operation);
        Sha256 resulting = rootHashOf(baseRootHash, nextOps);
        EditorCommand command = new EditorCommand(nextSequence++, previous, resulting, operation);
        history.add(command);
        cursor = history.size();
        return command;
    }

    public boolean canUndo() {
        return cursor > 0;
    }

    public boolean canRedo() {
        return cursor < history.size();
    }

    public Optional<EditorCommand> undo() {
        if (!canUndo()) {
            return Optional.empty();
        }
        cursor--;
        return Optional.of(history.get(cursor));
    }

    public Optional<EditorCommand> redo() {
        if (!canRedo()) {
            return Optional.empty();
        }
        EditorCommand command = history.get(cursor);
        cursor++;
        return Optional.of(command);
    }

    public EditorDocumentState currentState() {
        List<EditorOperation> operations = new ArrayList<>(cursor);
        for (int index = 0; index < cursor; index++) {
            operations.add(history.get(index).operation());
        }
        return new EditorDocumentState(operations);
    }

    public Sha256 rootHash() {
        return rootHashOf(baseRootHash, currentState().operations());
    }

    public Sha256 baseRootHash() {
        return baseRootHash;
    }

    public static Sha256 rootHashOf(Sha256 baseRootHash, List<EditorOperation> operations) {
        Objects.requireNonNull(baseRootHash, "baseRootHash");
        Objects.requireNonNull(operations, "operations");
        if (operations.isEmpty()) {
            return baseRootHash;
        }
        StringBuilder builder = new StringBuilder(baseRootHash.value());
        for (EditorOperation operation : operations) {
            builder.append('\n').append(canonical(operation));
        }
        return Sha256Digest.of(builder.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String canonical(EditorOperation operation) {
        if (operation instanceof EditorOperation.SetOpacity setOpacity) {
            return "set_opacity|" + setOpacity.layerId() + "|" + setOpacity.opacity();
        }
        if (operation instanceof EditorOperation.SetVisibility setVisibility) {
            return "set_visibility|" + setVisibility.layerId() + "|" + setVisibility.visible();
        }
        if (operation instanceof EditorOperation.PaintTile paintTile) {
            return "paint_tile|" + paintTile.layerId() + "|" + paintTile.tileX() + "|" + paintTile.tileY()
                    + "|" + paintTile.payloadHash().value() + "|" + paintTile.pixelCount();
        }
        throw new IllegalArgumentException("Unknown operation: " + operation);
    }
}
