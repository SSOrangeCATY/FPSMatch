package com.phasetranscrystal.fpsmatch.core.minimap.format;

import com.phasetranscrystal.fpsmatch.core.minimap.codec.MinimapModelCodecs;
import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapHardLimits;
import com.phasetranscrystal.fpsmatch.core.minimap.model.ContainerPath;
import com.phasetranscrystal.fpsmatch.core.minimap.model.MediaType;
import com.phasetranscrystal.fpsmatch.core.minimap.model.MinimapDefinition;
import com.phasetranscrystal.fpsmatch.core.minimap.model.Sha256;
import com.phasetranscrystal.fpsmatch.core.minimap.model.SourceEntryDescriptor;
import com.phasetranscrystal.fpsmatch.core.minimap.model.SourceManifest;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class SourceMapWriter {
    private static final Set<ContainerPath> GENERATED_PATHS = Set.of(
            MinimapContainerLayout.SOURCE_MANIFEST,
            MinimapContainerLayout.SOURCE_DOCUMENT,
            MinimapContainerLayout.SOURCE_REGIONS,
            MinimapContainerLayout.CONNECTIONS,
            MinimapContainerLayout.SOURCE_STYLES
    );

    private SourceMapWriter() {
    }

    public static byte[] write(
            MinimapDefinition definition,
            Map<ContainerPath, byte[]> additionalEntries
    ) {
        Objects.requireNonNull(additionalEntries, "additionalEntries");
        List<CanonicalZipWriter.EntrySource> sources = new ArrayList<>(additionalEntries.size());
        additionalEntries.forEach((path, bytes) -> sources.add(
                new CanonicalZipWriter.Entry(path, bytes)
        ));
        return write(new SourceMapDraft(definition, sources));
    }

    public static byte[] write(MinimapDefinition definition) {
        return write(definition, Map.of());
    }

    public static byte[] write(
            MinimapDefinition definition,
            List<? extends CanonicalZipWriter.EntrySource> additionalEntries
    ) {
        return write(new SourceMapDraft(definition, additionalEntries));
    }

    public static void write(
            OutputStream output,
            MinimapDefinition definition,
            List<? extends CanonicalZipWriter.EntrySource> additionalEntries
    ) throws IOException {
        write(output, new SourceMapDraft(definition, additionalEntries));
    }

    public static byte[] write(SourceMapDraft draft) {
        PreparedSource prepared = prepare(draft);
        return CanonicalZipWriter.writeSources(
                prepared.entries(), ContainerLimits.sourceHardLimits()
        );
    }

    public static void write(OutputStream output, SourceMapDraft draft) throws IOException {
        Objects.requireNonNull(output, "output");
        PreparedSource prepared = prepare(draft);
        CanonicalZipWriter.write(
                output, prepared.entries(), ContainerLimits.sourceHardLimits()
        );
    }

    private static PreparedSource prepare(SourceMapDraft draft) {
        Objects.requireNonNull(draft, "draft");
        MinimapDefinition definition = draft.definition();
        validateExtensionPaths(draft.authorityExtensions());

        List<CanonicalZipWriter.EntrySource> contentSources = new ArrayList<>();
        contentSources.add(jsonEntry(
                MinimapContainerLayout.SOURCE_DOCUMENT,
                CanonicalModelJson.write(
                        definition.document(),
                        MinimapModelCodecs.SOURCE_DOCUMENT,
                        extension(draft, MinimapContainerLayout.SOURCE_DOCUMENT)
                )
        ));
        contentSources.add(jsonEntry(
                MinimapContainerLayout.SOURCE_REGIONS,
                CanonicalModelJson.write(
                        definition.regions(),
                        MinimapModelCodecs.REGIONS,
                        extension(draft, MinimapContainerLayout.SOURCE_REGIONS)
                )
        ));
        contentSources.add(jsonEntry(
                MinimapContainerLayout.CONNECTIONS,
                CanonicalModelJson.write(
                        definition.connections(),
                        MinimapModelCodecs.CONNECTIONS,
                        extension(draft, MinimapContainerLayout.CONNECTIONS)
                )
        ));
        contentSources.add(jsonEntry(
                MinimapContainerLayout.SOURCE_STYLES,
                CanonicalModelJson.write(
                        definition.styles(),
                        MinimapModelCodecs.STYLES,
                        extension(draft, MinimapContainerLayout.SOURCE_STYLES)
                )
        ));

        Set<ContainerPath> paths = new HashSet<>(GENERATED_PATHS);
        for (CanonicalZipWriter.EntrySource source : draft.entries()) {
            Objects.requireNonNull(source, "source entry");
            CanonicalZipWriter.EntrySource frozen = FrozenMetadataEntrySource.capture(source);
            ContainerPath path = frozen.path();
            if (!MinimapContainerLayout.isSourcePath(path)) {
                throw new ContainerValidationException("Path is not allowed in a source map: " + path);
            }
            if (!paths.add(path)) {
                throw new ContainerValidationException("Source map entry path is duplicated or reserved: " + path);
            }
            contentSources.add(frozen);
        }
        contentSources.sort(Comparator.comparing(source -> source.path().value()));
        MinimapTileValidator.validateSourceCoverageBudget(definition);

        List<SourceEntryDescriptor> descriptors = new ArrayList<>(contentSources.size());
        List<CanonicalZipWriter.EntrySource> verifiedSources = new ArrayList<>(contentSources.size() + 1);
        for (CanonicalZipWriter.EntrySource source : contentSources) {
            SourceDigest digest = scan(source);
            descriptors.add(new SourceEntryDescriptor(
                    source.path(), source.size(), mediaType(source.path()), digest.sha256()
            ));
            verifiedSources.add(new DigestCheckedEntrySource(source, digest.sha256()));
        }
        validateOpaqueSourceJson(verifiedSources);
        MinimapTileValidator.validateSourceEntries(definition, verifiedSources);

        SourceManifest input = definition.manifest();
        SourceManifest manifest = new SourceManifest(
                input.formatVersion(),
                input.documentId(),
                input.binding(),
                input.revision(),
                input.dimension(),
                input.provenance(),
                input.tileEdge(),
                descriptors
        );
        MinimapDefinition completed = new MinimapDefinition(
                manifest,
                definition.document(),
                definition.regions(),
                definition.connections(),
                definition.styles()
        );
        List<MinimapValidationIssue> issues = MinimapValidator.validate(completed);
        if (!issues.isEmpty()) {
            throw new ContainerValidationException("Source map validation failed: " + issues.get(0));
        }

        byte[] manifestBytes = CanonicalModelJson.write(
                manifest,
                MinimapModelCodecs.SOURCE_MANIFEST,
                extension(draft, MinimapContainerLayout.SOURCE_MANIFEST)
        );
        if (manifestBytes.length > MinimapHardLimits.MAX_SOURCE_MANIFEST_BYTES) {
            throw new ContainerValidationException("Source manifest exceeds its byte limit");
        }
        verifiedSources.add(new CanonicalZipWriter.Entry(
                MinimapContainerLayout.SOURCE_MANIFEST, manifestBytes
        ));
        return new PreparedSource(List.copyOf(verifiedSources), manifest);
    }

    private static PreservedExtensions extension(SourceMapDraft draft, ContainerPath path) {
        return draft.authorityExtensions().getOrDefault(path, PreservedExtensions.missing());
    }

    private static void validateExtensionPaths(Map<ContainerPath, PreservedExtensions> extensions) {
        for (Map.Entry<ContainerPath, PreservedExtensions> entry : extensions.entrySet()) {
            if (!GENERATED_PATHS.contains(entry.getKey())) {
                throw new ContainerValidationException(
                        "Extensions may be attached only to source authority JSON: " + entry.getKey()
                );
            }
            Objects.requireNonNull(entry.getValue(), "source authority extensions");
        }
    }

    private static CanonicalZipWriter.Entry jsonEntry(ContainerPath path, byte[] bytes) {
        return new CanonicalZipWriter.Entry(path, bytes);
    }

    private static void validateOpaqueSourceJson(
            List<? extends CanonicalZipWriter.EntrySource> sources
    ) {
        for (CanonicalZipWriter.EntrySource source : sources) {
            MinimapContainerLayout.SourceEntryKind kind = MinimapContainerLayout.classifySource(source.path())
                    .orElseThrow(() -> new ContainerValidationException("Invalid source path: " + source.path()));
            if (kind == MinimapContainerLayout.SourceEntryKind.THUMBNAIL) {
                try {
                    BoundedPngReader.decode(readEntryBytes(source));
                } catch (PngValidationException exception) {
                    throw new ContainerValidationException("Source thumbnail is not canonical", exception);
                }
                continue;
            }
            if (kind != MinimapContainerLayout.SourceEntryKind.GENERATORS
                    && kind != MinimapContainerLayout.SourceEntryKind.VECTORS) {
                continue;
            }
            if (source.size() > com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapHardLimits.MAX_JSON_ENTRY_BYTES
                    || source.size() > Integer.MAX_VALUE) {
                throw new ContainerValidationException("Opaque source JSON exceeds its byte limit");
            }
            byte[] bytes = readEntryBytes(source);
            try {
                if (!Arrays.equals(bytes, JcsCanonicalizer.canonicalize(bytes))) {
                    throw new ContainerValidationException("Opaque source JSON is not canonical");
                }
            } catch (CanonicalJsonException exception) {
                throw new ContainerValidationException("Opaque source JSON is invalid", exception);
            }
        }
    }

    private static byte[] readEntryBytes(CanonicalZipWriter.EntrySource source) {
        if (source.size() < 0
                || source.size() > com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapHardLimits.MAX_ZIP_ENTRY_BYTES
                || source.size() > Integer.MAX_VALUE) {
            throw new ContainerValidationException("Source entry exceeds its byte limit");
        }
        try (InputStream input = source.openStream()) {
            byte[] value = new byte[Math.toIntExact(source.size())];
            int offset = 0;
            while (offset < value.length) {
                int count = input.read(value, offset, value.length - offset);
                if (count < 0 || count == 0) {
                    throw new ContainerValidationException("Source entry ended or made no progress");
                }
                offset += count;
            }
            if (input.read() != -1) {
                throw new ContainerValidationException("Source entry exceeds its declared length");
            }
            return value;
        } catch (IOException exception) {
            throw new ContainerValidationException("Failed to read source JSON", exception);
        }
    }

    private static MediaType mediaType(ContainerPath path) {
        return path.value().endsWith(".png")
                ? MediaType.IMAGE_PNG
                : MediaType.APPLICATION_JSON;
    }

    private static SourceDigest scan(CanonicalZipWriter.EntrySource source) {
        long declaredSize = source.size();
        if (declaredSize < 0 || declaredSize > ContainerLimits.sourceHardLimits().maxEntryBytes()) {
            throw new ContainerValidationException("Source entry bytes exceed the hard limit");
        }
        try (InputStream input = Objects.requireNonNull(source.openStream(), "source entry stream")) {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            long remaining = declaredSize;
            while (remaining > 0) {
                int count = input.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                if (count < 0) {
                    throw new ContainerValidationException("Source entry ended before its declared length");
                }
                if (count == 0) {
                    throw new ContainerValidationException("Source entry stream made no read progress");
                }
                digest.update(buffer, 0, count);
                remaining -= count;
            }
            if (input.read() != -1) {
                throw new ContainerValidationException("Source entry exceeds its declared length");
            }
            return new SourceDigest(Sha256.parse(java.util.HexFormat.of().formatHex(digest.digest())));
        } catch (IOException exception) {
            throw new ContainerValidationException("Failed to read source entry", exception);
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record PreparedSource(
            List<? extends CanonicalZipWriter.EntrySource> entries,
            SourceManifest manifest
    ) {
    }

    private record SourceDigest(Sha256 sha256) {
    }

    private record FrozenMetadataEntrySource(
            CanonicalZipWriter.EntrySource delegate,
            ContainerPath path,
            long size
    ) implements CanonicalZipWriter.EntrySource {
        private FrozenMetadataEntrySource {
            Objects.requireNonNull(delegate, "delegate");
            Objects.requireNonNull(path, "path");
            if (size < 0 || size > ContainerLimits.sourceHardLimits().maxEntryBytes()) {
                throw new ContainerValidationException("Source entry bytes exceed the hard limit");
            }
        }

        private static FrozenMetadataEntrySource capture(
                CanonicalZipWriter.EntrySource source
        ) {
            return new FrozenMetadataEntrySource(
                    source,
                    Objects.requireNonNull(source.path(), "source entry path"),
                    source.size()
            );
        }

        @Override
        public InputStream openStream() throws IOException {
            verifyMetadata();
            InputStream input = Objects.requireNonNull(
                    delegate.openStream(), "source entry stream"
            );
            try {
                verifyMetadata();
                return input;
            } catch (RuntimeException exception) {
                try {
                    input.close();
                } catch (IOException closeException) {
                    exception.addSuppressed(closeException);
                }
                throw exception;
            }
        }

        private void verifyMetadata() {
            if (!path.equals(delegate.path()) || size != delegate.size()) {
                throw new ContainerValidationException(
                        "Source entry metadata changed while the container was being written"
                );
            }
        }
    }

    private record DigestCheckedEntrySource(
            CanonicalZipWriter.EntrySource delegate,
            Sha256 expectedSha256
    ) implements CanonicalZipWriter.EntrySource {
        private DigestCheckedEntrySource {
            Objects.requireNonNull(delegate, "delegate");
            Objects.requireNonNull(expectedSha256, "expectedSha256");
        }

        @Override
        public ContainerPath path() {
            return delegate.path();
        }

        @Override
        public long size() {
            return delegate.size();
        }

        @Override
        public InputStream openStream() throws IOException {
            InputStream input = Objects.requireNonNull(delegate.openStream(), "source entry stream");
            return new DigestCheckedInputStream(input, size(), expectedSha256);
        }
    }

    private static final class DigestCheckedInputStream extends InputStream {
        private final InputStream delegate;
        private final java.security.MessageDigest digest;
        private final Sha256 expected;
        private long remaining;
        private boolean verified;

        private DigestCheckedInputStream(InputStream delegate, long size, Sha256 expected) {
            this.delegate = delegate;
            this.remaining = size;
            this.expected = expected;
            try {
                this.digest = java.security.MessageDigest.getInstance("SHA-256");
            } catch (java.security.NoSuchAlgorithmException exception) {
                throw new IllegalStateException("SHA-256 is unavailable", exception);
            }
        }

        @Override
        public int read() throws IOException {
            byte[] one = new byte[1];
            int count = read(one, 0, 1);
            return count < 0 ? -1 : one[0] & 0xff;
        }

        @Override
        public int read(byte[] output, int offset, int length) throws IOException {
            Objects.checkFromIndexSize(offset, length, output.length);
            if (length == 0) {
                return 0;
            }
            if (remaining == 0) {
                verifyAtEnd();
                return -1;
            }
            int count = delegate.read(output, offset, (int) Math.min(length, remaining));
            if (count < 0) {
                throw new ContainerValidationException("Source entry changed to a shorter stream");
            }
            if (count == 0) {
                throw new ContainerValidationException("Source entry stream made no read progress");
            }
            digest.update(output, offset, count);
            remaining -= count;
            return count;
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }

        private void verifyAtEnd() throws IOException {
            if (verified) {
                return;
            }
            if (delegate.read() != -1) {
                throw new ContainerValidationException("Source entry changed to a longer stream");
            }
            Sha256 actual = Sha256.parse(java.util.HexFormat.of().formatHex(digest.digest()));
            if (!actual.equals(expected)) {
                throw new ContainerValidationException("Source entry changed after manifest hashing");
            }
            verified = true;
        }
    }
}
