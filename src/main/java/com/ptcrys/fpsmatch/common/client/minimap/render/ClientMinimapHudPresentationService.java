package com.ptcrys.fpsmatch.common.client.minimap.render;

import com.ptcrys.fpsmatch.common.client.minimap.RuntimeGeneration;
import com.ptcrys.fpsmatch.common.client.minimap.cache.RuntimeEntryStore;
import com.ptcrys.fpsmatch.common.client.minimap.tactical.TacticalMapPresentation;
import com.ptcrys.fpsmatch.common.client.minimap.tactical.TacticalMapState;
import com.ptcrys.fpsmatch.common.client.minimap.tactical.TacticalViewport;
import com.ptcrys.fpsmatch.core.minimap.extension.MinimapExtensionRegistry;
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
import com.ptcrys.fpsmatch.core.minimap.view.ViewportCamera;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

public final class ClientMinimapHudPresentationService {
    private final Supplier<Optional<RuntimeGeneration>> currentGeneration;
    private final Supplier<Optional<RuntimeEntryStore.ActiveRuntime>> activeRuntime;
    private final Supplier<List<MarkerSnapshot.Marker>> markers;
    private final MinimapTileUploadQueue uploads;
    private final MinecraftMinimapTextureManager textures;
    private final RuntimeMinimapFramePlanner planner =
            new RuntimeMinimapFramePlanner();

    private RuntimeGeneration parsedGeneration;
    private RuntimeManifest parsedManifest;
    private RuntimeDefinition parsedDefinition;

    public ClientMinimapHudPresentationService(
            Supplier<Optional<RuntimeGeneration>> currentGeneration,
            Supplier<Optional<RuntimeEntryStore.ActiveRuntime>> activeRuntime,
            Supplier<List<MarkerSnapshot.Marker>> markers,
            MinimapTileUploadQueue uploads,
            MinecraftMinimapTextureManager textures
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
    }

    public synchronized Optional<MinimapFrame> prepareFrame(
            MinimapViewerPose viewer,
            MinimapClientSettings settings,
            int viewportWidth,
            int viewportHeight
    ) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(settings, "settings");
        Optional<RuntimeGeneration> current = currentGeneration.get();
        Optional<RuntimeEntryStore.ActiveRuntime> active = activeRuntime.get();
        if (current.isEmpty()) {
            return Optional.empty();
        }
        if (active.isEmpty()) {
            return Optional.of(loadingFrame(
                    settings, viewportWidth, viewportHeight
            ));
        }
        if (!matches(active.orElseThrow(), current.orElseThrow())) {
            return Optional.empty();
        }
        RuntimeGeneration generation = current.orElseThrow();
        RuntimeEntryStore.ActiveRuntime runtime = active.orElseThrow();
        Optional<RuntimeDefinition> definition = definition(generation, runtime);
        if (definition.isEmpty()) {
            return Optional.empty();
        }

        Set<ContainerPath> readyTiles = new LinkedHashSet<>();
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
            if (textures.resolve(textureKey).isEmpty()) {
                uploads.request(generation, textureKey, entry.payload());
            }
            if (textures.resolve(textureKey).isPresent()) {
                readyTiles.add(path);
            }
        }
        return Optional.of(planner.planHud(
                generation,
                definition.orElseThrow(),
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
        Optional<RuntimeGeneration> current = currentGeneration.get();
        Optional<RuntimeEntryStore.ActiveRuntime> active = activeRuntime.get();
        if (current.isEmpty()) {
            return Optional.empty();
        }
        if (active.isEmpty()) {
            return Optional.of(loadingTactical(settings, state));
        }
        RuntimeGeneration generation = current.orElseThrow();
        RuntimeEntryStore.ActiveRuntime runtime = active.orElseThrow();
        if (!matches(runtime, generation)) {
            return Optional.empty();
        }
        Optional<RuntimeDefinition> parsed = definition(generation, runtime);
        if (parsed.isEmpty()) {
            return Optional.empty();
        }
        RuntimeDefinition definition = parsed.orElseThrow();
        RuntimeManifest manifest = definition.manifest();
        Set<ContainerPath> readyTiles = readyTiles(generation, runtime);
        List<MarkerSnapshot.Marker> markerSnapshot = List.copyOf(markers.get());
        MinimapFrame frame = planner.planTactical(
                generation,
                definition,
                readyTiles,
                viewer,
                markerSnapshot,
                settings,
                state
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
    }

    private Optional<RuntimeManifest> manifest(
            RuntimeGeneration generation,
            RuntimeEntryStore.ActiveRuntime runtime
    ) {
        if (generation.equals(parsedGeneration) && parsedManifest != null) {
            return Optional.of(parsedManifest);
        }
        Optional<byte[]> bytes = runtime.entry(
                MinimapContainerLayout.RUNTIME_MANIFEST.value()
        );
        if (bytes.isEmpty()) {
            return Optional.empty();
        }
        try {
            RuntimeManifest manifest = RuntimeEntryValidation.readManifest(
                    bytes.orElseThrow()
            ).value();
            if (!manifest.binding().equals(generation.mapKey())
                    || !manifest.documentId().equals(generation.documentId())
                    || manifest.publishRevision() != generation.revision()) {
                return Optional.empty();
            }
            parsedGeneration = generation;
            parsedManifest = manifest;
            parsedDefinition = null;
            return Optional.of(manifest);
        } catch (ContainerValidationException | IllegalArgumentException invalid) {
            return Optional.empty();
        }
    }

    private Optional<RuntimeDefinition> definition(
            RuntimeGeneration generation,
            RuntimeEntryStore.ActiveRuntime runtime
    ) {
        if (generation.equals(parsedGeneration) && parsedDefinition != null) {
            return Optional.of(parsedDefinition);
        }
        Optional<RuntimeManifest> manifest = manifest(generation, runtime);
        if (manifest.isEmpty()) {
            return Optional.empty();
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
            return Optional.empty();
        }
        try {
            parsedDefinition = RuntimeEntryValidation.readDefinition(
                    manifest.orElseThrow(),
                    regions.orElseThrow(),
                    connections.orElseThrow(),
                    styles.orElseThrow()
            );
            return Optional.of(parsedDefinition);
        } catch (ContainerValidationException | IllegalArgumentException invalid) {
            return Optional.empty();
        }
    }

    private Set<ContainerPath> readyTiles(
            RuntimeGeneration generation,
            RuntimeEntryStore.ActiveRuntime runtime
    ) {
        Set<ContainerPath> readyTiles = new LinkedHashSet<>();
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
            if (textures.resolve(textureKey).isEmpty()) {
                uploads.request(generation, textureKey, entry.payload());
            }
            if (textures.resolve(textureKey).isPresent()) {
                readyTiles.add(path);
            }
        }
        return Set.copyOf(readyTiles);
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

    private static MinimapFrame loadingFrame(
            MinimapClientSettings settings,
            int viewportWidth,
            int viewportHeight
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
                .placeholder(PlaceholderKind.LOADING)
                .build();
    }

    private static TacticalMapPresentation loadingTactical(
            MinimapClientSettings settings,
            TacticalMapState state
    ) {
        CanvasBounds canvas = new CanvasBounds(1, 1);
        TacticalViewport viewport = new TacticalViewport(
                canvas,
                new CanvasRect(0, 0, 1, 1),
                state.viewportWidth(),
                state.viewportHeight()
        );
        return new TacticalMapPresentation(
                loadingFrame(
                        settings, state.viewportWidth(), state.viewportHeight()
                ),
                viewport,
                List.of(),
                List.of()
        );
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
