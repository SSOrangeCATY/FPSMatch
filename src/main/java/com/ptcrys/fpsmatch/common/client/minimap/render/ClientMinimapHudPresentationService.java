package com.ptcrys.fpsmatch.common.client.minimap.render;

import com.ptcrys.fpsmatch.common.client.minimap.RuntimeGeneration;
import com.ptcrys.fpsmatch.common.client.minimap.cache.RuntimeEntryStore;
import com.ptcrys.fpsmatch.common.client.minimap.generated.GeneratedMinimapTile;
import com.ptcrys.fpsmatch.common.client.minimap.generated.GeneratedMinimapRuntimeBinding;
import com.ptcrys.fpsmatch.common.client.minimap.generated.GeneratedMinimapTileComposer;
import com.ptcrys.fpsmatch.common.client.minimap.tactical.TacticalMapPresentation;
import com.ptcrys.fpsmatch.common.client.minimap.tactical.TacticalMapState;
import com.ptcrys.fpsmatch.common.client.minimap.tactical.TacticalViewport;
import com.ptcrys.fpsmatch.core.minimap.extension.MinimapExtensionRegistry;
import com.ptcrys.fpsmatch.core.minimap.format.CanonicalJsonException;
import com.ptcrys.fpsmatch.core.minimap.format.ContainerValidationException;
import com.ptcrys.fpsmatch.core.minimap.format.MinimapContainerLayout;
import com.ptcrys.fpsmatch.core.minimap.format.RuntimeEntryValidation;
import com.ptcrys.fpsmatch.core.minimap.marker.MarkerSnapshot;
import com.ptcrys.fpsmatch.core.minimap.model.ContainerPath;
import com.ptcrys.fpsmatch.core.minimap.model.CanvasBounds;
import com.ptcrys.fpsmatch.core.minimap.model.CanvasRect;
import com.ptcrys.fpsmatch.core.minimap.model.RuntimeFloor;
import com.ptcrys.fpsmatch.core.minimap.model.RuntimeDefinition;
import com.ptcrys.fpsmatch.core.minimap.model.RuntimeRegion;
import com.ptcrys.fpsmatch.core.minimap.model.RuntimeManifest;
import com.ptcrys.fpsmatch.core.minimap.view.PlaceholderKind;
import com.ptcrys.fpsmatch.core.minimap.view.ShapeMode;
import com.ptcrys.fpsmatch.core.minimap.view.ViewportCamera;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.function.Consumer;

public final class ClientMinimapHudPresentationService {
    private final Supplier<Optional<RuntimeGeneration>> currentGeneration;
    private final Supplier<Optional<RuntimeEntryStore.ActiveRuntime>> activeRuntime;
    private final Supplier<List<MarkerSnapshot.Marker>> markers;
    private final MinimapTileUploadQueue uploads;
    private final MinecraftMinimapTextureManager textures;
    private final Supplier<List<GeneratedMinimapTile>> generatedTiles;
    private final Consumer<GeneratedMinimapRuntimeBinding> generatedBinding;
    private final RuntimeMinimapFramePlanner planner =
            new RuntimeMinimapFramePlanner();
    private final java.util.Map<String, Long> uploadedGeneratedRevisions =
            new java.util.LinkedHashMap<>();
    private RuntimeGeneration uploadedGeneratedGeneration;

    private RuntimeGeneration parsedGeneration;
    private RuntimeManifest parsedManifest;
    private RuntimeDefinition parsedDefinition;
    private MinimapClientSettings tacticalSourceSettings;
    private MinimapClientSettings tacticalProjectionSettings;

    public ClientMinimapHudPresentationService(
            Supplier<Optional<RuntimeGeneration>> currentGeneration,
            Supplier<Optional<RuntimeEntryStore.ActiveRuntime>> activeRuntime,
            Supplier<List<MarkerSnapshot.Marker>> markers,
            MinimapTileUploadQueue uploads,
            MinecraftMinimapTextureManager textures
    ) {
        this(
                currentGeneration,
                activeRuntime,
                markers,
                uploads,
                textures,
                List::of,
                ignored -> { }
        );
    }

    public ClientMinimapHudPresentationService(
            Supplier<Optional<RuntimeGeneration>> currentGeneration,
            Supplier<Optional<RuntimeEntryStore.ActiveRuntime>> activeRuntime,
            Supplier<List<MarkerSnapshot.Marker>> markers,
            MinimapTileUploadQueue uploads,
            MinecraftMinimapTextureManager textures,
            Supplier<List<GeneratedMinimapTile>> generatedTiles
    ) {
        this(
                currentGeneration,
                activeRuntime,
                markers,
                uploads,
                textures,
                generatedTiles,
                ignored -> { }
        );
    }

    public ClientMinimapHudPresentationService(
            Supplier<Optional<RuntimeGeneration>> currentGeneration,
            Supplier<Optional<RuntimeEntryStore.ActiveRuntime>> activeRuntime,
            Supplier<List<MarkerSnapshot.Marker>> markers,
            MinimapTileUploadQueue uploads,
            MinecraftMinimapTextureManager textures,
            Supplier<List<GeneratedMinimapTile>> generatedTiles,
            Consumer<GeneratedMinimapRuntimeBinding> generatedBinding
    ) {
        this.currentGeneration = Objects.requireNonNull(
                currentGeneration, "currentGeneration"
        );
        this.activeRuntime = Objects.requireNonNull(
                activeRuntime, "activeRuntime"
        );
        this.markers = Objects.requireNonNull(markers, "markers");
        this.uploads = Objects.requireNonNull(uploads, "uploads");
        this.textures = Objects.requireNonNull(textures, "textures");
        this.generatedTiles = Objects.requireNonNull(generatedTiles, "generatedTiles");
        this.generatedBinding = Objects.requireNonNull(generatedBinding, "generatedBinding");
    }

    public synchronized Optional<MinimapFrame> prepareFrame(
            MinimapViewerPose viewer,
            MinimapClientSettings settings,
            int viewportWidth,
            int viewportHeight
    ) {
        return prepareFrame(
                viewer,
                settings,
                viewportWidth,
                viewportHeight,
                Optional.empty()
        );
    }

    public synchronized Optional<MinimapFrame> prepareFrame(
            MinimapViewerPose viewer,
            MinimapClientSettings settings,
            int viewportWidth,
            int viewportHeight,
            Optional<PlaceholderKind> forcedPlaceholder
    ) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(forcedPlaceholder, "forcedPlaceholder");
        if (forcedPlaceholder.isPresent()) {
            return Optional.of(placeholderFrame(
                    settings,
                    viewportWidth,
                    viewportHeight,
                    forcedPlaceholder.orElseThrow()
            ));
        }
        AuthorityLoadResult<LoadedRuntime> load = loadRuntime();
        if (!load.ready()) {
            return load.placeholder().map(placeholder -> placeholderFrame(
                    settings, viewportWidth, viewportHeight, placeholder
            ));
        }
        LoadedRuntime loaded = load.value().orElseThrow();
        RuntimeGeneration generation = loaded.generation();
        RuntimeEntryStore.ActiveRuntime runtime = loaded.runtime();

        Set<ContainerPath> readyTiles = readyTiles(
                generation, runtime, Optional.empty(), viewer.y()
        );
        return Optional.of(planner.planHud(
                generation,
                loaded.definition(),
                readyTiles,
                viewer,
                markers.get(),
                settings,
                viewportWidth,
                viewportHeight
        ));
    }

    public synchronized Optional<TacticalMapPresentation> prepareTactical(
            MinimapViewerPose viewer,
            MinimapClientSettings settings,
            TacticalMapState state
    ) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(state, "state");
        MinimapClientSettings tacticalSettings = tacticalSettingsFor(settings);
        AuthorityLoadResult<LoadedRuntime> load = loadRuntime();
        if (!load.ready()) {
            return load.placeholder().map(placeholder -> placeholderTactical(
                    tacticalSettings, state, placeholder
            ));
        }
        LoadedRuntime loaded = load.value().orElseThrow();
        RuntimeGeneration generation = loaded.generation();
        RuntimeEntryStore.ActiveRuntime runtime = loaded.runtime();
        RuntimeDefinition definition = loaded.definition();
        RuntimeManifest manifest = definition.manifest();
        RuntimeMinimapFramePlanner.TacticalFloorResolution floorResolution =
                planner.resolveTacticalFloor(
                        generation, manifest, viewer.y(), state.floor()
                );
        Set<ContainerPath> readyTiles = readyTiles(
                generation, runtime, Optional.of(floorResolution.floor()), viewer.y()
        );
        List<MarkerSnapshot.Marker> markerSnapshot = List.copyOf(markers.get());
        MinimapFrame frame = planner.planTactical(
                generation,
                definition,
                readyTiles,
                viewer,
                markerSnapshot,
                tacticalSettings,
                state,
                floorResolution
        );
        RuntimeFloor selectedFloor = frame.floor().effectiveFloorId()
                .flatMap(id -> manifest.floors().stream()
                        .filter(floor -> floor.selection().id().equals(id))
                        .findFirst())
                .orElse(manifest.floors().get(0));
        TacticalViewport viewport = new TacticalViewport(
                manifest.canvas(),
                selectedFloor.contentBounds().orElseGet(
                        () -> canvasRect(manifest.canvas())
                ),
                state.viewportWidth(),
                state.viewportHeight()
        );
        List<TacticalMapPresentation.FloorOption> floors = manifest.floors()
                .stream()
                .map(floor -> new TacticalMapPresentation.FloorOption(
                        floor.selection().id(), floor.label()
                ))
                .toList();
        List<TacticalMapPresentation.LegendEntry> legend =
                MinimapExtensionRegistry.markerPresentations(generation.mapKey()).stream()
                .map(presentation -> new TacticalMapPresentation.LegendEntry(
                        presentation.typeId(), presentation.styleId(), presentation.label()
                ))
                .toList();
        List<TacticalMapPresentation.RegionDetail> regions = definition
                .regions().regions().stream()
                .map(ClientMinimapHudPresentationService::regionDetail)
                .toList();
        return Optional.of(new TacticalMapPresentation(
                frame, viewport, floors, legend, regions
        ));
    }

    public synchronized void reset() {
        uploads.reset();
        textures.reset();
        planner.reset();
        parsedGeneration = null;
        parsedManifest = null;
        parsedDefinition = null;
        tacticalSourceSettings = null;
        tacticalProjectionSettings = null;
        uploadedGeneratedRevisions.clear();
        uploadedGeneratedGeneration = null;
    }

    static MinimapClientSettings tacticalSettings(
            MinimapClientSettings hudSettings
    ) {
        return Objects.requireNonNull(hudSettings, "hudSettings")
                .withShape(ShapeMode.SQUARE)
                .withOpacity(1.0f)
                .withShowLabels(true);
    }

    private MinimapClientSettings tacticalSettingsFor(
            MinimapClientSettings hudSettings
    ) {
        if (hudSettings == tacticalSourceSettings) {
            return tacticalProjectionSettings;
        }
        // The planner caches settings by instance, so keep this immutable copy stable.
        tacticalSourceSettings = hudSettings;
        tacticalProjectionSettings = tacticalSettings(hudSettings);
        return tacticalProjectionSettings;
    }

    private AuthorityLoadResult<LoadedRuntime> loadRuntime() {
        Optional<RuntimeGeneration> current = currentGeneration.get();
        Optional<RuntimeEntryStore.ActiveRuntime> active = activeRuntime.get();
        if (current.isEmpty()) {
            return active.isEmpty()
                    ? AuthorityLoadResult.ineligible()
                    : AuthorityLoadResult.stale();
        }
        if (active.isEmpty()) {
            return AuthorityLoadResult.missing();
        }
        RuntimeGeneration generation = current.orElseThrow();
        RuntimeEntryStore.ActiveRuntime runtime = active.orElseThrow();
        if (!matches(runtime, generation)) {
            return AuthorityLoadResult.stale();
        }
        AuthorityLoadResult<RuntimeDefinition> definition = definition(
                generation, runtime
        );
        if (!definition.ready()) {
            return definition.unavailable();
        }
        return AuthorityLoadResult.ready(new LoadedRuntime(
                generation, runtime, definition.value().orElseThrow()
        ));
    }

    private AuthorityLoadResult<RuntimeManifest> manifest(
            RuntimeGeneration generation,
            RuntimeEntryStore.ActiveRuntime runtime
    ) {
        if (generation.equals(parsedGeneration) && parsedManifest != null) {
            return AuthorityLoadResult.ready(parsedManifest);
        }
        Optional<byte[]> bytes = runtime.entry(
                MinimapContainerLayout.RUNTIME_MANIFEST.value()
        );
        if (bytes.isEmpty()) {
            return AuthorityLoadResult.missing();
        }
        try {
            RuntimeManifest manifest = RuntimeEntryValidation.readManifest(
                    bytes.orElseThrow()
            ).value();
            if (!manifest.binding().equals(generation.mapKey())
                    || !manifest.documentId().equals(generation.documentId())
                    || manifest.publishRevision() != generation.revision()) {
                return AuthorityLoadResult.stale();
            }
            parsedGeneration = generation;
            parsedManifest = manifest;
            parsedDefinition = null;
            return AuthorityLoadResult.ready(manifest);
        } catch (ContainerValidationException | CanonicalJsonException
                 | IllegalArgumentException invalid) {
            return AuthorityLoadResult.invalid();
        }
    }

    private AuthorityLoadResult<RuntimeDefinition> definition(
            RuntimeGeneration generation,
            RuntimeEntryStore.ActiveRuntime runtime
    ) {
        if (generation.equals(parsedGeneration) && parsedDefinition != null) {
            return AuthorityLoadResult.ready(parsedDefinition);
        }
        AuthorityLoadResult<RuntimeManifest> manifest = manifest(
                generation, runtime
        );
        if (!manifest.ready()) {
            return manifest.unavailable();
        }
        Optional<byte[]> regions = runtime.entry(
                MinimapContainerLayout.RUNTIME_REGIONS.value()
        );
        Optional<byte[]> connections = runtime.entry(
                MinimapContainerLayout.CONNECTIONS.value()
        );
        Optional<byte[]> styles = runtime.entry(
                MinimapContainerLayout.RUNTIME_STYLES.value()
        );
        if (regions.isEmpty() || connections.isEmpty() || styles.isEmpty()) {
            return AuthorityLoadResult.missing();
        }
        try {
            parsedDefinition = RuntimeEntryValidation.readDefinition(
                    manifest.value().orElseThrow(),
                    regions.orElseThrow(),
                    connections.orElseThrow(),
                    styles.orElseThrow()
            );
            return AuthorityLoadResult.ready(parsedDefinition);
        } catch (ContainerValidationException | CanonicalJsonException
                 | IllegalArgumentException invalid) {
            return AuthorityLoadResult.invalid();
        }
    }

    private Set<ContainerPath> readyTiles(
            RuntimeGeneration generation,
            RuntimeEntryStore.ActiveRuntime runtime,
            Optional<RuntimeFloor> preferredFloor,
            double viewerY
    ) {
        Set<ContainerPath> readyTiles = new LinkedHashSet<>();
        Set<String> desiredGeneratedKeys = new LinkedHashSet<>();
        if (uploadedGeneratedGeneration != null
                && !uploadedGeneratedGeneration.equals(generation)) {
            textures.clearGenerated();
            uploadedGeneratedRevisions.clear();
        }
        uploadedGeneratedGeneration = generation;
        RuntimeManifest manifest = parsedManifest;
        Optional<RuntimeFloor> activeFloor = preferredFloor.isPresent()
                ? preferredFloor
                : planner.resolveHudFloor(generation, manifest, viewerY);
        Set<String> staticTextureKeys = new LinkedHashSet<>();
        for (RuntimeEntryStore.ActiveEntry entry : runtime.entries().values()) {
            ContainerPath path;
            try {
                path = ContainerPath.parse(entry.key().stablePath());
            } catch (IllegalArgumentException invalidPath) {
                continue;
            }
            if (MinimapContainerLayout.parseRuntimeTile(path).isEmpty()) {
                continue;
            }
            String textureKey = path.value();
            if (!textures.hasStaticRecord(textureKey, generation)) {
                uploads.request(generation, textureKey, entry.payload());
            }
            if (textures.isVisibleStatic(textureKey, generation)
                    && textures.resolve(textureKey).isPresent()) {
                staticTextureKeys.add(textureKey);
                readyTiles.add(path);
            }
        }
        if (activeFloor.isPresent()) {
            RuntimeFloor floor = activeFloor.orElseThrow();
            int activeZoom = generatedZoom(runtime, floor);
            generatedBinding.accept(new GeneratedMinimapRuntimeBinding(
                    generation, floor, activeZoom, manifest.tileEdge()
            ));
            List<GeneratedMinimapTile> scopedGeneratedTiles = generatedTiles.get().stream()
                    .filter(tile -> tile.key().dimension().equals(generation.dimension()))
                    .filter(tile -> tile.floorId().equals(floor.selection().id()))
                    .filter(tile -> tile.zoom() == activeZoom)
                    .toList();
            List<GeneratedMinimapTileComposer.ComposedTile> composed =
                    GeneratedMinimapTileComposer.compose(
                            scopedGeneratedTiles,
                            floor,
                            new CanvasBounds(manifest.canvas().width(),
                                    manifest.canvas().height()),
                            manifest.tileEdge(),
                            activeZoom,
                            staticTextureKeys
                    );
            for (GeneratedMinimapTileComposer.ComposedTile tile : composed) {
                String textureKey = tile.textureKey();
                desiredGeneratedKeys.add(textureKey);
                if (!Objects.equals(
                        uploadedGeneratedRevisions.get(textureKey), tile.signature()
                ) || textures.resolve(textureKey).isEmpty()
                        || !textures.isGenerated(textureKey, generation)) {
                    textures.invalidateGenerated(textureKey, generation);
                    if (textures.uploadGenerated(
                            textureKey, tile.width(), tile.height(),
                            tile.rgba(), generation
                    ).isPresent()) {
                        uploadedGeneratedRevisions.put(textureKey, tile.signature());
                    }
                }
                if (textures.resolve(textureKey).isPresent()) {
                    readyTiles.add(ContainerPath.parse(textureKey));
                }
            }
        }
        for (String textureKey : new LinkedHashSet<>(uploadedGeneratedRevisions.keySet())) {
            if (!desiredGeneratedKeys.contains(textureKey)) {
                textures.invalidateGenerated(textureKey, generation);
                uploadedGeneratedRevisions.remove(textureKey);
            }
        }
        return Set.copyOf(readyTiles);
    }

    private static int generatedZoom(
            RuntimeEntryStore.ActiveRuntime runtime,
            RuntimeFloor floor
    ) {
        int highest = -1;
        for (RuntimeEntryStore.ActiveEntry entry : runtime.entries().values()) {
            try {
                ContainerPath path = ContainerPath.parse(entry.key().stablePath());
                var address = MinimapContainerLayout.parseRuntimeTile(path).orElse(null);
                if (address != null
                        && address.floorId().equals(floor.selection().id())) {
                    highest = Math.max(highest, address.zoom());
                }
            } catch (IllegalArgumentException ignored) {
                // Invalid entry paths are rejected by the normal runtime parser.
            }
        }
        // A floor with no authored tiles still needs a stable generated identity.
        // Use its highest manifest-supported zoom rather than inheriting zoom 0
        // from an unrelated floor's static tile set.
        return highest < 0
                ? floor.zoomLevels() - 1
                : Math.min(highest, floor.zoomLevels() - 1);
    }

    private static boolean matches(
            RuntimeEntryStore.ActiveRuntime active,
            RuntimeGeneration generation
    ) {
        return active.serverIdentity().equals(generation.serverIdentity())
                && active.dimension().equals(generation.dimension())
                && active.mapKey().equals(generation.mapKey())
                && active.documentId().equals(generation.documentId())
                && active.revision() == generation.revision()
                && active.runtimeHash().equals(generation.runtimeHash());
    }

    private static MinimapFrame placeholderFrame(
            MinimapClientSettings settings,
            int viewportWidth,
            int viewportHeight,
            PlaceholderKind placeholder
    ) {
        MinimapClientSettings clamped = settings.clamp();
        return MinimapFrame.builder()
                .camera(ViewportCamera.fixedNorth(
                        0, 0, 1, viewportWidth, viewportHeight
                ))
                .shape(clamped.shape())
                .backgroundOpacity(
                        clamped.backgroundOpacity() * clamped.opacity()
                )
                .placeholder(placeholder)
                .build();
    }

    private static TacticalMapPresentation placeholderTactical(
            MinimapClientSettings settings,
            TacticalMapState state,
            PlaceholderKind placeholder
    ) {
        CanvasBounds canvas = new CanvasBounds(1, 1);
        TacticalViewport viewport = new TacticalViewport(
                canvas,
                new CanvasRect(0, 0, 1, 1),
                state.viewportWidth(),
                state.viewportHeight()
        );
        return new TacticalMapPresentation(
                placeholderFrame(
                        settings,
                        state.viewportWidth(),
                        state.viewportHeight(),
                        placeholder
                ),
                viewport,
                List.of(),
                List.of()
        );
    }

    private record LoadedRuntime(
            RuntimeGeneration generation,
            RuntimeEntryStore.ActiveRuntime runtime,
            RuntimeDefinition definition
    ) {
        private LoadedRuntime {
            Objects.requireNonNull(generation, "generation");
            Objects.requireNonNull(runtime, "runtime");
            Objects.requireNonNull(definition, "definition");
        }
    }

    /** Keeps pending authority data distinct from stale or invalid authority. */
    private record AuthorityLoadResult<T>(
            AuthorityState state,
            Optional<T> value
    ) {
        private AuthorityLoadResult {
            Objects.requireNonNull(state, "state");
            value = Objects.requireNonNull(value, "value");
            if ((state == AuthorityState.READY) != value.isPresent()) {
                throw new IllegalArgumentException(
                        "Only a ready authority result may carry a value"
                );
            }
        }

        private static <T> AuthorityLoadResult<T> ready(T value) {
            return new AuthorityLoadResult<>(
                    AuthorityState.READY,
                    Optional.of(Objects.requireNonNull(value, "value"))
            );
        }

        private static <T> AuthorityLoadResult<T> ineligible() {
            return unavailable(AuthorityState.INELIGIBLE);
        }

        private static <T> AuthorityLoadResult<T> missing() {
            return unavailable(AuthorityState.MISSING);
        }

        private static <T> AuthorityLoadResult<T> stale() {
            return unavailable(AuthorityState.STALE);
        }

        private static <T> AuthorityLoadResult<T> invalid() {
            return unavailable(AuthorityState.INVALID);
        }

        private static <T> AuthorityLoadResult<T> unavailable(
                AuthorityState state
        ) {
            return new AuthorityLoadResult<>(state, Optional.empty());
        }

        private boolean ready() {
            return state == AuthorityState.READY;
        }

        private Optional<PlaceholderKind> placeholder() {
            return switch (state) {
                case READY, INELIGIBLE -> Optional.empty();
                case MISSING -> Optional.of(PlaceholderKind.LOADING);
                case STALE -> Optional.of(PlaceholderKind.STALE);
                case INVALID -> Optional.of(PlaceholderKind.ERROR);
            };
        }

        private <R> AuthorityLoadResult<R> unavailable() {
            if (ready()) {
                throw new IllegalStateException(
                        "A ready authority result cannot drop its value"
                );
            }
            return unavailable(state);
        }
    }

    private enum AuthorityState {
        READY,
        INELIGIBLE,
        MISSING,
        STALE,
        INVALID
    }

    private static CanvasRect canvasRect(CanvasBounds canvas) {
        return new CanvasRect(0, 0, canvas.width(), canvas.height());
    }

    private static TacticalMapPresentation.RegionDetail regionDetail(
            RuntimeRegion region
    ) {
        return new TacticalMapPresentation.RegionDetail(
                region.id(),
                region.floorId(),
                region.label(),
                region.geometry(),
                region.semanticType(),
                region.tags(),
                region.gameplayReference(),
                region.styleId(),
                region.priority()
        );
    }
}
