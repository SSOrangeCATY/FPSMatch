package com.ptcrys.fpsmatch.core.minimap.editor.command;

import com.ptcrys.fpsmatch.core.minimap.model.Sha256;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class RebaseEngine {
    private RebaseEngine() {
    }

    public static RebaseResult rebase(
            Sha256 baseRootHash,
            List<EditorOperation> ours,
            List<EditorOperation> theirs
    ) {
        Objects.requireNonNull(baseRootHash, "baseRootHash");
        Objects.requireNonNull(ours, "ours");
        Objects.requireNonNull(theirs, "theirs");

        Map<String, EditorOperation> ourByPath = indexByPath(ours);
        Map<String, EditorOperation> theirByPath = indexByPath(theirs);
        Set<String> conflictPaths = new HashSet<>();
        List<MergeConflict> conflicts = new ArrayList<>();
        for (Map.Entry<String, EditorOperation> entry : ourByPath.entrySet()) {
            EditorOperation their = theirByPath.get(entry.getKey());
            if (their == null) {
                continue;
            }
            MergeConflict.Kind kind = entry.getKey().contains("/tiles/")
                    ? MergeConflict.Kind.SAME_TILE
                    : MergeConflict.Kind.SAME_OBJECT;
            conflicts.add(new MergeConflict(
                    UUID.nameUUIDFromBytes(entry.getKey().getBytes()),
                    kind,
                    entry.getKey(),
                    entry.getValue(),
                    their
            ));
            conflictPaths.add(entry.getKey());
        }

        List<EditorOperation> merged = new ArrayList<>();
        for (EditorOperation operation : ours) {
            if (!conflictPaths.contains(operation.path())) {
                merged.add(operation);
            }
        }
        for (EditorOperation operation : theirs) {
            if (!conflictPaths.contains(operation.path())) {
                merged.add(operation);
            }
        }
        return new RebaseResult(
                baseRootHash,
                merged,
                conflicts,
                EditorCommandLog.rootHashOf(baseRootHash, merged)
        );
    }

    public static RebaseResult resolve(RebaseResult current, List<ConflictResolution> resolutions) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(resolutions, "resolutions");
        Map<UUID, ConflictResolution.Choice> choices = new HashMap<>();
        for (ConflictResolution resolution : resolutions) {
            choices.put(resolution.conflictId(), resolution.choice());
        }
        List<EditorOperation> merged = new ArrayList<>(current.mergedOperations());
        List<MergeConflict> remaining = new ArrayList<>();
        for (MergeConflict conflict : current.conflicts()) {
            ConflictResolution.Choice choice = choices.get(conflict.id());
            if (choice == null) {
                remaining.add(conflict);
                continue;
            }
            merged.add(choice == ConflictResolution.Choice.KEEP_OURS ? conflict.ours() : conflict.theirs());
        }
        return new RebaseResult(
                current.baseRootHash(),
                merged,
                remaining,
                EditorCommandLog.rootHashOf(current.baseRootHash(), merged)
        );
    }

    private static Map<String, EditorOperation> indexByPath(List<EditorOperation> operations) {
        Map<String, EditorOperation> indexed = new LinkedHashMap<>();
        for (EditorOperation operation : operations) {
            indexed.put(operation.path(), operation);
        }
        return indexed;
    }
}
