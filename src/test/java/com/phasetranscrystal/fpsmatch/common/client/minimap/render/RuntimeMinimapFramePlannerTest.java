package com.phasetranscrystal.fpsmatch.common.client.minimap.render;

import com.phasetranscrystal.fpsmatch.common.client.minimap.RuntimeGeneration;
import com.phasetranscrystal.fpsmatch.core.minimap.format.CompiledMapPair;
import com.phasetranscrystal.fpsmatch.core.minimap.format.MinimapContainerFixtures;
import com.phasetranscrystal.fpsmatch.core.minimap.format.RuntimeCompileRequest;
import com.phasetranscrystal.fpsmatch.core.minimap.format.RuntimeMap;
import com.phasetranscrystal.fpsmatch.core.minimap.format.RuntimeMapCompiler;
import com.phasetranscrystal.fpsmatch.core.minimap.format.RuntimeMapReader;
import com.phasetranscrystal.fpsmatch.core.minimap.format.SourceMap;
import com.phasetranscrystal.fpsmatch.core.minimap.format.SourceMapReader;
import com.phasetranscrystal.fpsmatch.core.minimap.format.SourceMapWriter;
import com.phasetranscrystal.fpsmatch.core.minimap.marker.MarkerSnapshot;
import com.phasetranscrystal.fpsmatch.core.minimap.model.CompilerProfile;
import com.phasetranscrystal.fpsmatch.core.minimap.model.ContainerPath;
import com.phasetranscrystal.fpsmatch.core.minimap.model.DefaultViewMode;
import com.phasetranscrystal.fpsmatch.core.minimap.model.AffineTransform2D;
import com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId;
import com.phasetranscrystal.fpsmatch.core.minimap.model.MinimapFloor;
import com.phasetranscrystal.fpsmatch.core.minimap.model.RuntimeFloor;
import com.phasetranscrystal.fpsmatch.core.minimap.model.RuntimeManifest;
import com.phasetranscrystal.fpsmatch.common.client.minimap.tactical.TacticalMapState;
import com.phasetranscrystal.fpsmatch.core.minimap.model.CanvasRect;
import com.phasetranscrystal.fpsmatch.core.minimap.model.CanvasPoint;
import com.phasetranscrystal.fpsmatch.core.minimap.model.ConnectionsFile;
import com.phasetranscrystal.fpsmatch.core.minimap.model.DisplayLabel;
import com.phasetranscrystal.fpsmatch.core.minimap.model.RectangleGeometry;
import com.phasetranscrystal.fpsmatch.core.minimap.model.RgbaColor;
import com.phasetranscrystal.fpsmatch.core.minimap.model.RuntimeDefinition;
import com.phasetranscrystal.fpsmatch.core.minimap.model.RuntimeRegion;
import com.phasetranscrystal.fpsmatch.core.minimap.model.RuntimeRegionsFile;
import com.phasetranscrystal.fpsmatch.core.minimap.model.RuntimeStyle;
import com.phasetranscrystal.fpsmatch.core.minimap.model.RuntimeStylesFile;
import com.phasetranscrystal.fpsmatch.core.minimap.model.TextAppearance;
import com.phasetranscrystal.fpsmatch.core.minimap.view.FloorViewMode;
import com.phasetranscrystal.fpsmatch.core.minimap.view.FloorViewState;
import com.phasetranscrystal.fpsmatch.core.minimap.view.MapDrawCommand;
import com.phasetranscrystal.fpsmatch.core.minimap.view.PlaceholderKind;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeMinimapFramePlannerTest {
    @Test
    void cacheHitReusesFramesUntilViewerOrMarkerInputChanges() throws Exception {
        Fixture fixture = fixture();
        RuntimeMinimapFramePlanner planner = new RuntimeMinimapFramePlanner();
        MinimapViewerPose viewer = new MinimapViewerPose(50, 8, 50, 0f);
        List<MarkerSnapshot.Marker> markers = List.of(
                marker("fpsmatch:self", "fpsmatch:type/player", 50, 8, 50)
        );
        MinimapClientSettings settings = MinimapClientSettings.defaults();

        MinimapFrame first = planner.planHud(
                fixture.generation(), fixture.manifest(), fixture.availablePaths(),
                viewer, markers, settings, 128, 128
        );
        MinimapFrame cached = planner.planHud(
                fixture.generation(), fixture.manifest(), fixture.availablePaths(),
                viewer, markers, settings, 128, 128
        );
        MinimapFrame movedViewer = planner.planHud(
                fixture.generation(), fixture.manifest(), fixture.availablePaths(),
                new MinimapViewerPose(51, 8, 50, 0f), markers, settings, 128, 128
        );
        MinimapFrame movedMarker = planner.planHud(
                fixture.generation(), fixture.manifest(), fixture.availablePaths(),
                viewer,
                List.of(marker(
                        "fpsmatch:self", "fpsmatch:type/player", 51, 8, 50
                )),
                settings,
                128,
                128
        );

        assertSame(first, cached);
        assertNotSame(first, movedViewer);
        assertNotSame(first, movedMarker);
    }

    @Test
    void tacticalCacheHitReusesFrameUntilViewportStateChanges() throws Exception {
        Fixture fixture = fixture();
        RuntimeMinimapFramePlanner planner = new RuntimeMinimapFramePlanner();
        MinimapViewerPose viewer = new MinimapViewerPose(50, 8, 50, 0f);
        List<MarkerSnapshot.Marker> markers = List.of(
                marker("fpsmatch:self", "fpsmatch:type/player", 50, 8, 50)
        );
        MinimapClientSettings settings = MinimapClientSettings.defaults();
        TacticalMapState state = TacticalMapState.initial().withViewport(320, 180);

        MinimapFrame first = planner.planTactical(
                fixture.generation(), fixture.manifest(), fixture.availablePaths(),
                viewer, markers, settings, state
        );
        MinimapFrame cached = planner.planTactical(
                fixture.generation(), fixture.manifest(), fixture.availablePaths(),
                viewer, markers, settings, state
        );
        MinimapFrame panned = planner.planTactical(
                fixture.generation(), fixture.manifest(), fixture.availablePaths(),
                viewer, markers, settings, state.withCamera(24, 20, 2.0)
        );

        assertSame(first, cached);
        assertNotSame(first, panned);
    }

    @Test
    void plansAvailableFloorTilesAndAuthorizedMarkersInCanvasCoordinates()
            throws Exception {
        Fixture fixture = fixture();
        RuntimeMinimapFramePlanner planner = new RuntimeMinimapFramePlanner();
        MinimapClientSettings settings = MinimapClientSettings.defaults()
                .withPreferredSize(128)
                .withOrientation(MinimapOrientation.FIXED_NORTH)
                .clamp();

        MinimapFrame frame = planner.planHud(
                fixture.generation(),
                fixture.manifest(),
                fixture.availablePaths(),
                new MinimapViewerPose(50, 8, 50, 90f),
                List.of(
                        marker("fpsmatch:self", "fpsmatch:type/player", 50, 8, 50),
                        marker("fpsmatch:hidden", "fpsmatch:type/objective", 25, 8, 25)
                ),
                settings,
                128,
                128
        );

        assertEquals("ground", frame.floor().effectiveFloorId().orElseThrow());
        assertTrue(frame.placeholder().isEmpty());
        MapDrawCommand.Tile tile = assertInstanceOf(
                MapDrawCommand.Tile.class, frame.commands().get(0)
        );
        assertEquals("floors/ground/tiles/0/0_0.png", tile.textureKey());
        assertEquals(0.0, tile.x(), 1e-9);
        assertEquals(0.0, tile.y(), 1e-9);
        assertEquals(64.0, tile.width(), 1e-9);
        assertEquals(64.0, tile.height(), 1e-9);
        MapDrawCommand.MarkerIcon self = frame.commands().stream()
                .filter(MapDrawCommand.MarkerIcon.class::isInstance)
                .map(MapDrawCommand.MarkerIcon.class::cast)
                .filter(marker -> marker.markerId().equals("fpsmatch:self"))
                .findFirst()
                .orElseThrow();
        assertEquals(32.0, self.x(), 1e-6);
        assertEquals(32.0, self.y(), 1e-6);
        assertEquals(
                NamespacedId.parse("fpsmatch:type/player"),
                self.typeId()
        );
        assertEquals(
                NamespacedId.parse("fpsmatch:style/default"),
                self.styleId()
        );

        MinimapFrame filtered = planner.planHud(
                fixture.generation(),
                fixture.manifest(),
                fixture.availablePaths(),
                new MinimapViewerPose(50, 8, 50, 90f),
                List.of(
                        marker("fpsmatch:self", "fpsmatch:type/player", 50, 8, 50),
                        marker("fpsmatch:hidden", "fpsmatch:type/objective", 25, 8, 25)
                ),
                settings.withMarkerFilter(List.of("fpsmatch:type/objective")),
                128,
                128
        );
        assertFalse(filtered.commands().stream()
                .filter(MapDrawCommand.MarkerIcon.class::isInstance)
                .map(MapDrawCommand.MarkerIcon.class::cast)
                .anyMatch(marker -> marker.markerId().equals("fpsmatch:hidden")));
    }

    @Test
    void heightOutsideEveryFloorProducesAnErrorPlaceholderWithoutTileCommands()
            throws Exception {
        Fixture fixture = fixture();
        RuntimeMinimapFramePlanner planner = new RuntimeMinimapFramePlanner();

        MinimapFrame frame = planner.planHud(
                fixture.generation(),
                fixture.manifest(),
                fixture.availablePaths(),
                new MinimapViewerPose(50, 200, 50, 0f),
                List.of(),
                MinimapClientSettings.defaults().clamp(),
                128,
                128
        );

        assertEquals(PlaceholderKind.ERROR, frame.placeholder().orElseThrow());
        assertTrue(frame.commands().stream().noneMatch(MapDrawCommand.Tile.class::isInstance));
    }

    @Test
    void documentModeUsesRuntimeFollowDefaultAndViewerCanvasDirection()
            throws Exception {
        Fixture fixture = fixture();
        RuntimeManifest manifest = withDefaultMode(
                fixture.manifest(), DefaultViewMode.FOLLOW_PLAYER
        );
        RuntimeMinimapFramePlanner planner = new RuntimeMinimapFramePlanner();

        MinimapFrame frame = planner.planHud(
                fixture.generation(),
                manifest,
                fixture.availablePaths(),
                new MinimapViewerPose(25, 8, 25, 180f),
                List.of(),
                MinimapClientSettings.defaults()
                        .withOrientation(MinimapOrientation.DOCUMENT)
                        .withFollowZoom(3.0),
                128,
                128
        );

        var expectedCenter = manifest.floors().get(0).worldToCanvas()
                .transform(new com.phasetranscrystal.fpsmatch.core.minimap.model.WorldPoint2D(
                        25, 25
                ));
        assertEquals(expectedCenter.u(), frame.camera().panX(), 1e-6);
        assertEquals(expectedCenter.v(), frame.camera().panY(), 1e-6);
        assertEquals(3.0, frame.camera().zoom(), 1e-6);
        assertEquals(0f, frame.camera().rotationDegrees(), 1e-5);
    }

    @Test
    void fixedNorthUsesDerivedNorthVectorForTilesAndMarkerYaw()
            throws Exception {
        Fixture fixture = fixture();
        RuntimeFloor original = fixture.manifest().floors().get(0);
        RuntimeFloor rotated = new RuntimeFloor(
                original.selection(),
                original.label(),
                original.contentBounds(),
                new AffineTransform2D(0, 0.64, 0, -0.64, 0, 64),
                original.zoomLevels()
        );
        RuntimeManifest manifest = withFloors(
                fixture.manifest(), List.of(rotated)
        );
        RuntimeMinimapFramePlanner planner = new RuntimeMinimapFramePlanner();

        MinimapFrame frame = planner.planHud(
                fixture.generation(),
                manifest,
                fixture.availablePaths(),
                new MinimapViewerPose(50, 8, 50, 180f),
                List.of(marker(
                        "fpsmatch:north",
                        "fpsmatch:type/player",
                        50,
                        8,
                        50,
                        180f
                )),
                MinimapClientSettings.defaults()
                        .withOrientation(MinimapOrientation.FIXED_NORTH),
                128,
                128
        );

        assertEquals(90f, frame.camera().rotationDegrees(), 1e-5);
        MapDrawCommand.MarkerIcon marker = frame.commands().stream()
                .filter(MapDrawCommand.MarkerIcon.class::isInstance)
                .map(MapDrawCommand.MarkerIcon.class::cast)
                .findFirst()
                .orElseThrow();
        assertEquals(
                0f,
                frame.camera().projectCanvas(
                        marker.x(), marker.y(), marker.yawDegrees()
                ).displayYawDegrees(),
                1e-5
        );
    }

    @Test
    void adjacentFloorStyleEmitsOnlyNeighboringFloorMarkers()
            throws Exception {
        Fixture fixture = fixture();
        RuntimeFloor ground = fixture.manifest().floors().get(0);
        RuntimeFloor upper = new RuntimeFloor(
                new MinimapFloor("upper", 16, 32, 1, 1, 1),
                com.phasetranscrystal.fpsmatch.core.minimap.model.DisplayLabel
                        .literal("Upper"),
                ground.contentBounds(),
                ground.worldToCanvas(),
                ground.zoomLevels()
        );
        RuntimeFloor roof = new RuntimeFloor(
                new MinimapFloor("roof", 32, 48, 2, 1, 1),
                com.phasetranscrystal.fpsmatch.core.minimap.model.DisplayLabel
                        .literal("Roof"),
                ground.contentBounds(),
                ground.worldToCanvas(),
                ground.zoomLevels()
        );
        RuntimeManifest manifest = withFloors(
                fixture.manifest(), List.of(ground, upper, roof)
        );
        RuntimeMinimapFramePlanner planner = new RuntimeMinimapFramePlanner();
        List<MarkerSnapshot.Marker> markers = List.of(
                markerOnFloor("fpsmatch:upper", "upper"),
                markerOnFloor("fpsmatch:roof", "roof")
        );

        MinimapFrame faded = planner.planHud(
                fixture.generation(), manifest, fixture.availablePaths(),
                new MinimapViewerPose(50, 8, 50, 0f),
                markers,
                MinimapClientSettings.defaults()
                        .withAdjacentFloorMarkerStyle(
                                AdjacentFloorMarkerStyle.FADED_ARROWS
                        ),
                128, 128
        );

        List<MapDrawCommand.MarkerIcon> fadedMarkers = faded.commands().stream()
                .filter(MapDrawCommand.MarkerIcon.class::isInstance)
                .map(MapDrawCommand.MarkerIcon.class::cast)
                .toList();
        assertEquals(1, fadedMarkers.size());
        assertEquals("fpsmatch:upper", fadedMarkers.get(0).markerId());
        assertTrue(fadedMarkers.get(0).adjacent());
        assertEquals(0.55f, fadedMarkers.get(0).opacity(), 1e-6);

        MinimapFrame hidden = planner.planHud(
                fixture.generation(), manifest, fixture.availablePaths(),
                new MinimapViewerPose(50, 8, 50, 0f),
                markers,
                MinimapClientSettings.defaults()
                        .withAdjacentFloorMarkerStyle(
                                AdjacentFloorMarkerStyle.HIDDEN
                        ),
                128, 128
        );
        assertTrue(hidden.commands().stream()
                .noneMatch(MapDrawCommand.MarkerIcon.class::isInstance));
    }

    @Test
    void tacticalFrameUsesIndependentManualFloorCameraAndFilters()
            throws Exception {
        Fixture fixture = fixture();
        RuntimeFloor ground = fixture.manifest().floors().get(0);
        RuntimeFloor upper = new RuntimeFloor(
                new MinimapFloor("upper", 16, 32, 1, 1, 1),
                com.phasetranscrystal.fpsmatch.core.minimap.model.DisplayLabel
                        .literal("Upper"),
                Optional.of(new CanvasRect(8, 12, 56, 52)),
                ground.worldToCanvas(),
                ground.zoomLevels()
        );
        RuntimeManifest manifest = withFloors(
                fixture.manifest(), List.of(ground, upper)
        );
        RuntimeMinimapFramePlanner planner = new RuntimeMinimapFramePlanner();
        TacticalMapState tactical = TacticalMapState.initial()
                .withViewport(320, 180)
                .withFloor(FloorViewState.manual("upper", 100))
                .withCamera(20, 24, 3.0)
                .withHiddenMarkerTypes(Set.of("fpsmatch:type/objective"));

        MinimapFrame frame = planner.planTactical(
                fixture.generation(),
                manifest,
                fixture.availablePaths(),
                new MinimapViewerPose(50, 8, 50, 0f),
                List.of(
                        markerOnFloor("fpsmatch:upper", "upper"),
                        marker(
                                "fpsmatch:hidden",
                                "fpsmatch:type/objective",
                                25,
                                20,
                                25
                        )
                ),
                MinimapClientSettings.defaults(),
                tactical
        );

        assertEquals(FloorViewMode.MANUAL, frame.floor().mode());
        assertEquals("upper", frame.floor().effectiveFloorId().orElseThrow());
        assertEquals(20.0, frame.camera().panX(), 1e-9);
        assertEquals(24.0, frame.camera().panY(), 1e-9);
        assertEquals(3.0, frame.camera().zoom(), 1e-9);
        assertEquals(320.0, frame.camera().viewportWidth(), 1e-9);
        assertEquals(180.0, frame.camera().viewportHeight(), 1e-9);
        assertTrue(frame.commands().stream()
                .filter(MapDrawCommand.MarkerIcon.class::isInstance)
                .map(MapDrawCommand.MarkerIcon.class::cast)
                .noneMatch(marker -> marker.markerId().equals("fpsmatch:hidden")));

        MinimapFrame hud = planner.planHud(
                fixture.generation(), manifest, fixture.availablePaths(),
                new MinimapViewerPose(50, 8, 50, 0f), List.of(),
                MinimapClientSettings.defaults(), 128, 128
        );
        assertEquals("ground", hud.floor().effectiveFloorId().orElseThrow());
    }

    @Test
    void tacticalFitModesUseExactCanvasAndFloorBounds() throws Exception {
        Fixture fixture = fixture();
        RuntimeFloor ground = fixture.manifest().floors().get(0);
        RuntimeFloor bounded = new RuntimeFloor(
                ground.selection(),
                ground.label(),
                Optional.of(new CanvasRect(8, 16, 56, 48)),
                ground.worldToCanvas(),
                ground.zoomLevels()
        );
        RuntimeManifest manifest = withFloors(
                fixture.manifest(), List.of(bounded)
        );
        RuntimeMinimapFramePlanner planner = new RuntimeMinimapFramePlanner();

        MinimapFrame wholeMap = planner.planTactical(
                fixture.generation(), manifest, fixture.availablePaths(),
                new MinimapViewerPose(50, 8, 50, 0f), List.of(),
                MinimapClientSettings.defaults(),
                TacticalMapState.initial().withViewport(256, 128).fitAll()
        );
        assertEquals(2.0, wholeMap.camera().zoom(), 1e-9);
        assertEquals(32.0, wholeMap.camera().panX(), 1e-9);
        assertEquals(32.0, wholeMap.camera().panY(), 1e-9);

        MinimapFrame currentFloor = planner.planTactical(
                fixture.generation(), manifest, fixture.availablePaths(),
                new MinimapViewerPose(50, 8, 50, 0f), List.of(),
                MinimapClientSettings.defaults(),
                TacticalMapState.initial().withViewport(256, 128).fitFloor()
        );
        assertEquals(4.0, currentFloor.camera().zoom(), 1e-9);
        assertEquals(32.0, currentFloor.camera().panX(), 1e-9);
        assertEquals(32.0, currentFloor.camera().panY(), 1e-9);
    }

    @Test
    void followHudOmitsRegionCommandsOutsideTheViewport() throws Exception {
        Fixture fixture = fixture();
        NamespacedId semantic = NamespacedId.parse("fpsmatch:region/test");
        NamespacedId styleId = NamespacedId.parse("fpsmatch:style/test");
        RuntimeRegion visible = region(
                "visible", semantic, styleId,
                new CanvasRect(30, 30, 34, 34), new CanvasPoint(32, 32)
        );
        RuntimeRegion offscreen = region(
                "offscreen", semantic, styleId,
                new CanvasRect(0, 0, 4, 4), new CanvasPoint(2, 2)
        );
        RuntimeDefinition definition = new RuntimeDefinition(
                withDefaultMode(fixture.manifest(), DefaultViewMode.FOLLOW_PLAYER),
                new RuntimeRegionsFile(List.of(visible, offscreen)),
                new ConnectionsFile(List.of()),
                new RuntimeStylesFile(List.of(new RuntimeStyle(
                        styleId,
                        Optional.of(new TextAppearance(
                                new RgbaColor(255, 255, 255, 255), 1.0
                        )),
                        Optional.empty()
                )))
        );

        MinimapFrame frame = new RuntimeMinimapFramePlanner().planHud(
                fixture.generation(),
                definition,
                fixture.availablePaths(),
                new MinimapViewerPose(50, 8, 50, 0f),
                List.of(),
                MinimapClientSettings.defaults()
                        .withOrientation(MinimapOrientation.FOLLOW_PLAYER)
                        .withFollowZoom(8.0),
                128,
                128
        );

        assertEquals(
                List.of("visible"),
                frame.commands().stream()
                        .filter(MapDrawCommand.RegionOutline.class::isInstance)
                        .map(MapDrawCommand.RegionOutline.class::cast)
                        .map(MapDrawCommand.RegionOutline::regionId)
                        .toList()
        );
        assertEquals(
                List.of("Visible"),
                frame.commands().stream()
                        .filter(MapDrawCommand.Label.class::isInstance)
                        .map(MapDrawCommand.Label.class::cast)
                        .map(MapDrawCommand.Label::text)
                        .toList()
        );
    }

    private static RuntimeRegion region(
            String id,
            NamespacedId semantic,
            NamespacedId styleId,
            CanvasRect bounds,
            CanvasPoint anchor
    ) {
        return new RuntimeRegion(
                id,
                "ground",
                DisplayLabel.literal(
                        Character.toUpperCase(id.charAt(0)) + id.substring(1)
                ),
                new RectangleGeometry(bounds),
                semantic,
                List.of(),
                Optional.empty(),
                styleId,
                anchor,
                1,
                0.0,
                64.0
        );
    }

    private static Fixture fixture() throws Exception {
        byte[] sourceBytes = SourceMapWriter.write(
                MinimapContainerFixtures.sourceDefinition()
        );
        try (SourceMap source = SourceMapReader.read(sourceBytes)) {
            CompiledMapPair pair = RuntimeMapCompiler.compile(
                    source,
                    new RuntimeCompileRequest(
                            source.manifest().revision(),
                            new CompilerProfile(
                                    NamespacedId.parse("fpsmatch:test-compiler"),
                                    source.manifest().formatVersion()
                            ),
                            MinimapContainerFixtures.fullRuntimeTiles()
                    )
            );
            try (RuntimeMap runtime = RuntimeMapReader.read(pair.runtimeBytes())) {
                RuntimeManifest manifest = runtime.manifest();
                RuntimeGeneration generation = new RuntimeGeneration(
                        1L,
                        "server-a",
                        manifest.binding(),
                        manifest.documentId(),
                        manifest.publishRevision(),
                        runtime.runtimeHash(),
                        NamespacedId.parse("minecraft:overworld"),
                        1L
                );
                Set<ContainerPath> paths = manifest.entries().stream()
                        .map(entry -> entry.path())
                        .collect(Collectors.toUnmodifiableSet());
                return new Fixture(generation, manifest, paths);
            }
        }
    }

    private static MarkerSnapshot.Marker marker(
            String id,
            String type,
            double x,
            double y,
            double z
    ) {
        return new MarkerSnapshot.Marker(
                NamespacedId.parse(id),
                NamespacedId.parse(type),
                NamespacedId.parse("fpsmatch:style/default"),
                x, y, z, 0f, 0L, Optional.empty(), Optional.of("ground")
        );
    }

    private static MarkerSnapshot.Marker markerOnFloor(
            String id,
            String floor
    ) {
        return new MarkerSnapshot.Marker(
                NamespacedId.parse(id),
                NamespacedId.parse("fpsmatch:type/player"),
                NamespacedId.parse("fpsmatch:style/default"),
                50, 20, 50, 0f, 0L, Optional.empty(), Optional.of(floor)
        );
    }

    private static MarkerSnapshot.Marker marker(
            String id,
            String type,
            double x,
            double y,
            double z,
            float yaw
    ) {
        return new MarkerSnapshot.Marker(
                NamespacedId.parse(id),
                NamespacedId.parse(type),
                NamespacedId.parse("fpsmatch:style/default"),
                x, y, z, yaw, 0L, Optional.empty(), Optional.of("ground")
        );
    }

    private static RuntimeManifest withDefaultMode(
            RuntimeManifest manifest,
            DefaultViewMode mode
    ) {
        return new RuntimeManifest(
                manifest.formatVersion(),
                manifest.documentId(),
                manifest.binding(),
                manifest.publishRevision(),
                manifest.sourceHash(),
                manifest.compilerProfile(),
                manifest.canvas(),
                mode,
                manifest.floors(),
                manifest.tileEdge(),
                manifest.entries()
        );
    }

    private static RuntimeManifest withFloors(
            RuntimeManifest manifest,
            List<RuntimeFloor> floors
    ) {
        return new RuntimeManifest(
                manifest.formatVersion(),
                manifest.documentId(),
                manifest.binding(),
                manifest.publishRevision(),
                manifest.sourceHash(),
                manifest.compilerProfile(),
                manifest.canvas(),
                manifest.defaultViewMode(),
                floors,
                manifest.tileEdge(),
                manifest.entries()
        );
    }

    private record Fixture(
            RuntimeGeneration generation,
            RuntimeManifest manifest,
            Set<ContainerPath> availablePaths
    ) {
    }
}
