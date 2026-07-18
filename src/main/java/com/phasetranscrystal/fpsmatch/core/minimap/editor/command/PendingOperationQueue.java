package com.phasetranscrystal.fpsmatch.core.minimap.editor.command;

import com.phasetranscrystal.fpsmatch.core.minimap.model.Sha256;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class PendingOperationQueue {
    private final Map<UUID, DraftQueue> queues = new LinkedHashMap<>();

    public void enqueue(UUID draftId, EditorCommand command) {
        Objects.requireNonNull(draftId, "draftId");
        Objects.requireNonNull(command, "command");
        DraftQueue queue = queues.computeIfAbsent(draftId, ignored -> new DraftQueue());
        if (queue.pending.containsKey(command.sequence())) {
            return;
        }
        long expected = queue.ackCursor + queue.pending.size() + 1L;
        if (command.sequence() != expected) {
            throw new EditorCommandException(
                    "Command sequence gap: expected " + expected + " but was " + command.sequence());
        }
        queue.pending.put(command.sequence(), command);
    }

    public DraftSnapshot ack(UUID draftId, long ackCursor, Sha256 draftRootHash) {
        Objects.requireNonNull(draftId, "draftId");
        Objects.requireNonNull(draftRootHash, "draftRootHash");
        DraftQueue queue = requireQueue(draftId);
        if (ackCursor != queue.ackCursor + 1L) {
            throw new EditorCommandException(
                    "ACK cursor must be contiguous: expected " + (queue.ackCursor + 1L) + " but was " + ackCursor);
        }
        EditorCommand acked = queue.pending.remove(ackCursor);
        if (acked == null) {
            throw new EditorCommandException("No pending command for ACK cursor " + ackCursor);
        }
        queue.ackCursor = ackCursor;
        queue.ackedOperations.add(acked.operation());
        queue.draftRootHash = draftRootHash;
        return new DraftSnapshot(
                draftId,
                queue.ackCursor,
                queue.baseRootHash,
                draftRootHash,
                List.copyOf(queue.ackedOperations)
        );
    }

    public List<Long> pendingSequences(UUID draftId) {
        DraftQueue queue = queues.get(draftId);
        if (queue == null) {
            return List.of();
        }
        return List.copyOf(queue.pending.keySet());
    }

    public Optional<EditorCommand> nextResend(UUID draftId) {
        DraftQueue queue = queues.get(draftId);
        if (queue == null || queue.pending.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(queue.pending.values().iterator().next());
    }

    private DraftQueue requireQueue(UUID draftId) {
        DraftQueue queue = queues.get(draftId);
        if (queue == null) {
            throw new EditorCommandException("Unknown draft queue: " + draftId);
        }
        return queue;
    }

    private static final class DraftQueue {
        private long ackCursor;
        private Sha256 baseRootHash = Sha256.parse("0".repeat(64));
        private Sha256 draftRootHash = baseRootHash;
        private final Map<Long, EditorCommand> pending = new LinkedHashMap<>();
        private final List<EditorOperation> ackedOperations = new ArrayList<>();
    }
}
