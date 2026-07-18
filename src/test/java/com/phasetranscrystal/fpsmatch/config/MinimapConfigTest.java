package com.phasetranscrystal.fpsmatch.config;

import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapHardLimits;
import net.minecraftforge.common.ForgeConfigSpec;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinimapConfigTest {
    private static ForgeConfigSpec serverSpec;

    @BeforeAll
    static void initializeServerSpec() {
        serverSpec = FPSMConfig.initServer();
    }

    @Test
    void clientDefaultsMatchTheMinimapContract() {
        assertEquals(true, FPSMConfig.client.minimapEnabled.getDefault());
        assertEquals(128, FPSMConfig.client.minimapPreferredSize.getDefault());
        assertEquals(96, FPSMConfig.client.minimapMinimumSize.getDefault());
        assertEquals("TOP_LEFT", FPSMConfig.client.minimapHudAnchor.getDefault());
        assertEquals(12, FPSMConfig.client.minimapHudMarginX.getDefault());
        assertEquals(12, FPSMConfig.client.minimapHudMarginY.getDefault());
        assertEquals(50, FPSMConfig.client.minimapHudSafeAreaPriority.getDefault());
        assertEquals("SQUARE", FPSMConfig.client.minimapClipShape.getDefault());
        assertEquals(1.0, FPSMConfig.client.minimapOpacity.getDefault());
        assertEquals(0.6, FPSMConfig.client.minimapBackgroundOpacity.getDefault());
        assertEquals("DOCUMENT", FPSMConfig.client.minimapDefaultMode.getDefault());
        assertEquals(1.0, FPSMConfig.client.minimapFollowZoom.getDefault());
        assertEquals(true, FPSMConfig.client.minimapShowRegionLabels.getDefault());
        assertEquals(true, FPSMConfig.client.minimapShowFloorLabel.getDefault());
        assertEquals(true, FPSMConfig.client.minimapShowCompass.getDefault());
        assertEquals(
                "FADED_ARROWS",
                FPSMConfig.client.minimapAdjacentFloorMarkerStyle.getDefault()
        );
        assertEquals("", FPSMConfig.client.minimapMarkerFilterCsv.getDefault());
        assertEquals(100, FPSMConfig.client.minimapManualFloorTimeoutTicks.getDefault());
        assertEquals(256, FPSMConfig.client.minimapCacheMiB.getDefault());
    }

    @Test
    void clientNumericRangesRejectOutOfContractValues() {
        assertClientIntRange("minimap.preferredSize", 96, 512);
        assertClientIntRange("minimap.minimumSize", 64, 512);
        assertClientIntRange("minimap.hudMarginX", 0, 256);
        assertClientIntRange("minimap.hudMarginY", 0, 256);
        assertClientIntRange("minimap.hudSafeAreaPriority", 0, 1000);
        assertClientIntRange("minimap.manualFloorTimeoutTicks", 20, 1200);
        assertClientIntRange("minimap.cacheMiB", 64, 4096);

        ForgeConfigSpec.ValueSpec opacity = clientValueSpec("minimap.opacity");
        assertFalse(opacity.test(-0.01));
        assertTrue(opacity.test(0.0));
        assertTrue(opacity.test(1.0));
        assertFalse(opacity.test(1.01));

        ForgeConfigSpec.ValueSpec background = clientValueSpec("minimap.backgroundOpacity");
        assertFalse(background.test(-0.01));
        assertTrue(background.test(0.0));
        assertTrue(background.test(1.0));
        assertFalse(background.test(1.01));

        ForgeConfigSpec.ValueSpec zoom = clientValueSpec("minimap.followZoom");
        assertFalse(zoom.test(0.24));
        assertTrue(zoom.test(0.25));
        assertTrue(zoom.test(8.0));
        assertFalse(zoom.test(8.01));
    }

    @Test
    void clientOptionSetsRejectUnknownValues() {
        ForgeConfigSpec.ValueSpec anchor = clientValueSpec("minimap.hudAnchor");
        assertTrue(anchor.test("TOP_LEFT"));
        assertTrue(anchor.test("BOTTOM_RIGHT"));
        assertFalse(anchor.test("CENTER"));

        ForgeConfigSpec.ValueSpec clip = clientValueSpec("minimap.clipShape");
        assertTrue(clip.test("SQUARE"));
        assertTrue(clip.test("CIRCLE"));
        assertFalse(clip.test("HEXAGON"));

        ForgeConfigSpec.ValueSpec mode = clientValueSpec("minimap.defaultMode");
        assertTrue(mode.test("DOCUMENT"));
        assertTrue(mode.test("FIXED_NORTH"));
        assertTrue(mode.test("FOLLOW_PLAYER"));
        assertFalse(mode.test("PLAYER_UP"));

        ForgeConfigSpec.ValueSpec adjacent = clientValueSpec(
                "minimap.adjacentFloorMarkerStyle"
        );
        assertTrue(adjacent.test("HIDDEN"));
        assertTrue(adjacent.test("FADED_ARROWS"));
        assertFalse(adjacent.test("FULL"));
    }

    @Test
    void markerFilterCsvIsEmptyOrACompactNamespacedIdList() {
        ForgeConfigSpec.ValueSpec filter = clientValueSpec("minimap.markerFilterCsv");

        assertTrue(filter.test(""));
        assertTrue(filter.test("fpsmatch:player,fpsmatch:objective/c4"));
        assertFalse(filter.test("FPSMatch:player"));
        assertFalse(filter.test("fpsmatch:player, fpsmatch:c4"));
        assertFalse(filter.test("fpsmatch:player,"));
        assertFalse(filter.test("fpsmatch:../player"));
    }

    @Test
    void serverDefaultsMatchTheMinimapContract() {
        assertEquals(2, FPSMConfig.Server.minimapEditorPermissionLevel.getDefault());
        assertEquals(10, FPSMConfig.Server.minimapEditorSessionTtlMinutes.getDefault());
        assertEquals(7, FPSMConfig.Server.minimapDraftTtlDays.getDefault());
        assertEquals(30, FPSMConfig.Server.minimapUploadTtlMinutes.getDefault());
        assertEquals(30, FPSMConfig.Server.minimapPublishTokenTtlMinutes.getDefault());
        assertEquals(5, FPSMConfig.Server.minimapMarkerHz.getDefault());
        assertEquals(false, FPSMConfig.Server.minimapDirtyTrackingEnabled.getDefault());
        assertEquals(true, FPSMConfig.Server.minimapObserverOmniscient.getDefault());
        assertEquals(60, FPSMConfig.Server.minimapIntelligenceTtlTicks.getDefault());
        assertEquals(1200, FPSMConfig.Server.minimapSectionStateSaveIntervalTicks.getDefault());

        assertEquals(8192, FPSMConfig.Server.minimapMaxCanvasEdge.getDefault());
        assertEquals(16, FPSMConfig.Server.minimapMaxFloors.getDefault());
        assertEquals(128, FPSMConfig.Server.minimapMaxSourceLayers.getDefault());
        assertEquals(4096, FPSMConfig.Server.minimapMaxRegions.getDefault());
        assertEquals(65536, FPSMConfig.Server.minimapMaxVectorVertices.getDefault());
        assertEquals(16384, FPSMConfig.Server.minimapMaxZipEntries.getDefault());
        assertEquals(2, FPSMConfig.Server.minimapMaxManifestMiB.getDefault());
        assertEquals(512, FPSMConfig.Server.minimapMaxSourceExpandedMiB.getDefault());
        assertEquals(256, FPSMConfig.Server.minimapMaxRuntimeExpandedMiB.getDefault());
        assertEquals(64, FPSMConfig.Server.minimapMaxCanonicalPngMiB.getDefault());
        assertEquals(512, FPSMConfig.Server.minimapMaxTileEdge.getDefault());
        assertEquals(2, FPSMConfig.Server.minimapSnapshotBudgetMillis.getDefault());
        assertEquals(512, FPSMConfig.Server.minimapSnapshotBudgetKiB.getDefault());
    }

    @Test
    void permissionLifecycleAndRuntimeRangesRejectUnsafeValues() {
        assertServerIntRange("minimap.editorPermissionLevel", 2, 4);
        assertServerIntRange("minimap.editorSessionTtlMinutes", 1, 1440);
        assertServerIntRange("minimap.draftTtlDays", 1, 365);
        assertServerIntRange("minimap.uploadTtlMinutes", 1, 1440);
        assertServerIntRange("minimap.publishTokenTtlMinutes", 1, 1440);
        assertServerIntRange("minimap.markerHz", 1, 20);
        assertServerIntRange("minimap.intelligenceTtlTicks", 0, 1200);
        assertServerIntRange("minimap.sectionStateSaveIntervalTicks", 20, 12000);
        assertServerIntRange("minimap.snapshotBudgetMillis", 1, 5);
        assertServerIntRange("minimap.snapshotBudgetKiB", 1, 512);
    }

    @Test
    void businessQuotaRangesCannotExceedCompileTimeHardLimits() {
        assertServerIntRange(
                "minimap.maxCanvasEdge",
                1,
                MinimapHardLimits.MAX_CANVAS_EDGE
        );
        assertServerIntRange("minimap.maxFloors", 1, MinimapHardLimits.MAX_FLOORS);
        assertServerIntRange(
                "minimap.maxSourceLayers",
                1,
                MinimapHardLimits.MAX_SOURCE_LAYERS
        );
        assertServerIntRange("minimap.maxRegions", 0, MinimapHardLimits.MAX_REGIONS);
        assertServerIntRange(
                "minimap.maxVectorVertices",
                0,
                MinimapHardLimits.MAX_VECTOR_VERTICES
        );
        assertServerIntRange(
                "minimap.maxZipEntries",
                1,
                MinimapHardLimits.MAX_ZIP_ENTRIES
        );
        assertServerIntRange(
                "minimap.maxManifestMiB",
                1,
                Math.min(
                        mib(MinimapHardLimits.MAX_SOURCE_MANIFEST_BYTES),
                        mib(MinimapHardLimits.MAX_RUNTIME_MANIFEST_BYTES)
                )
        );
        assertServerIntRange(
                "minimap.maxSourceExpandedMiB",
                1,
                mib(MinimapHardLimits.MAX_SOURCE_EXPANDED_BYTES)
        );
        assertServerIntRange(
                "minimap.maxRuntimeExpandedMiB",
                1,
                mib(MinimapHardLimits.MAX_RUNTIME_EXPANDED_BYTES)
        );
        assertServerIntRange(
                "minimap.maxCanonicalPngMiB",
                1,
                mib(MinimapHardLimits.MAX_CANONICAL_PNG_COMPRESSED_BYTES)
        );
        assertServerIntRange(
                "minimap.maxTileEdge",
                1,
                MinimapHardLimits.MAX_TILE_EDGE
        );
    }

    @Test
    void protocolAndFormatIdentityRemainNonConfigurable() {
        assertTrue(Arrays.stream(FPSMConfig.Server.class.getDeclaredFields())
                .map(Field::getName)
                .filter(name -> name.startsWith("minimap"))
                .map(name -> name.toLowerCase(Locale.ROOT))
                .noneMatch(name -> name.contains("wire")
                        || name.contains("protocol")
                        || name.contains("formatversion")
                        || name.contains("fragment")
                        || name.contains("pathgrammar")));
        assertNull(serverSpec.getSpec().get("minimap.maxWireBodyBytes"));
    }

    @Test
    void repeatedServerInitializationReturnsTheSameSpecAndValueHandles() {
        ForgeConfigSpec firstSpec = FPSMConfig.initServer();
        ForgeConfigSpec.IntValue firstPermission =
                FPSMConfig.Server.minimapEditorPermissionLevel;
        ForgeConfigSpec.IntValue firstCanvasLimit = FPSMConfig.Server.minimapMaxCanvasEdge;

        ForgeConfigSpec secondSpec = FPSMConfig.initServer();

        assertSame(firstSpec, secondSpec);
        assertSame(firstPermission, FPSMConfig.Server.minimapEditorPermissionLevel);
        assertSame(firstCanvasLimit, FPSMConfig.Server.minimapMaxCanvasEdge);
    }

    @Test
    void concurrentServerInitializationPublishesOneStableSpec() throws Exception {
        int workers = 8;
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<ServerConfigIdentity>> futures = new ArrayList<>();
            for (int index = 0; index < workers; index++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("config test start timed out");
                    }
                    ForgeConfigSpec spec = FPSMConfig.initServer();
                    return new ServerConfigIdentity(
                            spec,
                            FPSMConfig.Server.minimapEditorPermissionLevel,
                            FPSMConfig.Server.minimapMaxCanvasEdge
                    );
                }));
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            ServerConfigIdentity expected = futures.get(0).get(5, TimeUnit.SECONDS);
            for (Future<ServerConfigIdentity> future : futures) {
                ServerConfigIdentity actual = future.get(5, TimeUnit.SECONDS);
                assertSame(expected.spec(), actual.spec());
                assertSame(expected.permission(), actual.permission());
                assertSame(expected.canvasLimit(), actual.canvasLimit());
            }
            assertSame(expected.spec(), FPSMConfig.serverSpec);
            assertSame(expected.permission(), FPSMConfig.Server.minimapEditorPermissionLevel);
            assertSame(expected.canvasLimit(), FPSMConfig.Server.minimapMaxCanvasEdge);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private static void assertClientIntRange(String path, int minimum, int maximum) {
        assertIntRange(clientValueSpec(path), minimum, maximum);
    }

    private static void assertServerIntRange(String path, int minimum, int maximum) {
        assertIntRange(serverValueSpec(path), minimum, maximum);
    }

    private static void assertIntRange(
            ForgeConfigSpec.ValueSpec valueSpec,
            int minimum,
            int maximum
    ) {
        assertFalse(valueSpec.test(minimum - 1), "accepted below minimum");
        assertTrue(valueSpec.test(minimum), "rejected minimum");
        assertTrue(valueSpec.test(maximum), "rejected maximum");
        assertFalse(valueSpec.test(maximum + 1), "accepted above maximum");
    }

    private static int mib(long bytes) {
        return Math.toIntExact(bytes / (1024L * 1024L));
    }

    private static ForgeConfigSpec.ValueSpec clientValueSpec(String path) {
        return (ForgeConfigSpec.ValueSpec) FPSMConfig.clientSpec.getSpec().get(path);
    }

    private static ForgeConfigSpec.ValueSpec serverValueSpec(String path) {
        return (ForgeConfigSpec.ValueSpec) serverSpec.getSpec().get(path);
    }

    private record ServerConfigIdentity(
            ForgeConfigSpec spec,
            ForgeConfigSpec.IntValue permission,
            ForgeConfigSpec.IntValue canvasLimit
    ) {
    }
}
