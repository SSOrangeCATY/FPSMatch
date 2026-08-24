package com.ptcrys.fpsmatch.core.minimap.storage;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.ptcrys.fpsmatch.core.minimap.codec.MinimapCodecs;
import com.ptcrys.fpsmatch.core.minimap.format.JcsCanonicalizer;
import com.ptcrys.fpsmatch.core.minimap.format.Sha256Digest;
import com.ptcrys.fpsmatch.core.minimap.format.StrictJsonParser;
import com.ptcrys.fpsmatch.core.minimap.model.MapKey;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

final class MinimapRepositoryPersistence {
    private static final String STATE_FILE = "publish-state.json";
    private static final String RECOVERY_MARKER = "RECOVERY_REQUIRED";
    private static final long MAX_METADATA_BYTES = 1024L * 1024;
    private static final int MAX_DIRECTORY_ENTRIES = 4096;

    private final Path root;
    private final Path realRoot;
    private final RepositoryFileSystem fileSystem;
    // Keep the durability signal with the filesystem operations that can degrade it.
    private volatile boolean directorySyncDegraded;

    MinimapRepositoryPersistence(
            Path root,
            Path realRoot,
            RepositoryFileSystem fileSystem
    ) {
        this.root = Objects.requireNonNull(root, "root");
        this.realRoot = Objects.requireNonNull(realRoot, "realRoot");
        this.fileSystem = Objects.requireNonNull(fileSystem, "fileSystem");
    }

    Path mapDirectory(MapKey key) {
        Objects.requireNonNull(key, "key");
        JsonElement encoded = MapKey.codec().encodeStart(JsonOps.INSTANCE, key)
                .result()
                .orElseThrow(() -> new ContainerStorageException("Unable to encode map key"));
        String digest = Sha256Digest.of(JcsCanonicalizer.canonicalize(encoded)).value();
        Path result = root.resolve("maps").resolve(digest).normalize();
        if (!result.startsWith(root)) {
            throw new ContainerStorageException("Map repository path escaped its root");
        }
        return result;
    }

    RepositoryFileSystem.LockHandle acquireMapLock(Path mapDirectory)
            throws IOException {
        Path maps = mapDirectory.getParent();
        boolean mapsMissing = !Files.exists(maps, LinkOption.NOFOLLOW_LINKS);
        boolean mapMissing = !Files.exists(mapDirectory, LinkOption.NOFOLLOW_LINKS);
        verifySafeRepositoryPath(mapDirectory);
        fileSystem.createDirectories(mapDirectory);
        verifySafeRepositoryPath(mapDirectory);
        if (mapsMissing) {
            syncDirectory(root);
        }
        if (mapMissing) {
            syncDirectory(maps);
        }
        RepositoryFileSystem.LockHandle lock = fileSystem.acquireExclusiveLock(
                mapDirectory.resolve(".repository.lock")
        );
        if (!mapMissing) {
            return lock;
        }
        try {
            syncDirectory(mapDirectory);
            return lock;
        } catch (IOException | RuntimeException failure) {
            try {
                lock.close();
            } catch (IOException closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw failure;
        }
    }

    void verifySafeRepositoryPath(Path candidate) {
        Path absolute = Objects.requireNonNull(candidate, "candidate")
                .toAbsolutePath().normalize();
        if (!absolute.startsWith(root)) {
            throw new ContainerStorageException("Repository path escaped its root");
        }
        try {
            if (!root.toRealPath().equals(realRoot)) {
                throw new ContainerStorageException(
                        "Repository root changed after initialization"
                );
            }
            Path current = root;
            for (Path component : root.relativize(absolute)) {
                current = current.resolve(component);
                BasicFileAttributes attributes;
                try {
                    attributes = fileSystem.readAttributesNoFollow(current);
                } catch (NoSuchFileException missingComponent) {
                    continue;
                }
                if (attributes.isSymbolicLink() || attributes.isOther()) {
                    throw new ContainerStorageException(
                            "Repository path contains a link or reparse point: " + current
                    );
                }
                if (!current.toRealPath().startsWith(realRoot)) {
                    throw new ContainerStorageException(
                            "Repository path resolved outside its real root"
                    );
                }
            }
        } catch (IOException exception) {
            throw storageFailure("Unable to verify repository path", exception);
        }
    }

    Optional<CurrentPointer> readCurrent(Path mapDirectory) {
        Path current = mapDirectory.resolve("CURRENT");
        try {
            return Optional.of(CurrentPointer.read(readMetadata(current)));
        } catch (NoSuchFileException missing) {
            return Optional.empty();
        } catch (IOException exception) {
            throw storageFailure("Unable to read CURRENT pointer", exception);
        }
    }

    long readHighWaterMark(Path mapDirectory) {
        return readHighWaterMarkIfPresent(mapDirectory).orElse(0L);
    }

    java.util.OptionalLong readHighWaterMarkIfPresent(Path mapDirectory) {
        Path state = mapDirectory.resolve(STATE_FILE);
        try {
            return java.util.OptionalLong.of(readHighWaterStateFile(state));
        } catch (NoSuchFileException missing) {
            return java.util.OptionalLong.empty();
        } catch (IOException exception) {
            throw storageFailure("Unable to read publish state", exception);
        } catch (RuntimeException exception) {
            if (exception instanceof ContainerStorageException storageException) {
                throw storageException;
            }
            throw new ContainerStorageException("Publish state is invalid", exception);
        }
    }

    long readHighWaterStateFile(Path state) throws IOException {
        byte[] bytes = readMetadata(state);
        JsonElement parsed = StrictJsonParser.parse(bytes);
        if (!parsed.isJsonObject()
                || !Arrays.equals(bytes, JcsCanonicalizer.canonicalize(parsed))) {
            throw new ContainerStorageException("Publish state is not canonical JSON");
        }
        JsonObject root = parsed.getAsJsonObject();
        if (!root.keySet().equals(Set.of("highWaterMark"))) {
            throw new ContainerStorageException("Publish state fields are invalid");
        }
        return MinimapCodecs.NON_NEGATIVE_LONG
                .parse(JsonOps.INSTANCE, root.get("highWaterMark"))
                .result()
                .orElseThrow(() -> new ContainerStorageException(
                        "Publish high-water mark is invalid"
                ));
    }

    PublishRecord readPublishRecord(Path path) throws IOException {
        return PublishRecord.read(readMetadata(path));
    }

    byte[] readMetadata(Path path) throws IOException {
        verifySafeRepositoryPath(path);
        try (RepositoryFileSystem.BoundedReadChannel input =
                     fileSystem.openBoundedReadChannel(path, MAX_METADATA_BYTES)) {
            if (input.size() > Integer.MAX_VALUE) {
                throw new IOException("Repository metadata exceeds the JVM array limit");
            }
            byte[] bytes = new byte[(int) input.size()];
            ByteBuffer destination = ByteBuffer.wrap(bytes);
            while (destination.hasRemaining()) {
                int read = input.channel().read(destination);
                if (read < 0) {
                    throw new IOException("Repository metadata changed while being read");
                }
                if (read == 0) {
                    continue;
                }
            }
            ByteBuffer trailing = ByteBuffer.allocate(1);
            if (input.channel().read(trailing) != -1) {
                throw new IOException("Repository metadata changed while being read");
            }
            return bytes;
        }
    }

    void writeHighWaterMark(Path mapDirectory, long highWaterMark) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("highWaterMark", Long.toString(highWaterMark));
        Path state = mapDirectory.resolve(STATE_FILE);
        Path temporary = mapDirectory.resolve(
                STATE_FILE + "." + UUID.randomUUID() + ".tmp"
        );
        try {
            writeDurable(temporary, JcsCanonicalizer.canonicalize(root));
            fileSystem.replaceAtomically(temporary, state);
            syncDirectory(mapDirectory);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    void writeDurable(Path file, byte[] bytes) throws IOException {
        fileSystem.write(file, bytes);
        fileSystem.fsyncFile(file);
    }

    void writeRecordDurable(Path record, byte[] bytes) throws IOException {
        Path temporary = record.resolveSibling(
                record.getFileName() + "." + UUID.randomUUID() + ".tmp"
        );
        try {
            // Durability order is part of the repository recovery contract.
            writeDurable(temporary, bytes);
            fileSystem.replaceAtomically(temporary, record);
            syncDirectory(record.getParent());
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    boolean hasPersistedRecoveryMarker() {
        Path maps = root.resolve("maps");
        try {
            BasicFileAttributes mapsAttributes = readAttributesIfPresent(maps);
            if (mapsAttributes == null) {
                return false;
            }
            if (!mapsAttributes.isDirectory()
                    || mapsAttributes.isSymbolicLink()
                    || mapsAttributes.isOther()) {
                return true;
            }
            try (java.util.stream.Stream<Path> stream = Files.list(maps)) {
                java.util.List<Path> mapDirectories = stream
                        .limit(MAX_DIRECTORY_ENTRIES + 1L)
                        .toList();
                if (mapDirectories.size() > MAX_DIRECTORY_ENTRIES) {
                    return true;
                }
                for (Path mapDirectory : mapDirectories) {
                    BasicFileAttributes mapAttributes = readAttributesIfPresent(mapDirectory);
                    if (mapAttributes == null) {
                        continue;
                    }
                    if (mapAttributes.isSymbolicLink() || mapAttributes.isOther()) {
                        return true;
                    }
                    if (!mapAttributes.isDirectory()) {
                        continue;
                    }
                    if (recoveryMarkerPresent(mapDirectory)) {
                        return true;
                    }
                }
            }
            return false;
        } catch (IOException | RuntimeException inspectionFailure) {
            return true;
        }
    }

    boolean recoveryMarkerPresent(Path mapDirectory) {
        Path marker = mapDirectory.resolve(RECOVERY_MARKER);
        try {
            readMetadata(marker);
            return true;
        } catch (NoSuchFileException missing) {
            return false;
        } catch (IOException failure) {
            throw storageFailure("Unable to read recovery marker", failure);
        }
    }

    boolean recoveryMarkerExists(Path mapDirectory) {
        return Files.exists(
                mapDirectory.resolve(RECOVERY_MARKER), LinkOption.NOFOLLOW_LINKS
        );
    }

    void markRecoveryRequired(
            Path mapDirectory,
            PublishDescriptor descriptor
    ) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("descriptorChecksum", descriptor.descriptorChecksum().value());
        root.addProperty("publishRevision", Long.toString(descriptor.publishRevision()));
        root.addProperty("publishToken", descriptor.publishToken());
        writeRecoveryMarker(mapDirectory, root);
    }

    void resetCurrentDurably(
            Path mapDirectory,
            CurrentPointer expected,
            CurrentPointer tombstone
    ) throws IOException {
        JsonObject marker = new JsonObject();
        marker.addProperty("expectedDescriptorChecksum", expected.descriptorChecksum().value());
        marker.addProperty("expectedRevision", Long.toString(expected.revision()));
        marker.addProperty("operation", "CURRENT_RESET");
        writeRecoveryMarker(mapDirectory, marker);
        Path temporary = mapDirectory.resolve("CURRENT." + UUID.randomUUID() + ".tmp");
        try {
            // Keep recovery required until both CURRENT and its parent directory are durable.
            writeDurable(temporary, tombstone.canonicalBytes());
            fileSystem.replaceAtomically(temporary, mapDirectory.resolve("CURRENT"));
            syncDirectory(mapDirectory);
            clearRecoveryRequired(mapDirectory);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private void writeRecoveryMarker(Path mapDirectory, JsonObject root)
            throws IOException {
        Path marker = mapDirectory.resolve(RECOVERY_MARKER);
        Path temporary = mapDirectory.resolve(
                RECOVERY_MARKER + "." + UUID.randomUUID() + ".tmp"
        );
        try {
            writeDurable(temporary, JcsCanonicalizer.canonicalize(root));
            fileSystem.replaceAtomically(temporary, marker);
            syncDirectory(mapDirectory);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    void clearRecoveryRequired(Path mapDirectory) throws IOException {
        if (Files.deleteIfExists(mapDirectory.resolve(RECOVERY_MARKER))) {
            syncDirectory(mapDirectory);
        }
    }

    void syncDirectory(Path directory) throws IOException {
        if (fileSystem.directorySyncSupport()
                == RepositoryFileSystem.DirectorySyncSupport.UNSUPPORTED) {
            directorySyncDegraded = true;
            throw new DirectorySyncUnavailableException(directory);
        }
        fileSystem.fsyncDirectory(directory);
    }

    void requireDirectorySyncForPublish() {
        if (fileSystem.directorySyncSupport()
                == RepositoryFileSystem.DirectorySyncSupport.UNSUPPORTED) {
            directorySyncDegraded = true;
            throw new ContainerStorageException(
                    "Publishing requires durable directory synchronization"
            );
        }
    }

    boolean directorySyncDegraded() {
        return directorySyncDegraded;
    }

    void markDirectorySyncDegraded() {
        directorySyncDegraded = true;
    }

    private static BasicFileAttributes readAttributesIfPresent(Path path)
            throws IOException {
        try {
            return Files.readAttributes(
                    path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS
            );
        } catch (NoSuchFileException missing) {
            return null;
        }
    }

    private static ContainerStorageException storageFailure(
            String message,
            IOException exception
    ) {
        return new ContainerStorageException(message, exception);
    }

    static final class DirectorySyncUnavailableException extends IOException {
        private DirectorySyncUnavailableException(Path directory) {
            super("Directory synchronization is unavailable: " + directory);
        }
    }
}
