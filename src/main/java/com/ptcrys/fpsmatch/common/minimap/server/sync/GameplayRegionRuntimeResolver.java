package com.ptcrys.fpsmatch.common.minimap.server.sync;

import com.ptcrys.fpsmatch.core.minimap.codec.MinimapModelCodecs;
import com.ptcrys.fpsmatch.core.minimap.format.CanonicalModelJson;
import com.ptcrys.fpsmatch.core.minimap.format.MinimapContainerLayout;
import com.ptcrys.fpsmatch.core.minimap.format.RuntimeEntryValidation;
import com.ptcrys.fpsmatch.core.minimap.format.Sha256Digest;
import com.ptcrys.fpsmatch.core.minimap.model.ContainerPath;
import com.ptcrys.fpsmatch.core.minimap.model.MapKey;
import com.ptcrys.fpsmatch.core.minimap.model.RuntimeEntryDescriptor;
import com.ptcrys.fpsmatch.core.minimap.model.RuntimeManifest;
import com.ptcrys.fpsmatch.core.minimap.model.RuntimeRegionsFile;
import com.ptcrys.fpsmatch.core.minimap.model.RuntimeStyle;
import com.ptcrys.fpsmatch.core.minimap.model.RuntimeStylesFile;
import com.ptcrys.fpsmatch.core.minimap.region.MinimapRegionProvider;
import com.ptcrys.fpsmatch.core.minimap.region.RegionPresentation;
import com.ptcrys.fpsmatch.core.minimap.region.RuntimeRegionDescriptor;
import com.ptcrys.fpsmatch.core.minimap.region.RuntimeRegionMerger;
import com.ptcrys.fpsmatch.core.minimap.wire.WireIdentity;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Adds map-scoped gameplay regions without rebuilding or rewriting the rest
 * of a committed runtime container.
 */
public final class GameplayRegionRuntimeResolver
        implements ServerMinimapRuntimeRouter.RuntimeResolver {
    private final ServerMinimapRuntimeRouter.RuntimeResolver delegate;
    private final Function<MapKey, List<MinimapRegionProvider>> providers;
    private final Function<MapKey, List<RegionPresentation>> presentations;

    public GameplayRegionRuntimeResolver(
            ServerMinimapRuntimeRouter.RuntimeResolver delegate,
            Function<MapKey, List<MinimapRegionProvider>> providers
    ) {
        this(delegate, providers, mapKey -> List.of());
    }

    public GameplayRegionRuntimeResolver(
            ServerMinimapRuntimeRouter.RuntimeResolver delegate,
            Function<MapKey, List<MinimapRegionProvider>> providers,
            Function<MapKey, List<RegionPresentation>> presentations
    ) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.providers = Objects.requireNonNull(providers, "providers");
        this.presentations = Objects.requireNonNull(presentations, "presentations");
    }

    @Override
    public Optional<RuntimeMapSource> resolve(
            java.util.UUID actorId,
            WireIdentity.MapTarget target
    ) {
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(target, "target");
        Optional<RuntimeMapSource> resolved = delegate.resolve(actorId, target);
        if (resolved.isEmpty()) {
            return Optional.empty();
        }
        RuntimeMapSource source = resolved.orElseThrow();
        try {
            List<MinimapRegionProvider> regionProviders = List.copyOf(Objects.requireNonNull(
                    providers.apply(target.mapKey()), "region providers"
            ));
            if (regionProviders.isEmpty()) {
                return Optional.of(source);
            }

            byte[] manifestBytes = source.manifestBytes();
            CanonicalModelJson.Document<RuntimeManifest> manifest =
                    RuntimeEntryValidation.readManifest(manifestBytes);
            verifyIdentity(source.identity(), target, manifestBytes, manifest.value());
            String defaultFloor = manifest.value().floors().get(0).selection().id();
            java.util.ArrayList<RuntimeRegionDescriptor> gameplay = new java.util.ArrayList<>();
            for (MinimapRegionProvider provider : regionProviders) {
                gameplay.addAll(Objects.requireNonNull(
                        provider.collect(target.mapKey(), defaultFloor), "gameplay regions"
                ));
            }
            if (gameplay.isEmpty()) {
                return Optional.of(source);
            }

            RuntimeEntryDescriptor originalDescriptor = manifest.value().entries().stream()
                    .filter(entry -> entry.path().equals(MinimapContainerLayout.RUNTIME_REGIONS))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeMapUnavailableException(
                            "Published runtime has no regions entry"
                    ));
            byte[] originalRegions = readDeclared(source, originalDescriptor);
            RuntimeEntryValidation.validateEntry(
                    manifest.value(), MinimapContainerLayout.RUNTIME_REGIONS, originalRegions
            );
            CanonicalModelJson.Document<RuntimeRegionsFile> regions = CanonicalModelJson.read(
                    originalRegions, MinimapModelCodecs.RUNTIME_REGIONS, java.util.Set.of("regions")
            );
            RuntimeRegionMerger.MergeResult mergeResult =
                    RuntimeRegionMerger.mergeWithPresentations(
                            manifest.value(), regions.value(), gameplay,
                            List.copyOf(Objects.requireNonNull(
                                    presentations.apply(target.mapKey()),
                                    "region presentations"
                            ))
            );
            RuntimeRegionsFile merged = mergeResult.regions();
            byte[] mergedRegions = CanonicalModelJson.write(
                    merged, MinimapModelCodecs.RUNTIME_REGIONS, regions.extensions()
            );
            if (Arrays.equals(originalRegions, mergedRegions)) {
                return Optional.of(source);
            }

            byte[] mergedStyles = mergeStyles(
                    source, manifest.value(), mergeResult.usedPresentations()
            );

            RuntimeEntryDescriptor regionsDescriptor = new RuntimeEntryDescriptor(
                    MinimapContainerLayout.RUNTIME_REGIONS,
                    mergedRegions.length, Sha256Digest.of(mergedRegions)
            );
            RuntimeEntryDescriptor stylesDescriptor = mergedStyles == null
                    ? null
                    : new RuntimeEntryDescriptor(
                    MinimapContainerLayout.RUNTIME_STYLES,
                    mergedStyles.length, Sha256Digest.of(mergedStyles)
            );

            List<RuntimeEntryDescriptor> descriptors = manifest.value().entries().stream()
                    .map(entry -> entry.path().equals(MinimapContainerLayout.RUNTIME_REGIONS)
                            ? regionsDescriptor
                            : stylesDescriptor != null
                            && entry.path().equals(MinimapContainerLayout.RUNTIME_STYLES)
                            ? stylesDescriptor
                            : entry)
                    .toList();
            RuntimeManifest derivedManifest = new RuntimeManifest(
                    manifest.value().formatVersion(), manifest.value().documentId(),
                    manifest.value().binding(), manifest.value().publishRevision(),
                    manifest.value().sourceHash(), manifest.value().compilerProfile(),
                    manifest.value().canvas(), manifest.value().defaultViewMode(),
                    manifest.value().floors(), manifest.value().tileEdge(), descriptors
            );
            byte[] derivedManifestBytes = CanonicalModelJson.write(
                    derivedManifest, MinimapModelCodecs.RUNTIME_MANIFEST, manifest.extensions()
            );
            RuntimeManifest validatedManifest = RuntimeEntryValidation.readManifest(
                    derivedManifestBytes
            ).value();
            validateDerivedDefinition(
                    source, validatedManifest, mergedRegions, mergedStyles
            );
            WireIdentity.RuntimeIdentity identity = new WireIdentity.RuntimeIdentity(
                    source.identity().binding(), source.identity().revision(),
                    Sha256Digest.of(derivedManifestBytes), Optional.empty()
            );
            return Optional.of(new OverlaySource(
                    source, identity, derivedManifestBytes,
                    regionsDescriptor, mergedRegions,
                    stylesDescriptor, mergedStyles
            ));
        } catch (RuntimeMapUnavailableException unavailable) {
            closeAfterFailure(source, unavailable);
            throw unavailable;
        } catch (IOException | RuntimeException unavailable) {
            closeAfterFailure(source, unavailable);
            throw new RuntimeMapUnavailableException(
                    "gameplay regions are unavailable", unavailable
            );
        }
    }

    private static void verifyIdentity(
            WireIdentity.RuntimeIdentity identity,
            WireIdentity.MapTarget target,
            byte[] manifestBytes,
            RuntimeManifest manifest
    ) {
        if (!identity.binding().target().equals(target)
                || !identity.binding().documentId().equals(manifest.documentId())
                || identity.revision() != manifest.publishRevision()
                || !identity.runtimeHash().equals(Sha256Digest.of(manifestBytes))) {
            throw new RuntimeMapUnavailableException(
                    "Committed runtime identity does not match its manifest"
            );
        }
        if (!manifest.binding().equals(target.mapKey())) {
            throw new RuntimeMapUnavailableException("Runtime manifest binding does not match target");
        }
    }

    private static byte[] readDeclared(
            RuntimeMapSource source,
            RuntimeEntryDescriptor descriptor
    ) throws IOException {
        if (descriptor.byteLength() > Integer.MAX_VALUE) {
            throw new IOException("Runtime entry is too large: " + descriptor.path());
        }
        try (InputStream input = source.openEntry(descriptor.path())) {
            byte[] bytes = input.readNBytes((int) descriptor.byteLength());
            if (bytes.length != descriptor.byteLength() || input.read() != -1) {
                throw new IOException("Runtime regions entry length changed while reading");
            }
            return bytes;
        }
    }

    private static byte[] mergeStyles(
            RuntimeMapSource source,
            RuntimeManifest manifest,
            List<RegionPresentation> usedPresentations
    ) throws IOException {
        if (usedPresentations.isEmpty()) {
            return null;
        }
        RuntimeEntryDescriptor descriptor = manifest.entries().stream()
                .filter(entry -> entry.path().equals(
                        MinimapContainerLayout.RUNTIME_STYLES))
                .findFirst()
                .orElseThrow(() -> new RuntimeMapUnavailableException(
                        "Published runtime has no styles entry"
                ));
        byte[] originalBytes = readDeclared(source, descriptor);
        RuntimeEntryValidation.validateEntry(
                manifest, MinimapContainerLayout.RUNTIME_STYLES, originalBytes
        );
        CanonicalModelJson.Document<RuntimeStylesFile> document = CanonicalModelJson.read(
                originalBytes, MinimapModelCodecs.RUNTIME_STYLES,
                java.util.Set.of("styles")
        );
        java.util.ArrayList<RuntimeStyle> styles = new java.util.ArrayList<>(
                document.value().styles()
        );
        java.util.LinkedHashMap<com.ptcrys.fpsmatch.core.minimap.model.NamespacedId,
                RuntimeStyle> byId = new java.util.LinkedHashMap<>();
        for (RuntimeStyle style : styles) {
            if (byId.putIfAbsent(style.id(), style) != null) {
                throw new IllegalArgumentException(
                        "Duplicate runtime style id: " + style.id()
                );
            }
        }
        for (RegionPresentation presentation : usedPresentations) {
            RuntimeStyle existing = byId.get(presentation.styleId());
            if (existing == null) {
                RuntimeStyle added = new RuntimeStyle(
                        presentation.styleId(), Optional.of(presentation.label()),
                        Optional.empty()
                );
                byId.put(added.id(), added);
                styles.add(added);
            } else if (!existing.label().equals(Optional.of(presentation.label()))) {
                throw new IllegalArgumentException(
                        "Conflicting runtime style label for extension region presentation: "
                                + presentation.styleId()
                );
            }
        }
        byte[] mergedBytes = CanonicalModelJson.write(
                new RuntimeStylesFile(styles), MinimapModelCodecs.RUNTIME_STYLES,
                document.extensions()
        );
        return Arrays.equals(originalBytes, mergedBytes) ? null : mergedBytes;
    }

    private static void validateDerivedDefinition(
            RuntimeMapSource source,
            RuntimeManifest manifest,
            byte[] regionsBytes,
            byte[] overlaidStylesBytes
    ) throws IOException {
        RuntimeEntryDescriptor connectionsDescriptor = manifest.entries().stream()
                .filter(entry -> entry.path().equals(MinimapContainerLayout.CONNECTIONS))
                .findFirst()
                .orElseThrow(() -> new RuntimeMapUnavailableException(
                        "Published runtime has no connections entry"
                ));
        RuntimeEntryDescriptor stylesDescriptor = manifest.entries().stream()
                .filter(entry -> entry.path().equals(MinimapContainerLayout.RUNTIME_STYLES))
                .findFirst()
                .orElseThrow(() -> new RuntimeMapUnavailableException(
                        "Published runtime has no styles entry"
                ));
        byte[] connectionsBytes = readDeclared(source, connectionsDescriptor);
        byte[] stylesBytes = overlaidStylesBytes == null
                ? readDeclared(source, stylesDescriptor)
                : overlaidStylesBytes;

        RuntimeEntryValidation.validateEntry(
                manifest, MinimapContainerLayout.RUNTIME_REGIONS, regionsBytes
        );
        RuntimeEntryValidation.validateEntry(
                manifest, MinimapContainerLayout.CONNECTIONS, connectionsBytes
        );
        RuntimeEntryValidation.validateEntry(
                manifest, MinimapContainerLayout.RUNTIME_STYLES, stylesBytes
        );
        // Dynamic overlays can violate aggregate constraints even when each entry is valid.
        RuntimeEntryValidation.readDefinition(
                manifest, regionsBytes, connectionsBytes, stylesBytes
        );
    }

    private static void closeAfterFailure(RuntimeMapSource source, Throwable failure) {
        try {
            source.close();
        } catch (IOException closeFailure) {
            failure.addSuppressed(closeFailure);
        }
    }

    private static final class OverlaySource implements RuntimeMapSource {
        private final RuntimeMapSource delegate;
        private final WireIdentity.RuntimeIdentity identity;
        private final byte[] manifestBytes;
        private final RuntimeEntryDescriptor regionsDescriptor;
        private final byte[] regionsBytes;
        private final RuntimeEntryDescriptor stylesDescriptor;
        private final byte[] stylesBytes;

        private OverlaySource(
                RuntimeMapSource delegate,
                WireIdentity.RuntimeIdentity identity,
                byte[] manifestBytes,
                RuntimeEntryDescriptor regionsDescriptor,
                byte[] regionsBytes,
                RuntimeEntryDescriptor stylesDescriptor,
                byte[] stylesBytes
        ) {
            this.delegate = delegate;
            this.identity = identity;
            this.manifestBytes = manifestBytes.clone();
            this.regionsDescriptor = regionsDescriptor;
            this.regionsBytes = regionsBytes.clone();
            this.stylesDescriptor = stylesDescriptor;
            this.stylesBytes = stylesBytes == null ? null : stylesBytes.clone();
        }

        @Override
        public WireIdentity.RuntimeIdentity identity() {
            return identity;
        }

        @Override
        public byte[] manifestBytes() {
            return manifestBytes.clone();
        }

        @Override
        public Optional<RuntimeEntryDescriptor> descriptor(ContainerPath path) {
            if (path.equals(MinimapContainerLayout.RUNTIME_REGIONS)) {
                return Optional.of(regionsDescriptor);
            }
            if (stylesDescriptor != null
                    && path.equals(MinimapContainerLayout.RUNTIME_STYLES)) {
                return Optional.of(stylesDescriptor);
            }
            return delegate.descriptor(path);
        }

        @Override
        public InputStream openEntry(ContainerPath path) throws IOException {
            if (path.equals(MinimapContainerLayout.RUNTIME_REGIONS)) {
                return new ByteArrayInputStream(regionsBytes);
            }
            if (stylesBytes != null
                    && path.equals(MinimapContainerLayout.RUNTIME_STYLES)) {
                return new ByteArrayInputStream(stylesBytes);
            }
            return delegate.openEntry(path);
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }
}
