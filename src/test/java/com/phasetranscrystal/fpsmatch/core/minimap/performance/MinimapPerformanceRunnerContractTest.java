package com.phasetranscrystal.fpsmatch.core.minimap.performance;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinimapPerformanceRunnerContractTest {
    @Test
    void headlessRunnerMeasuresProductionCadenceRoutingAndResourceDeltas()
            throws IOException {
        String source = Files.readString(Path.of(
                "src/performanceAcceptance/java/com/phasetranscrystal/fpsmatch/"
                        + "performance/MinimapHeadlessPerformanceAcceptance.java"
        ));

        assertTrue(source.contains("ServerMinimapRuntimeRouter"));
        assertTrue(source.contains("MinimapPerformanceContract.CLIENT_FRAME_HZ"));
        assertTrue(source.contains("MinimapPerformanceContract.SERVER_TICK_HZ"));
        assertTrue(source.contains("MinimapPerformanceContract.MARKER_UPDATE_HZ"));
        assertTrue(source.contains("MinimapPerformanceContract.SERVER_RECEIVERS"));
        assertTrue(source.contains("heapDeltaBytes"));
        assertTrue(source.contains("residentTextureBytes"));
        assertTrue(source.contains("tacticalOpenMs"));
        assertTrue(source.contains("renderTaskMaxMs"));
        assertTrue(source.contains("runCombinedWarmup"));
        assertTrue(source.contains("runCombinedSample"));
        assertTrue(source.contains("AtomicReference<RuntimeMarkerSnapshot>"));
        assertTrue(source.contains(
                "while (frameIndex < frameCount || serverIndex < serverCount)"
        ));
        assertTrue(source.contains("ArrayList<Long> hudSamples"));
        assertTrue(source.contains("ArrayList<Long> tacticalSamples"));
        assertTrue(source.contains("ArrayList<Long> serverSamples"));
        assertFalse(source.contains("steadyMarkers(markers, serverTick.get())"));
        assertFalse(source.contains(".limit(MarkerWireMessage.MAX_PAGE_ITEMS)"));
        assertFalse(source.contains("tilesPerSide * tilesPerSide"));
    }

    @Test
    void headlessRunnerReportsFixtureAndRunProgressDuringFormalGate()
            throws IOException {
        String source = Files.readString(Path.of(
                "src/performanceAcceptance/java/com/phasetranscrystal/fpsmatch/"
                        + "performance/MinimapHeadlessPerformanceAcceptance.java"
        ));

        assertTrue(source.contains("reportProgress"));
        assertTrue(source.contains("fixture="));
        assertTrue(source.contains("run="));
        assertTrue(source.contains("System.out.flush()"));
    }

    @Test
    void eachRunUsesOneSharedWarmupAndSamplingWindow() throws IOException {
        String source = Files.readString(Path.of(
                "src/performanceAcceptance/java/com/phasetranscrystal/fpsmatch/"
                        + "performance/MinimapHeadlessPerformanceAcceptance.java"
        ));

        assertTrue(source.contains("runCombinedWarmup"));
        assertTrue(source.contains("runCombinedSample"));
        assertTrue(source.contains("long deadline = Math.addExact("));
        assertTrue(source.contains("nextClientNanos"));
        assertTrue(source.contains("nextServerNanos"));
        assertFalse(source.contains("runClientWarmup(data, duration);"));
        assertFalse(source.contains("runServerSchedule(data, duration, false);"));
        assertFalse(source.contains(
                "runClientSchedule(\n                data, duration, ClientView.HUD"
        ));
        assertFalse(source.contains(
                "runClientSchedule(\n                data, duration, ClientView.TACTICAL"
        ));
    }

    @Test
    void cpuThresholdsUseCapabilityDisabledBaselineOverhead() throws IOException {
        String source = Files.readString(Path.of(
                "src/performanceAcceptance/java/com/phasetranscrystal/fpsmatch/"
                        + "performance/MinimapHeadlessPerformanceAcceptance.java"
        ));

        assertTrue(source.contains("BaselineSamples"));
        assertTrue(source.contains("cpuOverheadNanos"));
        assertTrue(source.contains("cpuOverheadSamples"));
        assertTrue(source.contains("hudP95OverheadMs"));
        assertTrue(source.contains("hudP99OverheadMs"));
        assertTrue(source.contains("tacticalP95OverheadMs"));
        assertTrue(source.contains("serverP95OverheadMs"));
        assertTrue(source.contains("serverP99OverheadMs"));
        assertTrue(source.contains("hudP95BaselineMs"));
        assertTrue(source.contains("serverP95BaselineMs"));
    }

    @Test
    void markerHotPathsKeepTypedIdsInsteadOfAllocatingStringKeys()
            throws IOException {
        String snapshot = Files.readString(Path.of(
                "src/main/java/com/phasetranscrystal/fpsmatch/core/minimap/"
                        + "marker/MarkerSnapshot.java"
        ));
        String stream = Files.readString(Path.of(
                "src/main/java/com/phasetranscrystal/fpsmatch/core/minimap/"
                        + "marker/MarkerStreamManager.java"
        ));

        assertFalse(snapshot.contains("marker.markerId().toString()"));
        assertFalse(stream.contains("marker.markerId().toString()"));
        assertFalse(stream.contains("Map<String, MarkerSnapshot.Marker>"));
        assertFalse(stream.contains("LinkedHashMap"));
        assertFalse(stream.contains("Map<NamespacedId, MarkerSnapshot.Marker>"));
    }

    @Test
    void wireWriterHasDirectAsciiPathForCanonicalMarkerIdentifiers()
            throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/phasetranscrystal/fpsmatch/core/minimap/"
                        + "wire/WireWriter.java"
        ));

        assertTrue(source.contains("writeAsciiUtf8"));
        assertTrue(source.contains("isAscii"));
        assertTrue(source.contains("writeRawByteUnchecked"));
        assertFalse(source.contains("HexFormat.of().parseHex"));
    }

    @Test
    void serverRouterCachesStableSubscriberOrderBetweenLifecycleChanges()
            throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/phasetranscrystal/fpsmatch/common/minimap/"
                        + "server/sync/ServerMinimapRuntimeRouter.java"
        ));

        assertTrue(source.contains("markerSubscriptionSnapshot"));
        assertTrue(source.contains("invalidateMarkerSubscriptionSnapshot"));
        assertTrue(source.contains(
                "IdentityHashMap<MarkerSnapshot.Marker, WireMarker.Marker>"
        ));
        assertTrue(source.contains("wireMarkerCache"));
    }

    @Test
    void markerCodecPreallocatesForExpectedOperationPayloads()
            throws IOException {
        String writer = Files.readString(Path.of(
                "src/main/java/com/phasetranscrystal/fpsmatch/core/minimap/"
                        + "wire/WireWriter.java"
        ));
        String markerCodec = Files.readString(Path.of(
                "src/main/java/com/phasetranscrystal/fpsmatch/core/minimap/"
                        + "wire/MarkerWireCodec.java"
        ));

        assertTrue(writer.contains("WireWriter(int maximumBytes, int initialBytes)"));
        assertTrue(markerCodec.contains("estimatedMarkerBytes"));
        assertTrue(markerCodec.contains("MARKER_BYTES"));
        assertTrue(markerCodec.contains("encodeMarker"));
        assertTrue(markerCodec.contains("IdentityHashMap<WireMarker.Marker, byte[]>"));
        assertTrue(markerCodec.contains("MAX_CACHED_MARKERS"));
        assertFalse(markerCodec.contains("WeakHashMap"));
        String values = Files.readString(Path.of(
                "src/main/java/com/phasetranscrystal/fpsmatch/core/minimap/"
                        + "wire/WireValueCodec.java"
        ));
        assertTrue(values.contains("RUNTIME_IDENTITY_BYTES"));
        assertTrue(values.contains("writeRawBytes"));
        assertTrue(values.contains(
                "IdentityHashMap<WireIdentity.RuntimeIdentity, byte[]>"
        ));
        assertTrue(values.contains("MAX_CACHED_RUNTIME_IDENTITIES"));
        assertFalse(values.contains("WeakHashMap"));
    }

    @Test
    void markerFramesEncodeDirectlyIntoTheirExactFinalArray()
            throws IOException {
        String writer = Files.readString(Path.of(
                "src/main/java/com/phasetranscrystal/fpsmatch/core/minimap/"
                        + "wire/WireWriter.java"
        ));
        String frameCodec = Files.readString(Path.of(
                "src/main/java/com/phasetranscrystal/fpsmatch/core/minimap/"
                        + "wire/MinimapWireCodec.java"
        ));
        String markerCodec = Files.readString(Path.of(
                "src/main/java/com/phasetranscrystal/fpsmatch/core/minimap/"
                        + "wire/MarkerWireCodec.java"
        ));

        assertTrue(writer.contains("takeExactByteArray"));
        assertTrue(frameCodec.contains("encodeMarkerFrame"));
        assertTrue(markerCodec.contains("writeReset"));
        assertTrue(markerCodec.contains("writeDelta"));
    }

    @Test
    void wireMarkersFastPathAsciiAndEmptyStateValidation()
            throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/phasetranscrystal/fpsmatch/core/minimap/"
                        + "wire/WireMarker.java"
        ));

        assertTrue(source.contains("asciiLength"));
        assertTrue(source.contains("stateFields.isEmpty()"));
        assertTrue(source.contains("canonicalStateFields"));
    }

    @Test
    void runtimeSnapshotsAreCanonicalizedOnceBeforePerViewerDiffing()
            throws IOException {
        String snapshot = Files.readString(Path.of(
                "src/main/java/com/phasetranscrystal/fpsmatch/common/minimap/"
                        + "server/sync/RuntimeMarkerSnapshot.java"
        ));
        String router = Files.readString(Path.of(
                "src/main/java/com/phasetranscrystal/fpsmatch/common/minimap/"
                        + "server/sync/ServerMinimapRuntimeRouter.java"
        ));

        assertTrue(snapshot.contains("MarkerSnapshot markerSnapshot"));
        assertTrue(router.contains("snapshot.markerSnapshot()"));
    }
}
