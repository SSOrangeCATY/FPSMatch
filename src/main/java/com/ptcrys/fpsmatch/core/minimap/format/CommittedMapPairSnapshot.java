package com.ptcrys.fpsmatch.core.minimap.format;

import com.ptcrys.fpsmatch.core.minimap.codec.MinimapModelCodecs;
import com.ptcrys.fpsmatch.core.minimap.contract.MinimapFormatContract;
import com.ptcrys.fpsmatch.core.minimap.contract.MinimapHardLimits;
import com.ptcrys.fpsmatch.core.minimap.model.ContainerPath;
import com.ptcrys.fpsmatch.core.minimap.model.MapKey;
import com.ptcrys.fpsmatch.core.minimap.model.RuntimeManifest;
import com.ptcrys.fpsmatch.core.minimap.model.Sha256;
import com.ptcrys.fpsmatch.core.minimap.model.SourceManifest;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.SeekableByteChannel;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Metadata-only view of a pair that was fully validated before it was committed. */
public final class CommittedMapPairSnapshot implements Closeable {
    private static final Set<String> SOURCE_MANIFEST_FIELDS = Set.of(
            "formatVersion", "documentId", "binding", "revision", "dimension",
            "provenance", "tileEdge", "entries"
    );
    private static final Set<String> RUNTIME_MANIFEST_FIELDS = Set.of(
            "formatVersion", "documentId", "binding", "publishRevision", "sourceHash",
            "compilerProfile", "canvas", "defaultViewMode", "floors", "tileEdge", "entries"
    );

    private final CanonicalZipReader.Archive sourceArchive;
    private final CanonicalZipReader.Archive runtimeArchive;
    private final SourceManifest sourceManifest;
    private final RuntimeManifest runtimeManifest;
    private final Map<ContainerPath, PreservedExtensions> sourceExtensions;
    private final Map<ContainerPath, PreservedExtensions> runtimeExtensions;

    private CommittedMapPairSnapshot(
            CanonicalZipReader.Archive sourceArchive,
            CanonicalZipReader.Archive runtimeArchive,
            SourceManifest sourceManifest,
            RuntimeManifest runtimeManifest,
            Map<ContainerPath, PreservedExtensions> sourceExtensions,
            Map<ContainerPath, PreservedExtensions> runtimeExtensions
    ) {
        this.sourceArchive = sourceArchive;
        this.runtimeArchive = runtimeArchive;
        this.sourceManifest = sourceManifest;
        this.runtimeManifest = runtimeManifest;
        this.sourceExtensions = sourceExtensions;
        this.runtimeExtensions = runtimeExtensions;
    }

    public static CommittedMapPairSnapshot open(
            byte[] sourceBytes,
            byte[] runtimeBytes,
            MapKey expectedBinding,
            long expectedRevision,
            Sha256 expectedSourceHash,
            Sha256 expectedRuntimeHash,
            Sha256 expectedRuntimeContainerHash
    ) {
        Objects.requireNonNull(sourceBytes, "sourceBytes");
        Objects.requireNonNull(runtimeBytes, "runtimeBytes");
        Objects.requireNonNull(expectedBinding, "expectedBinding");
        Objects.requireNonNull(expectedSourceHash, "expectedSourceHash");
        Objects.requireNonNull(expectedRuntimeHash, "expectedRuntimeHash");
        Objects.requireNonNull(expectedRuntimeContainerHash, "expectedRuntimeContainerHash");
        CanonicalZipReader.Archive source = null;
        CanonicalZipReader.Archive runtime = null;
        try {
            source = CanonicalZipReader.read(sourceBytes, ContainerLimits.sourceHardLimits());
            runtime = CanonicalZipReader.read(runtimeBytes, ContainerLimits.runtimeHardLimits());
            return validateOwnedArchives(
                    source, runtime, expectedBinding, expectedRevision,
                    expectedSourceHash, expectedRuntimeHash,
                    expectedRuntimeContainerHash
            );
        } catch (RuntimeException exception) {
            closeAfterFailure(source, runtime, exception);
            throw exception;
        }
    }

    public static CommittedMapPairSnapshot open(
            SeekableByteChannel sourceChannel,
            long sourceSize,
            SeekableByteChannel runtimeChannel,
            long runtimeSize,
            MapKey expectedBinding,
            long expectedRevision,
            Sha256 expectedSourceHash,
            Sha256 expectedRuntimeHash,
            Sha256 expectedRuntimeContainerHash
    ) {
        Objects.requireNonNull(sourceChannel, "sourceChannel");
        Objects.requireNonNull(runtimeChannel, "runtimeChannel");
        Objects.requireNonNull(expectedBinding, "expectedBinding");
        Objects.requireNonNull(expectedSourceHash, "expectedSourceHash");
        Objects.requireNonNull(expectedRuntimeHash, "expectedRuntimeHash");
        Objects.requireNonNull(expectedRuntimeContainerHash, "expectedRuntimeContainerHash");
        CanonicalZipReader.Archive source = null;
        CanonicalZipReader.Archive runtime = null;
        boolean runtimeOpenAttempted = false;
        try {
            source = CanonicalZipReader.open(
                    sourceChannel, sourceSize, ContainerLimits.sourceHardLimits()
            );
            runtimeOpenAttempted = true;
            runtime = CanonicalZipReader.open(
                    runtimeChannel, runtimeSize, ContainerLimits.runtimeHardLimits()
            );
            return validateOwnedArchives(
                    source, runtime, expectedBinding, expectedRevision,
                    expectedSourceHash, expectedRuntimeHash,
                    expectedRuntimeContainerHash
            );
        } catch (RuntimeException exception) {
            closeAfterFailure(source, runtime, exception);
            if (source == null && !runtimeOpenAttempted) {
                closeAfterFailure(runtimeChannel, exception);
            }
            throw exception;
        }
    }

    private static CommittedMapPairSnapshot validateOwnedArchives(
            CanonicalZipReader.Archive source,
            CanonicalZipReader.Archive runtime,
            MapKey expectedBinding,
            long expectedRevision,
            Sha256 expectedSourceHash,
            Sha256 expectedRuntimeHash,
            Sha256 expectedRuntimeContainerHash
    ) {
        requireAllowedPaths(source.paths(), true);
        requireAllowedPaths(runtime.paths(), false);
        requireHash(expectedSourceHash, source.containerSha256(), "source container");
        requireHash(
                expectedRuntimeContainerHash,
                runtime.containerSha256(),
                "runtime container"
        );

        byte[] sourceManifestBytes = boundedEntry(
                source,
                MinimapContainerLayout.SOURCE_MANIFEST,
                MinimapHardLimits.MAX_SOURCE_MANIFEST_BYTES
        );
        CanonicalModelJson.Document<SourceManifest> sourceDocument =
                CanonicalModelJson.read(
                        sourceManifestBytes,
                        MinimapModelCodecs.SOURCE_MANIFEST,
                        SOURCE_MANIFEST_FIELDS
                );
        byte[] runtimeManifestBytes = boundedEntry(
                runtime,
                MinimapContainerLayout.RUNTIME_MANIFEST,
                MinimapHardLimits.MAX_RUNTIME_MANIFEST_BYTES
        );
        CanonicalModelJson.Document<RuntimeManifest> runtimeDocument =
                CanonicalModelJson.read(
                        runtimeManifestBytes,
                        MinimapModelCodecs.RUNTIME_MANIFEST,
                        RUNTIME_MANIFEST_FIELDS
                );
        SourceManifest sourceManifest = sourceDocument.value();
        RuntimeManifest runtimeManifest = runtimeDocument.value();
        requireCurrentFormat(sourceManifest, runtimeManifest);
        requireSorted(sourceManifest.entries().stream()
                .map(entry -> entry.path()).toList(), "source");
        requireSorted(runtimeManifest.entries().stream()
                .map(entry -> entry.path()).toList(), "runtime");
        source.verifyManifestEntries(
                MinimapContainerLayout.SOURCE_MANIFEST,
                sourceManifest.entries().stream()
                        .map(entry -> new CanonicalZipReader.ExpectedEntry(
                                entry.path(), entry.byteLength(), entry.sha256()
                        )).toList()
        );
        runtime.verifyManifestEntries(
                MinimapContainerLayout.RUNTIME_MANIFEST,
                runtimeManifest.entries().stream()
                        .map(entry -> new CanonicalZipReader.ExpectedEntry(
                                entry.path(), entry.byteLength(), entry.sha256()
                        )).toList()
        );
        requireAuthorityEntries(sourceManifest, runtimeManifest);
        requireHash(
                expectedRuntimeHash,
                Sha256Digest.of(runtimeManifestBytes),
                "runtime manifest"
        );
        if (!sourceManifest.binding().equals(expectedBinding)
                || !runtimeManifest.binding().equals(expectedBinding)
                || sourceManifest.revision() != expectedRevision
                || runtimeManifest.publishRevision() != expectedRevision
                || !sourceManifest.documentId().equals(runtimeManifest.documentId())
                || !expectedSourceHash.equals(runtimeManifest.sourceHash())) {
            throw new ContainerValidationException(
                    "Committed source/runtime binding does not match its publish record"
            );
        }
        return new CommittedMapPairSnapshot(
                source,
                runtime,
                sourceManifest,
                runtimeManifest,
                Map.of(
                        MinimapContainerLayout.SOURCE_MANIFEST,
                        sourceDocument.extensions()
                ),
                Map.of(
                        MinimapContainerLayout.RUNTIME_MANIFEST,
                        runtimeDocument.extensions()
                )
        );
    }

    public SourceManifest sourceManifest() {
        return sourceManifest;
    }

    public RuntimeManifest runtimeManifest() {
        return runtimeManifest;
    }

    public Set<ContainerPath> sourcePaths() {
        return sourceArchive.paths();
    }

    public Set<ContainerPath> runtimePaths() {
        return runtimeArchive.paths();
    }

    public PreservedExtensions sourceManifestExtensions() {
        return sourceExtensions.get(MinimapContainerLayout.SOURCE_MANIFEST);
    }

    public PreservedExtensions runtimeManifestExtensions() {
        return runtimeExtensions.get(MinimapContainerLayout.RUNTIME_MANIFEST);
    }

    public CanonicalZipWriter.EntrySource sourceEntrySource(ContainerPath path) {
        return entrySource(sourceArchive, path);
    }

    public CanonicalZipWriter.EntrySource runtimeEntrySource(ContainerPath path) {
        return entrySource(runtimeArchive, path);
    }

    @Override
    public void close() throws IOException {
        IOException failure = null;
        try {
            sourceArchive.close();
        } catch (IOException exception) {
            failure = exception;
        }
        try {
            runtimeArchive.close();
        } catch (IOException exception) {
            if (failure == null) {
                failure = exception;
            } else {
                failure.addSuppressed(exception);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private static CanonicalZipWriter.EntrySource entrySource(
            CanonicalZipReader.Archive archive,
            ContainerPath path
    ) {
        long size = archive.entryLength(path);
        return new CanonicalZipWriter.EntrySource() {
            @Override
            public ContainerPath path() {
                return path;
            }

            @Override
            public long size() {
                return size;
            }

            @Override
            public InputStream openStream() {
                return archive.openBounded(path);
            }
        };
    }

    private static byte[] boundedEntry(
            CanonicalZipReader.Archive archive,
            ContainerPath path,
            long maximumBytes
    ) {
        if (archive.entryLength(path) > maximumBytes) {
            throw new ContainerValidationException("Committed manifest exceeds its byte limit");
        }
        return archive.entryBytes(path);
    }

    private static void requireAllowedPaths(Set<ContainerPath> paths, boolean source) {
        for (ContainerPath path : paths) {
            boolean allowed = source
                    ? MinimapContainerLayout.isSourcePath(path)
                    : MinimapContainerLayout.isRuntimePath(path);
            if (!allowed) {
                throw new ContainerValidationException(
                        "Committed container has a path outside its layout: " + path
                );
            }
        }
    }

    private static void requireCurrentFormat(
            SourceManifest source,
            RuntimeManifest runtime
    ) {
        if (source.formatVersion().major() != MinimapFormatContract.CURRENT.major()
                || source.formatVersion().minor() < MinimapFormatContract.CURRENT.minor()
                || runtime.formatVersion().major() != MinimapFormatContract.CURRENT.major()
                || runtime.formatVersion().minor() < MinimapFormatContract.CURRENT.minor()) {
            throw new ContainerValidationException("Committed pair format is unsupported");
        }
    }

    private static void requireSorted(List<ContainerPath> paths, String kind) {
        String previous = null;
        for (ContainerPath path : paths) {
            if (previous != null && previous.compareTo(path.value()) >= 0) {
                throw new ContainerValidationException(
                        "Committed " + kind + " manifest entries are not strictly sorted"
                );
            }
            previous = path.value();
        }
    }

    private static void requireAuthorityEntries(
            SourceManifest source,
            RuntimeManifest runtime
    ) {
        Set<ContainerPath> sourcePaths = source.entries().stream()
                .map(entry -> entry.path()).collect(java.util.stream.Collectors.toSet());
        Set<ContainerPath> runtimePaths = runtime.entries().stream()
                .map(entry -> entry.path()).collect(java.util.stream.Collectors.toSet());
        if (!sourcePaths.containsAll(Set.of(
                MinimapContainerLayout.SOURCE_DOCUMENT,
                MinimapContainerLayout.SOURCE_REGIONS,
                MinimapContainerLayout.CONNECTIONS,
                MinimapContainerLayout.SOURCE_STYLES
        )) || !runtimePaths.containsAll(Set.of(
                MinimapContainerLayout.RUNTIME_REGIONS,
                MinimapContainerLayout.CONNECTIONS,
                MinimapContainerLayout.RUNTIME_STYLES
        ))) {
            throw new ContainerValidationException("Committed pair is missing authority entries");
        }
    }

    private static void requireHash(Sha256 expected, Sha256 actual, String kind) {
        if (!expected.equals(actual)) {
            throw new ContainerValidationException(
                    "Committed " + kind + " hash does not match its publish record"
            );
        }
    }

    private static void closeAfterFailure(
            CanonicalZipReader.Archive source,
            CanonicalZipReader.Archive runtime,
            RuntimeException failure
    ) {
        for (CanonicalZipReader.Archive archive : new CanonicalZipReader.Archive[]{source, runtime}) {
            if (archive == null) {
                continue;
            }
            try {
                archive.close();
            } catch (IOException exception) {
                failure.addSuppressed(exception);
            }
        }
    }

    private static void closeAfterFailure(
            SeekableByteChannel channel,
            RuntimeException failure
    ) {
        try {
            channel.close();
        } catch (IOException exception) {
            failure.addSuppressed(exception);
        }
    }
}
