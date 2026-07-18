package com.phasetranscrystal.fpsmatch.core.minimap.editor.command;

import com.phasetranscrystal.fpsmatch.core.minimap.format.Sha256Digest;
import com.phasetranscrystal.fpsmatch.core.minimap.model.Sha256;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThreeWayRebaseTest {
    @Test
    void cleanThreeWayMergeAutoAppliesNonOverlappingOps() {
        Sha256 base = hash("base");
        List<EditorOperation> ours = List.of(
                EditorOperation.setOpacity("paint_a", 0.5),
                EditorOperation.paintTile("paint_a", 0, 0, hash("tile-a"), 4)
        );
        List<EditorOperation> theirs = List.of(
                EditorOperation.setOpacity("paint_b", 0.7),
                EditorOperation.paintTile("paint_b", 1, 0, hash("tile-b"), 4)
        );
        RebaseResult result = RebaseEngine.rebase(base, ours, theirs);
        assertTrue(result.conflicts().isEmpty());
        assertEquals(4, result.mergedOperations().size());
        assertEquals(hashMerged(base, result.mergedOperations()), result.mergedRootHash());
    }

    @Test
    void overlappingObjectOrTilePathsProduceExplicitConflicts() {
        Sha256 base = hash("base");
        List<EditorOperation> ours = List.of(EditorOperation.setOpacity("paint_a", 0.5));
        List<EditorOperation> theirs = List.of(EditorOperation.setOpacity("paint_a", 0.9));
        RebaseResult result = RebaseEngine.rebase(base, ours, theirs);
        assertEquals(1, result.conflicts().size());
        MergeConflict conflict = result.conflicts().get(0);
        assertEquals("paint_a", conflict.path());
        assertEquals(MergeConflict.Kind.SAME_OBJECT, conflict.kind());

        RebaseResult resolved = RebaseEngine.resolve(result, List.of(
                ConflictResolution.keepOurs(conflict.id())
        ));
        assertTrue(resolved.conflicts().isEmpty());
        assertEquals(1, resolved.mergedOperations().size());
        assertEquals(0.5, ((EditorOperation.SetOpacity) resolved.mergedOperations().get(0)).opacity());
    }

    private static Sha256 hash(String seed) {
        return Sha256Digest.of(seed.getBytes(StandardCharsets.UTF_8));
    }

    private static Sha256 hashMerged(Sha256 base, List<EditorOperation> ops) {
        return EditorCommandLog.rootHashOf(base, ops);
    }
}
