package com.ptcrys.fpsmatch.core.minimap.editor.command;

import com.ptcrys.fpsmatch.core.minimap.model.Sha256;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

public final class PendingOperationQueue {
    private final Map<UUID, DraftQueue> queues = new LinkedHashMap<>();

    public void enqueue(UUID draftId, EditorCommand command) {
        Objects.requireNonNull(draftId, "draftId");
        Objects.requireNonNull(command, "command");
        DraftQueue queue = queues.get(draftId);
        boolean registerQueue = queue == null;
        if (registerQueue) {
            queue = new DraftQueue(command.previousRoot());
        }
        // Validate the whole first command before registering queue state.
        EditorCommand duplicate = queue.pending.get(command.sequence());
        if (duplicate != null) {
            if (!duplicate.equals(command)) {
                throw new EditorCommandException(
                        "Command sequence conflicts with a different pending command");
            }
            return;
        }
        long expected = queue.pending.isEmpty()
                ? queue.ackCursor + 1L
                : queue.pending.lastKey() + 1L;
        if (command.sequence() != expected) {
            throw new EditorCommandException(
                    "Command sequence gap: expected " + expected
                            + " but was " + command.sequence());
        }
        Sha256 expectedPrevious = queue.pending.isEmpty()
                ? queue.ackRootHash
                : queue.pending.lastEntry().getValue().rootHash();
        if (!command.previousRoot().equals(expectedPrevious)) {
            throw new EditorCommandException("Command previous root does not match queue tip");
        }
        queue.pending.put(command.sequence(), command);
        if (registerQueue) {
            queues.put(draftId, queue);
        }
    }

    public List<EditorCommand> snapshot(UUID draftId) {
        DraftQueue queue = queues.get(draftId);
        return queue == null ? List.of() : List.copyOf(queue.pending.values());
    }

    public void markSent(UUID draftId, long sequence) {
        DraftQueue queue = requireQueue(draftId);
        if (sequence <= queue.highestSent) {
            return;
        }
        if (sequence != queue.highestSent + 1L || !queue.pending.containsKey(sequence)) {
            throw new EditorCommandException(
                    "Commands must be marked sent in sequence order");
        }
        queue.highestSent = sequence;
    }

    public PendingOperationAck acknowledge(UUID draftId, long ackCursor, Sha256 draftRootHash) {
        Objects.requireNonNull(draftId, "draftId");
        Objects.requireNonNull(draftRootHash, "draftRootHash");
        DraftQueue queue = requireQueue(draftId);
        if (ackCursor == queue.ackCursor) {
            if (!draftRootHash.equals(queue.ackRootHash)) {
                throw new EditorCommandException("Idempotent ACK root does not match");
            }
            return queue.acknowledgement();
        }
        if (ackCursor < queue.ackCursor) {
            throw new EditorCommandException("ACK cursor cannot move backward");
        }
        if (ackCursor > queue.highestSent) {
            throw new EditorCommandException("ACK cursor exceeds the highest sent sequence");
        }
        EditorCommand target = queue.pending.get(ackCursor);
        if (target == null) {
            throw new EditorCommandException("ACK cursor does not identify a pending command");
        }
        if (!target.rootHash().equals(draftRootHash)) {
            throw new EditorCommandException("ACK root does not match the command root");
        }

        queue.pending.headMap(ackCursor, true).clear();
        queue.ackCursor = ackCursor;
        queue.ackRootHash = draftRootHash;
        return queue.acknowledgement();
    }

    /** @deprecated Use {@link #acknowledge(UUID, long, Sha256)}. */
    @Deprecated(forRemoval = false)
    public PendingOperationAck ack(UUID draftId, long ackCursor, Sha256 draftRootHash) {
        return acknowledge(draftId, ackCursor, draftRootHash);
    }

    public boolean hasPending(UUID draftId) {
        return !isEmpty(draftId);
    }

    public long ackCursor(UUID draftId) {
        DraftQueue queue = queues.get(Objects.requireNonNull(draftId, "draftId"));
        return queue == null ? 0L : queue.ackCursor;
    }

    public List<Long> pendingSequences(UUID draftId) {
        DraftQueue queue = queues.get(draftId);
        return queue == null ? List.of() : List.copyOf(queue.pending.keySet());
    }

    /**
     * Moves a provisional queue to the server-assigned draft identity.
     * The move is atomic and refuses to merge two independent draft queues.
     */
    public void rebind(UUID provisionalDraftId, UUID authoritativeDraftId) {
        Objects.requireNonNull(provisionalDraftId, "provisionalDraftId");
        Objects.requireNonNull(authoritativeDraftId, "authoritativeDraftId");
        if (provisionalDraftId.equals(authoritativeDraftId)) {
            return;
        }
        DraftQueue provisional = queues.get(provisionalDraftId);
        if (provisional == null) {
            return;
        }
        if (queues.containsKey(authoritativeDraftId)) {
            throw new EditorCommandException(
                    "Cannot rebind a draft queue onto an existing draft queue");
        }
        queues.remove(provisionalDraftId);
        queues.put(authoritativeDraftId, provisional);
    }

    /**
     * Establishes an empty queue at an authoritative reconnect checkpoint.
     * Pending work is deliberately rejected: callers must reconcile it before
     * adopting a new source so an old-base command cannot be sent accidentally.
     */
    public void reanchor(UUID draftId, long ackCursor, Sha256 ackRootHash) {
        Objects.requireNonNull(draftId, "draftId");
        Objects.requireNonNull(ackRootHash, "ackRootHash");
        if (ackCursor < 0) {
            throw new IllegalArgumentException("ACK cursor must be non-negative");
        }
        DraftQueue existing = queues.get(draftId);
        if (existing != null) {
            if (!existing.pending.isEmpty()) {
                throw new EditorCommandException(
                        "Cannot reanchor a draft queue with pending commands");
            }
            if (ackCursor < existing.ackCursor) {
                throw new EditorCommandException("ACK cursor cannot move backward on reanchor");
            }
        }
        DraftQueue anchored = new DraftQueue(ackRootHash);
        anchored.ackCursor = ackCursor;
        anchored.highestSent = ackCursor;
        queues.put(draftId, anchored);
    }

    public Optional<EditorCommand> nextResend(UUID draftId) {
        DraftQueue queue = queues.get(draftId);
        return queue == null || queue.pending.isEmpty()
                ? Optional.empty()
                : Optional.of(queue.pending.firstEntry().getValue());
    }

    public boolean isEmpty(UUID draftId) {
        DraftQueue queue = queues.get(draftId);
        return queue == null || queue.pending.isEmpty();
    }

    private DraftQueue requireQueue(UUID draftId) {
        DraftQueue queue = queues.get(Objects.requireNonNull(draftId, "draftId"));
        if (queue == null) {
            throw new EditorCommandException("Unknown draft queue: " + draftId);
        }
        return queue;
    }

    private static final class DraftQueue {
        private final TreeMap<Long, EditorCommand> pending = new TreeMap<>();
        private long ackCursor;
        private long highestSent;
        private Sha256 ackRootHash;

        private DraftQueue(Sha256 initialRootHash) {
            this.ackRootHash = Objects.requireNonNull(initialRootHash, "initialRootHash");
        }

        private PendingOperationAck acknowledgement() {
            return new PendingOperationAck(ackCursor, ackRootHash);
        }
    }
}
