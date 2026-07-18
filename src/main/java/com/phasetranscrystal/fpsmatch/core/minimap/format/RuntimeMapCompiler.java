package com.phasetranscrystal.fpsmatch.core.minimap.format;

import com.phasetranscrystal.fpsmatch.core.minimap.codec.MinimapModelCodecs;
import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapFormatContract;
import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapHardLimits;
import com.phasetranscrystal.fpsmatch.core.minimap.model.AffineTransform2D;
import com.phasetranscrystal.fpsmatch.core.minimap.model.CompilerProfile;
import com.phasetranscrystal.fpsmatch.core.minimap.model.ContainerPath;
import com.phasetranscrystal.fpsmatch.core.minimap.model.IconStyle;
import com.phasetranscrystal.fpsmatch.core.minimap.model.LineStyle;
import com.phasetranscrystal.fpsmatch.core.minimap.model.MapKey;
import com.phasetranscrystal.fpsmatch.core.minimap.model.MinimapDefinition;
import com.phasetranscrystal.fpsmatch.core.minimap.model.MinimapRegion;
import com.phasetranscrystal.fpsmatch.core.minimap.model.MinimapStyle;
import com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId;
import com.phasetranscrystal.fpsmatch.core.minimap.model.RegionStyle;
import com.phasetranscrystal.fpsmatch.core.minimap.model.RuntimeDefinition;
import com.phasetranscrystal.fpsmatch.core.minimap.model.RuntimeFloor;
import com.phasetranscrystal.fpsmatch.core.minimap.model.RuntimeManifest;
import com.phasetranscrystal.fpsmatch.core.minimap.model.RuntimeRegion;
import com.phasetranscrystal.fpsmatch.core.minimap.model.RuntimeRegionsFile;
import com.phasetranscrystal.fpsmatch.core.minimap.model.RuntimeStyle;
import com.phasetranscrystal.fpsmatch.core.minimap.model.RuntimeStylesFile;
import com.phasetranscrystal.fpsmatch.core.minimap.model.RuntimeEntryDescriptor;
import com.phasetranscrystal.fpsmatch.core.minimap.model.Sha256;
import com.phasetranscrystal.fpsmatch.core.minimap.model.SourceFloor;
import com.phasetranscrystal.fpsmatch.core.minimap.model.SourceManifest;
import com.phasetranscrystal.fpsmatch.core.minimap.model.StyleType;
import com.phasetranscrystal.fpsmatch.core.minimap.model.TextAppearance;
import com.phasetranscrystal.fpsmatch.core.minimap.model.TextStyle;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class RuntimeMapCompiler {
    private RuntimeMapCompiler() {
    }

    public static CompiledMapPair compile(SourceMap source, RuntimeCompileRequest request) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(request, "request");
        SourceManifest sourceManifest = source.manifest();
        if (sourceManifest.formatVersion().major() != MinimapFormatContract.CURRENT.major()) {
            throw new ContainerValidationException("Unsupported source format major");
        }
        if (request.publishRevision() != sourceManifest.revision()) {
            throw new ContainerValidationException(
                    "Runtime publish revision must match the source manifest revision"
            );
        }
        if (request.documentId() != null
                && !request.documentId().equals(sourceManifest.documentId())) {
            throw new ContainerValidationException(
                    "Runtime document ID must match the source manifest"
            );
        }
        if (request.binding() != null && !request.binding().equals(sourceManifest.binding())) {
            throw new ContainerValidationException(
                    "Runtime map binding must match the source manifest"
            );
        }
        Sha256 sourceHash = source.sourceHash();

        NamespacedId documentId = request.documentId() == null
                ? sourceManifest.documentId() : request.documentId();
        MapKey binding = request.binding() == null ? sourceManifest.binding() : request.binding();
        int tileEdge = request.tileEdge() == 0 ? sourceManifest.tileEdge() : request.tileEdge();

        List<RuntimeFloor> floors = new ArrayList<>();
        for (SourceFloor floor : source.definition().document().floors()) {
            AffineTransform2D transform;
            try {
                transform = floor.calibration().fit().transform();
            } catch (RuntimeException exception) {
                throw new ContainerValidationException("Unable to fit source floor calibration", exception);
            }
            floors.add(new RuntimeFloor(
                    floor.selection(), floor.label(), floor.contentBounds(), transform,
                    request.zoomLevels()
            ));
        }

        ResolvedStyles resolved = resolveStyles(source.definition());
        List<RuntimeRegion> runtimeRegions = new ArrayList<>();
        for (MinimapRegion region : source.definition().regions().regions()) {
            NamespacedId resolvedStyle = resolved.regionStyleIds().get(region.id());
            if (resolvedStyle == null) {
                throw new ContainerValidationException("Region has no resolvable region style: " + region.id());
            }
            runtimeRegions.add(new RuntimeRegion(
                    region.id(), region.floorId(), region.label(), region.geometry(),
                    region.semanticType(), region.tags(), region.gameplayReference(), resolvedStyle,
                    region.labelAnchor(), region.priority(), region.minVisibleScale(), region.maxVisibleScale()
            ));
        }

        RuntimeStylesFile runtimeStyles = new RuntimeStylesFile(resolved.runtimeStyles());
        RuntimeRegionsFile runtimeRegionsFile = new RuntimeRegionsFile(runtimeRegions);

        List<CanonicalZipWriter.EntrySource> content = new ArrayList<>();
        content.add(new CanonicalZipWriter.Entry(
                MinimapContainerLayout.RUNTIME_REGIONS,
                CanonicalModelJson.write(runtimeRegionsFile, MinimapModelCodecs.RUNTIME_REGIONS)
        ));
        content.add(new CanonicalZipWriter.Entry(
                MinimapContainerLayout.CONNECTIONS,
                CanonicalModelJson.write(
                        source.definition().connections(), MinimapModelCodecs.CONNECTIONS
                )
        ));
        content.add(new CanonicalZipWriter.Entry(
                MinimapContainerLayout.RUNTIME_STYLES,
                CanonicalModelJson.write(runtimeStyles, MinimapModelCodecs.RUNTIME_STYLES)
        ));

        Set<ContainerPath> paths = new HashSet<>();
        for (CanonicalZipWriter.EntrySource tile : request.tileEntries()) {
            Objects.requireNonNull(tile, "runtime tile");
            ContainerPath path = Objects.requireNonNull(tile.path(), "runtime tile path");
            if (MinimapContainerLayout.classifyRuntime(path).orElse(null)
                    != MinimapContainerLayout.RuntimeEntryKind.FLOOR_TILE) {
                throw new ContainerValidationException("Runtime compiler accepts only floor tile paths: " + path);
            }
            if (!paths.add(path)) {
                throw new ContainerValidationException("Duplicate runtime entry path: " + path);
            }
            content.add(tile);
        }
        request.thumbnail().ifPresent(thumbnail -> {
            if (!thumbnail.path().equals(MinimapContainerLayout.THUMBNAIL)) {
                throw new ContainerValidationException("Runtime thumbnail path must be thumbnail.png");
            }
            if (!paths.add(thumbnail.path())) {
                throw new ContainerValidationException("Duplicate runtime thumbnail path");
            }
            content.add(thumbnail);
        });

        Set<ContainerPath> fixed = Set.of(
                MinimapContainerLayout.RUNTIME_REGIONS,
                MinimapContainerLayout.CONNECTIONS,
                MinimapContainerLayout.RUNTIME_STYLES
        );
        for (ContainerPath path : fixed) {
            if (!paths.add(path)) {
                throw new ContainerValidationException("Runtime entry collides with authority path: " + path);
            }
        }

        content.sort(Comparator.comparing(entry -> entry.path().value()));
        List<RuntimeEntryDescriptor> descriptors = new ArrayList<>();
        List<CanonicalZipWriter.EntrySource> verified = new ArrayList<>();
        for (CanonicalZipWriter.EntrySource entry : content) {
            SourceDigest digest = scan(entry);
            descriptors.add(new RuntimeEntryDescriptor(entry.path(), entry.size(), digest.sha256()));
            verified.add(new DigestCheckedEntrySource(entry, digest.sha256()));
        }

        RuntimeManifest manifest = new RuntimeManifest(
                sourceManifest.formatVersion(), documentId, binding, request.publishRevision(),
                sourceHash, request.compilerProfile(), source.definition().document().canvas(),
                source.definition().document().defaultViewMode(), floors, tileEdge, descriptors
        );
        MinimapTileValidator.validateRuntimeCoverageBudget(manifest);
        RuntimeDefinition definition = new RuntimeDefinition(
                manifest, runtimeRegionsFile, source.definition().connections(), runtimeStyles
        );
        List<MinimapValidationIssue> issues = MinimapValidator.validate(definition);
        if (!issues.isEmpty()) {
            throw new ContainerValidationException("Runtime map validation failed: " + issues.get(0));
        }

        byte[] manifestBytes = CanonicalModelJson.write(
                manifest, MinimapModelCodecs.RUNTIME_MANIFEST
        );
        if (manifestBytes.length > MinimapHardLimits.MAX_RUNTIME_MANIFEST_BYTES) {
            throw new ContainerValidationException("Runtime manifest exceeds its byte limit");
        }
        verified.add(new CanonicalZipWriter.Entry(
                MinimapContainerLayout.RUNTIME_MANIFEST, manifestBytes
        ));
        byte[] runtimeBytes = CanonicalZipWriter.writeSources(
                verified, ContainerLimits.runtimeHardLimits()
        );

        RuntimeDefinition decoded;
        byte[] exactManifest;
        try (RuntimeMap runtime = RuntimeMapReader.read(runtimeBytes)) {
            decoded = runtime.definition();
            exactManifest = runtime.manifestBytes();
        } catch (IOException exception) {
            throw new ContainerValidationException("Failed to close compiled runtime map", exception);
        } catch (RuntimeException exception) {
            if (exception instanceof ContainerValidationException validationException) {
                throw validationException;
            }
            throw new ContainerValidationException("Compiled runtime map failed validation", exception);
        }
        Sha256 runtimeHash = Sha256Digest.of(exactManifest);
        Sha256 runtimeContainerHash = Sha256Digest.of(runtimeBytes);
        return new CompiledMapPair(
                sourceManifest, decoded, runtimeBytes, exactManifest,
                sourceHash, runtimeHash, runtimeContainerHash
        );
    }

    private static ResolvedStyles resolveStyles(MinimapDefinition definition) {
        Map<NamespacedId, MinimapStyle> styles = new HashMap<>();
        for (MinimapStyle style : definition.styles().styles()) {
            if (styles.put(style.id(), style) != null) {
                throw new ContainerValidationException("Duplicate style ID: " + style.id());
            }
        }

        Map<String, NamespacedId> regionStyleIds = new HashMap<>();
        Map<Sha256, ResolvedAppearance> byHash = new HashMap<>();
        List<RuntimeStyle> generated = new ArrayList<>();
        for (MinimapRegion region : definition.regions().regions()) {
            MinimapStyle style = styles.get(region.styleId());
            if (!(style instanceof RegionStyle regionStyle)) {
                throw new ContainerValidationException("Region style is not a region style: " + region.styleId());
            }
            TextAppearance appearance = region.styleOverride().label().orElse(regionStyle.label());
            byte[] appearanceBytes = CanonicalModelJson.write(
                    appearance, MinimapModelCodecs.TEXT_APPEARANCE
            );
            Sha256 hash = Sha256Digest.of(appearanceBytes);
            ResolvedAppearance existing = byHash.get(hash);
            if (existing != null && !java.util.Arrays.equals(existing.bytes(), appearanceBytes)) {
                throw new ContainerValidationException("Resolved region style hash collision");
            }
            NamespacedId resolvedId = existing == null
                    ? NamespacedId.parse("fpsmatch:resolved-region/" + hash.value())
                    : existing.id();
            if (existing == null) {
                byHash.put(hash, new ResolvedAppearance(resolvedId, appearanceBytes));
                generated.add(new RuntimeStyle(resolvedId, Optional.of(appearance), Optional.empty()));
            }
            regionStyleIds.put(region.id(), resolvedId);
        }

        for (MinimapStyle style : definition.styles().styles()) {
            RuntimeStyle runtime = switch (style.type()) {
                case REGION -> null;
                case LINE -> null;
                case TEXT -> {
                    TextStyle text = (TextStyle) style;
                    yield new RuntimeStyle(style.id(), Optional.of(text.text()), Optional.empty());
                }
                case ICON -> {
                    IconStyle icon = (IconStyle) style;
                    yield new RuntimeStyle(style.id(), Optional.empty(), Optional.of(icon.icon()));
                }
            };
            if (runtime != null) {
                generated.add(runtime);
            }
        }
        generated.sort(Comparator.comparing(style -> style.id().toString()));
        Map<NamespacedId, RuntimeStyle> unique = new LinkedHashMap<>();
        for (RuntimeStyle style : generated) {
            RuntimeStyle previous = unique.putIfAbsent(style.id(), style);
            if (previous != null && !previous.equals(style)) {
                throw new ContainerValidationException("Runtime style ID collision: " + style.id());
            }
        }
        return new ResolvedStyles(regionStyleIds, List.copyOf(unique.values()));
    }

    private static SourceDigest scan(CanonicalZipWriter.EntrySource source) {
        long size = source.size();
        if (size < 0 || size > ContainerLimits.runtimeHardLimits().maxEntryBytes()) {
            throw new ContainerValidationException("Runtime entry exceeds the hard limit");
        }
        try (InputStream input = Objects.requireNonNull(source.openStream(), "runtime entry stream")) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            long remaining = size;
            while (remaining > 0) {
                int count = input.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                if (count < 0) {
                    throw new ContainerValidationException("Runtime entry ended before declared length");
                }
                if (count == 0) {
                    throw new ContainerValidationException("Runtime entry stream made no progress");
                }
                digest.update(buffer, 0, count);
                remaining -= count;
            }
            if (input.read() != -1) {
                throw new ContainerValidationException("Runtime entry exceeds declared length");
            }
            return new SourceDigest(Sha256.parse(java.util.HexFormat.of().formatHex(digest.digest())));
        } catch (IOException exception) {
            throw new ContainerValidationException("Failed to read runtime entry", exception);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record ResolvedStyles(
            Map<String, NamespacedId> regionStyleIds,
            List<RuntimeStyle> runtimeStyles
    ) {
    }

    private record ResolvedAppearance(NamespacedId id, byte[] bytes) {
        private ResolvedAppearance {
            bytes = bytes.clone();
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }
    }

    private record SourceDigest(Sha256 sha256) {
    }

    private record DigestCheckedEntrySource(
            CanonicalZipWriter.EntrySource delegate,
            Sha256 expectedSha256
    ) implements CanonicalZipWriter.EntrySource {
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
            return new DigestInputStream(delegate.openStream(), size(), expectedSha256);
        }
    }

    private static final class DigestInputStream extends InputStream {
        private final InputStream delegate;
        private final MessageDigest digest;
        private final Sha256 expected;
        private long remaining;
        private boolean verified;

        private DigestInputStream(InputStream delegate, long remaining, Sha256 expected) {
            this.delegate = Objects.requireNonNull(delegate, "delegate");
            this.remaining = remaining;
            this.expected = expected;
            try {
                this.digest = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException exception) {
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
                verify();
                return -1;
            }
            int count = delegate.read(output, offset, (int) Math.min(length, remaining));
            if (count < 0 || count == 0) {
                throw new ContainerValidationException("Runtime entry changed during compilation");
            }
            digest.update(output, offset, count);
            remaining -= count;
            return count;
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }

        private void verify() throws IOException {
            if (verified) {
                return;
            }
            if (delegate.read() != -1) {
                throw new ContainerValidationException("Runtime entry grew during compilation");
            }
            Sha256 actual = Sha256.parse(java.util.HexFormat.of().formatHex(digest.digest()));
            if (!actual.equals(expected)) {
                throw new ContainerValidationException("Runtime entry changed during compilation");
            }
            verified = true;
        }
    }
}
