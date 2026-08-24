package com.ptcrys.fpsmatch.core.minimap.editor.command;

import com.ptcrys.fpsmatch.core.minimap.model.Sha256;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record RebaseResult(
        Sha256 baseRootHash,
        List<EditorOperation> mergedOperations,
        List<MergeConflict> conflicts,
        Sha256 mergedRootHash,
        long previewSequence,
        List<MergeSlot> mergePlan
) {
    public RebaseResult(
            Sha256 baseRootHash,
            List<EditorOperation> mergedOperations,
            List<MergeConflict> conflicts,
            Sha256 mergedRootHash,
            long previewSequence
    ) {
        this(
                baseRootHash,
                mergedOperations,
                conflicts,
                mergedRootHash,
                previewSequence,
                defaultPlan(mergedOperations, conflicts)
        );
    }

    public RebaseResult {
        Objects.requireNonNull(baseRootHash, "baseRootHash");
        mergedOperations = List.copyOf(mergedOperations);
        conflicts = List.copyOf(conflicts);
        Objects.requireNonNull(mergedRootHash, "mergedRootHash");
        if (previewSequence <= 0) {
            throw new IllegalArgumentException("Rebase preview sequence must be positive");
        }
        mergePlan = List.copyOf(mergePlan);
    }

    private static List<MergeSlot> defaultPlan(
            List<EditorOperation> mergedOperations,
            List<MergeConflict> conflicts
    ) {
        Objects.requireNonNull(mergedOperations, "mergedOperations");
        Objects.requireNonNull(conflicts, "conflicts");
        if (!conflicts.isEmpty()) {
            throw new IllegalArgumentException(
                    "Rebase results with conflicts require an explicit merge plan"
            );
        }
        java.util.ArrayList<MergeSlot> plan = new java.util.ArrayList<>();
        for (EditorOperation operation : mergedOperations) {
            plan.add(new OperationsSlot(List.of(operation)));
        }
        return List.copyOf(plan);
    }

    public sealed interface MergeSlot permits OperationsSlot, ConflictSlot {
    }

    public record OperationsSlot(List<EditorOperation> operations) implements MergeSlot {
        public OperationsSlot {
            operations = List.copyOf(operations);
            if (operations.isEmpty()) {
                throw new IllegalArgumentException("Rebase operation slots cannot be empty");
            }
        }
    }

    public record ConflictSlot(UUID conflictId, int oursOrdinal) implements MergeSlot {
        public ConflictSlot(UUID conflictId) {
            this(conflictId, 0);
        }

        public ConflictSlot {
            Objects.requireNonNull(conflictId, "conflictId");
            if (oursOrdinal < 0) {
                throw new IllegalArgumentException("Rebase conflict ordinals cannot be negative");
            }
        }
    }
}
