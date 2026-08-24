package com.ptcrys.fpsmatch.common.minimap.server;

import com.google.gson.JsonObject;
import com.ptcrys.fpsmatch.core.minimap.contract.MinimapErrorCode;
import com.ptcrys.fpsmatch.core.minimap.contract.MinimapHardLimits;
import com.ptcrys.fpsmatch.core.minimap.format.JcsCanonicalizer;
import com.ptcrys.fpsmatch.core.minimap.format.Sha256Digest;
import com.ptcrys.fpsmatch.core.minimap.model.MapKey;
import com.ptcrys.fpsmatch.core.minimap.model.NamespacedId;
import com.ptcrys.fpsmatch.core.minimap.model.Sha256;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DraftStore {
    private final Duration draftTtl;
    private final int outOfOrderWindow;
    private final Clock clock;
    private final DraftAncestorPins ancestorPins;
    private final DraftStoreLimits limits;
    private final DraftStorePersistence persistence;
    private final Object creationLock = new Object();
    private final Object[] locks = createLockStripes();

    public DraftStore(
            Path root,
            Duration draftTtl,
            int outOfOrderWindow,
            Clock clock
    ) {
        this(
                root,
                draftTtl,
                outOfOrderWindow,
                clock,
                DraftAncestorPins.NONE,
                FileSystem.nio(),
                DraftStoreLimits.hardDefaults()
        );
    }

    public DraftStore(
            Path root,
            Duration draftTtl,
            int outOfOrderWindow,
            Clock clock,
            DraftAncestorPins ancestorPins
    ) {
        this(
                root,
                draftTtl,
                outOfOrderWindow,
                clock,
                ancestorPins,
                FileSystem.nio(),
                DraftStoreLimits.hardDefaults()
        );
    }

    DraftStore(
            Path root,
            Duration draftTtl,
            int outOfOrderWindow,
            Clock clock,
            DraftAncestorPins ancestorPins,
            FileSystem fileSystem
    ) {
        this(
                root, draftTtl, outOfOrderWindow, clock, ancestorPins,
                fileSystem, DraftStoreLimits.hardDefaults()
        );
    }

    DraftStore(
            Path root,
            Duration draftTtl,
            int outOfOrderWindow,
            Clock clock,
            DraftAncestorPins ancestorPins,
            FileSystem fileSystem,
            DraftStoreLimits limits
    ) {
        Path normalizedRoot = Objects.requireNonNull(root, "root")
                .toAbsolutePath().normalize();
        this.draftTtl = Objects.requireNonNull(draftTtl, "draftTtl");
        if (draftTtl.isZero() || draftTtl.isNegative() || outOfOrderWindow <= 0) {
            throw new IllegalArgumentException("Draft retention settings are invalid");
        }
        this.outOfOrderWindow = outOfOrderWindow;
        this.clock = Objects.requireNonNull(clock, "clock");
        this.ancestorPins = Objects.requireNonNull(ancestorPins, "ancestorPins");
        this.limits = Objects.requireNonNull(limits, "limits");
        this.persistence = new DraftStorePersistence(
                normalizedRoot,
                Objects.requireNonNull(fileSystem, "fileSystem"),
                this.limits
        );
        try {
            persistence.ensureRoot();
        } catch (IOException exception) {
            throw failure("Unable to create draft store", exception);
        }
        revalidateActivePins();
    }

    public DraftState create(
            MapKey mapKey,
            NamespacedId dimension,
            NamespacedId documentId,
            long baseRevision,
            Sha256 baseSourceHash,
            Sha256 initialRootHash
    ) {
        if (baseRevision > 0 && !ancestorPins.supportsPersistentPins()) {
            throw new IllegalStateException(
                    "Published draft ancestors require persistent revision pins"
            );
        }
        synchronized (creationLock) {
            if (countDraftDirectories() >= limits.maximumActiveDrafts()) {
                throw error(MinimapErrorCode.QUOTA_EXCEEDED, "Active draft quota is exhausted");
            }
            UUID draftId = UUID.randomUUID();
            MutableDraft draft = new MutableDraft(
                    draftId,
                    Objects.requireNonNull(mapKey, "mapKey"),
                    Objects.requireNonNull(dimension, "dimension"),
                    Objects.requireNonNull(documentId, "documentId"),
                    baseRevision,
                    Objects.requireNonNull(baseSourceHash, "baseSourceHash"),
                    Objects.requireNonNull(initialRootHash, "initialRootHash"),
                    initialRootHash,
                    0,
                    clock.instant().plus(draftTtl),
                    DraftLifecycle.CREATING,
                    new HashMap<>(),
                    0
            );
            synchronized (lockFor(draftId)) {
                String pinId = pinId(draftId);
                persist(draft);
                try {
                    persistence.syncRoot();
                } catch (IOException exception) {
                    throw failure("Unable to sync draft creation", exception);
                }
                if (baseRevision > 0) {
                    ancestorPins.pin(mapKey, baseRevision, baseSourceHash, pinId);
                }
                draft.lifecycle = DraftLifecycle.ACTIVE;
                persist(draft);
            }
            return draft.snapshot();
        }
    }

    public Optional<DraftState> get(UUID draftId) {
        Objects.requireNonNull(draftId, "draftId");
        synchronized (lockFor(draftId)) {
            try {
                MutableDraft draft = read(draftId);
                if (draft.lifecycle != DraftLifecycle.ACTIVE) {
                    return Optional.empty();
                }
                if (!clock.instant().isBefore(draft.expiresAt)) {
                    beginDeletion(draft);
                    return Optional.empty();
                }
                return Optional.of(draft.snapshot());
            } catch (DraftException missing) {
                if (missing.errorCode() == MinimapErrorCode.SESSION_NOT_FOUND) {
                    return Optional.empty();
                }
                throw missing;
            }
        }
    }

    public DraftAck apply(
            UUID draftId,
            Sha256 expectedRootHash,
            long opSequence,
            Sha256 payloadHash,
            byte[] payload
    ) {
        Objects.requireNonNull(draftId, "draftId");
        Objects.requireNonNull(expectedRootHash, "expectedRootHash");
        Objects.requireNonNull(payloadHash, "payloadHash");
        Objects.requireNonNull(payload, "payload");
        if (opSequence <= 0 || payload.length == 0
                || payload.length > MinimapHardLimits.MAX_WIRE_BODY_BYTES) {
            throw new IllegalArgumentException("Draft operation is outside its limits");
        }
        if (!Sha256Digest.of(payload).equals(payloadHash)) {
            throw error(MinimapErrorCode.HASH_MISMATCH, "Draft payload hash does not match");
        }
        synchronized (lockFor(draftId)) {
            MutableDraft draft = requireActive(read(draftId));
            Operation existing = draft.operations.get(opSequence);
            if (existing != null) {
                if (!existing.payloadHash.equals(payloadHash)) {
                    throw error(
                            MinimapErrorCode.FRAGMENT_CONFLICT,
                            "Draft operation sequence conflicts with its prior hash"
                    );
                }
                return existing.originalAck(draftId);
            }
            if (!draft.draftRootHash.equals(expectedRootHash)) {
                throw error(
                        MinimapErrorCode.REVISION_CONFLICT,
                        "Draft root changed since the optimistic request"
                );
            }
            if (opSequence <= draft.ackCursor
                    || opSequence - draft.ackCursor > outOfOrderWindow) {
                throw error(
                        MinimapErrorCode.VALIDATION_FAILED,
                        "Draft operation is outside the ACK window"
                );
            }
            if (draft.operations.size() >= limits.maximumOperationsPerDraft()) {
                throw error(
                        MinimapErrorCode.QUOTA_EXCEEDED,
                        "Draft operation quota is exhausted"
                );
            }
            boolean newContent = draft.operations.values().stream()
                    .noneMatch(operation -> operation.payloadHash.equals(payloadHash));
            if (newContent && payload.length > limits.maximumContentBytesPerDraft()
                    - draft.contentBytes) {
                throw error(
                        MinimapErrorCode.QUOTA_EXCEEDED,
                        "Draft content byte quota is exhausted"
                );
            }
            persistPayload(draftId, payloadHash, payload);
            Operation submitted = new Operation(
                    opSequence,
                    payloadHash,
                    null,
                    draft.ackCursor,
                    draft.draftRootHash,
                    null
            );
            draft.operations.put(opSequence, submitted);
            if (newContent) {
                draft.contentBytes += payload.length;
            }
            while (true) {
                long nextSequence;
                try {
                    nextSequence = Math.addExact(draft.ackCursor, 1);
                } catch (ArithmeticException exhausted) {
                    throw error(MinimapErrorCode.QUOTA_EXCEEDED, "Draft ACK cursor is exhausted");
                }
                Operation next = draft.operations.get(nextSequence);
                if (next == null) {
                    break;
                }
                Sha256 nextRoot = nextRoot(draft.draftRootHash, next);
                next.ackRootHash = nextRoot;
                draft.draftRootHash = nextRoot;
                draft.ackCursor = nextSequence;
            }
            submitted.originalAckCursor = draft.ackCursor;
            submitted.originalAckRootHash = draft.draftRootHash;
            draft.expiresAt = clock.instant().plus(draftTtl);
            persist(draft);
            return draft.ack();
        }
    }

    /**
     * Applies one canonical forward editor descriptor. Legacy five-argument
     * payloads intentionally remain on the compatibility path above.
     */
    public DraftAck apply(
            UUID draftId,
            Sha256 expectedRootHash,
            long opSequence,
            Sha256 descriptorHash,
            byte[] descriptorBytes,
            Map<Sha256, byte[]> referencedContent
    ) {
        Objects.requireNonNull(draftId, "draftId");
        Objects.requireNonNull(expectedRootHash, "expectedRootHash");
        Objects.requireNonNull(descriptorHash, "descriptorHash");
        Objects.requireNonNull(descriptorBytes, "descriptorBytes");
        Objects.requireNonNull(referencedContent, "referencedContent");
        if (opSequence <= 0 || opSequence > limits.maximumOperationsPerDraft()) {
            throw error(
                    MinimapErrorCode.QUOTA_EXCEEDED,
                    "Draft operation sequence is outside its limit"
            );
        }
        DraftOperationDescriptor submission = DraftOperationDescriptor.validate(
                descriptorHash,
                descriptorBytes,
                referencedContent,
                limits.maximumContentBytesPerDraft()
        );
        synchronized (lockFor(draftId)) {
            MutableDraft draft = requireActive(read(draftId));
            Operation existing = draft.operations.get(opSequence);
            if (existing != null) {
                if (!existing.payloadHash.equals(descriptorHash)
                        || existing.referencedContentHashes == null) {
                    throw error(
                            MinimapErrorCode.FRAGMENT_CONFLICT,
                            "Draft operation sequence conflicts with its prior descriptor"
                    );
                }
                if (!existing.referencedContentHashes.equals(
                        submission.referencedContentHashes()
                )) {
                    throw error(
                            MinimapErrorCode.FRAGMENT_CONFLICT,
                            "Draft operation descriptor references changed content"
                    );
                }
                // A replay may arrive after one-time uploads have been consumed.
                return existing.originalAck(draftId);
            }
            submission.requireCompleteContent();
            if (!draft.draftRootHash.equals(expectedRootHash)) {
                throw error(
                        MinimapErrorCode.REVISION_CONFLICT,
                        "Draft root changed since the optimistic request"
                );
            }
            long expectedSequence;
            try {
                expectedSequence = Math.addExact(draft.ackCursor, 1L);
            } catch (ArithmeticException exhausted) {
                throw error(MinimapErrorCode.QUOTA_EXCEEDED, "Draft ACK cursor is exhausted");
            }
            if (opSequence != expectedSequence) {
                throw error(
                        MinimapErrorCode.VALIDATION_FAILED,
                        "Draft operation sequence must follow the ACK cursor"
                );
            }
            if (draft.operations.size() >= limits.maximumOperationsPerDraft()) {
                throw error(
                        MinimapErrorCode.QUOTA_EXCEEDED,
                        "Draft operation quota is exhausted"
                );
            }
            Map<Sha256, byte[]> entries = new LinkedHashMap<>();
            submission.submittedContent().forEach((hash, bytes) -> entries.put(hash, bytes));
            byte[] descriptor = submission.bytes();
            byte[] collision = entries.put(descriptorHash, descriptor);
            if (collision != null && !Arrays.equals(collision, descriptor)) {
                throw error(MinimapErrorCode.HASH_MISMATCH, "Draft CAS hash collision");
            }
            long additionalBytes = ensureContentQuota(draft, entries);
            // Content-addressed entries are durable before the state pointer moves.
            entries.forEach((hash, bytes) -> persistPayload(draftId, hash, bytes));
            Operation submitted = new Operation(
                    opSequence,
                    descriptorHash,
                    null,
                    draft.ackCursor,
                    draft.draftRootHash,
                    submission.referencedContentHashes()
            );
            draft.operations.put(opSequence, submitted);
            Sha256 nextRoot = nextRoot(draft.draftRootHash, submitted);
            submitted.ackRootHash = nextRoot;
            draft.draftRootHash = nextRoot;
            draft.ackCursor = opSequence;
            submitted.originalAckCursor = opSequence;
            submitted.originalAckRootHash = nextRoot;
            draft.contentBytes = Math.addExact(draft.contentBytes, additionalBytes);
            draft.expiresAt = clock.instant().plus(draftTtl);
            persist(draft);
            return draft.ack();
        }
    }

    public DraftMaterialization requireMaterialization(
            UUID draftId,
            Sha256 expectedRootHash
    ) {
        Objects.requireNonNull(draftId, "draftId");
        Objects.requireNonNull(expectedRootHash, "expectedRootHash");
        synchronized (lockFor(draftId)) {
            MutableDraft draft = requireActive(read(draftId));
            if (!draft.draftRootHash.equals(expectedRootHash)) {
                throw error(
                        MinimapErrorCode.REVISION_CONFLICT,
                        "Draft root changed since the optimistic request"
                );
            }
            if (draft.ackCursor > draft.operations.size()) {
                throw error(
                        MinimapErrorCode.VALIDATION_FAILED,
                        "Draft ACK cursor exceeds its operation prefix"
                );
            }
            List<DraftMaterialization.Operation> materialized = new ArrayList<>();
            Map<Sha256, byte[]> content = new LinkedHashMap<>();
            for (long sequence = 1; sequence <= draft.ackCursor; sequence++) {
                Operation operation = draft.operations.get(sequence);
                if (operation == null || operation.referencedContentHashes == null) {
                    throw error(
                            MinimapErrorCode.VALIDATION_FAILED,
                            "Draft materialization contains a legacy operation"
                    );
                }
                byte[] descriptor = readPayload(draftId, operation.payloadHash);
                Map<Sha256, byte[]> operationContent = new LinkedHashMap<>();
                for (Sha256 hash : operation.referencedContentHashes) {
                    operationContent.put(hash, readPayload(draftId, hash));
                }
                DraftOperationDescriptor validated = DraftOperationDescriptor.validate(
                        operation.payloadHash,
                        descriptor,
                        operationContent,
                        limits.maximumContentBytesPerDraft()
                );
                validated.requireCompleteContent();
                materialized.add(new DraftMaterialization.Operation(
                        sequence,
                        operation.payloadHash,
                        descriptor,
                        validated.referencedContentHashes()
                ));
                operationContent.forEach((hash, bytes) -> content.putIfAbsent(hash, bytes));
            }
            return new DraftMaterialization(draft.snapshot(), materialized, content);
        }
    }

    public DraftState requireRoot(UUID draftId, Sha256 expectedRootHash) {
        Objects.requireNonNull(expectedRootHash, "expectedRootHash");
        synchronized (lockFor(draftId)) {
            MutableDraft draft = requireActive(read(draftId));
            if (!draft.draftRootHash.equals(expectedRootHash)) {
                throw error(
                        MinimapErrorCode.REVISION_CONFLICT,
                        "Draft root changed since the optimistic request"
                );
            }
            return draft.snapshot();
        }
    }

    public int removeExpired() {
        Instant now = clock.instant();
        int removed = 0;
        for (Path directory : persistence.listDraftDirectories()) {
            UUID draftId;
            try {
                draftId = UUID.fromString(directory.getFileName().toString());
            } catch (IllegalArgumentException invalidName) {
                continue;
            }
            synchronized (lockFor(draftId)) {
                try {
                    MutableDraft draft = read(draftId);
                    if (!now.isBefore(draft.expiresAt)) {
                        beginDeletion(draft);
                        removed++;
                    }
                } catch (DraftException missing) {
                    if (missing.errorCode() != MinimapErrorCode.SESSION_NOT_FOUND) {
                        throw missing;
                    }
                }
            }
        }
        return removed;
    }

    public boolean discard(UUID draftId) {
        Objects.requireNonNull(draftId, "draftId");
        synchronized (lockFor(draftId)) {
            MutableDraft draft;
            try {
                draft = read(draftId);
            } catch (DraftException missing) {
                if (missing.errorCode() == MinimapErrorCode.SESSION_NOT_FOUND) {
                    return false;
                }
                throw missing;
            }
            beginDeletion(draft);
            return true;
        }
    }

    private MutableDraft requireActive(MutableDraft draft) {
        if (draft.lifecycle != DraftLifecycle.ACTIVE) {
            throw error(MinimapErrorCode.SESSION_NOT_FOUND, "Draft is not active");
        }
        if (!clock.instant().isBefore(draft.expiresAt)) {
            throw error(MinimapErrorCode.SESSION_EXPIRED, "Draft has expired");
        }
        return draft;
    }

    private void revalidateActivePins() {
        for (Path directory : persistence.listDraftDirectories()) {
            UUID draftId;
            try {
                draftId = UUID.fromString(directory.getFileName().toString());
            } catch (IllegalArgumentException invalidName) {
                continue;
            }
            synchronized (lockFor(draftId)) {
                MutableDraft draft;
                try {
                    draft = read(draftId);
                } catch (DraftException missing) {
                    if (missing.errorCode() == MinimapErrorCode.SESSION_NOT_FOUND) {
                        continue;
                    }
                    throw missing;
                }
                if (draft.lifecycle == DraftLifecycle.CREATING) {
                    beginDeletion(draft);
                    continue;
                }
                if (draft.lifecycle == DraftLifecycle.DELETING) {
                    finishDeletion(draft);
                    continue;
                }
                if (draft.lifecycle != DraftLifecycle.ACTIVE || draft.baseRevision == 0) {
                    continue;
                }
                if (!ancestorPins.supportsPersistentPins()) {
                    throw new IllegalStateException(
                            "Published draft ancestors require persistent revision pins"
                    );
                }
                ancestorPins.pin(
                        draft.mapKey,
                        draft.baseRevision,
                        draft.baseSourceHash,
                        pinId(draft.draftId)
                );
            }
        }
    }

    private void beginDeletion(MutableDraft draft) {
        draft.lifecycle = DraftLifecycle.DELETING;
        persist(draft);
        finishDeletion(draft);
    }

    private void finishDeletion(MutableDraft draft) {
        unpinAncestor(draft);
        try {
            persistence.delete(draft.draftId);
            persistence.syncRoot();
        } catch (IOException exception) {
            throw failure("Unable to finish draft removal", exception);
        }
    }

    private Sha256 nextRoot(Sha256 previousRoot, Operation operation) {
        JsonObject value = new JsonObject();
        value.addProperty("opSequence", Long.toString(operation.sequence));
        value.addProperty("payloadHash", operation.payloadHash.value());
        value.addProperty("previousRootHash", previousRoot.value());
        return Sha256Digest.of(JcsCanonicalizer.canonicalize(value));
    }

    private void persistPayload(UUID draftId, Sha256 hash, byte[] payload) {
        try {
            persistence.writePayload(draftId, hash, payload);
        } catch (IOException exception) {
            throw failure("Unable to persist draft entry", exception);
        }
    }

    private byte[] readPayload(UUID draftId, Sha256 hash) {
        try {
            return persistence.readPayload(draftId, hash);
        } catch (IOException exception) {
            throw failure("Unable to read draft entry", exception);
        }
    }

    private void persist(MutableDraft draft) {
        try {
            persistence.write(new DraftStorePersistence.PersistedDraft(
                    draft.draftId,
                    draft.mapKey,
                    draft.dimension,
                    draft.documentId,
                    draft.baseRevision,
                    draft.baseSourceHash,
                    draft.initialRootHash,
                    draft.draftRootHash,
                    draft.ackCursor,
                    draft.expiresAt,
                    draft.lifecycle.name(),
                    draft.operations.values().stream()
                            .map(operation -> new DraftStorePersistence.PersistedOperation(
                                    operation.sequence,
                                    operation.payloadHash,
                                    operation.ackRootHash,
                                    operation.originalAckCursor,
                                    operation.originalAckRootHash,
                                    operation.referencedContentHashes
                            ))
                            .toList()
            ));
        } catch (IOException exception) {
            throw failure("Unable to persist draft state", exception);
        }
    }

    private MutableDraft read(UUID draftId) {
        try {
            DraftStorePersistence.PersistedDraft persisted = persistence.read(draftId);
            Map<Long, Operation> operations = new HashMap<>();
            long previousSequence = 0;
            for (DraftStorePersistence.PersistedOperation operation
                    : persisted.operations()) {
                if (operation.sequence() <= previousSequence) {
                    throw error(
                            MinimapErrorCode.VALIDATION_FAILED,
                            "Draft operations are not strictly ordered"
                    );
                }
                previousSequence = operation.sequence();
                if (operations.put(
                        operation.sequence(),
                        new Operation(
                                operation.sequence(),
                                operation.payloadHash(),
                                operation.ackRootHash(),
                                operation.originalAckCursor(),
                                operation.originalAckRootHash(),
                                operation.referencedContentHashes()
                        )
                ) != null) {
                    throw error(MinimapErrorCode.VALIDATION_FAILED, "Draft operation is duplicated");
                }
            }
            validateOperationChain(
                    persisted.initialRootHash(),
                    persisted.draftRootHash(),
                    persisted.ackCursor(),
                    operations
            );
            long contentBytes = contentBytes(persisted.draftId(), operations.values());
            if (contentBytes > limits.maximumContentBytesPerDraft()) {
                throw error(MinimapErrorCode.QUOTA_EXCEEDED, "Draft content quota is exceeded");
            }
            return new MutableDraft(
                    persisted.draftId(),
                    persisted.mapKey(),
                    persisted.dimension(),
                    persisted.documentId(),
                    persisted.baseRevision(),
                    persisted.baseSourceHash(),
                    persisted.initialRootHash(),
                    persisted.draftRootHash(),
                    persisted.ackCursor(),
                    persisted.expiresAt(),
                    DraftLifecycle.valueOf(persisted.lifecycle()),
                    operations,
                    contentBytes
            );
        } catch (NoSuchFileException missing) {
            throw error(MinimapErrorCode.SESSION_NOT_FOUND, "Draft was not found");
        } catch (DraftException exception) {
            throw exception;
        } catch (IOException exception) {
            throw failure("Unable to read draft state", exception);
        } catch (RuntimeException exception) {
            throw new DraftException(
                    MinimapErrorCode.VALIDATION_FAILED,
                    "Draft state is invalid",
                    exception
            );
        }
    }

    private void validateOperationChain(
            Sha256 initialRoot,
            Sha256 persistedRoot,
            long ackCursor,
            Map<Long, Operation> operations
    ) {
        if (ackCursor > operations.size()
                || ackCursor > limits.maximumOperationsPerDraft()) {
            throw error(
                    MinimapErrorCode.VALIDATION_FAILED,
                    "Draft ACK cursor exceeds its bounded operation prefix"
            );
        }
        Sha256 replayedRoot = initialRoot;
        Map<Long, Sha256> rootsByCursor = new HashMap<>();
        rootsByCursor.put(0L, initialRoot);
        for (long sequence = 1; sequence <= ackCursor; sequence++) {
            Operation operation = operations.get(sequence);
            if (operation == null || operation.ackRootHash == null) {
                throw error(
                        MinimapErrorCode.VALIDATION_FAILED,
                        "Draft ACK chain is not contiguous"
                );
            }
            replayedRoot = nextRoot(replayedRoot, operation);
            if (!replayedRoot.equals(operation.ackRootHash)) {
                throw error(
                        MinimapErrorCode.VALIDATION_FAILED,
                        "Draft ACK root does not match its operation chain"
                );
            }
            rootsByCursor.put(sequence, replayedRoot);
            if (sequence == Long.MAX_VALUE) {
                break;
            }
        }
        if (!replayedRoot.equals(persistedRoot)) {
            throw error(
                    MinimapErrorCode.VALIDATION_FAILED,
                    "Draft root does not match its operation chain"
            );
        }
        for (Operation operation : operations.values()) {
            if (operation.sequence > ackCursor) {
                if (operation.ackRootHash != null
                        || operation.sequence - ackCursor > outOfOrderWindow) {
                    throw error(
                            MinimapErrorCode.VALIDATION_FAILED,
                            "Draft pending operation is outside its ACK window"
                    );
                }
            }
            Sha256 originalRoot = rootsByCursor.get(operation.originalAckCursor);
            if (originalRoot == null
                    || !originalRoot.equals(operation.originalAckRootHash)) {
                throw error(
                        MinimapErrorCode.VALIDATION_FAILED,
                        "Draft original ACK does not match a verified chain checkpoint"
                );
            }
        }
    }

    private Object lockFor(UUID draftId) {
        int index = (draftId.hashCode() & Integer.MAX_VALUE) % locks.length;
        return locks[index];
    }

    private long contentBytes(UUID draftId, java.util.Collection<Operation> operations)
            throws IOException {
        Set<Sha256> unique = contentAddresses(operations);
        if (unique.size() > DraftStorePersistence.MAX_CONTENT_ENTRIES_PER_DRAFT) {
            throw error(MinimapErrorCode.QUOTA_EXCEEDED, "Draft content entry quota is exceeded");
        }
        Map<Sha256, byte[]> loaded = new HashMap<>();
        long total = 0;
        for (Sha256 hash : unique) {
            byte[] content = persistence.readPayload(draftId, hash);
            loaded.put(hash, content);
            try {
                total = Math.addExact(total, content.length);
            } catch (ArithmeticException overflow) {
                throw new IOException("Draft content byte count overflowed", overflow);
            }
        }
        for (Operation operation : operations) {
            if (operation.referencedContentHashes == null) {
                continue;
            }
            Map<Sha256, byte[]> references = new LinkedHashMap<>();
            for (Sha256 hash : operation.referencedContentHashes) {
                references.put(hash, loaded.get(hash));
            }
            DraftOperationDescriptor descriptor = DraftOperationDescriptor.validate(
                    operation.payloadHash,
                    loaded.get(operation.payloadHash),
                    references,
                    limits.maximumContentBytesPerDraft()
            );
            descriptor.requireCompleteContent();
            if (!descriptor.referencedContentHashes().equals(
                    operation.referencedContentHashes
            )) {
                throw error(
                        MinimapErrorCode.VALIDATION_FAILED,
                        "Draft descriptor references do not match persisted metadata"
                );
            }
        }
        return total;
    }

    private Set<Sha256> contentAddresses(java.util.Collection<Operation> operations) {
        Set<Sha256> addresses = new HashSet<>();
        for (Operation operation : operations) {
            addresses.add(operation.payloadHash);
            if (operation.referencedContentHashes != null) {
                addresses.addAll(operation.referencedContentHashes);
            }
        }
        return addresses;
    }

    private long ensureContentQuota(MutableDraft draft, Map<Sha256, byte[]> entries) {
        Set<Sha256> existing = contentAddresses(draft.operations.values());
        Set<Sha256> combined = new HashSet<>(existing);
        combined.addAll(entries.keySet());
        if (combined.size() > DraftStorePersistence.MAX_CONTENT_ENTRIES_PER_DRAFT) {
            throw error(MinimapErrorCode.QUOTA_EXCEEDED, "Draft content entry quota is exhausted");
        }
        long additionalBytes = 0;
        int additionalFiles = 0;
        try {
            for (Map.Entry<Sha256, byte[]> entry : entries.entrySet()) {
                if (existing.contains(entry.getKey())) {
                    continue;
                }
                additionalBytes = Math.addExact(additionalBytes, entry.getValue().length);
                if (!persistence.payloadExists(draft.draftId, entry.getKey())) {
                    additionalFiles++;
                }
            }
            if (persistence.payloadEntryCount(draft.draftId)
                    > DraftStorePersistence.MAX_CONTENT_ENTRIES_PER_DRAFT - additionalFiles) {
                throw error(
                        MinimapErrorCode.QUOTA_EXCEEDED,
                        "Draft content entry quota is exhausted"
                );
            }
        } catch (ArithmeticException overflow) {
            throw error(MinimapErrorCode.QUOTA_EXCEEDED, "Draft content size overflowed");
        } catch (IOException exception) {
            throw failure("Unable to inspect draft content quota", exception);
        }
        if (additionalBytes > limits.maximumContentBytesPerDraft() - draft.contentBytes) {
            throw error(MinimapErrorCode.QUOTA_EXCEEDED, "Draft content byte quota is exhausted");
        }
        return additionalBytes;
    }

    private int countDraftDirectories() {
        int count = 0;
        for (Path directory : persistence.listDraftDirectories()) {
            try {
                UUID.fromString(directory.getFileName().toString());
                count++;
            } catch (IllegalArgumentException ignored) {
                // Unrelated directories are outside the draft namespace.
            }
        }
        return count;
    }

    private static Object[] createLockStripes() {
        Object[] stripes = new Object[64];
        Arrays.setAll(stripes, ignored -> new Object());
        return stripes;
    }

    private void unpinAncestor(MutableDraft draft) {
        if (draft.baseRevision > 0) {
            ancestorPins.unpin(
                    draft.mapKey, draft.baseRevision, pinId(draft.draftId)
            );
        }
    }

    private static String pinId(UUID draftId) {
        return "draft:" + draftId;
    }

    interface FileSystem {
        static FileSystem nio() {
            return NioFileSystem.INSTANCE;
        }

        void createDirectories(Path directory) throws IOException;

        void writeAtomically(Path target, byte[] bytes) throws IOException;

        List<Path> listTree(Path root) throws IOException;

        void deleteIfExists(Path path) throws IOException;

        void syncDirectory(Path directory) throws IOException;
    }

    private static final class NioFileSystem implements FileSystem {
        private static final NioFileSystem INSTANCE = new NioFileSystem();

        @Override
        public void createDirectories(Path directory) throws IOException {
            Files.createDirectories(directory);
        }

        @Override
        public void writeAtomically(Path target, byte[] bytes) throws IOException {
            DraftStorePersistence.writeAtomic(target, bytes);
        }

        @Override
        public List<Path> listTree(Path root) throws IOException {
            return DraftStorePersistence.listTree(root);
        }

        @Override
        public void deleteIfExists(Path path) throws IOException {
            Files.deleteIfExists(path);
        }

        @Override
        public void syncDirectory(Path directory) throws IOException {
            DraftStorePersistence.syncDirectory(directory);
        }
    }

    private static DraftException error(MinimapErrorCode code, String message) {
        return new DraftException(code, message);
    }

    private static DraftException failure(String message, IOException cause) {
        return new DraftException(MinimapErrorCode.PUBLISH_IO_FAILED, message, cause);
    }

    static final class Operation {
        private final long sequence;
        private final Sha256 payloadHash;
        private Sha256 ackRootHash;
        private long originalAckCursor;
        private Sha256 originalAckRootHash;
        private final List<Sha256> referencedContentHashes;

        private Operation(
                long sequence,
                Sha256 payloadHash,
                Sha256 ackRootHash,
                long originalAckCursor,
                Sha256 originalAckRootHash,
                List<Sha256> referencedContentHashes
        ) {
            if (sequence <= 0 || originalAckCursor < 0) {
                throw new IllegalArgumentException("Draft operation sequence must be positive");
            }
            this.sequence = sequence;
            this.payloadHash = Objects.requireNonNull(payloadHash, "payloadHash");
            this.ackRootHash = ackRootHash;
            this.originalAckCursor = originalAckCursor;
            this.originalAckRootHash = Objects.requireNonNull(
                    originalAckRootHash, "originalAckRootHash"
            );
            this.referencedContentHashes = referencedContentHashes == null
                    ? null : List.copyOf(referencedContentHashes);
        }

        private DraftAck originalAck(UUID draftId) {
            return new DraftAck(draftId, originalAckCursor, originalAckRootHash);
        }
    }

}
