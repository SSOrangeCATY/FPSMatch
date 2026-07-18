package com.phasetranscrystal.fpsmatch.core.minimap.format;

import com.phasetranscrystal.fpsmatch.core.minimap.codec.MinimapModelCodecs;
import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapHardLimits;
import com.phasetranscrystal.fpsmatch.core.minimap.model.ContainerPath;
import com.phasetranscrystal.fpsmatch.core.minimap.model.Provenance;
import com.phasetranscrystal.fpsmatch.core.minimap.model.RuntimeManifest;
import com.phasetranscrystal.fpsmatch.core.minimap.model.Sha256;
import com.phasetranscrystal.fpsmatch.core.minimap.model.SourceManifest;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Rebinds already-compiled content to a new revision without image processing. */
public final class MapRebindService {
    private MapRebindService() {
    }

    public static ReboundMapPair rebindCommitted(
            CommittedMapPairSnapshot snapshot,
            long publishRevision
    ) {
        Objects.requireNonNull(snapshot, "snapshot");
        SourceManifest oldSource = snapshot.sourceManifest();
        if (publishRevision <= oldSource.revision()) {
            throw new ContainerValidationException(
                    "Rebound revision must be newer than its source"
            );
        }

        SourceManifest reboundSource = new SourceManifest(
                oldSource.formatVersion(),
                oldSource.documentId(),
                oldSource.binding(),
                publishRevision,
                oldSource.dimension(),
                Optional.of(new Provenance(
                        oldSource.documentId(),
                        oldSource.binding(),
                        oldSource.dimension(),
                        oldSource.revision(),
                        snapshot.runtimeManifest().sourceHash()
                )),
                oldSource.tileEdge(),
                oldSource.entries()
        );
        byte[] sourceManifestBytes = CanonicalModelJson.write(
                reboundSource,
                MinimapModelCodecs.SOURCE_MANIFEST,
                snapshot.sourceManifestExtensions()
        );
        requireManifestLimit(
                sourceManifestBytes,
                MinimapHardLimits.MAX_SOURCE_MANIFEST_BYTES,
                "Source"
        );
        byte[] reboundSourceBytes = writeSource(snapshot, sourceManifestBytes);
        Sha256 reboundSourceHash = Sha256Digest.of(reboundSourceBytes);

        RuntimeManifest oldRuntime = snapshot.runtimeManifest();
        RuntimeManifest reboundRuntime = new RuntimeManifest(
                oldRuntime.formatVersion(),
                oldRuntime.documentId(),
                oldRuntime.binding(),
                publishRevision,
                reboundSourceHash,
                oldRuntime.compilerProfile(),
                oldRuntime.canvas(),
                oldRuntime.defaultViewMode(),
                oldRuntime.floors(),
                oldRuntime.tileEdge(),
                oldRuntime.entries()
        );
        byte[] runtimeManifestBytes = CanonicalModelJson.write(
                reboundRuntime,
                MinimapModelCodecs.RUNTIME_MANIFEST,
                snapshot.runtimeManifestExtensions()
        );
        requireManifestLimit(
                runtimeManifestBytes,
                MinimapHardLimits.MAX_RUNTIME_MANIFEST_BYTES,
                "Runtime"
        );
        byte[] reboundRuntimeBytes = writeRuntime(snapshot, runtimeManifestBytes);
        return new ReboundMapPair(
                reboundSourceBytes,
                reboundRuntimeBytes,
                reboundSourceHash,
                Sha256Digest.of(runtimeManifestBytes),
                Sha256Digest.of(reboundRuntimeBytes)
        );
    }

    public static ContainerOutputDigest writeReboundSource(
            CommittedMapPairSnapshot snapshot,
            long publishRevision,
            OutputStream output
    ) throws IOException {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(output, "output");
        SourceManifest oldSource = snapshot.sourceManifest();
        if (publishRevision <= oldSource.revision()) {
            throw new ContainerValidationException(
                    "Rebound revision must be newer than its source"
            );
        }
        SourceManifest reboundSource = new SourceManifest(
                oldSource.formatVersion(),
                oldSource.documentId(),
                oldSource.binding(),
                publishRevision,
                oldSource.dimension(),
                Optional.of(new Provenance(
                        oldSource.documentId(),
                        oldSource.binding(),
                        oldSource.dimension(),
                        oldSource.revision(),
                        snapshot.runtimeManifest().sourceHash()
                )),
                oldSource.tileEdge(),
                oldSource.entries()
        );
        byte[] manifestBytes = CanonicalModelJson.write(
                reboundSource,
                MinimapModelCodecs.SOURCE_MANIFEST,
                snapshot.sourceManifestExtensions()
        );
        requireManifestLimit(
                manifestBytes,
                MinimapHardLimits.MAX_SOURCE_MANIFEST_BYTES,
                "Source"
        );
        return writeContainer(
                output,
                sourceEntries(snapshot, manifestBytes),
                ContainerLimits.sourceHardLimits(),
                manifestBytes
        );
    }

    public static ContainerOutputDigest writeReboundRuntime(
            CommittedMapPairSnapshot snapshot,
            long publishRevision,
            Sha256 reboundSourceHash,
            OutputStream output
    ) throws IOException {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(reboundSourceHash, "reboundSourceHash");
        Objects.requireNonNull(output, "output");
        RuntimeManifest oldRuntime = snapshot.runtimeManifest();
        if (publishRevision <= snapshot.sourceManifest().revision()) {
            throw new ContainerValidationException(
                    "Rebound revision must be newer than its source"
            );
        }
        RuntimeManifest reboundRuntime = new RuntimeManifest(
                oldRuntime.formatVersion(),
                oldRuntime.documentId(),
                oldRuntime.binding(),
                publishRevision,
                reboundSourceHash,
                oldRuntime.compilerProfile(),
                oldRuntime.canvas(),
                oldRuntime.defaultViewMode(),
                oldRuntime.floors(),
                oldRuntime.tileEdge(),
                oldRuntime.entries()
        );
        byte[] manifestBytes = CanonicalModelJson.write(
                reboundRuntime,
                MinimapModelCodecs.RUNTIME_MANIFEST,
                snapshot.runtimeManifestExtensions()
        );
        requireManifestLimit(
                manifestBytes,
                MinimapHardLimits.MAX_RUNTIME_MANIFEST_BYTES,
                "Runtime"
        );
        return writeContainer(
                output,
                runtimeEntries(snapshot, manifestBytes),
                ContainerLimits.runtimeHardLimits(),
                manifestBytes
        );
    }

    public static ReboundMapPair rebind(
            byte[] sourceBytes,
            byte[] runtimeBytes,
            long publishRevision
    ) {
        Objects.requireNonNull(sourceBytes, "sourceBytes");
        Objects.requireNonNull(runtimeBytes, "runtimeBytes");
        try (SourceMap source = SourceMapReader.read(sourceBytes);
             RuntimeMap runtime = RuntimeMapReader.read(runtimeBytes)) {
            CompiledMapPair.verifyBinding(source, runtime);
            if (publishRevision <= source.manifest().revision()) {
                throw new ContainerValidationException(
                        "Rebound revision must be newer than its source"
                );
            }

            SourceManifest oldSource = source.manifest();
            SourceManifest reboundSource = new SourceManifest(
                    oldSource.formatVersion(),
                    oldSource.documentId(),
                    oldSource.binding(),
                    publishRevision,
                    oldSource.dimension(),
                    Optional.of(new Provenance(
                            oldSource.documentId(),
                            oldSource.binding(),
                            oldSource.dimension(),
                            oldSource.revision(),
                            source.sourceHash()
                    )),
                    oldSource.tileEdge(),
                    oldSource.entries()
            );
            byte[] reboundSourceBytes = writeSource(source, reboundSource);
            Sha256 reboundSourceHash = Sha256Digest.of(reboundSourceBytes);

            RuntimeManifest oldRuntime = runtime.manifest();
            RuntimeManifest reboundRuntime = new RuntimeManifest(
                    oldRuntime.formatVersion(),
                    oldRuntime.documentId(),
                    oldRuntime.binding(),
                    publishRevision,
                    reboundSourceHash,
                    oldRuntime.compilerProfile(),
                    oldRuntime.canvas(),
                    oldRuntime.defaultViewMode(),
                    oldRuntime.floors(),
                    oldRuntime.tileEdge(),
                    oldRuntime.entries()
            );
            byte[] runtimeManifestBytes = CanonicalModelJson.write(
                    reboundRuntime,
                    MinimapModelCodecs.RUNTIME_MANIFEST,
                    runtime.authorityExtensions().getOrDefault(
                            MinimapContainerLayout.RUNTIME_MANIFEST,
                            PreservedExtensions.missing()
                    )
            );
            byte[] reboundRuntimeBytes = writeRuntime(
                    runtime, runtimeManifestBytes
            );

            try (SourceMap reboundSourceMap = SourceMapReader.read(reboundSourceBytes);
                 RuntimeMap reboundRuntimeMap = RuntimeMapReader.read(reboundRuntimeBytes)) {
                CompiledMapPair.verifyBinding(reboundSourceMap, reboundRuntimeMap);
                return new ReboundMapPair(
                        reboundSourceBytes,
                        reboundRuntimeBytes,
                        reboundSourceMap.sourceHash(),
                        reboundRuntimeMap.runtimeHash(),
                        reboundRuntimeMap.runtimeContainerHash()
                );
            }
        } catch (IOException exception) {
            throw new ContainerValidationException("Unable to close rebound map pair", exception);
        }
    }

    private static byte[] writeSource(SourceMap source, SourceManifest manifest) {
        List<CanonicalZipWriter.EntrySource> entries = new ArrayList<>();
        for (ContainerPath path : source.paths()) {
            if (!path.equals(MinimapContainerLayout.SOURCE_MANIFEST)) {
                entries.add(source.entrySource(path));
            }
        }
        entries.add(new CanonicalZipWriter.Entry(
                MinimapContainerLayout.SOURCE_MANIFEST,
                CanonicalModelJson.write(
                        manifest,
                        MinimapModelCodecs.SOURCE_MANIFEST,
                        source.authorityExtensions().getOrDefault(
                                MinimapContainerLayout.SOURCE_MANIFEST,
                                PreservedExtensions.missing()
                        )
                )
        ));
        return CanonicalZipWriter.writeSources(entries, ContainerLimits.sourceHardLimits());
    }

    private static byte[] writeSource(
            CommittedMapPairSnapshot snapshot,
            byte[] manifestBytes
    ) {
        return CanonicalZipWriter.writeSources(
                sourceEntries(snapshot, manifestBytes),
                ContainerLimits.sourceHardLimits()
        );
    }

    private static List<CanonicalZipWriter.EntrySource> sourceEntries(
            CommittedMapPairSnapshot snapshot,
            byte[] manifestBytes
    ) {
        List<CanonicalZipWriter.EntrySource> entries = new ArrayList<>();
        for (ContainerPath path : snapshot.sourcePaths()) {
            if (!path.equals(MinimapContainerLayout.SOURCE_MANIFEST)) {
                entries.add(snapshot.sourceEntrySource(path));
            }
        }
        entries.add(new CanonicalZipWriter.Entry(
                MinimapContainerLayout.SOURCE_MANIFEST,
                manifestBytes
        ));
        return entries;
    }

    private static byte[] writeRuntime(RuntimeMap runtime, byte[] manifestBytes) {
        List<CanonicalZipWriter.EntrySource> entries = new ArrayList<>();
        for (ContainerPath path : runtime.paths()) {
            if (!path.equals(MinimapContainerLayout.RUNTIME_MANIFEST)) {
                entries.add(runtimeEntrySource(runtime, path));
            }
        }
        entries.add(new CanonicalZipWriter.Entry(
                MinimapContainerLayout.RUNTIME_MANIFEST, manifestBytes
        ));
        return CanonicalZipWriter.writeSources(entries, ContainerLimits.runtimeHardLimits());
    }

    private static void requireManifestLimit(byte[] bytes, long maximum, String kind) {
        if (bytes.length > maximum) {
            throw new ContainerValidationException(
                    kind + " manifest exceeds its byte limit"
            );
        }
    }

    private static byte[] writeRuntime(
            CommittedMapPairSnapshot snapshot,
            byte[] manifestBytes
    ) {
        return CanonicalZipWriter.writeSources(
                runtimeEntries(snapshot, manifestBytes),
                ContainerLimits.runtimeHardLimits()
        );
    }

    private static List<CanonicalZipWriter.EntrySource> runtimeEntries(
            CommittedMapPairSnapshot snapshot,
            byte[] manifestBytes
    ) {
        List<CanonicalZipWriter.EntrySource> entries = new ArrayList<>();
        for (ContainerPath path : snapshot.runtimePaths()) {
            if (!path.equals(MinimapContainerLayout.RUNTIME_MANIFEST)) {
                entries.add(snapshot.runtimeEntrySource(path));
            }
        }
        entries.add(new CanonicalZipWriter.Entry(
                MinimapContainerLayout.RUNTIME_MANIFEST, manifestBytes
        ));
        return entries;
    }

    private static ContainerOutputDigest writeContainer(
            OutputStream output,
            List<? extends CanonicalZipWriter.EntrySource> entries,
            ContainerLimits limits,
            byte[] manifestBytes
    ) throws IOException {
        DigestCountingOutputStream digesting = new DigestCountingOutputStream(output);
        CanonicalZipWriter.write(digesting, entries, limits);
        return new ContainerOutputDigest(
                digesting.finish(),
                digesting.count(),
                Sha256Digest.of(manifestBytes),
                manifestBytes.length
        );
    }

    private static CanonicalZipWriter.EntrySource runtimeEntrySource(
            RuntimeMap runtime,
            ContainerPath path
    ) {
        long size = runtime.entryLength(path);
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
                return runtime.openEntry(path);
            }
        };
    }

    public record ReboundMapPair(
            byte[] sourceBytes,
            byte[] runtimeBytes,
            Sha256 sourceHash,
            Sha256 runtimeHash,
            Sha256 runtimeContainerHash
    ) {
        public ReboundMapPair {
            sourceBytes = Objects.requireNonNull(sourceBytes, "sourceBytes").clone();
            runtimeBytes = Objects.requireNonNull(runtimeBytes, "runtimeBytes").clone();
            Objects.requireNonNull(sourceHash, "sourceHash");
            Objects.requireNonNull(runtimeHash, "runtimeHash");
            Objects.requireNonNull(runtimeContainerHash, "runtimeContainerHash");
        }

        @Override
        public byte[] sourceBytes() {
            return sourceBytes.clone();
        }

        @Override
        public byte[] runtimeBytes() {
            return runtimeBytes.clone();
        }
    }

    public record ContainerOutputDigest(
            Sha256 containerHash,
            long containerLength,
            Sha256 manifestHash,
            long manifestLength
    ) {
        public ContainerOutputDigest {
            Objects.requireNonNull(containerHash, "containerHash");
            Objects.requireNonNull(manifestHash, "manifestHash");
            if (containerLength < 0 || manifestLength < 0) {
                throw new IllegalArgumentException("Container lengths must be non-negative");
            }
        }
    }

    private static final class DigestCountingOutputStream extends OutputStream {
        private final OutputStream delegate;
        private final MessageDigest digest;
        private long count;
        private boolean finished;

        private DigestCountingOutputStream(OutputStream delegate) {
            this.delegate = delegate;
            try {
                digest = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException exception) {
                throw new IllegalStateException("SHA-256 is unavailable", exception);
            }
        }

        @Override
        public void write(int value) throws IOException {
            ensureWritable();
            delegate.write(value);
            digest.update((byte) value);
            count++;
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            Objects.checkFromIndexSize(offset, length, bytes.length);
            ensureWritable();
            delegate.write(bytes, offset, length);
            digest.update(bytes, offset, length);
            count = Math.addExact(count, length);
        }

        private long count() {
            return count;
        }

        private Sha256 finish() {
            ensureWritable();
            finished = true;
            return Sha256.parse(HexFormat.of().formatHex(digest.digest()));
        }

        private void ensureWritable() {
            if (finished) {
                throw new IllegalStateException("Container output digest is already finished");
            }
        }
    }
}
