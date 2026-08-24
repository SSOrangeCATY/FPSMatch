package com.ptcrys.fpsmatch.core.minimap.editor.command;

import com.ptcrys.fpsmatch.core.minimap.format.Sha256Digest;
import com.ptcrys.fpsmatch.core.minimap.model.Sha256;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class EditorCommandLog {
    private Sha256 baseRootHash;
    private final List<EditorEdit> logicalHistory = new ArrayList<>();
    private final List<EditorCommand> outboundJournal = new ArrayList<>();
    private int cursor;
    private long nextSequence = 1L;

    private EditorCommandLog(Sha256 baseRootHash) {
        this.baseRootHash = Objects.requireNonNull(baseRootHash, "baseRootHash");
    }

    public static EditorCommandLog empty(Sha256 baseRootHash) {
        return new EditorCommandLog(baseRootHash);
    }

    public EditorCommand append(EditorEdit edit) {
        Objects.requireNonNull(edit, "edit");
        if (cursor < logicalHistory.size()) {
            logicalHistory.subList(cursor, logicalHistory.size()).clear();
        }
        logicalHistory.add(edit);
        cursor++;
        return journal(edit);
    }

    public boolean canUndo() {
        return cursor > 0;
    }

    public boolean canRedo() {
        return cursor < logicalHistory.size();
    }

    public Optional<EditorEdit> nextUndoEdit() {
        return canUndo()
                ? Optional.of(logicalHistory.get(cursor - 1).reversed())
                : Optional.empty();
    }

    public Optional<EditorEdit> nextUndo() {
        return nextUndoEdit();
    }

    public Optional<EditorEdit> nextRedoEdit() {
        return canRedo()
                ? Optional.of(logicalHistory.get(cursor))
                : Optional.empty();
    }

    public Optional<EditorEdit> nextRedo() {
        return nextRedoEdit();
    }

    public Optional<EditorCommand> undo() {
        return canUndo() ? Optional.of(commitUndo()) : Optional.empty();
    }

    public EditorCommand commitUndo() {
        Optional<EditorEdit> inverse = nextUndoEdit();
        if (inverse.isEmpty()) {
            throw new EditorCommandException("No local command available to undo");
        }
        cursor--;
        return journal(inverse.orElseThrow());
    }

    public Optional<EditorCommand> redo() {
        return canRedo() ? Optional.of(commitRedo()) : Optional.empty();
    }

    public EditorCommand commitRedo() {
        Optional<EditorEdit> forward = nextRedoEdit();
        if (forward.isEmpty()) {
            throw new EditorCommandException("No local command available to redo");
        }
        cursor++;
        return journal(forward.orElseThrow());
    }

    public EditorDocumentState currentState() {
        return new EditorDocumentState(logicalHistory.subList(0, cursor));
    }

    public List<EditorCommand> outboundJournal() {
        return List.copyOf(outboundJournal);
    }

    public Sha256 rootHash() {
        return outboundJournal.isEmpty()
                ? baseRootHash
                : outboundJournal.get(outboundJournal.size() - 1).rootHash();
    }

    public Sha256 baseRootHash() {
        return baseRootHash;
    }

    /**
     * Moves the outbound command baseline to an authoritative server root while
     * retaining the logical undo/redo history. A clean reconnect has already
     * applied that history to the downloaded source, so replaying the old
     * journal would duplicate edits; only future commands need a new sequence.
     */
    public void reanchor(Sha256 authoritativeRootHash, long acknowledgedCursor) {
        Objects.requireNonNull(authoritativeRootHash, "authoritativeRootHash");
        if (acknowledgedCursor < 0 || acknowledgedCursor == Long.MAX_VALUE) {
            throw new IllegalArgumentException("Authoritative ACK cursor is outside its range");
        }
        baseRootHash = authoritativeRootHash;
        outboundJournal.clear();
        nextSequence = acknowledgedCursor + 1L;
    }

    public static Sha256 rootHashOf(
            Sha256 baseRootHash,
            long sequence,
            List<EditorOperation> operations
    ) {
        Objects.requireNonNull(baseRootHash, "baseRootHash");
        Objects.requireNonNull(operations, "operations");
        if (sequence <= 0) {
            throw new IllegalArgumentException("Preview sequence must be positive");
        }
        if (operations.isEmpty()) {
            return baseRootHash;
        }
        return EditorCommandHasher.nextRoot(
                baseRootHash,
                sequence,
                Sha256Digest.of(EditorCommandHasher.descriptorBytes(operations))
        );
    }

    private EditorCommand journal(EditorEdit edit) {
        EditorCommand command = new EditorCommand(nextSequence++, rootHash(), edit);
        outboundJournal.add(command);
        return command;
    }
}
