package com.phasetranscrystal.fpsmatch.core.minimap.editor.command;

import com.phasetranscrystal.fpsmatch.core.minimap.model.Sha256;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EditorCommandLogTest {
    @Test
    void assignsMonotonicSequencesAndSupportsUndoRedoInverses() {
        EditorCommandLog log = EditorCommandLog.empty(hash("aa"));
        EditorCommand first = log.append(EditorOperation.setOpacity("paint_a", 0.5));
        EditorCommand second = log.append(EditorOperation.setOpacity("paint_a", 0.2));
        assertEquals(1L, first.sequence());
        assertEquals(2L, second.sequence());
        assertEquals(hash("aa"), first.baseRootHash());
        assertTrue(log.canUndo());
        assertFalse(log.canRedo());

        EditorCommand undone = log.undo().orElseThrow();
        assertEquals(second.sequence(), undone.sequence());
        assertTrue(log.canRedo());
        assertEquals(0.5, ((EditorOperation.SetOpacity) log.currentState().operations().get(0)).opacity());

        EditorCommand redone = log.redo().orElseThrow();
        assertEquals(second.sequence(), redone.sequence());
        assertEquals(0.2, ((EditorOperation.SetOpacity) log.currentState().operations().get(1)).opacity());
    }

    @Test
    void newEditAfterUndoTruncatesRedoBranch() {
        EditorCommandLog log = EditorCommandLog.empty(hash("bb"));
        log.append(EditorOperation.setOpacity("a", 0.1));
        log.append(EditorOperation.setOpacity("a", 0.2));
        log.undo();
        log.append(EditorOperation.setOpacity("a", 0.3));
        assertFalse(log.canRedo());
        assertEquals(2, log.currentState().operations().size());
        assertEquals(0.3, ((EditorOperation.SetOpacity) log.currentState().operations().get(1)).opacity());
    }

    @Test
    void commandsReferencePayloadHashesInsteadOfEmbeddingLargeTiles() {
        Sha256 payload = hash("cc");
        EditorCommand command = EditorCommandLog.empty(hash("dd"))
                .append(EditorOperation.paintTile("paint_a", 0, 0, payload, 64 * 64));
        EditorOperation.PaintTile paint = (EditorOperation.PaintTile) command.operation();
        assertEquals(payload, paint.payloadHash());
        assertEquals(64 * 64, paint.pixelCount());
    }

    private static Sha256 hash(String seed) {
        return com.phasetranscrystal.fpsmatch.core.minimap.format.Sha256Digest.of(seed.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
