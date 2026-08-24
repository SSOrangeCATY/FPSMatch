package com.ptcrys.fpsmatch.core.minimap.editor.command;

import com.ptcrys.fpsmatch.core.minimap.model.Sha256;

import java.nio.charset.StandardCharsets;
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
            long previewSequence,
            List<EditorOperation> ours,
            List<EditorOperation> theirs
    ) {
        Objects.requireNonNull(baseRootHash, "baseRootHash");
        Objects.requireNonNull(ours, "ours");
        Objects.requireNonNull(theirs, "theirs");
        requirePreviewSequence(previewSequence);

        List<ConflictDomain> domains = conflictDomains(ours, theirs);
        Map<Integer, RebaseResult.ConflictSlot> ourConflictSlots = new HashMap<>();
        Set<Integer> omittedTheirs = new HashSet<>();
        List<MergeConflict> conflicts = new ArrayList<>();
        for (ConflictDomain domain : domains) {
            if (domain.oursOperations().equals(domain.theirsOperations())) {
                domain.theirs().forEach(reference -> omittedTheirs.add(reference.ordinal()));
                continue;
            }

            String path = commonAncestorPath(domain.references());
            UUID conflictId = conflictId(path);
            conflicts.add(new MergeConflict(
                    conflictId,
                    kindFor(path),
                    path,
                    domain.oursOperations(),
                    domain.theirsOperations()
            ));
            // A slot retains the ours-side ordinal so resolving [p, q, p] restores q between p's.
            for (int ordinal = 0; ordinal < domain.ours().size(); ordinal++) {
                ourConflictSlots.put(
                        domain.ours().get(ordinal).ordinal(),
                        new RebaseResult.ConflictSlot(conflictId, ordinal)
                );
            }
            domain.theirs().forEach(reference -> omittedTheirs.add(reference.ordinal()));
        }

        List<RebaseResult.MergeSlot> plan = new ArrayList<>();
        for (int ordinal = 0; ordinal < ours.size(); ordinal++) {
            RebaseResult.ConflictSlot conflictSlot = ourConflictSlots.get(ordinal);
            if (conflictSlot != null) {
                plan.add(conflictSlot);
            } else {
                plan.add(new RebaseResult.OperationsSlot(List.of(ours.get(ordinal))));
            }
        }
        for (int ordinal = 0; ordinal < theirs.size(); ordinal++) {
            if (!omittedTheirs.contains(ordinal)) {
                plan.add(new RebaseResult.OperationsSlot(List.of(theirs.get(ordinal))));
            }
        }
        List<EditorOperation> merged = flatten(plan);
        return new RebaseResult(
                baseRootHash,
                merged,
                conflicts,
                EditorCommandLog.rootHashOf(baseRootHash, previewSequence, merged),
                previewSequence,
                plan
        );
    }

    public static RebaseResult resolve(RebaseResult current, List<ConflictResolution> resolutions) {
        return resolve(current, current.previewSequence(), resolutions);
    }

    public static RebaseResult resolve(
            RebaseResult current,
            long previewSequence,
            List<ConflictResolution> resolutions
    ) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(resolutions, "resolutions");
        requirePreviewSequence(previewSequence);
        Map<UUID, ConflictResolution.Choice> choices = new HashMap<>();
        for (ConflictResolution resolution : resolutions) {
            if (choices.put(resolution.conflictId(), resolution.choice()) != null) {
                throw new IllegalArgumentException("A rebase conflict was resolved more than once");
            }
        }
        Map<UUID, MergeConflict> conflictsById = new LinkedHashMap<>();
        for (MergeConflict conflict : current.conflicts()) {
            conflictsById.put(conflict.id(), conflict);
        }
        for (UUID choiceId : choices.keySet()) {
            if (!conflictsById.containsKey(choiceId)) {
                throw new IllegalArgumentException("Resolution references an unknown conflict");
            }
        }

        List<EditorOperation> merged = new ArrayList<>();
        List<MergeConflict> remaining = new ArrayList<>();
        List<RebaseResult.MergeSlot> nextPlan = new ArrayList<>();
        Set<UUID> remainingIds = new HashSet<>();
        for (RebaseResult.MergeSlot slot : current.mergePlan()) {
            if (slot instanceof RebaseResult.OperationsSlot operationsSlot) {
                merged.addAll(operationsSlot.operations());
                nextPlan.add(operationsSlot);
                continue;
            }
            RebaseResult.ConflictSlot conflictSlot = (RebaseResult.ConflictSlot) slot;
            MergeConflict conflict = conflictsById.get(conflictSlot.conflictId());
            if (conflict == null) {
                throw new IllegalArgumentException("Rebase plan references an unknown conflict");
            }
            ConflictResolution.Choice choice = choices.get(conflict.id());
            if (choice == null) {
                if (remainingIds.add(conflict.id())) {
                    remaining.add(conflict);
                }
                nextPlan.add(conflictSlot);
                continue;
            }

            if (choice == ConflictResolution.Choice.KEEP_OURS) {
                EditorOperation selected = conflict.oursOperations().get(conflictSlot.oursOrdinal());
                merged.add(selected);
                nextPlan.add(new RebaseResult.OperationsSlot(List.of(selected)));
            } else if (conflictSlot.oursOrdinal() == 0) {
                List<EditorOperation> selected = conflict.theirsOperations();
                merged.addAll(selected);
                nextPlan.add(new RebaseResult.OperationsSlot(selected));
            }
        }
        return new RebaseResult(
                current.baseRootHash(),
                merged,
                remaining,
                EditorCommandLog.rootHashOf(current.baseRootHash(), previewSequence, merged),
                previewSequence,
                nextPlan
        );
    }

    private static List<ConflictDomain> conflictDomains(
            List<EditorOperation> ours,
            List<EditorOperation> theirs
    ) {
        List<OperationReference> references = new ArrayList<>(ours.size() + theirs.size());
        for (int ordinal = 0; ordinal < ours.size(); ordinal++) {
            references.add(new OperationReference(Side.OURS, ordinal, ours.get(ordinal)));
        }
        for (int ordinal = 0; ordinal < theirs.size(); ordinal++) {
            references.add(new OperationReference(Side.THEIRS, ordinal, theirs.get(ordinal)));
        }

        DisjointSet groups = new DisjointSet(references.size());
        for (int ourOrdinal = 0; ourOrdinal < ours.size(); ourOrdinal++) {
            for (int theirOrdinal = 0; theirOrdinal < theirs.size(); theirOrdinal++) {
                if (pathsConflict(ours.get(ourOrdinal).path(), theirs.get(theirOrdinal).path())) {
                    groups.union(ourOrdinal, ours.size() + theirOrdinal);
                }
            }
        }

        Map<Integer, List<OperationReference>> grouped = new LinkedHashMap<>();
        for (int index = 0; index < references.size(); index++) {
            grouped.computeIfAbsent(groups.find(index), ignored -> new ArrayList<>())
                    .add(references.get(index));
        }
        List<ConflictDomain> domains = new ArrayList<>();
        for (List<OperationReference> group : grouped.values()) {
            List<OperationReference> oursInDomain = new ArrayList<>();
            List<OperationReference> theirsInDomain = new ArrayList<>();
            for (OperationReference reference : group) {
                if (reference.side() == Side.OURS) {
                    oursInDomain.add(reference);
                } else {
                    theirsInDomain.add(reference);
                }
            }
            if (!oursInDomain.isEmpty() && !theirsInDomain.isEmpty()) {
                domains.add(new ConflictDomain(oursInDomain, theirsInDomain));
            }
        }
        return List.copyOf(domains);
    }

    private static boolean pathsConflict(String left, String right) {
        return left.equals(right)
                || left.startsWith(right + "/")
                || right.startsWith(left + "/");
    }

    private static String commonAncestorPath(List<OperationReference> references) {
        String[] common = references.get(0).operation().path().split("/");
        int commonLength = common.length;
        for (int index = 1; index < references.size(); index++) {
            String[] candidate = references.get(index).operation().path().split("/");
            commonLength = Math.min(commonLength, candidate.length);
            for (int segment = 0; segment < commonLength; segment++) {
                if (!common[segment].equals(candidate[segment])) {
                    commonLength = segment;
                    break;
                }
            }
        }
        if (commonLength == 0) {
            throw new IllegalArgumentException("Conflicting operation paths have no common ancestor");
        }
        return String.join("/", java.util.Arrays.copyOf(common, commonLength));
    }

    private static MergeConflict.Kind kindFor(String path) {
        return path.contains("/tiles/")
                ? MergeConflict.Kind.SAME_TILE
                : MergeConflict.Kind.SAME_OBJECT;
    }

    private static void requirePreviewSequence(long previewSequence) {
        if (previewSequence <= 0) {
            throw new IllegalArgumentException("Rebase preview sequence must be positive");
        }
    }

    private static List<EditorOperation> flatten(List<RebaseResult.MergeSlot> plan) {
        List<EditorOperation> operations = new ArrayList<>();
        for (RebaseResult.MergeSlot slot : plan) {
            if (slot instanceof RebaseResult.OperationsSlot operationSlot) {
                operations.addAll(operationSlot.operations());
            }
        }
        return List.copyOf(operations);
    }

    private static UUID conflictId(String path) {
        return UUID.nameUUIDFromBytes(path.getBytes(StandardCharsets.UTF_8));
    }

    private enum Side {
        OURS,
        THEIRS
    }

    private record OperationReference(Side side, int ordinal, EditorOperation operation) {
        private OperationReference {
            Objects.requireNonNull(side, "side");
            Objects.requireNonNull(operation, "operation");
        }
    }

    private record ConflictDomain(
            List<OperationReference> ours,
            List<OperationReference> theirs
    ) {
        private ConflictDomain {
            ours = List.copyOf(ours);
            theirs = List.copyOf(theirs);
        }

        private List<EditorOperation> oursOperations() {
            return ours.stream().map(OperationReference::operation).toList();
        }

        private List<EditorOperation> theirsOperations() {
            return theirs.stream().map(OperationReference::operation).toList();
        }

        private List<OperationReference> references() {
            List<OperationReference> references = new ArrayList<>(ours.size() + theirs.size());
            references.addAll(ours);
            references.addAll(theirs);
            return references;
        }
    }

    private static final class DisjointSet {
        private final int[] parents;

        private DisjointSet(int size) {
            parents = new int[size];
            for (int index = 0; index < size; index++) {
                parents[index] = index;
            }
        }

        private int find(int value) {
            if (parents[value] != value) {
                parents[value] = find(parents[value]);
            }
            return parents[value];
        }

        private void union(int left, int right) {
            int leftRoot = find(left);
            int rightRoot = find(right);
            if (leftRoot != rightRoot) {
                parents[rightRoot] = leftRoot;
            }
        }
    }
}
