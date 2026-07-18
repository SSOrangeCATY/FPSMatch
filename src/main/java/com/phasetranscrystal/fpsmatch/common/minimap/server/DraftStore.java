package com.phasetranscrystal.fpsmatch.common.minimap.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapErrorCode;
import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapHardLimits;
import com.phasetranscrystal.fpsmatch.core.minimap.format.JcsCanonicalizer;
import com.phasetranscrystal.fpsmatch.core.minimap.format.Sha256Digest;
import com.phasetranscrystal.fpsmatch.core.minimap.format.StrictJsonParser;
import com.phasetranscrystal.fpsmatch.core.minimap.model.MapKey;
import com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId;
import com.phasetranscrystal.fpsmatch.core.minimap.model.Sha256;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.ByteBuffer;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DraftStore {
    private static final String STATE_FILE = "draft.json";
    private static final long MAX_STATE_BYTES = MinimapHardLimits.MAX_SOURCE_MANIFEST_BYTES;
    private static final int MAX_ROOT_ENTRIES = 4_096;
    private static final int MAX_DELETE_TREE_NODES = 16_384;
    private static final int MAX_DELETE_TREE_DEPTH = 16;
    private static final Set<String> STATE_FIELDS = Set.of(
            "ackCursor", "baseRevision", "baseSourceHash", "draftId",
            "draftRootHash", "initialRootHash", "expiresAtEpochMillis", "lifecycle", "mapKey",
            "dimension", "documentId", "operations"
    );
    private static final Set<String> PRE_CHAIN_STATE_FIELDS = Set.of(
            "ackCursor", "baseRevision", "baseSourceHash", "draftId",
            "draftRootHash", "expiresAtEpochMillis", "lifecycle", "mapKey",
            "dimension", "documentId", "operations"
    );
    private static final Set<String> LEGACY_STATE_FIELDS = Set.of(
            "ackCursor", "baseRevision", "baseSourceHash", "draftId",
            "draftRootHash", "expiresAtEpochMillis", "mapKey", "operations"
    );
    private static final Set<String> OPERATION_FIELDS = Set.of(
            "ackCursor", "ackRootHash", "originalAckRootHash",
            "payloadHash", "sequence"
    );

    private final Path root;
    private final Duration draftTtl;
    private final int outOfOrderWindow;
    private final Clock clock;
    private final DraftAncestorPins ancestorPins;
    private final FileSystem fileSystem;
    private final DraftStoreLimits limits;
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
        this.root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
        this.draftTtl = Objects.requireNonNull(draftTtl, "draftTtl");
        if (draftTtl.isZero() || draftTtl.isNegative() || outOfOrderWindow <= 0) {
            throw new IllegalArgumentException("Draft retention settings are invalid");
        }
        this.outOfOrderWindow = outOfOrderWindow;
        this.clock = Objects.requireNonNull(clock, "clock");
        this.ancestorPins = Objects.requireNonNull(ancestorPins, "ancestorPins");
        this.fileSystem = Objects.requireNonNull(fileSystem, "fileSystem");
        this.limits = Objects.requireNonNull(limits, "limits");
        try {
            fileSystem.createDirectories(this.root);
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
                    fileSystem.syncDirectory(root);
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
                    draft.draftRootHash
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
        for (Path directory : listDirectories(root)) {
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
        for (Path directory : listDirectories(root)) {
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
            deleteTree(draftDirectory(draft.draftId));
            fileSystem.syncDirectory(root);
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
        Path directory = requireDraftDirectory(draftId);
        Path entries = directory.resolve("entries");
        Path target = entries.resolve(hash.value() + ".bin");
        try {
            ensureDirectory(entries, "Draft entry directory");
            if (Files.exists(target)) {
                if (!Arrays.equals(readBoundedRegularFile(
                        target,
                        limits.maximumContentBytesPerDraft(),
                        "Draft content entry"
                ), payload)) {
                    throw error(
                            MinimapErrorCode.HASH_MISMATCH,
                            "Content-addressed draft entry conflicts with its hash"
                    );
                }
                return;
            }
            fileSystem.writeAtomically(target, payload);
        } catch (IOException exception) {
            throw failure("Unable to persist draft entry", exception);
        }
    }

    private void persist(MutableDraft draft) {
        JsonObject root = new JsonObject();
        root.addProperty("ackCursor", Long.toString(draft.ackCursor));
        root.addProperty("baseRevision", Long.toString(draft.baseRevision));
        root.addProperty("baseSourceHash", draft.baseSourceHash.value());
        root.addProperty("draftId", draft.draftId.toString());
        root.addProperty("draftRootHash", draft.draftRootHash.value());
        root.addProperty("initialRootHash", draft.initialRootHash.value());
        root.addProperty("dimension", draft.dimension.toString());
        root.addProperty("documentId", draft.documentId.toString());
        root.addProperty("expiresAtEpochMillis", Long.toString(draft.expiresAt.toEpochMilli()));
        root.addProperty("lifecycle", draft.lifecycle.name());
        JsonObject mapKey = new JsonObject();
        mapKey.addProperty("gameType", draft.mapKey.gameType());
        mapKey.addProperty("mapName", draft.mapKey.mapName());
        root.add("mapKey", mapKey);
        JsonArray operations = new JsonArray();
        draft.operations.values().stream()
                .sorted(Comparator.comparingLong(operation -> operation.sequence))
                .forEach(operation -> {
                    JsonObject encoded = new JsonObject();
                    encoded.add(
                            "ackRootHash",
                            operation.ackRootHash == null
                                    ? JsonNull.INSTANCE
                                    : new com.google.gson.JsonPrimitive(
                                    operation.ackRootHash.value()
                            )
                    );
                    encoded.addProperty(
                            "ackCursor", Long.toString(operation.originalAckCursor)
                    );
                    encoded.addProperty(
                            "originalAckRootHash", operation.originalAckRootHash.value()
                    );
                    encoded.addProperty("payloadHash", operation.payloadHash.value());
                    encoded.addProperty("sequence", Long.toString(operation.sequence));
                    operations.add(encoded);
                });
        root.add("operations", operations);
        try {
            Path directory = draftDirectory(draft.draftId);
            ensureDirectory(directory, "Draft directory");
            fileSystem.writeAtomically(
                    directory.resolve(STATE_FILE),
                    JcsCanonicalizer.canonicalize(root)
            );
        } catch (IOException exception) {
            throw failure("Unable to persist draft state", exception);
        }
    }

    private MutableDraft read(UUID draftId) {
        Path state = requireDraftDirectory(draftId).resolve(STATE_FILE);
        try {
            byte[] bytes = readBoundedRegularFile(
                    state, MAX_STATE_BYTES, "Draft state"
            );
            JsonElement parsed = StrictJsonParser.parse(bytes);
            if (!parsed.isJsonObject()
                    || !Arrays.equals(bytes, JcsCanonicalizer.canonicalize(parsed))) {
                throw error(MinimapErrorCode.VALIDATION_FAILED, "Draft state is not canonical");
            }
            JsonObject root = parsed.getAsJsonObject();
            if (!root.keySet().equals(STATE_FIELDS)
                    && !root.keySet().equals(PRE_CHAIN_STATE_FIELDS)
                    && !root.keySet().equals(LEGACY_STATE_FIELDS)) {
                throw error(MinimapErrorCode.VALIDATION_FAILED, "Draft state fields are invalid");
            }
            UUID persistedId = UUID.fromString(string(root, "draftId"));
            if (!persistedId.equals(draftId)) {
                throw error(MinimapErrorCode.VALIDATION_FAILED, "Draft ID does not match its path");
            }
            JsonObject map = root.getAsJsonObject("mapKey");
            if (map == null || !map.keySet().equals(Set.of("gameType", "mapName"))) {
                throw error(MinimapErrorCode.VALIDATION_FAILED, "Draft map key is invalid");
            }
            Map<Long, Operation> operations = new HashMap<>();
            JsonArray persistedOperations = root.getAsJsonArray("operations");
            if (persistedOperations == null
                    || persistedOperations.size() > limits.maximumOperationsPerDraft()) {
                throw error(MinimapErrorCode.QUOTA_EXCEEDED, "Draft operation quota is exceeded");
            }
            for (JsonElement element : persistedOperations) {
                JsonObject operation = element.getAsJsonObject();
                if (operation == null || !operation.keySet().equals(OPERATION_FIELDS)) {
                    throw error(MinimapErrorCode.VALIDATION_FAILED, "Draft operation is invalid");
                }
                long sequence = count(operation, "sequence");
                Sha256 payloadHash = Sha256.parse(string(operation, "payloadHash"));
                JsonElement ack = operation.get("ackRootHash");
                Sha256 ackRoot = ack.isJsonNull() ? null : Sha256.parse(ack.getAsString());
                long originalAckCursor = count(operation, "ackCursor");
                Sha256 originalAckRoot = Sha256.parse(
                        string(operation, "originalAckRootHash")
                );
                if (operations.put(
                        sequence,
                        new Operation(
                                sequence, payloadHash, ackRoot,
                                originalAckCursor, originalAckRoot
                        )
                ) != null) {
                    throw error(MinimapErrorCode.VALIDATION_FAILED, "Draft operation is duplicated");
                }
            }
            Sha256 persistedRoot = Sha256.parse(string(root, "draftRootHash"));
            Sha256 initialRoot;
            if (root.has("initialRootHash")) {
                initialRoot = Sha256.parse(string(root, "initialRootHash"));
            } else if (operations.isEmpty()) {
                initialRoot = persistedRoot;
            } else {
                throw error(
                        MinimapErrorCode.VALIDATION_FAILED,
                        "Draft operation chain is missing its initial root"
                );
            }
            long ackCursor = count(root, "ackCursor");
            validateOperationChain(initialRoot, persistedRoot, ackCursor, operations);
            long contentBytes = contentBytes(persistedId, operations.values());
            if (contentBytes > limits.maximumContentBytesPerDraft()) {
                throw error(MinimapErrorCode.QUOTA_EXCEEDED, "Draft content quota is exceeded");
            }
            return new MutableDraft(
                    persistedId,
                    new MapKey(map.get("gameType").getAsString(), map.get("mapName").getAsString()),
                    NamespacedId.parse(string(root, "dimension")),
                    NamespacedId.parse(string(root, "documentId")),
                    count(root, "baseRevision"),
                    Sha256.parse(string(root, "baseSourceHash")),
                    initialRoot,
                    persistedRoot,
                    ackCursor,
                    Instant.ofEpochMilli(count(root, "expiresAtEpochMillis")),
                    root.has("lifecycle")
                            ? DraftLifecycle.valueOf(string(root, "lifecycle"))
                            : DraftLifecycle.ACTIVE,
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

    private static byte[] readBoundedRegularFile(
            Path path,
            long maximumBytes,
            String label
    ) throws IOException {
        try (FileChannel channel = FileChannel.open(
                path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS
        )) {
            if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException(label + " is not a regular file");
            }
            long size = channel.size();
            if (size > maximumBytes) {
                throw error(
                        MinimapErrorCode.QUOTA_EXCEEDED,
                        label + " exceeds its byte hard limit"
                );
            }
            byte[] bytes = new byte[Math.toIntExact(size)];
            ByteBuffer destination = ByteBuffer.wrap(bytes);
            while (destination.hasRemaining()) {
                if (channel.read(destination) <= 0) {
                    throw new IOException(label + " changed while being read");
                }
            }
            ByteBuffer trailing = ByteBuffer.allocate(1);
            if (channel.read(trailing) != -1) {
                throw new IOException(label + " changed while being read");
            }
            return bytes;
        }
    }

    private static String string(JsonObject root, String key) {
        JsonElement value = root.get(key);
        if (value == null || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isString()) {
            throw error(MinimapErrorCode.VALIDATION_FAILED, "Draft string is invalid: " + key);
        }
        return value.getAsString();
    }

    private static long count(JsonObject root, String key) {
        String value = string(root, key);
        if (!value.matches("0|[1-9][0-9]{0,18}")) {
            throw error(MinimapErrorCode.VALIDATION_FAILED, "Draft count is invalid: " + key);
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw error(MinimapErrorCode.VALIDATION_FAILED, "Draft count is invalid: " + key);
        }
    }

    private Path draftDirectory(UUID draftId) {
        return root.resolve(draftId.toString()).normalize();
    }

    private Path requireDraftDirectory(UUID draftId) {
        Path directory = draftDirectory(draftId);
        try {
            if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
                if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
                    throw error(MinimapErrorCode.SESSION_NOT_FOUND, "Draft was not found");
                }
                throw new IOException("Draft path is not a regular directory");
            }
            return directory;
        } catch (DraftException exception) {
            throw exception;
        } catch (IOException exception) {
            throw failure("Unable to validate draft directory", exception);
        }
    }

    private void ensureDirectory(Path directory, String label) throws IOException {
        if (Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException(label + " is not a regular directory");
            }
            return;
        }
        fileSystem.createDirectories(directory);
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException(label + " is not a regular directory");
        }
    }

    private Object lockFor(UUID draftId) {
        int index = (draftId.hashCode() & Integer.MAX_VALUE) % locks.length;
        return locks[index];
    }

    private long contentBytes(UUID draftId, java.util.Collection<Operation> operations)
            throws IOException {
        Set<Sha256> unique = new HashSet<>();
        long total = 0;
        for (Operation operation : operations) {
            if (!unique.add(operation.payloadHash)) {
                continue;
            }
            Path entry = draftDirectory(draftId).resolve("entries")
                    .resolve(operation.payloadHash.value() + ".bin");
            byte[] content;
            try {
                content = readBoundedRegularFile(
                        entry,
                        limits.maximumContentBytesPerDraft(),
                        "Draft content entry"
                );
            } catch (NoSuchFileException missingEntry) {
                throw new IOException("Draft content entry is missing", missingEntry);
            }
            if (!Sha256Digest.of(content).equals(operation.payloadHash)) {
                throw error(
                        MinimapErrorCode.HASH_MISMATCH,
                        "Draft content entry does not match its address hash"
                );
            }
            try {
                total = Math.addExact(total, content.length);
            } catch (ArithmeticException overflow) {
                throw new IOException("Draft content byte count overflowed", overflow);
            }
        }
        return total;
    }

    private int countDraftDirectories() {
        int count = 0;
        for (Path directory : listDirectories(root)) {
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

    private static List<Path> listDirectories(Path directory) {
        try (var paths = Files.list(directory)) {
            List<Path> entries = paths.limit(MAX_ROOT_ENTRIES + 1L).toList();
            if (entries.size() > MAX_ROOT_ENTRIES) {
                throw new IOException("Draft store directory exceeds its entry limit");
            }
            return entries.stream().filter(path -> Files.isDirectory(
                    path, LinkOption.NOFOLLOW_LINKS
            )).toList();
        } catch (IOException exception) {
            throw failure("Unable to list draft store", exception);
        }
    }

    private static void writeAtomic(Path target, byte[] bytes) throws IOException {
        Path temporary = target.resolveSibling(
                target.getFileName() + "." + UUID.randomUUID() + ".tmp"
        );
        try {
            Files.write(
                    temporary,
                    bytes,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE
            );
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            try {
                Files.move(
                        temporary,
                        target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                );
            } catch (AtomicMoveNotSupportedException unsupported) {
                throw new IOException("Draft store requires atomic file replacement", unsupported);
            }
            syncDirectory(target.getParent());
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void syncDirectory(Path directory) throws IOException {
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            channel.force(true);
        } catch (AccessDeniedException unsupportedOnWindows) {
            if (!System.getProperty("os.name", "").startsWith("Windows")) {
                throw unsupportedOnWindows;
            }
        }
    }

    private void deleteTree(Path root) throws IOException {
        Path state = root.resolve(STATE_FILE);
        List<Path> paths = fileSystem.listTree(root);
        if (paths.size() > MAX_DELETE_TREE_NODES) {
            throw new IOException("Draft cleanup tree exceeds its node limit");
        }
        if (paths.stream().anyMatch(path -> root.relativize(path).getNameCount()
                > MAX_DELETE_TREE_DEPTH)) {
            throw new IOException("Draft cleanup tree exceeds its depth limit");
        }
        for (Path path : paths.stream()
                .filter(path -> !path.equals(root) && !path.equals(state))
                .sorted(Comparator.reverseOrder()).toList()) {
            fileSystem.deleteIfExists(path);
        }
        fileSystem.deleteIfExists(state);
        fileSystem.deleteIfExists(root);
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
            writeAtomic(target, bytes);
        }

        @Override
        public List<Path> listTree(Path root) throws IOException {
            try (var paths = Files.walk(root, MAX_DELETE_TREE_DEPTH + 1)) {
                return paths.limit(MAX_DELETE_TREE_NODES + 1L).toList();
            }
        }

        @Override
        public void deleteIfExists(Path path) throws IOException {
            Files.deleteIfExists(path);
        }

        @Override
        public void syncDirectory(Path directory) throws IOException {
            DraftStore.syncDirectory(directory);
        }
    }

    private static DraftException error(MinimapErrorCode code, String message) {
        return new DraftException(code, message);
    }

    private static DraftException failure(String message, IOException cause) {
        return new DraftException(MinimapErrorCode.PUBLISH_IO_FAILED, message, cause);
    }

    private static final class Operation {
        private final long sequence;
        private final Sha256 payloadHash;
        private Sha256 ackRootHash;
        private long originalAckCursor;
        private Sha256 originalAckRootHash;

        private Operation(
                long sequence,
                Sha256 payloadHash,
                Sha256 ackRootHash,
                long originalAckCursor,
                Sha256 originalAckRootHash
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
        }

        private DraftAck originalAck(UUID draftId) {
            return new DraftAck(draftId, originalAckCursor, originalAckRootHash);
        }
    }

    private enum DraftLifecycle {
        CREATING,
        ACTIVE,
        DELETING
    }

    private static final class MutableDraft {
        private final UUID draftId;
        private final MapKey mapKey;
        private final NamespacedId dimension;
        private final NamespacedId documentId;
        private final long baseRevision;
        private final Sha256 baseSourceHash;
        private final Sha256 initialRootHash;
        private Sha256 draftRootHash;
        private long ackCursor;
        private Instant expiresAt;
        private DraftLifecycle lifecycle;
        private final Map<Long, Operation> operations;
        private long contentBytes;

        private MutableDraft(
                UUID draftId,
                MapKey mapKey,
                NamespacedId dimension,
                NamespacedId documentId,
                long baseRevision,
                Sha256 baseSourceHash,
                Sha256 initialRootHash,
                Sha256 draftRootHash,
                long ackCursor,
                Instant expiresAt,
                DraftLifecycle lifecycle,
                Map<Long, Operation> operations,
                long contentBytes
        ) {
            if (baseRevision < 0 || ackCursor < 0 || contentBytes < 0) {
                throw new IllegalArgumentException("Draft counters must be non-negative");
            }
            this.draftId = Objects.requireNonNull(draftId, "draftId");
            this.mapKey = Objects.requireNonNull(mapKey, "mapKey");
            this.dimension = Objects.requireNonNull(dimension, "dimension");
            this.documentId = Objects.requireNonNull(documentId, "documentId");
            this.baseRevision = baseRevision;
            this.baseSourceHash = Objects.requireNonNull(baseSourceHash, "baseSourceHash");
            this.initialRootHash = Objects.requireNonNull(
                    initialRootHash, "initialRootHash"
            );
            this.draftRootHash = Objects.requireNonNull(draftRootHash, "draftRootHash");
            this.ackCursor = ackCursor;
            this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
            this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
            this.operations = Objects.requireNonNull(operations, "operations");
            this.contentBytes = contentBytes;
        }

        private DraftState snapshot() {
            return new DraftState(
                    draftId, mapKey, dimension, documentId,
                    baseRevision, baseSourceHash,
                    draftRootHash, ackCursor, expiresAt
            );
        }

        private DraftAck ack() {
            return new DraftAck(draftId, ackCursor, draftRootHash);
        }
    }
}
