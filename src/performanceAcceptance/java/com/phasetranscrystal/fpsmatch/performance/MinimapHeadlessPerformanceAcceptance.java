package com.phasetranscrystal.fpsmatch.performance;

import com.phasetranscrystal.fpsmatch.common.client.minimap.RuntimeGeneration;
import com.phasetranscrystal.fpsmatch.common.client.minimap.cache.BoundedPngDecoder;
import com.phasetranscrystal.fpsmatch.common.client.minimap.render.MinecraftMinimapTextureManager;
import com.phasetranscrystal.fpsmatch.common.client.minimap.render.MinimapClientSettings;
import com.phasetranscrystal.fpsmatch.common.client.minimap.render.MinimapFrame;
import com.phasetranscrystal.fpsmatch.common.client.minimap.render.MinimapTileUploadQueue;
import com.phasetranscrystal.fpsmatch.common.client.minimap.render.MinimapViewerPose;
import com.phasetranscrystal.fpsmatch.common.client.minimap.render.RuntimeMinimapFramePlanner;
import com.phasetranscrystal.fpsmatch.common.client.minimap.tactical.TacticalMapState;
import com.phasetranscrystal.fpsmatch.common.minimap.server.sync.RuntimeMapSource;
import com.phasetranscrystal.fpsmatch.common.minimap.server.sync.RuntimeMarkerSnapshot;
import com.phasetranscrystal.fpsmatch.common.minimap.server.sync.ServerMinimapRuntimeRouter;
import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapFormatContract;
import com.phasetranscrystal.fpsmatch.core.minimap.format.CanonicalPngCodecV1;
import com.phasetranscrystal.fpsmatch.core.minimap.format.MinimapContainerLayout;
import com.phasetranscrystal.fpsmatch.core.minimap.format.Sha256Digest;
import com.phasetranscrystal.fpsmatch.core.minimap.marker.MarkerSnapshot;
import com.phasetranscrystal.fpsmatch.core.minimap.marker.MinimapViewerContext;
import com.phasetranscrystal.fpsmatch.core.minimap.marker.ViewerRole;
import com.phasetranscrystal.fpsmatch.core.minimap.model.AffineTransform2D;
import com.phasetranscrystal.fpsmatch.core.minimap.model.CanvasBounds;
import com.phasetranscrystal.fpsmatch.core.minimap.model.CanvasPoint;
import com.phasetranscrystal.fpsmatch.core.minimap.model.CanvasRect;
import com.phasetranscrystal.fpsmatch.core.minimap.model.CompilerProfile;
import com.phasetranscrystal.fpsmatch.core.minimap.model.ConnectionsFile;
import com.phasetranscrystal.fpsmatch.core.minimap.model.ContainerPath;
import com.phasetranscrystal.fpsmatch.core.minimap.model.DefaultViewMode;
import com.phasetranscrystal.fpsmatch.core.minimap.model.DisplayLabel;
import com.phasetranscrystal.fpsmatch.core.minimap.model.MapKey;
import com.phasetranscrystal.fpsmatch.core.minimap.model.MinimapFloor;
import com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId;
import com.phasetranscrystal.fpsmatch.core.minimap.model.RectangleGeometry;
import com.phasetranscrystal.fpsmatch.core.minimap.model.RgbaColor;
import com.phasetranscrystal.fpsmatch.core.minimap.model.RuntimeDefinition;
import com.phasetranscrystal.fpsmatch.core.minimap.model.RuntimeEntryDescriptor;
import com.phasetranscrystal.fpsmatch.core.minimap.model.RuntimeFloor;
import com.phasetranscrystal.fpsmatch.core.minimap.model.RuntimeManifest;
import com.phasetranscrystal.fpsmatch.core.minimap.model.RuntimeRegion;
import com.phasetranscrystal.fpsmatch.core.minimap.model.RuntimeRegionsFile;
import com.phasetranscrystal.fpsmatch.core.minimap.model.RuntimeStyle;
import com.phasetranscrystal.fpsmatch.core.minimap.model.RuntimeStylesFile;
import com.phasetranscrystal.fpsmatch.core.minimap.model.TextAppearance;
import com.phasetranscrystal.fpsmatch.core.minimap.performance.MinimapPerformanceContract;
import com.phasetranscrystal.fpsmatch.core.minimap.view.MapDrawCommand;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.MarkerWireMessage;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.MinimapWireCodec;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.MinimapWireMessage;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.RuntimeWireMessage;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.WireIdentity;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

public final class MinimapHeadlessPerformanceAcceptance {
    private static final long NANOS_PER_SECOND = Duration.ofSeconds(1).toNanos();
    private static final int STEADY_MARKERS_PER_UPDATE = 16;
    private static final NamespacedId TYPE = NamespacedId.parse("fpsmatch:type/player");
    private static final NamespacedId STYLE = NamespacedId.parse("fpsmatch:style/default");
    private static final NamespacedId DIMENSION = NamespacedId.parse("minecraft:overworld");
    private static final NamespacedId DOCUMENT = NamespacedId.parse("fpsmatch:document/performance");
    private static final NamespacedId REGION_TYPE = NamespacedId.parse("fpsmatch:region/performance");
    private static final NamespacedId REGION_STYLE = NamespacedId.parse("fpsmatch:style/performance");
    private static final RuntimeGeneration GENERATION = generation();
    private static volatile Object blackhole;

    private MinimapHeadlessPerformanceAcceptance() {
    }

    public static void main(String[] args) throws Exception {
        String profile = System.getProperty("minimapPerformanceProfile", "formal");
        Sampling sampling = "calibration".equals(profile)
                ? new Sampling(Duration.ofSeconds(2), Duration.ofSeconds(5), 1)
                : new Sampling(
                        MinimapPerformanceContract.FORMAL.warmup(),
                        MinimapPerformanceContract.FORMAL.sample(),
                        MinimapPerformanceContract.FORMAL.runs()
                );
        List<FixtureResult> results = List.of(
                measure(MinimapPerformanceContract.STANDARD, sampling),
                measure(MinimapPerformanceContract.STRESS, sampling)
        );
        Report report = new Report(
                profile,
                Instant.now().toString(),
                System.getProperty("os.name") + " " + System.getProperty("os.version"),
                System.getProperty("os.arch"),
                System.getProperty("java.vendor") + " " + System.getProperty("java.version"),
                String.join(" ", ManagementFactory.getRuntimeMXBean().getInputArguments()),
                Runtime.getRuntime().availableProcessors(),
                Runtime.getRuntime().maxMemory(),
                "headless; no OpenGL/Vulkan; texture bytes use frame-resident RGBA tiles",
                sampling,
                results
        );
        Path output = Path.of(System.getProperty(
                "minimapPerformanceReport",
                "build/reports/minimap-performance/headless.json"
        ));
        Files.createDirectories(output.getParent());
        Files.writeString(output, report.json(), StandardCharsets.UTF_8);
        System.out.println(report.summary());
        if (!report.passed()) {
            throw new IllegalStateException(
                    "Headless minimap performance gate failed; see " + output
            );
        }
    }

    private static FixtureResult measure(
            MinimapPerformanceContract.Fixture fixture,
            Sampling sampling
    ) {
        reportProgress("fixture=" + fixture.id() + " start");
        FixtureData data = fixture(fixture);
        List<RunResult> runs = new ArrayList<>();
        for (int run = 0; run < sampling.runs(); run++) {
            reportProgress("fixture=" + fixture.id() + " run=" + (run + 1)
                    + "/" + sampling.runs() + " warmup");
            warmup(data, sampling.warmup());
            reportProgress("fixture=" + fixture.id() + " run=" + (run + 1)
                    + "/" + sampling.runs() + " sample");
            runs.add(sample(data, sampling.sample()));
            reportProgress("fixture=" + fixture.id() + " run=" + (run + 1)
                    + "/" + sampling.runs() + " complete");
        }
        reportProgress("fixture=" + fixture.id() + " complete");
        return FixtureResult.worst(fixture, data, sampling, runs);
    }

    private static void reportProgress(String message) {
        System.out.println("minimap performance progress " + message);
        System.out.flush();
    }

    private static void warmup(FixtureData data, Duration duration) {
        runCombinedWarmup(data, duration);
    }

    private static RunResult sample(FixtureData data, Duration duration) {
        double tacticalOpenMs = tacticalOpenMs(data);
        double renderTaskMaxMs = renderTaskMaxMs(data.tileEdge());

        long baselineHeap = fullGcBaseline();
        data.server().beginRecording();
        CombinedMeasurements measurements;
        try {
            measurements = runCombinedSample(data, duration);
        } finally {
            data.server().endRecording();
        }
        double seconds = duration.toNanos() / (double) NANOS_PER_SECOND;
        long heapDeltaBytes = MinimapPerformanceContract.heapDeltaBytes(
                baselineHeap, measurements.peakHeapBytes()
        );
        double hudP95Ms = percentile(measurements.hudSamples(), 0.95);
        double hudP99Ms = percentile(measurements.hudSamples(), 0.99);
        double hudP95BaselineMs = percentile(
                measurements.hudBaselineSamples(), 0.95
        );
        double hudP99BaselineMs = percentile(
                measurements.hudBaselineSamples(), 0.99
        );
        List<Long> hudOverheadSamples = cpuOverheadSamples(
                measurements.hudSamples(), measurements.hudBaselineSamples()
        );
        double tacticalP95Ms = percentile(
                measurements.tacticalSamples(), 0.95
        );
        double tacticalP99Ms = percentile(
                measurements.tacticalSamples(), 0.99
        );
        double tacticalP95BaselineMs = percentile(
                measurements.tacticalBaselineSamples(), 0.95
        );
        double tacticalP99BaselineMs = percentile(
                measurements.tacticalBaselineSamples(), 0.99
        );
        List<Long> tacticalOverheadSamples = cpuOverheadSamples(
                measurements.tacticalSamples(),
                measurements.tacticalBaselineSamples()
        );
        double serverP95Ms = percentile(measurements.serverSamples(), 0.95);
        double serverP99Ms = percentile(measurements.serverSamples(), 0.99);
        double serverP95BaselineMs = percentile(
                measurements.serverBaselineSamples(), 0.95
        );
        double serverP99BaselineMs = percentile(
                measurements.serverBaselineSamples(), 0.99
        );
        List<Long> serverOverheadSamples = cpuOverheadSamples(
                measurements.serverSamples(), measurements.serverBaselineSamples()
        );
        return new RunResult(
                hudP95Ms,
                hudP99Ms,
                hudP95BaselineMs,
                hudP99BaselineMs,
                percentile(hudOverheadSamples, 0.95),
                percentile(hudOverheadSamples, 0.99),
                tacticalP95Ms,
                tacticalP99Ms,
                tacticalP95BaselineMs,
                tacticalP99BaselineMs,
                percentile(tacticalOverheadSamples, 0.95),
                percentile(tacticalOverheadSamples, 0.99),
                tacticalOpenMs,
                serverP95Ms,
                serverP99Ms,
                serverP95BaselineMs,
                serverP99BaselineMs,
                percentile(serverOverheadSamples, 0.95),
                percentile(serverOverheadSamples, 0.99),
                data.server().markerBytes()
                        / (double) MinimapPerformanceContract.SERVER_RECEIVERS
                        / seconds,
                heapDeltaBytes,
                renderTaskMaxMs,
                measurements.hudSamples().size(),
                measurements.tacticalSamples().size(),
                measurements.serverSamples().size(),
                measurements.hudBaselineSamples().size(),
                measurements.tacticalBaselineSamples().size(),
                measurements.serverBaselineSamples().size(),
                data.server().markerMessages()
        );
    }

    private static void runCombinedWarmup(
            FixtureData data,
            Duration duration
    ) {
        int frameCount = MinimapPerformanceContract.expectedSamples(
                duration, MinimapPerformanceContract.CLIENT_FRAME_HZ
        );
        int serverCount = MinimapPerformanceContract.expectedSamples(
                duration, MinimapPerformanceContract.SERVER_TICK_HZ
        );
        long start = System.nanoTime();
        long deadline = Math.addExact(start, duration.toNanos());
        int frameIndex = 0;
        int serverIndex = 0;
        while (frameIndex < frameCount || serverIndex < serverCount) {
            long nextClientNanos = frameIndex < frameCount
                    ? scheduled(
                    start, frameIndex, MinimapPerformanceContract.CLIENT_FRAME_HZ
            ) : Long.MAX_VALUE;
            long nextServerNanos = serverIndex < serverCount
                    ? scheduled(
                    start, serverIndex, MinimapPerformanceContract.SERVER_TICK_HZ
            ) : Long.MAX_VALUE;
            if (nextClientNanos <= nextServerNanos) {
                await(nextClientNanos);
                capabilityDisabledClientFrame(ClientView.HUD);
                blackhole = planClientFrame(data, ClientView.HUD);
                capabilityDisabledClientFrame(ClientView.TACTICAL);
                blackhole = planClientFrame(data, ClientView.TACTICAL);
                frameIndex++;
            } else {
                await(nextServerNanos);
                capabilityDisabledServerTick(data);
                data.server().tick();
                serverIndex++;
            }
        }
        await(deadline);
    }

    private static CombinedMeasurements runCombinedSample(
            FixtureData data,
            Duration duration
    ) {
        int frameCount = MinimapPerformanceContract.expectedSamples(
                duration, MinimapPerformanceContract.CLIENT_FRAME_HZ
        );
        int serverCount = MinimapPerformanceContract.expectedSamples(
                duration, MinimapPerformanceContract.SERVER_TICK_HZ
        );
        ArrayList<Long> hudSamples = new ArrayList<>(frameCount);
        ArrayList<Long> tacticalSamples = new ArrayList<>(frameCount);
        ArrayList<Long> serverSamples = new ArrayList<>(serverCount);
        ArrayList<Long> hudBaselineSamples = new ArrayList<>(frameCount);
        ArrayList<Long> tacticalBaselineSamples = new ArrayList<>(frameCount);
        ArrayList<Long> serverBaselineSamples = new ArrayList<>(serverCount);
        long peakHeap = usedHeap();
        long start = System.nanoTime();
        long deadline = Math.addExact(start, duration.toNanos());
        int frameIndex = 0;
        int serverIndex = 0;
        while (frameIndex < frameCount || serverIndex < serverCount) {
            long nextClientNanos = frameIndex < frameCount
                    ? scheduled(
                    start, frameIndex, MinimapPerformanceContract.CLIENT_FRAME_HZ
            ) : Long.MAX_VALUE;
            long nextServerNanos = serverIndex < serverCount
                    ? scheduled(
                    start, serverIndex, MinimapPerformanceContract.SERVER_TICK_HZ
            ) : Long.MAX_VALUE;
            if (nextClientNanos <= nextServerNanos) {
                await(nextClientNanos);
                long before = System.nanoTime();
                capabilityDisabledClientFrame(ClientView.HUD);
                hudBaselineSamples.add(System.nanoTime() - before);
                before = System.nanoTime();
                blackhole = planClientFrame(data, ClientView.HUD);
                hudSamples.add(System.nanoTime() - before);
                before = System.nanoTime();
                capabilityDisabledClientFrame(ClientView.TACTICAL);
                tacticalBaselineSamples.add(System.nanoTime() - before);
                before = System.nanoTime();
                blackhole = planClientFrame(data, ClientView.TACTICAL);
                tacticalSamples.add(System.nanoTime() - before);
                frameIndex++;
            } else {
                await(nextServerNanos);
                long before = System.nanoTime();
                capabilityDisabledServerTick(data);
                serverBaselineSamples.add(System.nanoTime() - before);
                before = System.nanoTime();
                data.server().tick();
                blackhole = data.server();
                serverSamples.add(System.nanoTime() - before);
                serverIndex++;
            }
            peakHeap = Math.max(peakHeap, usedHeap());
        }
        await(deadline);
        return new CombinedMeasurements(
                List.copyOf(hudSamples),
                List.copyOf(tacticalSamples),
                List.copyOf(serverSamples),
                List.copyOf(hudBaselineSamples),
                List.copyOf(tacticalBaselineSamples),
                List.copyOf(serverBaselineSamples),
                Math.max(peakHeap, usedHeap())
        );
    }

    private static void capabilityDisabledClientFrame(ClientView view) {
        blackhole = view;
    }

    private static void capabilityDisabledServerTick(FixtureData data) {
        blackhole = data.server();
    }

    private static MinimapFrame planClientFrame(
            FixtureData data,
            ClientView view
    ) {
        if (view == ClientView.HUD) {
            return data.planner().planHud(
                    GENERATION,
                    data.definition(),
                    data.paths(),
                    data.viewer(),
                    data.markers(),
                    data.settings(),
                    192,
                    192
            );
        }
        return data.planner().planTactical(
                GENERATION,
                data.definition(),
                data.paths(),
                data.viewer(),
                data.markers(),
                data.settings(),
                data.tacticalState()
        );
    }

    private static long scheduled(long start, int index, int frequencyHz) {
        return Math.addExact(
                start,
                Math.multiplyExact((long) index, NANOS_PER_SECOND) / frequencyHz
        );
    }

    private static void await(long targetNanos) {
        while (true) {
            long remaining = targetNanos - System.nanoTime();
            if (remaining <= 0) {
                return;
            }
            if (Thread.currentThread().isInterrupted()) {
                throw new IllegalStateException("Performance acceptance was interrupted");
            }
            LockSupport.parkNanos(Math.min(remaining, 2_000_000L));
        }
    }

    private static double tacticalOpenMs(FixtureData data) {
        RuntimeMinimapFramePlanner planner = new RuntimeMinimapFramePlanner();
        long before = System.nanoTime();
        blackhole = planner.planTactical(
                GENERATION,
                data.definition(),
                data.paths(),
                data.viewer(),
                data.markers(),
                data.settings(),
                data.tacticalState()
        );
        return (System.nanoTime() - before) / 1_000_000.0;
    }

    private static double renderTaskMaxMs(int tileEdge) {
        byte[] rgba = new byte[Math.multiplyExact(
                Math.multiplyExact(tileEdge, tileEdge), 4
        )];
        byte[] png = CanonicalPngCodecV1.encode(tileEdge, tileEdge, rgba);
        ArrayList<Runnable> renderTasks = new ArrayList<>();
        HeadlessTexturePlatform platform = new HeadlessTexturePlatform();
        MinecraftMinimapTextureManager textures = new MinecraftMinimapTextureManager(
                () -> Optional.of(GENERATION),
                () -> true,
                platform
        );
        MinimapTileUploadQueue queue = new MinimapTileUploadQueue(
                new BoundedPngDecoder(
                        Math.max(1, png.length),
                        Math.multiplyExact((long) tileEdge * tileEdge, 4L)
                ),
                Runnable::run,
                renderTasks::add,
                () -> Optional.of(GENERATION),
                textures,
                16
        );
        long maximum = 0L;
        for (int index = 0; index < 10; index++) {
            if (!queue.request(GENERATION, "performance/tile_" + index, png)
                    || renderTasks.size() != 1) {
                throw new IllegalStateException("Headless render task was not queued");
            }
            Runnable renderTask = renderTasks.remove(0);
            long before = System.nanoTime();
            renderTask.run();
            long elapsed = System.nanoTime() - before;
            if (index >= 2) {
                maximum = Math.max(maximum, elapsed);
            }
        }
        textures.reset();
        if (platform.uploads != platform.releases) {
            throw new IllegalStateException("Headless texture audit leaked a texture");
        }
        return maximum / 1_000_000.0;
    }

    private static FixtureData fixture(MinimapPerformanceContract.Fixture fixture) {
        int tileEdge = 256;
        int zoomLevels = zoomLevels(fixture.canvasEdge(), tileEdge);
        List<RuntimeFloor> floors = new ArrayList<>();
        for (int index = 0; index < fixture.floors(); index++) {
            String id = "floor_" + index;
            floors.add(new RuntimeFloor(
                    new MinimapFloor(
                            id,
                            index * 16.0,
                            index * 16.0 + 16.0,
                            fixture.floors() - index,
                            1.0,
                            2.0
                    ),
                    DisplayLabel.literal(id),
                    Optional.of(new CanvasRect(
                            0, 0, fixture.canvasEdge(), fixture.canvasEdge()
                    )),
                    new AffineTransform2D(1, 0, 0, 0, 1, 0),
                    zoomLevels
            ));
        }
        MapKey mapKey = GENERATION.mapKey();
        RuntimeManifest manifest = new RuntimeManifest(
                MinimapFormatContract.CURRENT,
                DOCUMENT,
                mapKey,
                1L,
                GENERATION.runtimeHash(),
                new CompilerProfile(
                        NamespacedId.parse("fpsmatch:compiler/performance"),
                        MinimapFormatContract.CURRENT
                ),
                new CanvasBounds(fixture.canvasEdge(), fixture.canvasEdge()),
                DefaultViewMode.FOLLOW_PLAYER,
                floors,
                tileEdge,
                List.of()
        );
        List<RuntimeRegion> regions = new ArrayList<>(fixture.regions());
        int columns = (int) Math.ceil(Math.sqrt(fixture.regions()));
        double cell = fixture.canvasEdge() / (double) columns;
        for (int index = 0; index < fixture.regions(); index++) {
            int column = index % columns;
            int row = index / columns;
            double minU = column * cell;
            double minV = row * cell;
            double maxU = Math.min(
                    fixture.canvasEdge(), minU + Math.max(1.0, cell * 0.8)
            );
            double maxV = Math.min(
                    fixture.canvasEdge(), minV + Math.max(1.0, cell * 0.8)
            );
            regions.add(new RuntimeRegion(
                    "region_" + index,
                    "floor_0",
                    DisplayLabel.literal("R" + index),
                    new RectangleGeometry(new CanvasRect(minU, minV, maxU, maxV)),
                    REGION_TYPE,
                    List.of(),
                    Optional.empty(),
                    REGION_STYLE,
                    new CanvasPoint((minU + maxU) * 0.5, (minV + maxV) * 0.5),
                    index,
                    0.0,
                    64.0
            ));
        }
        RuntimeDefinition definition = new RuntimeDefinition(
                manifest,
                new RuntimeRegionsFile(regions),
                new ConnectionsFile(List.of()),
                new RuntimeStylesFile(List.of(new RuntimeStyle(
                        REGION_STYLE,
                        Optional.of(new TextAppearance(
                                new RgbaColor(255, 255, 255, 255), 1.0
                        )),
                        Optional.empty()
                )))
        );
        List<MarkerSnapshot.Marker> markers = new ArrayList<>(fixture.markers());
        for (int index = 0; index < fixture.markers(); index++) {
            double position = (index + 1.0) * fixture.canvasEdge()
                    / (fixture.markers() + 1.0);
            markers.add(new MarkerSnapshot.Marker(
                    NamespacedId.parse("fpsmatch:marker/m" + index),
                    TYPE,
                    STYLE,
                    position,
                    8.0,
                    fixture.canvasEdge() - position,
                    index,
                    index,
                    Optional.empty(),
                    Optional.of("floor_0")
            ));
        }
        Set<ContainerPath> paths = coarsestPaths(
                fixture.canvasEdge(), tileEdge, zoomLevels
        );
        MinimapViewerPose viewer = new MinimapViewerPose(
                fixture.canvasEdge() * 0.5,
                8.0,
                fixture.canvasEdge() * 0.5,
                0f
        );
        MinimapClientSettings settings = MinimapClientSettings.defaults();
        TacticalMapState tacticalState = TacticalMapState.initial()
                .withViewport(1280, 720);
        RuntimeMinimapFramePlanner planner = new RuntimeMinimapFramePlanner();
        long residentTextureBytes = residentTextureBytes(
                definition,
                paths,
                markers,
                viewer,
                settings,
                tacticalState,
                planner
        );
        MinimapViewerContext context = new MinimapViewerContext(
                ViewerRole.ACTIVE_PLAYER,
                "performance",
                Optional.of(markers.get(0).markerId()),
                true,
                false
        );
        ResetAudit resetAudit = resetAudit(markers, context, runtimeIdentity());
        ServerLoad server = new ServerLoad(
                markers,
                context,
                runtimeIdentity(),
                MinimapPerformanceContract.SERVER_RECEIVERS
        );
        return new FixtureData(
                definition,
                paths,
                List.copyOf(markers),
                viewer,
                settings,
                tacticalState,
                planner,
                server,
                tileEdge,
                residentTextureBytes,
                resetAudit
        );
    }

    private static int zoomLevels(int canvasEdge, int tileEdge) {
        int levels = 1;
        int scaled = canvasEdge;
        while (scaled > tileEdge) {
            scaled = (scaled + 1) / 2;
            levels++;
        }
        return levels;
    }

    private static Set<ContainerPath> coarsestPaths(
            int canvasEdge,
            int tileEdge,
            int zoomLevels
    ) {
        int zoom = zoomLevels - 1;
        int scaled = scaledEdge(canvasEdge, zoom);
        int tiles = (scaled + tileEdge - 1) / tileEdge;
        java.util.LinkedHashSet<ContainerPath> paths = new java.util.LinkedHashSet<>();
        for (int y = 0; y < tiles; y++) {
            for (int x = 0; x < tiles; x++) {
                paths.add(ContainerPath.parse(
                        "floors/floor_0/tiles/" + zoom + "/" + x + "_" + y + ".png"
                ));
            }
        }
        return Set.copyOf(paths);
    }

    private static long residentTextureBytes(
            RuntimeDefinition definition,
            Set<ContainerPath> paths,
            List<MarkerSnapshot.Marker> markers,
            MinimapViewerPose viewer,
            MinimapClientSettings settings,
            TacticalMapState tacticalState,
            RuntimeMinimapFramePlanner planner
    ) {
        MinimapFrame hud = planner.planHud(
                GENERATION,
                definition,
                paths,
                viewer,
                markers,
                settings,
                192,
                192
        );
        MinimapFrame tactical = planner.planTactical(
                GENERATION,
                definition,
                paths,
                viewer,
                markers,
                settings,
                tacticalState
        );
        ArrayList<MinimapPerformanceContract.ResidentTexture> textures = new ArrayList<>();
        collectResidentTextures(textures, hud, definition.manifest());
        collectResidentTextures(textures, tactical, definition.manifest());
        planner.reset();
        return MinimapPerformanceContract.residentTextureBytes(textures);
    }

    private static void collectResidentTextures(
            List<MinimapPerformanceContract.ResidentTexture> textures,
            MinimapFrame frame,
            RuntimeManifest manifest
    ) {
        for (MapDrawCommand command : frame.commands()) {
            if (!(command instanceof MapDrawCommand.Tile tile)) {
                continue;
            }
            MinimapContainerLayout.RuntimeTileAddress address =
                    MinimapContainerLayout.parseRuntimeTile(
                            ContainerPath.parse(tile.textureKey())
                    ).orElseThrow();
            int scaledWidth = scaledEdge(manifest.canvas().width(), address.zoom());
            int scaledHeight = scaledEdge(manifest.canvas().height(), address.zoom());
            int width = Math.min(
                    manifest.tileEdge(),
                    scaledWidth - address.x() * manifest.tileEdge()
            );
            int height = Math.min(
                    manifest.tileEdge(),
                    scaledHeight - address.y() * manifest.tileEdge()
            );
            textures.add(new MinimapPerformanceContract.ResidentTexture(
                    tile.textureKey(), width, height
            ));
        }
    }

    private static int scaledEdge(int edge, int zoom) {
        long divisor = 1L << zoom;
        return Math.toIntExact((edge + divisor - 1L) / divisor);
    }

    private static List<MarkerSnapshot.Marker> steadyMarkers(
            List<MarkerSnapshot.Marker> markers,
            long serverTick
    ) {
        if (serverTick < 0) {
            return markers;
        }
        long slot = markerSlot(serverTick);
        int batches = Math.max(
                1,
                (markers.size() + STEADY_MARKERS_PER_UPDATE - 1)
                        / STEADY_MARKERS_PER_UPDATE
        );
        ArrayList<MarkerSnapshot.Marker> moved = new ArrayList<>(markers.size());
        for (int index = 0; index < markers.size(); index++) {
            MarkerSnapshot.Marker marker = markers.get(index);
            int batch = index % batches;
            long latestSlot = slot - Math.floorMod(slot - batch, batches);
            if (latestSlot < 0) {
                moved.add(marker);
                continue;
            }
            moved.add(copyMarker(
                    marker,
                    marker.x() + (latestSlot + 1L) * 0.01,
                    marker.updatedTick() + latestSlot + 1L
            ));
        }
        return List.copyOf(moved);
    }

    private static List<MarkerSnapshot.Marker> movedAll(
            List<MarkerSnapshot.Marker> markers
    ) {
        return markers.stream()
                .map(marker -> copyMarker(
                        marker,
                        marker.x() + 0.5,
                        marker.updatedTick() + 1L
                ))
                .toList();
    }

    private static MarkerSnapshot.Marker copyMarker(
            MarkerSnapshot.Marker marker,
            double x,
            long updatedTick
    ) {
        return new MarkerSnapshot.Marker(
                marker.markerId(),
                marker.typeId(),
                marker.styleId(),
                x,
                marker.y(),
                marker.z(),
                marker.yaw(),
                updatedTick,
                marker.expiresTick(),
                marker.floorSlug(),
                marker.stateFields()
        );
    }

    private static long markerSlot(long serverTick) {
        return Math.addExact(
                Math.multiplyExact(
                        serverTick / MinimapPerformanceContract.SERVER_TICK_HZ,
                        MinimapPerformanceContract.MARKER_UPDATE_HZ
                ),
                serverTick % MinimapPerformanceContract.SERVER_TICK_HZ
                        * MinimapPerformanceContract.MARKER_UPDATE_HZ
                        / MinimapPerformanceContract.SERVER_TICK_HZ
        );
    }

    private static ResetAudit resetAudit(
            List<MarkerSnapshot.Marker> markers,
            MinimapViewerContext context,
            WireIdentity.RuntimeIdentity runtime
    ) {
        AtomicReference<List<MarkerSnapshot.Marker>> snapshot =
                new AtomicReference<>(markers);
        EncodedMarkerRecorder recorder = new EncodedMarkerRecorder();
        PerformanceRuntimeSource source = new PerformanceRuntimeSource(runtime);
        AtomicLong ids = new AtomicLong(1L);
        ServerMinimapRuntimeRouter router = new ServerMinimapRuntimeRouter(
                (actorId, target) -> Optional.of(source),
                (actorId, target) -> Optional.of(new RuntimeMarkerSnapshot(
                        context, snapshot.get()
                )),
                recorder::send,
                () -> new UUID(9L, ids.getAndIncrement()),
                () -> MinimapPerformanceContract.MARKER_UPDATE_HZ
        );
        UUID actor = new UUID(8L, 1L);
        WireIdentity.ScopeLease lease = new WireIdentity.ScopeLease(
                WireIdentity.Scope.MATCH_HUD, 1L, 1L
        );
        router.dispatch(actor, new RuntimeWireMessage.Subscribe(
                new UUID(7L, 1L),
                lease,
                runtime.binding().target(),
                Optional.empty()
        ));
        recorder.begin();
        snapshot.set(movedAll(markers));
        router.tick(0L);
        recorder.end();
        int expectedPages = Math.max(
                1,
                (markers.size() + MarkerWireMessage.MAX_PAGE_ITEMS - 1)
                        / MarkerWireMessage.MAX_PAGE_ITEMS
        );
        if (recorder.resetPages() != expectedPages
                || recorder.markerMessages() != expectedPages) {
            throw new IllegalStateException(
                    "Marker reset pagination audit did not use every required page"
            );
        }
        return new ResetAudit(
                recorder.resetPages(), recorder.markerBytes()
        );
    }

    private static RuntimeGeneration generation() {
        MapKey mapKey = new MapKey("performance", "headless");
        return new RuntimeGeneration(
                1L,
                "local-performance",
                mapKey,
                DOCUMENT,
                1L,
                Sha256Digest.of("runtime".getBytes(StandardCharsets.UTF_8)),
                DIMENSION,
                1L
        );
    }

    private static WireIdentity.RuntimeIdentity runtimeIdentity() {
        return new WireIdentity.RuntimeIdentity(
                new WireIdentity.DocumentBinding(
                        new WireIdentity.MapTarget(GENERATION.mapKey(), DIMENSION),
                        DOCUMENT
                ),
                1L,
                GENERATION.runtimeHash(),
                Optional.empty()
        );
    }

    private static long fullGcBaseline() {
        System.gc();
        System.runFinalization();
        System.gc();
        try {
            Thread.sleep(100L);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Full-GC baseline wait was interrupted", interrupted);
        }
        return usedHeap();
    }

    private static long usedHeap() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private static double percentile(List<Long> samples, double rank) {
        long[] values = samples.stream().mapToLong(Long::longValue).toArray();
        return MinimapPerformanceContract.percentileMillis(values, rank);
    }

    private static List<Long> cpuOverheadSamples(
            List<Long> enabledSamples,
            List<Long> baselineSamples
    ) {
        if (enabledSamples.size() != baselineSamples.size()) {
            throw new IllegalArgumentException("CPU sample counts must match");
        }
        ArrayList<Long> overhead = new ArrayList<>(enabledSamples.size());
        for (int index = 0; index < enabledSamples.size(); index++) {
            overhead.add(MinimapPerformanceContract.cpuOverheadNanos(
                    enabledSamples.get(index), baselineSamples.get(index)
            ));
        }
        return List.copyOf(overhead);
    }

    private static final class ServerLoad {
        private final AtomicLong serverTick = new AtomicLong(-1L);
        private final List<MarkerSnapshot.Marker> markers;
        private final MinimapViewerContext context;
        private final AtomicReference<RuntimeMarkerSnapshot> snapshot;
        private final EncodedMarkerRecorder recorder = new EncodedMarkerRecorder();
        private final ServerMinimapRuntimeRouter router;

        private ServerLoad(
                List<MarkerSnapshot.Marker> markers,
                MinimapViewerContext context,
                WireIdentity.RuntimeIdentity runtime,
                int receivers
        ) {
            this.markers = markers;
            this.context = context;
            this.snapshot = new AtomicReference<>(new RuntimeMarkerSnapshot(
                    context, markers
            ));
            PerformanceRuntimeSource source = new PerformanceRuntimeSource(runtime);
            AtomicLong ids = new AtomicLong(1L);
            router = new ServerMinimapRuntimeRouter(
                    (actorId, target) -> Optional.of(source),
                    (actorId, target) -> Optional.of(snapshot.get()),
                    recorder::send,
                    () -> new UUID(6L, ids.getAndIncrement()),
                    () -> MinimapPerformanceContract.MARKER_UPDATE_HZ
            );
            for (int index = 0; index < receivers; index++) {
                UUID actor = new UUID(5L, index + 1L);
                WireIdentity.ScopeLease lease = new WireIdentity.ScopeLease(
                        WireIdentity.Scope.MATCH_HUD, 1L, index + 1L
                );
                router.dispatch(actor, new RuntimeWireMessage.Subscribe(
                        new UUID(4L, index + 1L),
                        lease,
                        runtime.binding().target(),
                        Optional.empty()
                ));
            }
        }

        private void tick() {
            long nowTick = serverTick.incrementAndGet();
            if (nowTick == 0L
                    || markerSlot(nowTick) > markerSlot(nowTick - 1L)) {
                snapshot.set(new RuntimeMarkerSnapshot(
                        context, steadyMarkers(markers, nowTick)
                ));
            }
            router.tick(nowTick);
        }

        private void beginRecording() {
            recorder.begin();
        }

        private void endRecording() {
            recorder.end();
        }

        private long markerBytes() {
            return recorder.markerBytes();
        }

        private long markerMessages() {
            return recorder.markerMessages();
        }
    }

    private static final class EncodedMarkerRecorder
            implements MinimapWireCodec.FrameSink {
        private boolean recording;
        private long markerBytes;
        private long markerMessages;
        private int resetPages;
        private long checksum;

        private void send(UUID actorId, MinimapWireMessage message) {
            if (message instanceof MarkerWireMessage marker) {
                MinimapWireCodec.PreparedMarkerFrame frame =
                        MinimapWireCodec.prepareMarkerFrame(marker);
                checksum = 1L;
                frame.writeTo(this);
                blackhole = checksum;
                if (!recording) {
                    return;
                }
                markerBytes = Math.addExact(markerBytes, frame.length());
                markerMessages = Math.addExact(markerMessages, 1L);
                if (message instanceof MarkerWireMessage.Reset) {
                    resetPages++;
                }
                return;
            }
            blackhole = MinimapWireCodec.encode(message);
        }

        @Override
        public void writeByte(int value) {
            checksum = checksum * 31L + (value & 0xff);
        }

        @Override
        public void writeBytes(byte[] value) {
            for (byte next : value) {
                checksum = checksum * 31L + (next & 0xff);
            }
        }

        private void begin() {
            markerBytes = 0L;
            markerMessages = 0L;
            resetPages = 0;
            recording = true;
        }

        private void end() {
            recording = false;
        }

        private long markerBytes() {
            return markerBytes;
        }

        private long markerMessages() {
            return markerMessages;
        }

        private int resetPages() {
            return resetPages;
        }
    }

    private static final class PerformanceRuntimeSource implements RuntimeMapSource {
        private final WireIdentity.RuntimeIdentity identity;
        private final byte[] manifestBytes;

        private PerformanceRuntimeSource(WireIdentity.RuntimeIdentity identity) {
            this.identity = identity;
            this.manifestBytes = "headless-performance-manifest"
                    .getBytes(StandardCharsets.UTF_8);
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
            return Optional.empty();
        }

        @Override
        public InputStream openEntry(ContainerPath path) throws IOException {
            throw new IOException("Performance source has no streamed entries");
        }

        @Override
        public void close() {
        }
    }

    private static final class HeadlessTexturePlatform
            implements MinecraftMinimapTextureManager.TexturePlatform {
        private int uploads;
        private int releases;

        @Override
        public void upload(
                Identifier location,
                int width,
                int height,
                byte[] rgba
        ) {
            long checksum = 1L;
            for (int index = 0; index < rgba.length; index += 4) {
                int red = rgba[index] & 0xff;
                int green = rgba[index + 1] & 0xff;
                int blue = rgba[index + 2] & 0xff;
                int alpha = rgba[index + 3] & 0xff;
                long packedPixel = (long) alpha << 24
                        | (long) blue << 16
                        | (long) green << 8
                        | red;
                checksum = checksum * 31L + packedPixel;
            }
            blackhole = checksum;
            uploads++;
        }

        @Override
        public void release(Identifier location) {
            releases++;
        }
    }

    private record FixtureData(
            RuntimeDefinition definition,
            Set<ContainerPath> paths,
            List<MarkerSnapshot.Marker> markers,
            MinimapViewerPose viewer,
            MinimapClientSettings settings,
            TacticalMapState tacticalState,
            RuntimeMinimapFramePlanner planner,
            ServerLoad server,
            int tileEdge,
            long residentTextureBytes,
            ResetAudit resetAudit
    ) {
    }

    private record Sampling(Duration warmup, Duration sample, int runs) {
    }

    private enum ClientView {
        HUD,
        TACTICAL
    }

    private record CombinedMeasurements(
            List<Long> hudSamples,
            List<Long> tacticalSamples,
            List<Long> serverSamples,
            List<Long> hudBaselineSamples,
            List<Long> tacticalBaselineSamples,
            List<Long> serverBaselineSamples,
            long peakHeapBytes
    ) {
    }

    private record ResetAudit(int pageCount, long encodedBytesPerPlayer) {
    }

    private record RunResult(
            double hudP95Ms,
            double hudP99Ms,
            double hudP95BaselineMs,
            double hudP99BaselineMs,
            double hudP95OverheadMs,
            double hudP99OverheadMs,
            double tacticalP95Ms,
            double tacticalP99Ms,
            double tacticalP95BaselineMs,
            double tacticalP99BaselineMs,
            double tacticalP95OverheadMs,
            double tacticalP99OverheadMs,
            double tacticalOpenMs,
            double serverP95Ms,
            double serverP99Ms,
            double serverP95BaselineMs,
            double serverP99BaselineMs,
            double serverP95OverheadMs,
            double serverP99OverheadMs,
            double markerBytesPerSecond,
            long heapDeltaBytes,
            double renderTaskMaxMs,
            int hudSamples,
            int tacticalSamples,
            int serverSamples,
            int hudBaselineSamples,
            int tacticalBaselineSamples,
            int serverBaselineSamples,
            long markerMessages
    ) {
    }

    private record FixtureResult(
            String id,
            long residentTextureBytes,
            ResetAudit resetAudit,
            RunResult worst,
            boolean complete,
            boolean thresholdsPassed
    ) {
        private static FixtureResult worst(
                MinimapPerformanceContract.Fixture fixture,
                FixtureData data,
                Sampling sampling,
                List<RunResult> runs
        ) {
            RunResult worst = new RunResult(
                    runs.stream().mapToDouble(RunResult::hudP95Ms).max().orElseThrow(),
                    runs.stream().mapToDouble(RunResult::hudP99Ms).max().orElseThrow(),
                    runs.stream().mapToDouble(RunResult::hudP95BaselineMs).max().orElseThrow(),
                    runs.stream().mapToDouble(RunResult::hudP99BaselineMs).max().orElseThrow(),
                    runs.stream().mapToDouble(RunResult::hudP95OverheadMs).max().orElseThrow(),
                    runs.stream().mapToDouble(RunResult::hudP99OverheadMs).max().orElseThrow(),
                    runs.stream().mapToDouble(RunResult::tacticalP95Ms).max().orElseThrow(),
                    runs.stream().mapToDouble(RunResult::tacticalP99Ms).max().orElseThrow(),
                    runs.stream().mapToDouble(RunResult::tacticalP95BaselineMs).max().orElseThrow(),
                    runs.stream().mapToDouble(RunResult::tacticalP99BaselineMs).max().orElseThrow(),
                    runs.stream().mapToDouble(RunResult::tacticalP95OverheadMs).max().orElseThrow(),
                    runs.stream().mapToDouble(RunResult::tacticalP99OverheadMs).max().orElseThrow(),
                    runs.stream().mapToDouble(RunResult::tacticalOpenMs).max().orElseThrow(),
                    runs.stream().mapToDouble(RunResult::serverP95Ms).max().orElseThrow(),
                    runs.stream().mapToDouble(RunResult::serverP99Ms).max().orElseThrow(),
                    runs.stream().mapToDouble(RunResult::serverP95BaselineMs).max().orElseThrow(),
                    runs.stream().mapToDouble(RunResult::serverP99BaselineMs).max().orElseThrow(),
                    runs.stream().mapToDouble(RunResult::serverP95OverheadMs).max().orElseThrow(),
                    runs.stream().mapToDouble(RunResult::serverP99OverheadMs).max().orElseThrow(),
                    runs.stream().mapToDouble(RunResult::markerBytesPerSecond).max().orElseThrow(),
                    runs.stream().mapToLong(RunResult::heapDeltaBytes).max().orElseThrow(),
                    runs.stream().mapToDouble(RunResult::renderTaskMaxMs).max().orElseThrow(),
                    runs.stream().mapToInt(RunResult::hudSamples).min().orElseThrow(),
                    runs.stream().mapToInt(RunResult::tacticalSamples).min().orElseThrow(),
                    runs.stream().mapToInt(RunResult::serverSamples).min().orElseThrow(),
                    runs.stream().mapToInt(RunResult::hudBaselineSamples).min().orElseThrow(),
                    runs.stream().mapToInt(RunResult::tacticalBaselineSamples).min().orElseThrow(),
                    runs.stream().mapToInt(RunResult::serverBaselineSamples).min().orElseThrow(),
                    runs.stream().mapToLong(RunResult::markerMessages).min().orElseThrow()
            );
            int expectedFrames = MinimapPerformanceContract.expectedSamples(
                    sampling.sample(), MinimapPerformanceContract.CLIENT_FRAME_HZ
            );
            int expectedServer = MinimapPerformanceContract.expectedSamples(
                    sampling.sample(), MinimapPerformanceContract.SERVER_TICK_HZ
            );
            int expectedResetPages = Math.max(
                    1,
                    (fixture.markers() + MarkerWireMessage.MAX_PAGE_ITEMS - 1)
                            / MarkerWireMessage.MAX_PAGE_ITEMS
            );
            boolean complete = worst.hudSamples() == expectedFrames
                    && worst.tacticalSamples() == expectedFrames
                    && worst.serverSamples() == expectedServer
                    && worst.hudBaselineSamples() == expectedFrames
                    && worst.tacticalBaselineSamples() == expectedFrames
                    && worst.serverBaselineSamples() == expectedServer
                    && worst.markerMessages() > 0
                    && data.resetAudit().pageCount() == expectedResetPages
                    && data.resetAudit().encodedBytesPerPlayer() > 0
                    && data.residentTextureBytes() > 0;
            boolean standard = fixture.equals(MinimapPerformanceContract.STANDARD);
            boolean thresholdsPassed = complete && (standard
                    ? worst.hudP95OverheadMs()
                    <= MinimapPerformanceContract.STANDARD_HUD_P95_MS
                    && worst.hudP99OverheadMs()
                    <= MinimapPerformanceContract.STANDARD_HUD_P99_MS
                    && worst.tacticalP95OverheadMs()
                    <= MinimapPerformanceContract.STANDARD_TACTICAL_P95_MS
                    && worst.tacticalOpenMs()
                    <= MinimapPerformanceContract.TACTICAL_OPEN_MAX_MS
                    && worst.renderTaskMaxMs()
                    <= MinimapPerformanceContract.RENDER_TASK_MAX_MS
                    && worst.heapDeltaBytes()
                    <= MinimapPerformanceContract.STANDARD_HEAP_MAX_BYTES
                    && data.residentTextureBytes()
                    <= MinimapPerformanceContract.STANDARD_TEXTURE_MAX_BYTES
                    && worst.serverP95OverheadMs()
                    <= MinimapPerformanceContract.STANDARD_SERVER_P95_MS
                    && worst.serverP99OverheadMs()
                    <= MinimapPerformanceContract.STANDARD_SERVER_P99_MS
                    && worst.markerBytesPerSecond()
                    <= MinimapPerformanceContract.MARKER_BYTES_PER_SECOND_MAX
                    : worst.hudP99OverheadMs()
                    <= MinimapPerformanceContract.STRESS_HUD_P99_MS
                    && worst.serverP99OverheadMs()
                    <= MinimapPerformanceContract.STRESS_SERVER_P99_MS);
            return new FixtureResult(
                    fixture.id(),
                    data.residentTextureBytes(),
                    data.resetAudit(),
                    worst,
                    complete,
                    thresholdsPassed
            );
        }
    }

    private record Report(
            String profile,
            String timestamp,
            String os,
            String architecture,
            String java,
            String jvmArguments,
            int processors,
            long maxHeapBytes,
            String gpuStatus,
            Sampling sampling,
            List<FixtureResult> fixtures
    ) {
        private boolean gateEnforced() {
            return !"calibration".equals(profile);
        }

        private boolean formalThresholdsPassed() {
            return fixtures.stream().allMatch(FixtureResult::thresholdsPassed);
        }

        private boolean passed() {
            return fixtures.stream().allMatch(FixtureResult::complete)
                    && (!gateEnforced() || formalThresholdsPassed());
        }

        private String summary() {
            return "minimap performance profile=" + profile
                    + " gateEnforced=" + gateEnforced()
                    + " thresholdsPassed=" + formalThresholdsPassed()
                    + " passed=" + passed()
                    + " fixtures=" + fixtures;
        }

        private String json() {
            StringBuilder json = new StringBuilder();
            json.append("{\n")
                    .append("  \"profile\": \"").append(escape(profile)).append("\",\n")
                    .append("  \"timestamp\": \"").append(escape(timestamp)).append("\",\n")
                    .append("  \"os\": \"").append(escape(os)).append("\",\n")
                    .append("  \"architecture\": \"").append(escape(architecture)).append("\",\n")
                    .append("  \"java\": \"").append(escape(java)).append("\",\n")
                    .append("  \"jvmArguments\": \"").append(escape(jvmArguments)).append("\",\n")
                    .append("  \"processors\": ").append(processors).append(",\n")
                    .append("  \"maxHeapBytes\": ").append(maxHeapBytes).append(",\n")
                    .append("  \"gpuStatus\": \"").append(escape(gpuStatus)).append("\",\n")
                    .append("  \"warmupMillis\": ").append(sampling.warmup().toMillis()).append(",\n")
                    .append("  \"sampleMillis\": ").append(sampling.sample().toMillis()).append(",\n")
                    .append("  \"runs\": ").append(sampling.runs()).append(",\n")
                    .append("  \"clientFrameHz\": ")
                    .append(MinimapPerformanceContract.CLIENT_FRAME_HZ).append(",\n")
                    .append("  \"serverTickHz\": ")
                    .append(MinimapPerformanceContract.SERVER_TICK_HZ).append(",\n")
                    .append("  \"markerUpdateHz\": ")
                    .append(MinimapPerformanceContract.MARKER_UPDATE_HZ).append(",\n")
                    .append("  \"serverReceivers\": ")
                    .append(MinimapPerformanceContract.SERVER_RECEIVERS).append(",\n")
                    .append("  \"gateEnforced\": ").append(gateEnforced()).append(",\n")
                    .append("  \"formalThresholdsPassed\": ")
                    .append(formalThresholdsPassed()).append(",\n")
                    .append("  \"passed\": ").append(passed()).append(",\n")
                    .append("  \"fixtures\": [\n");
            for (int index = 0; index < fixtures.size(); index++) {
                FixtureResult fixture = fixtures.get(index);
                RunResult result = fixture.worst();
                json.append("    {\"id\":\"").append(escape(fixture.id()))
                        .append("\",\"residentTextureBytes\":")
                        .append(fixture.residentTextureBytes())
                        .append(",\"resetPageCount\":")
                        .append(fixture.resetAudit().pageCount())
                        .append(",\"resetEncodedBytesPerPlayer\":")
                        .append(fixture.resetAudit().encodedBytesPerPlayer())
                        .append(",\"hudP95Ms\":").append(result.hudP95Ms())
                        .append(",\"hudP99Ms\":").append(result.hudP99Ms())
                        .append(",\"hudP95BaselineMs\":")
                        .append(result.hudP95BaselineMs())
                        .append(",\"hudP99BaselineMs\":")
                        .append(result.hudP99BaselineMs())
                        .append(",\"hudP95OverheadMs\":")
                        .append(result.hudP95OverheadMs())
                        .append(",\"hudP99OverheadMs\":")
                        .append(result.hudP99OverheadMs())
                        .append(",\"tacticalP95Ms\":").append(result.tacticalP95Ms())
                        .append(",\"tacticalP99Ms\":").append(result.tacticalP99Ms())
                        .append(",\"tacticalP95BaselineMs\":")
                        .append(result.tacticalP95BaselineMs())
                        .append(",\"tacticalP99BaselineMs\":")
                        .append(result.tacticalP99BaselineMs())
                        .append(",\"tacticalP95OverheadMs\":")
                        .append(result.tacticalP95OverheadMs())
                        .append(",\"tacticalP99OverheadMs\":")
                        .append(result.tacticalP99OverheadMs())
                        .append(",\"tacticalOpenMs\":").append(result.tacticalOpenMs())
                        .append(",\"serverP95Ms\":").append(result.serverP95Ms())
                        .append(",\"serverP99Ms\":").append(result.serverP99Ms())
                        .append(",\"serverP95BaselineMs\":")
                        .append(result.serverP95BaselineMs())
                        .append(",\"serverP99BaselineMs\":")
                        .append(result.serverP99BaselineMs())
                        .append(",\"serverP95OverheadMs\":")
                        .append(result.serverP95OverheadMs())
                        .append(",\"serverP99OverheadMs\":")
                        .append(result.serverP99OverheadMs())
                        .append(",\"markerBytesPerSecond\":")
                        .append(result.markerBytesPerSecond())
                        .append(",\"heapDeltaBytes\":").append(result.heapDeltaBytes())
                        .append(",\"renderTaskMaxMs\":").append(result.renderTaskMaxMs())
                        .append(",\"hudSamples\":").append(result.hudSamples())
                        .append(",\"tacticalSamples\":").append(result.tacticalSamples())
                        .append(",\"serverSamples\":").append(result.serverSamples())
                        .append(",\"hudBaselineSamples\":")
                        .append(result.hudBaselineSamples())
                        .append(",\"tacticalBaselineSamples\":")
                        .append(result.tacticalBaselineSamples())
                        .append(",\"serverBaselineSamples\":")
                        .append(result.serverBaselineSamples())
                        .append(",\"markerMessages\":").append(result.markerMessages())
                        .append(",\"complete\":").append(fixture.complete())
                        .append(",\"thresholdsPassed\":")
                        .append(fixture.thresholdsPassed()).append('}');
                if (index + 1 < fixtures.size()) {
                    json.append(',');
                }
                json.append('\n');
            }
            return json.append("  ]\n}\n").toString();
        }

        private static String escape(String value) {
            return value.replace("\\", "\\\\").replace("\"", "\\\"");
        }
    }
}
