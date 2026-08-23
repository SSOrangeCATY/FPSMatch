package com.ptcrys.fpsmatch.common.client.minimap.render;

import com.ptcrys.fpsmatch.common.client.minimap.RuntimeGeneration;
import com.ptcrys.fpsmatch.common.client.minimap.tactical.TacticalMapState;
import com.ptcrys.fpsmatch.common.client.minimap.tactical.TacticalViewport;
import com.ptcrys.fpsmatch.core.minimap.format.MinimapContainerLayout;
import com.ptcrys.fpsmatch.core.minimap.marker.MarkerSnapshot;
import com.ptcrys.fpsmatch.core.minimap.model.*;
import com.ptcrys.fpsmatch.core.minimap.model.PolygonGeometry;
import com.ptcrys.fpsmatch.core.minimap.model.RectangleGeometry;
import com.ptcrys.fpsmatch.core.minimap.model.RgbaColor;
import com.ptcrys.fpsmatch.core.minimap.model.TextAppearance;
import com.ptcrys.fpsmatch.core.minimap.model.Vector2D;
import com.ptcrys.fpsmatch.core.minimap.model.WorldPoint2D;
import com.ptcrys.fpsmatch.core.minimap.view.FloorViewState;
import com.ptcrys.fpsmatch.core.minimap.view.LabelCandidate;
import com.ptcrys.fpsmatch.core.minimap.view.LabelCollisionResolver;
import com.ptcrys.fpsmatch.core.minimap.view.MapDrawCommand;
import com.ptcrys.fpsmatch.core.minimap.view.PlaceholderKind;
import com.ptcrys.fpsmatch.core.minimap.view.ViewportCamera;
import com.ptcrys.fpsmatch.core.minimap.view.FloorViewMode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class RuntimeMinimapFramePlanner {
    private final MinimapHudRenderer renderer = new MinimapHudRenderer();
    private RuntimeGeneration currentGeneration;
    private AutoFloorState automaticFloor = AutoFloorState.None.INSTANCE;
    private RuntimeGeneration tacticalGeneration;
    private AutoFloorState tacticalAutomaticFloor = AutoFloorState.None.INSTANCE;
    private HudCacheKey hudCacheKey;
    private MinimapFrame cachedHudFrame;
    private TacticalCacheKey tacticalCacheKey;
    private MinimapFrame cachedTacticalFrame;

    public MinimapFrame planHud(
            RuntimeGeneration generation,
            RuntimeManifest manifest,
            Set<ContainerPath> availablePaths,
            MinimapViewerPose viewer,
            List<MarkerSnapshot.Marker> markers,
            MinimapClientSettings settings,
            int viewportWidth,
            int viewportHeight
    ) {
        return planHud(
                generation, manifest, null, availablePaths, viewer, markers,
                settings, viewportWidth, viewportHeight
        );
    }

    public MinimapFrame planHud(
            RuntimeGeneration generation,
            RuntimeDefinition definition,
            Set<ContainerPath> availablePaths,
            MinimapViewerPose viewer,
            List<MarkerSnapshot.Marker> markers,
            MinimapClientSettings settings,
            int viewportWidth,
            int viewportHeight
    ) {
        Objects.requireNonNull(definition, "definition");
        return planHud(
                generation,
                definition.manifest(),
                definition,
                availablePaths,
                viewer,
                markers,
                settings,
                viewportWidth,
                viewportHeight
        );
    }

    private MinimapFrame planHud(
            RuntimeGeneration generation,
            RuntimeManifest manifest,
            RuntimeDefinition definition,
            Set<ContainerPath> availablePaths,
            MinimapViewerPose viewer,
            List<MarkerSnapshot.Marker> markers,
            MinimapClientSettings settings,
            int viewportWidth,
            int viewportHeight
    ) {
        Objects.requireNonNull(generation, "generation");
        Objects.requireNonNull(manifest, "manifest");
        Objects.requireNonNull(availablePaths, "availablePaths");
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(markers, "markers");
        Objects.requireNonNull(settings, "settings");
        if (viewportWidth <= 0 || viewportHeight <= 0 || manifest.floors().isEmpty()) {
            throw new IllegalArgumentException("Viewport and runtime floors must be non-empty");
        }
        if (hudCacheKey != null && hudCacheKey.matches(
                generation, manifest, definition, availablePaths, viewer,
                markers, settings, viewportWidth, viewportHeight
        )) {
            return cachedHudFrame;
        }

        boolean revisionChanged = !generation.equals(currentGeneration);
        automaticFloor = new AutoFloorSelector(
                manifest.floors().stream().map(RuntimeFloor::selection).toList()
        ).resolve(automaticFloor, viewer.y(), revisionChanged);
        currentGeneration = generation;

        RuntimeFloor fallback = manifest.floors().get(0);
        if (!(automaticFloor instanceof AutoFloorState.Floor selected)) {
            return cacheHud(
                    generation, manifest, definition, availablePaths, viewer,
                    markers, settings, viewportWidth, viewportHeight,
                    renderer.compose(
                    settings,
                    fullMapCamera(
                            manifest.canvas(), fallback,
                            viewportWidth, viewportHeight
                    ),
                    FloorViewState.automatic(fallback.selection().id()),
                    List.of(),
                    PlaceholderKind.ERROR
                    )
            );
        }
        RuntimeFloor floor = manifest.floors().stream()
                .filter(candidate -> candidate.selection().id().equals(selected.floorId()))
                .findFirst()
                .orElse(fallback);
        ViewportCamera camera = camera(
                manifest, floor, viewer, settings,
                viewportWidth, viewportHeight
        );
        PlannedCommands planned = commands(
                manifest, floor, availablePaths, markers,
                Set.copyOf(settings.markerFilter()), settings,
                definition, camera
        );
        return cacheHud(
                generation, manifest, definition, availablePaths, viewer,
                markers, settings, viewportWidth, viewportHeight,
                renderer.compose(
                        settings,
                        camera,
                        FloorViewState.automatic(floor.selection().id()),
                        planned.commands(),
                        planned.hasTiles() ? null : PlaceholderKind.LOADING
                )
        );
    }

    private MinimapFrame cacheHud(
            RuntimeGeneration generation,
            RuntimeManifest manifest,
            RuntimeDefinition definition,
            Set<ContainerPath> availablePaths,
            MinimapViewerPose viewer,
            List<MarkerSnapshot.Marker> markers,
            MinimapClientSettings settings,
            int viewportWidth,
            int viewportHeight,
            MinimapFrame frame
    ) {
        hudCacheKey = new HudCacheKey(
                generation,
                manifest,
                definition,
                Set.copyOf(availablePaths),
                viewer,
                List.copyOf(markers),
                settings,
                viewportWidth,
                viewportHeight
        );
        cachedHudFrame = frame;
        return frame;
    }

    public MinimapFrame planTactical(
            RuntimeGeneration generation,
            RuntimeManifest manifest,
            Set<ContainerPath> availablePaths,
            MinimapViewerPose viewer,
            List<MarkerSnapshot.Marker> markers,
            MinimapClientSettings settings,
            TacticalMapState state
    ) {
        return planTactical(
                generation, manifest, null, availablePaths, viewer, markers,
                settings, state
        );
    }

    public MinimapFrame planTactical(
            RuntimeGeneration generation,
            RuntimeDefinition definition,
            Set<ContainerPath> availablePaths,
            MinimapViewerPose viewer,
            List<MarkerSnapshot.Marker> markers,
            MinimapClientSettings settings,
            TacticalMapState state
    ) {
        Objects.requireNonNull(definition, "definition");
        return planTactical(
                generation,
                definition.manifest(),
                definition,
                availablePaths,
                viewer,
                markers,
                settings,
                state
        );
    }

    private MinimapFrame planTactical(
            RuntimeGeneration generation,
            RuntimeManifest manifest,
            RuntimeDefinition definition,
            Set<ContainerPath> availablePaths,
            MinimapViewerPose viewer,
            List<MarkerSnapshot.Marker> markers,
            MinimapClientSettings settings,
            TacticalMapState state
    ) {
        Objects.requireNonNull(generation, "generation");
        Objects.requireNonNull(manifest, "manifest");
        Objects.requireNonNull(availablePaths, "availablePaths");
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(markers, "markers");
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(state, "state");
        if (manifest.floors().isEmpty()) {
            throw new IllegalArgumentException("Runtime floors must be non-empty");
        }
        if (tacticalCacheKey != null && tacticalCacheKey.matches(
                generation, manifest, definition, availablePaths, viewer,
                markers, settings, state
        )) {
            return cachedTacticalFrame;
        }

        boolean revisionChanged = !generation.equals(tacticalGeneration);
        tacticalAutomaticFloor = new AutoFloorSelector(
                manifest.floors().stream().map(RuntimeFloor::selection).toList()
        ).resolve(tacticalAutomaticFloor, viewer.y(), revisionChanged);
        tacticalGeneration = generation;

        RuntimeFloor fallback = manifest.floors().get(0);
        String automaticId = tacticalAutomaticFloor instanceof AutoFloorState.Floor floor
                ? floor.floorId()
                : fallback.selection().id();
        FloorViewState floorState = state.floor().withAutomaticFloor(automaticId);
        RuntimeFloor selectedFloor = floorState.effectiveFloorId()
                .flatMap(id -> manifest.floors().stream()
                        .filter(floor -> floor.selection().id().equals(id))
                        .findFirst())
                .orElse(fallback);
        if (floorState.mode()
                == FloorViewMode.MANUAL
                && !selectedFloor.selection().id().equals(
                floorState.effectiveFloorId().orElse(automaticId)
        )) {
            floorState = FloorViewState.automatic(automaticId);
        }

        TacticalViewport viewport = new TacticalViewport(
                manifest.canvas(),
                selectedFloor.contentBounds().orElseGet(
                        () -> canvasRect(manifest.canvas())
                ),
                state.viewportWidth(),
                state.viewportHeight()
        );
        ViewportCamera camera = switch (state.fitMode()) {
            case ALL -> viewport.fitAll();
            case FLOOR -> viewport.fitFloor();
            case NONE -> viewport.constrain(ViewportCamera.fixedNorth(
                    state.panX(), state.panY(), state.zoom(),
                    state.viewportWidth(), state.viewportHeight()
            ));
        };
        Set<String> hiddenTypes = new java.util.HashSet<>(settings.markerFilter());
        hiddenTypes.addAll(state.hiddenMarkerTypes());
        PlannedCommands planned = commands(
                manifest, selectedFloor, availablePaths, markers,
                Set.copyOf(hiddenTypes), settings, definition, camera
        );
        return cacheTactical(
                generation, manifest, definition, availablePaths, viewer,
                markers, settings, state,
                renderer.compose(
                        settings,
                        camera,
                        floorState,
                        planned.commands(),
                        planned.hasTiles() ? null : PlaceholderKind.LOADING
                )
        );
    }

    private MinimapFrame cacheTactical(
            RuntimeGeneration generation,
            RuntimeManifest manifest,
            RuntimeDefinition definition,
            Set<ContainerPath> availablePaths,
            MinimapViewerPose viewer,
            List<MarkerSnapshot.Marker> markers,
            MinimapClientSettings settings,
            TacticalMapState state,
            MinimapFrame frame
    ) {
        tacticalCacheKey = new TacticalCacheKey(
                generation,
                manifest,
                definition,
                Set.copyOf(availablePaths),
                viewer,
                List.copyOf(markers),
                settings,
                state
        );
        cachedTacticalFrame = frame;
        return frame;
    }

    public void reset() {
        currentGeneration = null;
        automaticFloor = AutoFloorState.None.INSTANCE;
        tacticalGeneration = null;
        tacticalAutomaticFloor = AutoFloorState.None.INSTANCE;
        hudCacheKey = null;
        cachedHudFrame = null;
        tacticalCacheKey = null;
        cachedTacticalFrame = null;
    }

    private static CanvasRect canvasRect(CanvasBounds canvas) {
        return new CanvasRect(0, 0, canvas.width(), canvas.height());
    }

    private static PlannedCommands commands(
            RuntimeManifest manifest,
            RuntimeFloor floor,
            Set<ContainerPath> availablePaths,
            List<MarkerSnapshot.Marker> markers,
            Set<String> hiddenTypes,
            MinimapClientSettings settings,
            RuntimeDefinition definition,
            ViewportCamera camera
    ) {
        ArrayList<MapDrawCommand> commands = new ArrayList<>();
        List<TileAddress> tiles = availableTiles(
                floor.selection().id(), availablePaths
        );
        if (!tiles.isEmpty()) {
            int zoom = tiles.stream().mapToInt(TileAddress::zoom).max().orElseThrow();
            double scale = Math.scalb(1.0, zoom);
            tiles.stream()
                    .filter(tile -> tile.zoom() == zoom)
                    .sorted(Comparator.comparingInt(TileAddress::y)
                            .thenComparingInt(TileAddress::x))
                    .forEach(tile -> commands.add(tileCommand(
                            tile, manifest.canvas(), manifest.tileEdge(), scale
                    )));
        }
        if (definition != null) {
            commands.addAll(regionCommands(definition, floor, camera));
        }
        Map<String, Integer> floorOrder = new HashMap<>();
        for (int index = 0; index < manifest.floors().size(); index++) {
            floorOrder.put(
                    manifest.floors().get(index).selection().id(), index
            );
        }
        int activeFloorIndex = floorOrder.get(floor.selection().id());
        for (MarkerSnapshot.Marker marker : MarkerSnapshot.of(markers).markers()) {
            if (hiddenTypes.contains(marker.typeId().toString())) {
                continue;
            }
            String markerFloor = marker.floorSlug().orElse(floor.selection().id());
            boolean adjacent = false;
            float markerOpacity = 1f;
            if (!markerFloor.equals(floor.selection().id())) {
                Integer markerFloorIndex = floorOrder.get(markerFloor);
                if (settings.adjacentFloorMarkerStyle()
                        == AdjacentFloorMarkerStyle.HIDDEN
                        || markerFloorIndex == null
                        || Math.abs(markerFloorIndex - activeFloorIndex) != 1) {
                    continue;
                }
                adjacent = true;
                markerOpacity = 0.55f;
            }
            CanvasPoint point = floor.worldToCanvas().transform(
                    new WorldPoint2D(marker.x(), marker.z())
            );
            float canvasYaw = canvasYaw(floor.worldToCanvas().transformVector(
                    forwardX(marker.yaw()), forwardZ(marker.yaw())
            ));
            commands.add(new MapDrawCommand.MarkerIcon(
                    marker.markerId().toString(),
                    marker.typeId(),
                    marker.styleId(),
                    point.u(), point.v(),
                    canvasYaw, markerOpacity, adjacent
            ));
        }
        return new PlannedCommands(List.copyOf(commands), !tiles.isEmpty());
    }

    private static List<MapDrawCommand> regionCommands(
            RuntimeDefinition definition,
            RuntimeFloor floor,
            ViewportCamera camera
    ) {
        Map<NamespacedId,
                RuntimeStyle> styles = new HashMap<>();
        for (RuntimeStyle style : definition.styles().styles()) {
            styles.put(style.id(), style);
        }
        List<RuntimeRegion> visible = definition.regions().regions().stream()
                .filter(region -> region.floorId().equals(
                        floor.selection().id()
                ))
                .filter(region -> camera.zoom() >= region.minVisibleScale()
                        && camera.zoom() <= region.maxVisibleScale())
                .filter(region -> intersectsViewport(region, camera))
                .sorted(Comparator
                        .comparingInt(RuntimeRegion::priority)
                        .reversed()
                        .thenComparing(RuntimeRegion::id))
                .toList();

        ArrayList<MapDrawCommand> result = new ArrayList<>();
        Map<String, LabelRender> labels = new HashMap<>();
        ArrayList<LabelCandidate> candidates = new ArrayList<>();
        for (RuntimeRegion region : visible) {
            result.add(new MapDrawCommand.RegionOutline(
                    region.id(), geometryPoints(region), 0.85f
            ));
            RuntimeStyle style = styles.get(region.styleId());
            TextAppearance appearance = style == null
                    ? null
                    : style.label().orElse(null);
            if (appearance == null) {
                continue;
            }
            DisplayLabel label = region.label();
            double width = Math.max(
                    8.0,
                    label.value().length() * 6.0 * appearance.scale()
            );
            double height = 9.0 * appearance.scale();
            var projected = camera.projectCanvas(
                    region.labelAnchor().u(),
                    region.labelAnchor().v(),
                    0f
            );
            candidates.add(new LabelCandidate(
                    region.id(),
                    projected.canvasX() - width * 0.5,
                    projected.canvasY() - height * 0.5,
                    width,
                    height,
                    region.priority()
            ));
            labels.put(region.id(), new LabelRender(
                    region,
                    appearance
            ));
        }
        for (LabelCandidate accepted : LabelCollisionResolver.resolve(candidates)) {
            LabelRender render = labels.get(accepted.id());
            result.add(new MapDrawCommand.Label(
                    render.region().label(),
                    render.region().labelAnchor().u(),
                    render.region().labelAnchor().v(),
                    argb(render.appearance().color()),
                    render.appearance().scale(),
                    1f
            ));
        }
        return List.copyOf(result);
    }

    private static boolean intersectsViewport(
            RuntimeRegion region,
            ViewportCamera camera
    ) {
        double[] points = geometryPoints(region);
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (int index = 0; index < points.length; index += 2) {
            var projected = camera.projectCanvas(
                    points[index], points[index + 1], 0f
            );
            minX = Math.min(minX, projected.canvasX());
            minY = Math.min(minY, projected.canvasY());
            maxX = Math.max(maxX, projected.canvasX());
            maxY = Math.max(maxY, projected.canvasY());
        }
        double halfWidth = camera.viewportWidth() * 0.5;
        double halfHeight = camera.viewportHeight() * 0.5;
        return maxX >= -halfWidth
                && minX <= halfWidth
                && maxY >= -halfHeight
                && minY <= halfHeight;
    }

    private static double[] geometryPoints(RuntimeRegion region) {
        if (region.geometry() instanceof RectangleGeometry rectangle) {
            CanvasRect bounds = rectangle.bounds();
            return new double[] {
                    bounds.minU(), bounds.minV(),
                    bounds.maxU(), bounds.minV(),
                    bounds.maxU(), bounds.maxV(),
                    bounds.minU(), bounds.maxV()
            };
        }
        PolygonGeometry polygon = (PolygonGeometry) region.geometry();
        double[] points = new double[polygon.vertices().size() * 2];
        for (int index = 0; index < polygon.vertices().size(); index++) {
            CanvasPoint point = polygon.vertices().get(index);
            points[index * 2] = point.u();
            points[index * 2 + 1] = point.v();
        }
        return points;
    }

    private static int argb(RgbaColor color) {
        return color.alpha() << 24
                | color.red() << 16
                | color.green() << 8
                | color.blue();
    }

    private static ViewportCamera camera(
            RuntimeManifest manifest,
            RuntimeFloor floor,
            MinimapViewerPose viewer,
            MinimapClientSettings settings,
            int viewportWidth,
            int viewportHeight
    ) {
        boolean followPlayer = settings.orientation()
                == MinimapOrientation.FOLLOW_PLAYER
                || settings.orientation() == MinimapOrientation.DOCUMENT
                && manifest.defaultViewMode() == DefaultViewMode.FOLLOW_PLAYER;
        if (followPlayer) {
            CanvasPoint center = floor.worldToCanvas().transform(
                    new WorldPoint2D(viewer.x(), viewer.z())
            );
            float rotation = -canvasYaw(
                    floor.worldToCanvas().transformVector(
                            forwardX(viewer.yawDegrees()),
                            forwardZ(viewer.yawDegrees())
                    )
            );
            return ViewportCamera.oriented(
                    center.u(), center.v(), settings.followZoom(),
                    viewportWidth, viewportHeight, rotation, rotation
            );
        }
        return fullMapCamera(
                manifest.canvas(), floor, viewportWidth, viewportHeight
        );
    }

    private static ViewportCamera fullMapCamera(
            CanvasBounds canvas,
            RuntimeFloor floor,
            int viewportWidth,
            int viewportHeight
    ) {
        float rotation = -canvasYaw(floor.northVector());
        double radians = Math.toRadians(rotation);
        double rotatedWidth = Math.abs(canvas.width() * Math.cos(radians))
                + Math.abs(canvas.height() * Math.sin(radians));
        double rotatedHeight = Math.abs(canvas.width() * Math.sin(radians))
                + Math.abs(canvas.height() * Math.cos(radians));
        double zoom = Math.min(
                viewportWidth / rotatedWidth,
                viewportHeight / rotatedHeight
        );
        return ViewportCamera.oriented(
                canvas.width() * 0.5,
                canvas.height() * 0.5,
                zoom,
                viewportWidth,
                viewportHeight,
                rotation,
                rotation
        );
    }

    private static float canvasYaw(Vector2D direction) {
        return (float) Math.toDegrees(Math.atan2(
                direction.x(), -direction.y()
        ));
    }

    private static double forwardX(float yawDegrees) {
        return -Math.sin(Math.toRadians(yawDegrees));
    }

    private static double forwardZ(float yawDegrees) {
        return Math.cos(Math.toRadians(yawDegrees));
    }

    private static List<TileAddress> availableTiles(
            String floorId,
            Set<ContainerPath> availablePaths
    ) {
        ArrayList<TileAddress> result = new ArrayList<>();
        for (ContainerPath path : availablePaths) {
            MinimapContainerLayout.parseRuntimeTile(path)
                    .filter(address -> address.floorId().equals(floorId))
                    .ifPresent(address -> result.add(new TileAddress(
                            path, address.zoom(), address.x(), address.y()
                    )));
        }
        return List.copyOf(result);
    }

    private static MapDrawCommand.Tile tileCommand(
            TileAddress tile,
            CanvasBounds canvas,
            int tileEdge,
            double scale
    ) {
        double minU = tile.x() * tileEdge * scale;
        double minV = tile.y() * tileEdge * scale;
        double maxU = Math.min(canvas.width(), minU + tileEdge * scale);
        double maxV = Math.min(canvas.height(), minV + tileEdge * scale);
        return new MapDrawCommand.Tile(
                tile.path().value(),
                minU,
                minV,
                maxU - minU,
                maxV - minV,
                1f
        );
    }

    private record TileAddress(ContainerPath path, int zoom, int x, int y) {
    }

    private record PlannedCommands(
            List<MapDrawCommand> commands,
            boolean hasTiles
    ) {
    }

    private record LabelRender(
            RuntimeRegion region,
            TextAppearance appearance
    ) {
    }

    private record HudCacheKey(
            RuntimeGeneration generation,
            RuntimeManifest manifest,
            RuntimeDefinition definition,
            Set<ContainerPath> availablePaths,
            MinimapViewerPose viewer,
            List<MarkerSnapshot.Marker> markers,
            MinimapClientSettings settings,
            int viewportWidth,
            int viewportHeight
    ) {
        private boolean matches(
                RuntimeGeneration generation,
                RuntimeManifest manifest,
                RuntimeDefinition definition,
                Set<ContainerPath> availablePaths,
                MinimapViewerPose viewer,
                List<MarkerSnapshot.Marker> markers,
                MinimapClientSettings settings,
                int viewportWidth,
                int viewportHeight
        ) {
            return this.viewportWidth == viewportWidth
                    && this.viewportHeight == viewportHeight
                    && this.generation.equals(generation)
                    && this.manifest.equals(manifest)
                    && Objects.equals(this.definition, definition)
                    && this.availablePaths.equals(availablePaths)
                    && this.viewer.equals(viewer)
                    && this.markers.equals(markers)
                    && this.settings.equals(settings);
        }
    }

    private record TacticalCacheKey(
            RuntimeGeneration generation,
            RuntimeManifest manifest,
            RuntimeDefinition definition,
            Set<ContainerPath> availablePaths,
            MinimapViewerPose viewer,
            List<MarkerSnapshot.Marker> markers,
            MinimapClientSettings settings,
            TacticalMapState state
    ) {
        private boolean matches(
                RuntimeGeneration generation,
                RuntimeManifest manifest,
                RuntimeDefinition definition,
                Set<ContainerPath> availablePaths,
                MinimapViewerPose viewer,
                List<MarkerSnapshot.Marker> markers,
                MinimapClientSettings settings,
                TacticalMapState state
        ) {
            return this.generation.equals(generation)
                    && this.manifest.equals(manifest)
                    && Objects.equals(this.definition, definition)
                    && this.availablePaths.equals(availablePaths)
                    && this.viewer.equals(viewer)
                    && this.markers.equals(markers)
                    && this.settings.equals(settings)
                    && this.state.equals(state);
        }
    }
}
