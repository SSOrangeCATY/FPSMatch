package com.ptcrys.fpsmatch.common.minimap.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.ptcrys.fpsmatch.core.minimap.contract.MinimapErrorCode;
import com.ptcrys.fpsmatch.core.minimap.contract.MinimapHardLimits;
import com.ptcrys.fpsmatch.core.minimap.format.JcsCanonicalizer;
import com.ptcrys.fpsmatch.core.minimap.format.Sha256Digest;
import com.ptcrys.fpsmatch.core.minimap.format.StrictJsonParser;
import com.ptcrys.fpsmatch.core.minimap.model.MapKey;
import com.ptcrys.fpsmatch.core.minimap.model.NamespacedId;
import com.ptcrys.fpsmatch.core.minimap.model.Sha256;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

final class DraftStorePersistence {
    private static final String STATE_FILE = "draft.json";
    private static final long MAX_STATE_BYTES = MinimapHardLimits.MAX_SOURCE_MANIFEST_BYTES;
    private static final int MAX_ROOT_ENTRIES = 4_096;
    private static final int MAX_DELETE_TREE_NODES = 16_384;
    private static final int MAX_DELETE_TREE_DEPTH = 16;
    static final int MAX_CONTENT_ENTRIES_PER_DRAFT = MAX_DELETE_TREE_NODES - 3;
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
    private static final Set<String> LEGACY_OPERATION_FIELDS = Set.of(
            "ackCursor", "ackRootHash", "originalAckRootHash",
            "payloadHash", "sequence"
    );
    private static final Set<String> DESCRIPTOR_OPERATION_FIELDS = Set.of(
            "ackCursor", "ackRootHash", "originalAckRootHash",
            "payloadHash", "referencedContentHashes", "sequence"
    );

    private final Path root;
    private final DraftStore.FileSystem fileSystem;
    private final DraftStoreLimits limits;

    DraftStorePersistence(
            Path root,
            DraftStore.FileSystem fileSystem,
            DraftStoreLimits limits
    ) {
        this.root = Objects.requireNonNull(root, "root");
        this.fileSystem = Objects.requireNonNull(fileSystem, "fileSystem");
        this.limits = Objects.requireNonNull(limits, "limits");
    }

    void ensureRoot() throws IOException {
        fileSystem.createDirectories(root);
    }

    void syncRoot() throws IOException {
        fileSystem.syncDirectory(root);
    }

    List<Path> listDraftDirectories() {
        try (var paths = Files.list(root)) {
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

    PersistedDraft read(UUID draftId) throws IOException {
        Path state = requireDraftDirectory(draftId).resolve(STATE_FILE);
        byte[] bytes = readBoundedRegularFile(state, MAX_STATE_BYTES, "Draft state");
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
        JsonArray encodedOperations = root.getAsJsonArray("operations");
        if (encodedOperations == null
                || encodedOperations.size() > limits.maximumOperationsPerDraft()) {
            throw error(MinimapErrorCode.QUOTA_EXCEEDED, "Draft operation quota is exceeded");
        }
        List<PersistedOperation> operations = new ArrayList<>(encodedOperations.size());
        for (JsonElement element : encodedOperations) {
            JsonObject operation = element.getAsJsonObject();
            if (operation == null
                    || (!operation.keySet().equals(LEGACY_OPERATION_FIELDS)
                    && !operation.keySet().equals(DESCRIPTOR_OPERATION_FIELDS))) {
                throw error(MinimapErrorCode.VALIDATION_FAILED, "Draft operation is invalid");
            }
            JsonElement ack = operation.get("ackRootHash");
            List<Sha256> referencedContentHashes = null;
            if (operation.keySet().equals(DESCRIPTOR_OPERATION_FIELDS)) {
                JsonElement encodedReferences = operation.get("referencedContentHashes");
                if (encodedReferences == null || !encodedReferences.isJsonArray()
                        || encodedReferences.getAsJsonArray().size()
                        > MinimapHardLimits.MAX_EDITOR_MUTATIONS) {
                    throw error(
                            MinimapErrorCode.VALIDATION_FAILED,
                            "Draft content references are invalid"
                    );
                }
                Set<Sha256> unique = new HashSet<>();
                List<Sha256> decoded = new ArrayList<>();
                for (JsonElement reference : encodedReferences.getAsJsonArray()) {
                    if (!reference.isJsonPrimitive()
                            || !reference.getAsJsonPrimitive().isString()) {
                        throw error(
                                MinimapErrorCode.VALIDATION_FAILED,
                                "Draft content reference is invalid"
                        );
                    }
                    Sha256 hash = Sha256.parse(reference.getAsString());
                    if (!unique.add(hash)) {
                        throw error(
                                MinimapErrorCode.VALIDATION_FAILED,
                                "Draft content references are duplicated"
                        );
                    }
                    decoded.add(hash);
                }
                List<Sha256> sorted = decoded.stream()
                        .sorted(Comparator.comparing(Sha256::value))
                        .toList();
                if (!decoded.equals(sorted)) {
                    throw error(
                            MinimapErrorCode.VALIDATION_FAILED,
                            "Draft content references are not sorted"
                    );
                }
                referencedContentHashes = sorted;
            }
            operations.add(new PersistedOperation(
                    count(operation, "sequence"),
                    Sha256.parse(string(operation, "payloadHash")),
                    ack.isJsonNull() ? null : Sha256.parse(ack.getAsString()),
                    count(operation, "ackCursor"),
                    Sha256.parse(string(operation, "originalAckRootHash")),
                    referencedContentHashes
            ));
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
        // The legacy field set is recognized, but dimension/documentId remain required.
        return new PersistedDraft(
                persistedId,
                new MapKey(map.get("gameType").getAsString(), map.get("mapName").getAsString()),
                NamespacedId.parse(string(root, "dimension")),
                NamespacedId.parse(string(root, "documentId")),
                count(root, "baseRevision"),
                Sha256.parse(string(root, "baseSourceHash")),
                initialRoot,
                persistedRoot,
                count(root, "ackCursor"),
                Instant.ofEpochMilli(count(root, "expiresAtEpochMillis")),
                root.has("lifecycle") ? string(root, "lifecycle") : "ACTIVE",
                operations
        );
    }

    void write(PersistedDraft draft) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("ackCursor", Long.toString(draft.ackCursor()));
        root.addProperty("baseRevision", Long.toString(draft.baseRevision()));
        root.addProperty("baseSourceHash", draft.baseSourceHash().value());
        root.addProperty("draftId", draft.draftId().toString());
        root.addProperty("draftRootHash", draft.draftRootHash().value());
        root.addProperty("initialRootHash", draft.initialRootHash().value());
        root.addProperty("dimension", draft.dimension().toString());
        root.addProperty("documentId", draft.documentId().toString());
        root.addProperty("expiresAtEpochMillis", Long.toString(draft.expiresAt().toEpochMilli()));
        root.addProperty("lifecycle", draft.lifecycle());
        JsonObject mapKey = new JsonObject();
        mapKey.addProperty("gameType", draft.mapKey().gameType());
        mapKey.addProperty("mapName", draft.mapKey().mapName());
        root.add("mapKey", mapKey);
        JsonArray operations = new JsonArray();
        draft.operations().stream()
                .sorted(Comparator.comparingLong(PersistedOperation::sequence))
                .forEach(operation -> {
                    JsonObject encoded = new JsonObject();
                    encoded.add(
                            "ackRootHash",
                            operation.ackRootHash() == null
                                    ? JsonNull.INSTANCE
                                    : new com.google.gson.JsonPrimitive(
                                    operation.ackRootHash().value()
                            )
                    );
                    encoded.addProperty(
                            "ackCursor", Long.toString(operation.originalAckCursor())
                    );
                    encoded.addProperty(
                            "originalAckRootHash", operation.originalAckRootHash().value()
                    );
                    encoded.addProperty("payloadHash", operation.payloadHash().value());
                    if (operation.referencedContentHashes() != null) {
                        JsonArray references = new JsonArray();
                        operation.referencedContentHashes().stream()
                                .sorted(Comparator.comparing(Sha256::value))
                                .forEach(hash -> references.add(hash.value()));
                        encoded.add("referencedContentHashes", references);
                    }
                    encoded.addProperty("sequence", Long.toString(operation.sequence()));
                    operations.add(encoded);
                });
        root.add("operations", operations);
        Path directory = draftDirectory(draft.draftId());
        ensureDirectory(directory, "Draft directory");
        fileSystem.writeAtomically(
                directory.resolve(STATE_FILE),
                JcsCanonicalizer.canonicalize(root)
        );
    }

    void writePayload(UUID draftId, Sha256 hash, byte[] payload) throws IOException {
        Path directory = requireDraftDirectory(draftId);
        Path entries = directory.resolve("entries");
        Path target = entries.resolve(hash.value() + ".bin");
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
    }

    byte[] readPayload(UUID draftId, Sha256 hash) throws IOException {
        Path entry = draftDirectory(draftId).resolve("entries")
                .resolve(hash.value() + ".bin");
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
        if (!Sha256Digest.of(content).equals(hash)) {
            throw error(
                    MinimapErrorCode.HASH_MISMATCH,
                    "Draft content entry does not match its address hash"
            );
        }
        return content;
    }

    boolean payloadExists(UUID draftId, Sha256 hash) throws IOException {
        Path entry = requireDraftDirectory(draftId).resolve("entries")
                .resolve(hash.value() + ".bin");
        if (!Files.exists(entry, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }
        if (!Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Draft content entry is not a regular file");
        }
        return true;
    }

    int payloadEntryCount(UUID draftId) throws IOException {
        Path entries = requireDraftDirectory(draftId).resolve("entries");
        if (!Files.exists(entries, LinkOption.NOFOLLOW_LINKS)) {
            return 0;
        }
        if (!Files.isDirectory(entries, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Draft entry directory is not a regular directory");
        }
        try (var paths = Files.list(entries)) {
            List<Path> content = paths.limit(MAX_CONTENT_ENTRIES_PER_DRAFT + 1L).toList();
            if (content.size() > MAX_CONTENT_ENTRIES_PER_DRAFT) {
                throw error(
                        MinimapErrorCode.QUOTA_EXCEEDED,
                        "Draft content entry quota is exceeded"
                );
            }
            for (Path path : content) {
                if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("Draft content tree contains a non-regular entry");
                }
            }
            return content.size();
        }
    }

    void delete(UUID draftId) throws IOException {
        Path directory = draftDirectory(draftId);
        Path state = directory.resolve(STATE_FILE);
        List<Path> paths = fileSystem.listTree(directory);
        if (paths.size() > MAX_DELETE_TREE_NODES) {
            throw new IOException("Draft cleanup tree exceeds its node limit");
        }
        if (paths.stream().anyMatch(path -> directory.relativize(path).getNameCount()
                > MAX_DELETE_TREE_DEPTH)) {
            throw new IOException("Draft cleanup tree exceeds its depth limit");
        }
        for (Path path : paths.stream()
                .filter(path -> !path.equals(directory) && !path.equals(state))
                .sorted(Comparator.reverseOrder()).toList()) {
            fileSystem.deleteIfExists(path);
        }
        fileSystem.deleteIfExists(state);
        fileSystem.deleteIfExists(directory);
    }

    Path draftDirectory(UUID draftId) {
        return root.resolve(draftId.toString()).normalize();
    }

    static void writeAtomic(Path target, byte[] bytes) throws IOException {
        Path temporary = target.resolveSibling(
                target.getFileName() + "." + UUID.randomUUID() + ".tmp"
        );
        try {
            // Preserve temp write -> file force -> atomic replace -> parent force.
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

    static List<Path> listTree(Path root) throws IOException {
        try (var paths = Files.walk(root, MAX_DELETE_TREE_DEPTH + 1)) {
            return paths.limit(MAX_DELETE_TREE_NODES + 1L).toList();
        }
    }

    static void syncDirectory(Path directory) throws IOException {
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            channel.force(true);
        } catch (AccessDeniedException unsupportedOnWindows) {
            if (!System.getProperty("os.name", "").startsWith("Windows")) {
                throw unsupportedOnWindows;
            }
        }
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

    private static DraftException error(MinimapErrorCode code, String message) {
        return new DraftException(code, message);
    }

    private static DraftException failure(String message, IOException cause) {
        return new DraftException(MinimapErrorCode.PUBLISH_IO_FAILED, message, cause);
    }

    record PersistedDraft(
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
            String lifecycle,
            List<PersistedOperation> operations
    ) {
        PersistedDraft {
            Objects.requireNonNull(draftId, "draftId");
            Objects.requireNonNull(mapKey, "mapKey");
            Objects.requireNonNull(dimension, "dimension");
            Objects.requireNonNull(documentId, "documentId");
            Objects.requireNonNull(baseSourceHash, "baseSourceHash");
            Objects.requireNonNull(initialRootHash, "initialRootHash");
            Objects.requireNonNull(draftRootHash, "draftRootHash");
            Objects.requireNonNull(expiresAt, "expiresAt");
            Objects.requireNonNull(lifecycle, "lifecycle");
            operations = List.copyOf(operations);
        }
    }

    record PersistedOperation(
            long sequence,
            Sha256 payloadHash,
            Sha256 ackRootHash,
            long originalAckCursor,
            Sha256 originalAckRootHash,
            List<Sha256> referencedContentHashes
    ) {
        PersistedOperation {
            Objects.requireNonNull(payloadHash, "payloadHash");
            Objects.requireNonNull(originalAckRootHash, "originalAckRootHash");
            if (referencedContentHashes != null) {
                referencedContentHashes = List.copyOf(referencedContentHashes);
            }
        }
    }
}
