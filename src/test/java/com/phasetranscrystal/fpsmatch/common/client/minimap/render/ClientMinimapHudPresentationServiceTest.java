package com.phasetranscrystal.fpsmatch.common.client.minimap.render;

import com.phasetranscrystal.fpsmatch.common.client.minimap.RuntimeGeneration;
import com.phasetranscrystal.fpsmatch.common.client.minimap.cache.BoundedPngDecoder;
import com.phasetranscrystal.fpsmatch.common.client.minimap.cache.MinimapCacheKey;
import com.phasetranscrystal.fpsmatch.common.client.minimap.cache.RuntimeEntryStore;
import com.phasetranscrystal.fpsmatch.common.client.minimap.tactical.TacticalMapPresentation;
import com.phasetranscrystal.fpsmatch.common.client.minimap.tactical.TacticalMapState;
import com.phasetranscrystal.fpsmatch.core.minimap.format.CompiledMapPair;
import com.phasetranscrystal.fpsmatch.core.minimap.format.MinimapContainerFixtures;
import com.phasetranscrystal.fpsmatch.core.minimap.format.MinimapContainerLayout;
import com.phasetranscrystal.fpsmatch.core.minimap.format.RuntimeCompileRequest;
import com.phasetranscrystal.fpsmatch.core.minimap.format.RuntimeMap;
import com.phasetranscrystal.fpsmatch.core.minimap.format.RuntimeMapCompiler;
import com.phasetranscrystal.fpsmatch.core.minimap.format.RuntimeMapReader;
import com.phasetranscrystal.fpsmatch.core.minimap.format.SourceMap;
import com.phasetranscrystal.fpsmatch.core.minimap.format.SourceMapReader;
import com.phasetranscrystal.fpsmatch.core.minimap.format.SourceMapWriter;
import com.phasetranscrystal.fpsmatch.core.minimap.extension.MarkerPresentation;
import com.phasetranscrystal.fpsmatch.core.minimap.extension.MinimapExtensionRegistry;
import com.phasetranscrystal.fpsmatch.core.minimap.extension.MinimapGameplayExtension;
import com.phasetranscrystal.fpsmatch.core.minimap.model.CompilerProfile;
import com.phasetranscrystal.fpsmatch.core.minimap.model.CanvasPoint;
import com.phasetranscrystal.fpsmatch.core.minimap.model.CanvasRect;
import com.phasetranscrystal.fpsmatch.core.minimap.model.ContainerPath;
import com.phasetranscrystal.fpsmatch.core.minimap.model.DisplayLabel;
import com.phasetranscrystal.fpsmatch.core.minimap.model.FillStyle;
import com.phasetranscrystal.fpsmatch.core.minimap.model.MinimapDefinition;
import com.phasetranscrystal.fpsmatch.core.minimap.model.MinimapRegion;
import com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId;
import com.phasetranscrystal.fpsmatch.core.minimap.model.RectangleGeometry;
import com.phasetranscrystal.fpsmatch.core.minimap.model.RegionStyle;
import com.phasetranscrystal.fpsmatch.core.minimap.model.RegionStyleOverride;
import com.phasetranscrystal.fpsmatch.core.minimap.model.RegionsFile;
import com.phasetranscrystal.fpsmatch.core.minimap.model.RgbaColor;
import com.phasetranscrystal.fpsmatch.core.minimap.model.RuntimeEntryDescriptor;
import com.phasetranscrystal.fpsmatch.core.minimap.model.StrokeStyle;
import com.phasetranscrystal.fpsmatch.core.minimap.model.StylesFile;
import com.phasetranscrystal.fpsmatch.core.minimap.model.TextAppearance;
import com.phasetranscrystal.fpsmatch.core.minimap.marker.MarkerSnapshot;
import com.phasetranscrystal.fpsmatch.core.minimap.view.MapDrawCommand;
import com.phasetranscrystal.fpsmatch.core.minimap.view.PlaceholderKind;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientMinimapHudPresentationServiceTest {
    @AfterEach
    void clearExtensions() {
        MinimapExtensionRegistry.clearForTests();
    }

    @Test
    void acknowledgedGenerationWithoutActiveRuntimeProducesLoadingFrame()
            throws Exception {
        Fixture fixture = fixture();
        MinecraftMinimapTextureManager textures = new MinecraftMinimapTextureManager(
                () -> Optional.of(fixture.generation()),
                () -> true,
                new RecordingPlatform()
        );
        MinimapTileUploadQueue uploads = new MinimapTileUploadQueue(
                new BoundedPngDecoder(1024, 1024),
                Runnable::run,
                Runnable::run,
                () -> Optional.of(fixture.generation()),
                textures,
                1
        );
        ClientMinimapHudPresentationService presentation =
                new ClientMinimapHudPresentationService(
                        () -> Optional.of(fixture.generation()),
                        Optional::empty,
                        List::of,
                        uploads,
                        textures
                );

        MinimapFrame frame = presentation.prepareFrame(
                new MinimapViewerPose(0, 0, 0, 0f),
                MinimapClientSettings.defaults().withShape(
                        com.phasetranscrystal.fpsmatch.core.minimap.view.ShapeMode.CIRCLE
                ),
                96,
                96
        ).orElseThrow();

        assertEquals(PlaceholderKind.LOADING, frame.placeholder().orElseThrow());
        assertEquals(
                com.phasetranscrystal.fpsmatch.core.minimap.view.ShapeMode.CIRCLE,
                frame.shape()
        );
        assertTrue(frame.commands().isEmpty());
    }

    @Test
    void coldTileLoadsOffThreadThenAppearsWithoutDuplicateDecode()
            throws Exception {
        Fixture fixture = fixture();
        AtomicReference<RuntimeGeneration> current = new AtomicReference<>(
                fixture.generation()
        );
        List<Runnable> background = new ArrayList<>();
        List<Runnable> render = new ArrayList<>();
        RecordingPlatform platform = new RecordingPlatform();
        MinecraftMinimapTextureManager textures = new MinecraftMinimapTextureManager(
                () -> Optional.ofNullable(current.get()), () -> true, platform
        );
        MinimapTileUploadQueue uploads = new MinimapTileUploadQueue(
                new BoundedPngDecoder(8L * 1024 * 1024, 8L * 1024 * 1024),
                background::add,
                render::add,
                () -> Optional.ofNullable(current.get()),
                textures,
                8
        );
        ClientMinimapHudPresentationService presentation =
                new ClientMinimapHudPresentationService(
                        () -> Optional.ofNullable(current.get()),
                        () -> Optional.of(fixture.runtime()),
                        List::of,
                        uploads,
                        textures
                );

        MinimapFrame loading = presentation.prepareFrame(
                new MinimapViewerPose(50, 8, 50, 0f),
                MinimapClientSettings.defaults(),
                128,
                128
        ).orElseThrow();

        assertEquals(PlaceholderKind.LOADING, loading.placeholder().orElseThrow());
        assertEquals(1, background.size());
        assertEquals(0, platform.uploads);

        background.remove(0).run();
        assertEquals(1, render.size());
        render.remove(0).run();
        assertEquals(1, platform.uploads);

        MinimapFrame ready = presentation.prepareFrame(
                new MinimapViewerPose(50, 8, 50, 0f),
                MinimapClientSettings.defaults(),
                128,
                128
        ).orElseThrow();

        assertTrue(ready.placeholder().isEmpty());
        assertTrue(ready.commands().stream()
                .anyMatch(MapDrawCommand.Tile.class::isInstance));
        assertTrue(background.isEmpty());
    }

    @Test
    void mismatchedRuntimeIsRejectedAndResetDropsLateUpload()
            throws Exception {
        Fixture fixture = fixture();
        AtomicReference<RuntimeGeneration> current = new AtomicReference<>(
                fixture.generation()
        );
        List<Runnable> background = new ArrayList<>();
        List<Runnable> render = new ArrayList<>();
        RecordingPlatform platform = new RecordingPlatform();
        MinecraftMinimapTextureManager textures = new MinecraftMinimapTextureManager(
                () -> Optional.ofNullable(current.get()), () -> true, platform
        );
        MinimapTileUploadQueue uploads = new MinimapTileUploadQueue(
                new BoundedPngDecoder(8L * 1024 * 1024, 8L * 1024 * 1024),
                background::add,
                render::add,
                () -> Optional.ofNullable(current.get()),
                textures,
                8
        );
        AtomicReference<RuntimeEntryStore.ActiveRuntime> active =
                new AtomicReference<>(fixture.runtime());
        ClientMinimapHudPresentationService presentation =
                new ClientMinimapHudPresentationService(
                        () -> Optional.ofNullable(current.get()),
                        () -> Optional.ofNullable(active.get()),
                        List::of,
                        uploads,
                        textures
                );

        active.set(new RuntimeEntryStore.ActiveRuntime(
                fixture.runtime().serverIdentity(),
                fixture.runtime().dimension(),
                new com.phasetranscrystal.fpsmatch.core.minimap.model.MapKey(
                        "cs", "inferno"
                ),
                fixture.runtime().documentId(),
                fixture.runtime().revision(),
                fixture.runtime().runtimeHash(),
                fixture.runtime().entries()
        ));
        assertTrue(presentation.prepareFrame(
                new MinimapViewerPose(50, 8, 50, 0f),
                MinimapClientSettings.defaults(), 128, 128
        ).isEmpty());
        assertTrue(background.isEmpty());

        active.set(fixture.runtime());
        presentation.prepareFrame(
                new MinimapViewerPose(50, 8, 50, 0f),
                MinimapClientSettings.defaults(), 128, 128
        ).orElseThrow();
        background.remove(0).run();

        presentation.reset();
        render.remove(0).run();

        assertEquals(0, platform.uploads);
        assertEquals(0, uploads.pendingCount());
        assertTrue(textures.resolve(fixture.tilePath().value()).isEmpty());
    }

    @Test
    void tacticalPresentationUsesManifestFloorsAndStableAuthorizedLegend()
            throws Exception {
        Fixture fixture = fixture();
        AtomicReference<List<MarkerSnapshot.Marker>> markers =
                new AtomicReference<>(List.of(
                        marker(
                                "fpsmatch:z", "fpsmatch:type/player",
                                "fpsmatch:style/z"
                        ),
                        marker(
                                "fpsmatch:a", "fpsmatch:type/objective",
                                "fpsmatch:style/objective"
                        ),
                        marker(
                                "fpsmatch:b", "fpsmatch:type/player",
                                "fpsmatch:style/a"
                        )
                ));
        MinimapExtensionRegistry.register(new MinimapGameplayExtension() {
            @Override
            public String id() {
                return "test:client-marker-presentations";
            }

            @Override
            public boolean supports(com.phasetranscrystal.fpsmatch.core.minimap.model.MapKey mapKey) {
                return fixture.generation().mapKey().equals(mapKey);
            }

            @Override
            public List<MarkerPresentation> markerPresentations(
                    com.phasetranscrystal.fpsmatch.core.minimap.model.MapKey mapKey
            ) {
                return List.of(
                        presentation("fpsmatch:type/player", "fpsmatch:style/z", "z"),
                        presentation("fpsmatch:type/objective", "fpsmatch:style/objective", "objective"),
                        presentation("fpsmatch:type/player", "fpsmatch:style/a", "a"),
                        presentation("fpsmatch:type/death", "fpsmatch:style/death", "death")
                );
            }
        });
        MinecraftMinimapTextureManager textures = new MinecraftMinimapTextureManager(
                () -> Optional.of(fixture.generation()),
                () -> true,
                new RecordingPlatform()
        );
        MinimapTileUploadQueue uploads = new MinimapTileUploadQueue(
                new BoundedPngDecoder(8L * 1024 * 1024, 8L * 1024 * 1024),
                Runnable::run,
                Runnable::run,
                () -> Optional.of(fixture.generation()),
                textures,
                8
        );
        ClientMinimapHudPresentationService presentation =
                new ClientMinimapHudPresentationService(
                        () -> Optional.of(fixture.generation()),
                        () -> Optional.of(fixture.runtime()),
                        markers::get,
                        uploads,
                        textures
                );

        TacticalMapPresentation tactical = presentation.prepareTactical(
                new MinimapViewerPose(50, 8, 50, 0f),
                MinimapClientSettings.defaults(),
                TacticalMapState.initial().withViewport(320, 180)
        ).orElseThrow();

        assertEquals(
                List.of("ground"),
                tactical.floors().stream().map(floor -> floor.id()).toList()
        );
        assertEquals(
                List.of(
                        "fpsmatch:type/death|fpsmatch:style/death|fpsmatch.minimap.marker.death",
                        "fpsmatch:type/objective|fpsmatch:style/objective|fpsmatch.minimap.marker.objective",
                        "fpsmatch:type/player|fpsmatch:style/a|fpsmatch.minimap.marker.a",
                        "fpsmatch:type/player|fpsmatch:style/z|fpsmatch.minimap.marker.z"
                ),
                tactical.legend().stream()
                        .map(entry -> entry.typeId() + "|" + entry.styleId()
                                + "|" + entry.label().value())
                        .toList()
        );
        assertEquals(320.0, tactical.frame().camera().viewportWidth(), 1e-9);
        assertEquals(180.0, tactical.frame().camera().viewportHeight(), 1e-9);
        assertEquals(
                tactical.frame().camera().zoom(),
                tactical.viewport().fitAllZoom(),
                1e-9
        );
    }

    @Test
    void tacticalPresentationConsumesRuntimeRegionAuthorityEntries()
            throws Exception {
        Fixture fixture = fixture(definitionWithRegion());
        MinecraftMinimapTextureManager textures = new MinecraftMinimapTextureManager(
                () -> Optional.of(fixture.generation()),
                () -> true,
                new RecordingPlatform()
        );
        MinimapTileUploadQueue uploads = new MinimapTileUploadQueue(
                new BoundedPngDecoder(8L * 1024 * 1024, 8L * 1024 * 1024),
                Runnable::run,
                Runnable::run,
                () -> Optional.of(fixture.generation()),
                textures,
                8
        );
        ClientMinimapHudPresentationService presentation =
                new ClientMinimapHudPresentationService(
                        () -> Optional.of(fixture.generation()),
                        () -> Optional.of(fixture.runtime()),
                        List::of,
                        uploads,
                        textures
                );

        TacticalMapPresentation tactical = presentation.prepareTactical(
                new MinimapViewerPose(50, 8, 50, 0f),
                MinimapClientSettings.defaults(),
                TacticalMapState.initial().withViewport(320, 180)
        ).orElseThrow();

        assertTrue(tactical.frame().commands().stream()
                .filter(MapDrawCommand.RegionOutline.class::isInstance)
                .map(MapDrawCommand.RegionOutline.class::cast)
                .anyMatch(region -> region.regionId().equals("site_a")));
        MapDrawCommand.Label label = tactical.frame().commands().stream()
                .filter(MapDrawCommand.Label.class::isInstance)
                .map(MapDrawCommand.Label.class::cast)
                .findFirst()
                .orElseThrow();
        assertEquals(DisplayLabel.literal("Bombsite A"), label.displayLabel());
        assertEquals(0xFF112233, label.color());
        assertEquals(1.5, label.scale(), 1e-9);

        TacticalMapPresentation.RegionDetail detail = tactical
                .regionAt(16, 16)
                .orElseThrow();
        assertEquals("site_a", detail.id());
        assertEquals(
                NamespacedId.parse("fpsmatch:bomb_site"),
                detail.semanticType()
        );
        assertEquals(
                Optional.of(NamespacedId.parse("blockoffensive:site_a")),
                detail.gameplayReference()
        );
        assertTrue(tactical.regionAt(2, 2).isEmpty());
    }

    private static Fixture fixture() throws Exception {
        return fixture(MinimapContainerFixtures.sourceDefinition());
    }

    private static Fixture fixture(MinimapDefinition definition) throws Exception {
        byte[] sourceBytes = SourceMapWriter.write(
                definition
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
                ContainerPath tilePath = runtime.manifest().entries().stream()
                        .map(RuntimeEntryDescriptor::path)
                        .filter(path -> MinimapContainerLayout.parseRuntimeTile(path).isPresent())
                        .findFirst()
                        .orElseThrow();
                RuntimeGeneration generation = new RuntimeGeneration(
                        1L,
                        "server-a",
                        runtime.manifest().binding(),
                        runtime.manifest().documentId(),
                        runtime.manifest().publishRevision(),
                        runtime.runtimeHash(),
                        NamespacedId.parse("minecraft:overworld"),
                        1L
                );
                Map<String, RuntimeEntryStore.ActiveEntry> entries =
                        new LinkedHashMap<>();
                entries.put(
                        MinimapContainerLayout.RUNTIME_MANIFEST.value(),
                        activeEntry(
                                generation,
                                MinimapContainerLayout.RUNTIME_MANIFEST,
                                generation.runtimeHash(),
                                runtime.manifestBytes()
                        )
                );
                RuntimeEntryDescriptor tileDescriptor = runtime.manifest().entries()
                        .stream()
                        .filter(entry -> entry.path().equals(tilePath))
                        .findFirst()
                        .orElseThrow();
                entries.put(
                        tilePath.value(),
                        activeEntry(
                                generation,
                                tilePath,
                                tileDescriptor.sha256(),
                                runtime.entryBytes(tilePath)
                        )
                );
                for (ContainerPath authorityPath : List.of(
                        MinimapContainerLayout.RUNTIME_REGIONS,
                        MinimapContainerLayout.CONNECTIONS,
                        MinimapContainerLayout.RUNTIME_STYLES
                )) {
                    RuntimeEntryDescriptor authorityDescriptor = runtime.manifest()
                            .entries().stream()
                            .filter(entry -> entry.path().equals(authorityPath))
                            .findFirst()
                            .orElseThrow();
                    entries.put(
                            authorityPath.value(),
                            activeEntry(
                                    generation,
                                    authorityPath,
                                    authorityDescriptor.sha256(),
                                    runtime.entryBytes(authorityPath)
                            )
                    );
                }
                return new Fixture(
                        generation,
                        new RuntimeEntryStore.ActiveRuntime(
                                generation.serverIdentity(),
                                generation.dimension(),
                                generation.mapKey(),
                                generation.documentId(),
                                generation.revision(),
                                generation.runtimeHash(),
                                entries
                        ),
                        tilePath
                );
            }
        }
    }

    private static MinimapDefinition definitionWithRegion() {
        MinimapDefinition base = MinimapContainerFixtures.sourceDefinition();
        NamespacedId styleId = NamespacedId.parse("fpsmatch:region/site");
        MinimapRegion region = new MinimapRegion(
                "site_a",
                "ground",
                DisplayLabel.literal("Bombsite A"),
                new RectangleGeometry(new CanvasRect(8, 8, 24, 24)),
                NamespacedId.parse("fpsmatch:bomb_site"),
                List.of(NamespacedId.parse("fpsmatch:objective")),
                Optional.of(NamespacedId.parse("blockoffensive:site_a")),
                styleId,
                RegionStyleOverride.empty(),
                new CanvasPoint(16, 16),
                100,
                0,
                8
        );
        RegionStyle style = new RegionStyle(
                styleId,
                new FillStyle(new RgbaColor(0, 0, 0, 0), 0),
                new StrokeStyle(new RgbaColor(255, 255, 255, 255), 1, 1),
                new TextAppearance(new RgbaColor(17, 34, 51, 255), 1.5)
        );
        return new MinimapDefinition(
                base.manifest(),
                base.document(),
                new RegionsFile(List.of(region)),
                base.connections(),
                new StylesFile(List.of(style))
        );
    }

    private static RuntimeEntryStore.ActiveEntry activeEntry(
            RuntimeGeneration generation,
            ContainerPath path,
            com.phasetranscrystal.fpsmatch.core.minimap.model.Sha256 hash,
            byte[] payload
    ) {
        return new RuntimeEntryStore.ActiveEntry(
                new MinimapCacheKey(
                        generation.serverIdentity(),
                        generation.dimension(),
                        generation.mapKey(),
                        generation.documentId(),
                        generation.revision(),
                        generation.runtimeHash(),
                        hash,
                        path.value()
                ),
                payload
        );
    }

    private static MarkerSnapshot.Marker marker(
            String markerId,
            String typeId,
            String styleId
    ) {
        return new MarkerSnapshot.Marker(
                NamespacedId.parse(markerId),
                NamespacedId.parse(typeId),
                NamespacedId.parse(styleId),
                32, 8, 32, 0f, 0L,
                Optional.empty(), Optional.of("ground")
        );
    }

    private static MarkerPresentation presentation(
            String typeId,
            String styleId,
            String suffix
    ) {
        return new MarkerPresentation(
                NamespacedId.parse(typeId),
                NamespacedId.parse(styleId),
                NamespacedId.parse("fpsmatch:textures/minimap/markers/" + suffix + ".png"),
                DisplayLabel.translation("fpsmatch.minimap.marker." + suffix),
                1.0
        );
    }

    private record Fixture(
            RuntimeGeneration generation,
            RuntimeEntryStore.ActiveRuntime runtime,
            ContainerPath tilePath
    ) {
    }

    private static final class RecordingPlatform
            implements MinecraftMinimapTextureManager.TexturePlatform {
        private int uploads;

        @Override
        public void upload(
                ResourceLocation location,
                int width,
                int height,
                byte[] rgba
        ) {
            uploads++;
        }

        @Override
        public void release(ResourceLocation location) {
        }
    }
}
