package com.phasetranscrystal.fpsmatch.common.client.minimap.sync;

import com.phasetranscrystal.fpsmatch.common.client.minimap.ClientMinimapRuntime;
import com.phasetranscrystal.fpsmatch.common.client.minimap.MinimapScopeLease;
import com.phasetranscrystal.fpsmatch.common.client.minimap.cache.MinimapCacheKey;
import com.phasetranscrystal.fpsmatch.common.client.minimap.cache.MinimapDiskCache;
import com.phasetranscrystal.fpsmatch.common.client.minimap.cache.RuntimeEntryStore;
import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapFormatContract;
import com.phasetranscrystal.fpsmatch.core.minimap.format.CanonicalZipWriter;
import com.phasetranscrystal.fpsmatch.core.minimap.format.CompiledMapPair;
import com.phasetranscrystal.fpsmatch.core.minimap.format.MinimapContainerFixtures;
import com.phasetranscrystal.fpsmatch.core.minimap.format.RuntimeCompileRequest;
import com.phasetranscrystal.fpsmatch.core.minimap.format.RuntimeMap;
import com.phasetranscrystal.fpsmatch.core.minimap.format.RuntimeMapCompiler;
import com.phasetranscrystal.fpsmatch.core.minimap.format.RuntimeMapReader;
import com.phasetranscrystal.fpsmatch.core.minimap.format.Sha256Digest;
import com.phasetranscrystal.fpsmatch.core.minimap.format.SourceMap;
import com.phasetranscrystal.fpsmatch.core.minimap.format.SourceMapReader;
import com.phasetranscrystal.fpsmatch.core.minimap.format.SourceMapWriter;
import com.phasetranscrystal.fpsmatch.core.minimap.marker.ClientMarkerStore;
import com.phasetranscrystal.fpsmatch.core.minimap.model.CanvasBounds;
import com.phasetranscrystal.fpsmatch.core.minimap.model.CompilerProfile;
import com.phasetranscrystal.fpsmatch.core.minimap.model.ContainerPath;
import com.phasetranscrystal.fpsmatch.core.minimap.model.MapKey;
import com.phasetranscrystal.fpsmatch.core.minimap.model.MinimapDefinition;
import com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId;
import com.phasetranscrystal.fpsmatch.core.minimap.model.RuntimeEntryDescriptor;
import com.phasetranscrystal.fpsmatch.core.minimap.model.Sha256;
import com.phasetranscrystal.fpsmatch.core.minimap.model.SourceDocument;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.MarkerWireMessage;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.MinimapWireMessage;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.RuntimeWireMessage;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.WireIdentity;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.WireMarker;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.WireTransfer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientMinimapS2CDispatcherTest {
    private static final WireIdentity.MapTarget TARGET = new WireIdentity.MapTarget(
            new MapKey("cs", "dust2"),
            NamespacedId.parse("minecraft:overworld")
    );
    private static final NamespacedId DOCUMENT = NamespacedId.parse("fpsmatch:dust2");

    @TempDir
    Path temp;

    @Test
    void scopeAckRequiresTheTrackedRequestLeaseAndTarget() {
        Fixture fixture = fixture();
        RuntimeWireMessage.Subscribe subscribe = fixture.subscribe(uuid(1));
        assertTrue(fixture.dispatcher.trackSubscribe(subscribe, List.of()));

        fixture.dispatcher.dispatch(new RuntimeWireMessage.ScopeAck(
                uuid(2), subscribe.lease(), runtime()
        ));
        assertTrue(fixture.runtime.currentGeneration().isEmpty());

        WireIdentity.RuntimeIdentity wrongTarget = new WireIdentity.RuntimeIdentity(
                new WireIdentity.DocumentBinding(
                        new WireIdentity.MapTarget(
                                TARGET.mapKey(),
                                NamespacedId.parse("minecraft:the_nether")
                        ),
                        DOCUMENT
                ),
                1L, hash("runtime"), Optional.empty()
        );
        fixture.dispatcher.dispatch(new RuntimeWireMessage.ScopeAck(
                subscribe.requestId(), subscribe.lease(), wrongTarget
        ));
        assertTrue(fixture.runtime.currentGeneration().isEmpty());

        fixture.dispatcher.dispatch(new RuntimeWireMessage.ScopeAck(
                subscribe.requestId(), subscribe.lease(), runtime()
        ));
        assertTrue(fixture.runtime.currentGeneration().isPresent());
    }

    @Test
    void pagedMarkerResetCommitsOnceAndPreservesStateFields() {
        Fixture fixture = acknowledgedFixture();
        UUID streamEpoch = uuid(30);
        UUID resetId = uuid(31);
        WireMarker.StateField state = new WireMarker.StateField(
                NamespacedId.parse("fpsmatch:label"),
                new WireMarker.StringValue("Carrier")
        );
        WireMarker.Marker first = marker("fpsmatch:a", 1.0, List.of(state));
        WireMarker.Marker second = marker("fpsmatch:b", 2.0, List.of());

        fixture.dispatcher.dispatch(reset(
                fixture, streamEpoch, resetId, 1, 2, List.of(second)
        ));
        assertTrue(fixture.markerStore.markers().isEmpty());
        fixture.dispatcher.dispatch(reset(
                fixture, streamEpoch, resetId, 0, 2, List.of(first)
        ));

        assertEquals(2, fixture.markerStore.markers().size());
        assertEquals(
                List.of(state),
                fixture.markerStore.markers().get(0).stateFields()
        );
        fixture.dispatcher.dispatch(reset(
                fixture, streamEpoch, resetId, 0, 2, List.of(first)
        ));
        assertEquals(2, fixture.markerStore.markers().size());
    }

    @Test
    void markerDeltaGapRequestsOneResetAndDoesNotMutateTheStore() {
        Fixture fixture = acknowledgedFixture();
        UUID streamEpoch = uuid(40);
        fixture.dispatcher.dispatch(reset(
                fixture, streamEpoch, uuid(41), 0, 1,
                List.of(marker("fpsmatch:a", 1.0, List.of()))
        ));

        MarkerWireMessage.Delta gap = new MarkerWireMessage.Delta(
                fixture.subscribe.lease(), runtime(), streamEpoch, 2L,
                List.of(new WireMarker.Update(marker(
                        "fpsmatch:a", 9.0, List.of()
                )))
        );
        fixture.dispatcher.dispatch(gap);
        fixture.dispatcher.dispatch(gap);

        assertEquals(1.0, fixture.markerStore.markers().get(0).x());
        assertEquals(1, fixture.sent.size());
        RuntimeWireMessage.RequestMarkerReset request = assertInstanceOf(
                RuntimeWireMessage.RequestMarkerReset.class,
                fixture.sent.get(0)
        );
        assertEquals(fixture.subscribe.lease(), request.lease());
        assertEquals(runtime(), request.runtime());
        assertEquals(
                Optional.of(new WireIdentity.MarkerStreamCursor(streamEpoch, 0L)),
                request.cursor()
        );
    }

    @Test
    void staleLeaseOrRuntimeMessagesFailClosedWithoutChangingMarkers() {
        Fixture fixture = acknowledgedFixture();
        UUID streamEpoch = uuid(50);
        WireIdentity.ScopeLease stale = new WireIdentity.ScopeLease(
                fixture.subscribe.lease().scope(),
                fixture.subscribe.lease().scopeEpoch() + 1,
                fixture.subscribe.lease().runtimeGeneration()
        );
        fixture.dispatcher.dispatch(new MarkerWireMessage.Reset(
                Optional.empty(), stale, runtime(), streamEpoch, 0L, uuid(51),
                0, 1, List.of(marker("fpsmatch:a", 1.0, List.of()))
        ));
        fixture.dispatcher.dispatch(new MarkerWireMessage.Reset(
                Optional.empty(), fixture.subscribe.lease(), runtime(2L, hash("other")),
                streamEpoch, 0L, uuid(52), 0, 1,
                List.of(marker("fpsmatch:b", 2.0, List.of()))
        ));

        assertTrue(fixture.markerStore.markers().isEmpty());
        assertTrue(fixture.sent.isEmpty());
    }

    @Test
    void manifestRequestsOnlyMissingEntriesAndActivatesAfterTheTrackedRequestCompletes()
            throws Exception {
        RuntimeFixture runtimeFixture = runtimeFixture();
        Fixture fixture = fixture();
        RuntimeWireMessage.Subscribe subscribe = fixture.subscribe(
                uuid(60), runtimeFixture.identity().binding().target()
        );
        assertTrue(fixture.dispatcher.trackSubscribe(
                subscribe, List.of(runtimeFixture.tilePath())
        ));
        fixture.dispatcher.dispatch(new RuntimeWireMessage.ScopeAck(
                subscribe.requestId(), subscribe.lease(), runtimeFixture.identity()
        ));
        fixture.subscribe = subscribe;

        ContainerPath cachedPath = ContainerPath.parse("regions-runtime.json");
        byte[] cachedBytes = runtimeFixture.entries().get(cachedPath);
        RuntimeEntryDescriptor cachedDescriptor = runtimeFixture.descriptors().get(cachedPath);
        assertTrue(fixture.diskCache.put(
                cacheKey(runtimeFixture.identity(), cachedDescriptor), cachedBytes
        ));

        fixture.dispatcher.dispatch(new RuntimeWireMessage.Manifest(
                Optional.of(subscribe.requestId()),
                subscribe.lease(),
                runtimeFixture.identity(),
                transfer(uuid(61), runtimeFixture.manifestBytes())
        ));

        assertEquals(1, fixture.sent.size());
        RuntimeWireMessage.RequestEntries request = assertInstanceOf(
                RuntimeWireMessage.RequestEntries.class, fixture.sent.get(0)
        );
        assertFalse(request.entries().stream().anyMatch(entry ->
                entry.path().equals(cachedPath)
        ));
        assertEquals(3, request.entries().size());
        assertTrue(fixture.sync.activeRuntime(
                runtimeFixture.identity().binding().target().mapKey()
        ).isEmpty());

        List<WireTransfer.EntryRequest> requested = request.entries();
        for (int index = 0; index < requested.size(); index++) {
            WireTransfer.EntryRequest entry = requested.get(index);
            byte[] payload = runtimeFixture.entries().get(entry.path());
            if (index == requested.size() - 1) {
                fixture.dispatcher.dispatch(new RuntimeWireMessage.EntryFragment(
                        uuid(999), subscribe.lease(), runtimeFixture.identity(),
                        entry.path(), transfer(uuid(70 + index), payload)
                ));
                assertTrue(fixture.sync.activeRuntime(
                        runtimeFixture.identity().binding().target().mapKey()
                ).isEmpty());
            }
            fixture.dispatcher.dispatch(new RuntimeWireMessage.EntryFragment(
                    request.requestId(), subscribe.lease(), runtimeFixture.identity(),
                    entry.path(), transfer(uuid(70 + index), payload)
            ));
        }

        RuntimeEntryStore.ActiveRuntime active = fixture.sync.activeRuntime(
                runtimeFixture.identity().binding().target().mapKey()
        ).orElseThrow();
        assertEquals(runtimeFixture.identity().runtimeHash(), active.runtimeHash());
        assertEquals(active, fixture.dispatcher.activeRuntime().orElseThrow());
        assertTrue(active.entry("runtime-manifest.json").isPresent());
        assertTrue(active.entry(cachedPath.value()).isPresent());
        assertTrue(active.entry(runtimeFixture.tilePath().value()).isPresent());
    }

    @Test
    void emptyViewportRequirementUsesACompleteLowestResolutionFloor() throws Exception {
        RuntimeFixture runtimeFixture = runtimeFixture();
        Fixture fixture = fixture();
        RuntimeWireMessage.Subscribe subscribe = fixture.subscribe(
                uuid(80), runtimeFixture.identity().binding().target()
        );
        assertTrue(fixture.dispatcher.trackSubscribe(subscribe, List.of()));
        fixture.dispatcher.dispatch(new RuntimeWireMessage.ScopeAck(
                subscribe.requestId(), subscribe.lease(), runtimeFixture.identity()
        ));
        fixture.subscribe = subscribe;

        fixture.dispatcher.dispatch(new RuntimeWireMessage.Manifest(
                Optional.of(subscribe.requestId()), subscribe.lease(),
                runtimeFixture.identity(), transfer(uuid(81), runtimeFixture.manifestBytes())
        ));

        RuntimeWireMessage.RequestEntries request = assertInstanceOf(
                RuntimeWireMessage.RequestEntries.class, fixture.sent.get(0)
        );
        assertTrue(request.entries().stream().anyMatch(entry ->
                entry.path().equals(runtimeFixture.tilePath())
        ));
    }

    @Test
    void manifestSplitsMoreThanTheEntryRequestLimitAndActivatesAfterEveryBatch()
            throws Exception {
        RuntimeFixture runtimeFixture = runtimeFixture(16);
        Fixture fixture = fixture();
        RuntimeWireMessage.Subscribe subscribe = fixture.subscribe(
                uuid(90), runtimeFixture.identity().binding().target()
        );
        assertTrue(fixture.dispatcher.trackSubscribe(subscribe, List.of()));
        fixture.dispatcher.dispatch(new RuntimeWireMessage.ScopeAck(
                subscribe.requestId(), subscribe.lease(), runtimeFixture.identity()
        ));
        fixture.subscribe = subscribe;

        fixture.dispatcher.dispatch(new RuntimeWireMessage.Manifest(
                Optional.of(subscribe.requestId()), subscribe.lease(),
                runtimeFixture.identity(), transfer(uuid(91), runtimeFixture.manifestBytes())
        ));

        assertEquals(2, fixture.sent.size());
        RuntimeWireMessage.RequestEntries first = assertInstanceOf(
                RuntimeWireMessage.RequestEntries.class, fixture.sent.get(0)
        );
        RuntimeWireMessage.RequestEntries second = assertInstanceOf(
                RuntimeWireMessage.RequestEntries.class, fixture.sent.get(1)
        );
        assertEquals(256, first.entries().size());
        assertEquals(3, second.entries().size());
        assertFalse(first.requestId().equals(second.requestId()));
        assertTrue(first.entries().stream().noneMatch(second.entries()::contains));

        WireTransfer.EntryRequest secondEntry = second.entries().get(0);
        fixture.dispatcher.dispatch(new RuntimeWireMessage.EntryFragment(
                first.requestId(), subscribe.lease(), runtimeFixture.identity(),
                secondEntry.path(), transfer(uuid(92), runtimeFixture.entries().get(
                        secondEntry.path()
                ))
        ));
        for (WireTransfer.EntryRequest entry : first.entries()) {
            fixture.dispatcher.dispatch(new RuntimeWireMessage.EntryFragment(
                    first.requestId(), subscribe.lease(), runtimeFixture.identity(),
                    entry.path(), transfer(uuid(93), runtimeFixture.entries().get(entry.path()))
            ));
        }
        assertTrue(fixture.sync.activeRuntime(
                runtimeFixture.identity().binding().target().mapKey()
        ).isEmpty());

        for (int index = 0; index < second.entries().size(); index++) {
            WireTransfer.EntryRequest entry = second.entries().get(index);
            fixture.dispatcher.dispatch(new RuntimeWireMessage.EntryFragment(
                    second.requestId(), subscribe.lease(), runtimeFixture.identity(),
                    entry.path(), transfer(uuid(94 + index), runtimeFixture.entries().get(
                            entry.path()
                    ))
            ));
            if (index < second.entries().size() - 1) {
                assertTrue(fixture.sync.activeRuntime(
                        runtimeFixture.identity().binding().target().mapKey()
                ).isEmpty());
            }
        }

        RuntimeEntryStore.ActiveRuntime active = fixture.sync.activeRuntime(
                runtimeFixture.identity().binding().target().mapKey()
        ).orElseThrow();
        assertEquals(runtimeFixture.identity().runtimeHash(), active.runtimeHash());
        assertEquals(260, active.entries().size());
    }

    private Fixture acknowledgedFixture() {
        Fixture fixture = fixture();
        RuntimeWireMessage.Subscribe subscribe = fixture.subscribe(uuid(10));
        assertTrue(fixture.dispatcher.trackSubscribe(subscribe, List.of()));
        fixture.dispatcher.dispatch(new RuntimeWireMessage.ScopeAck(
                subscribe.requestId(), subscribe.lease(), runtime()
        ));
        assertTrue(fixture.runtime.currentGeneration().isPresent());
        fixture.subscribe = subscribe;
        return fixture;
    }

    private Fixture fixture() {
        ClientMinimapRuntime runtime = ClientMinimapRuntime.create();
        runtime.connect("server-a");
        ClientMarkerStore markerStore = new ClientMarkerStore();
        ArrayList<MinimapWireMessage> sent = new ArrayList<>();
        AtomicInteger ids = new AtomicInteger(100);
        MinimapDiskCache diskCache = new MinimapDiskCache(
                temp.resolve("cache"), 32L * 1024 * 1024
        );
        MinimapClientSyncManager sync = new MinimapClientSyncManager(
                new FragmentAccumulator(8, 16L * 1024 * 1024, 30_000L),
                diskCache,
                new RuntimeEntryStore(),
                (key, bytes) -> true
        );
        ClientMinimapS2CDispatcher dispatcher = new ClientMinimapS2CDispatcher(
                runtime,
                sync,
                markerStore,
                sent::add,
                () -> 0L,
                () -> uuid(ids.getAndIncrement()),
                new MarkerResetAccumulator(4, 16, 128, 30_000L)
        );
        return new Fixture(runtime, markerStore, sent, diskCache, sync, dispatcher);
    }

    private static MarkerWireMessage.Reset reset(
            Fixture fixture,
            UUID streamEpoch,
            UUID resetId,
            int pageIndex,
            int pageCount,
            List<WireMarker.Marker> markers
    ) {
        return new MarkerWireMessage.Reset(
                Optional.empty(), fixture.subscribe.lease(), runtime(), streamEpoch,
                0L, resetId, pageIndex, pageCount, markers
        );
    }

    private static WireMarker.Marker marker(
            String id,
            double x,
            List<WireMarker.StateField> stateFields
    ) {
        return new WireMarker.Marker(
                NamespacedId.parse(id),
                NamespacedId.parse("fpsmatch:type/player"),
                NamespacedId.parse("fpsmatch:style/default"),
                x, 0, 0, 0f, 0L, Optional.empty(), Optional.of("ground"),
                stateFields
        );
    }

    private static WireIdentity.RuntimeIdentity runtime() {
        return runtime(1L, hash("runtime"));
    }

    private static WireIdentity.RuntimeIdentity runtime(long revision, Sha256 hash) {
        return new WireIdentity.RuntimeIdentity(
                new WireIdentity.DocumentBinding(TARGET, DOCUMENT),
                revision,
                hash,
                Optional.empty()
        );
    }

    private RuntimeFixture runtimeFixture() throws Exception {
        return runtimeFixture(1);
    }

    private RuntimeFixture runtimeFixture(int tilesPerAxis) throws Exception {
        MinimapDefinition base = MinimapContainerFixtures.sourceDefinition();
        SourceDocument document = base.document();
        MinimapDefinition definition = new MinimapDefinition(
                base.manifest(),
                new SourceDocument(
                        document.worldBounds(),
                        new CanvasBounds(tilesPerAxis * base.manifest().tileEdge(),
                                tilesPerAxis * base.manifest().tileEdge()),
                        document.defaultViewMode(), document.floors(), document.layerOrder()
                ),
                base.regions(), base.connections(), base.styles()
        );
        byte[] sourceBytes = SourceMapWriter.write(definition);
        byte[] tileBytes = MinimapContainerFixtures.fullRuntimeTile();
        ArrayList<CanonicalZipWriter.EntrySource> tiles = new ArrayList<>();
        for (int x = 0; x < tilesPerAxis; x++) {
            for (int y = 0; y < tilesPerAxis; y++) {
                tiles.add(new CanonicalZipWriter.Entry(
                        ContainerPath.parse("floors/ground/tiles/0/" + x + "_" + y + ".png"),
                        tileBytes
                ));
            }
        }
        try (SourceMap source = SourceMapReader.read(sourceBytes)) {
            CompiledMapPair pair = RuntimeMapCompiler.compile(
                    source,
                    new RuntimeCompileRequest(
                            source.manifest().revision(),
                            new CompilerProfile(
                                    NamespacedId.parse("fpsmatch:test-compiler"),
                                    MinimapFormatContract.CURRENT
                            ),
                            tiles
                    )
            );
            try (RuntimeMap compiled = RuntimeMapReader.read(pair.runtimeBytes())) {
                LinkedHashMap<ContainerPath, byte[]> entries = new LinkedHashMap<>();
                LinkedHashMap<ContainerPath, RuntimeEntryDescriptor> descriptors =
                        new LinkedHashMap<>();
                for (RuntimeEntryDescriptor descriptor : compiled.manifest().entries()) {
                    descriptors.put(descriptor.path(), descriptor);
                    entries.put(descriptor.path(), compiled.entryBytes(descriptor.path()));
                }
                WireIdentity.RuntimeIdentity identity = new WireIdentity.RuntimeIdentity(
                        new WireIdentity.DocumentBinding(
                                new WireIdentity.MapTarget(
                                        compiled.manifest().binding(),
                                        NamespacedId.parse("minecraft:overworld")
                                ),
                                compiled.manifest().documentId()
                        ),
                        compiled.manifest().publishRevision(),
                        compiled.runtimeHash(),
                        Optional.of(compiled.runtimeContainerHash())
                );
                return new RuntimeFixture(
                        identity,
                        compiled.manifestBytes(),
                        Map.copyOf(entries),
                        Map.copyOf(descriptors),
                        ContainerPath.parse("floors/ground/tiles/0/0_0.png")
                );
            }
        }
    }

    private static MinimapCacheKey cacheKey(
            WireIdentity.RuntimeIdentity identity,
            RuntimeEntryDescriptor descriptor
    ) {
        return new MinimapCacheKey(
                "server-a",
                identity.binding().target().dimension(),
                identity.binding().target().mapKey(),
                identity.binding().documentId(),
                identity.revision(),
                identity.runtimeHash(),
                descriptor.sha256(),
                descriptor.path().value()
        );
    }

    private static WireTransfer.TransferFragment transfer(UUID transferId, byte[] bytes) {
        Sha256 hash = Sha256Digest.of(bytes);
        return new WireTransfer.TransferFragment(
                transferId, 0, 1, bytes.length, hash, hash, bytes
        );
    }

    private static Sha256 hash(String value) {
        return Sha256Digest.of(value.getBytes(StandardCharsets.UTF_8));
    }

    private static UUID uuid(int value) {
        return new UUID(0L, value);
    }

    private static final class Fixture {
        private final ClientMinimapRuntime runtime;
        private final ClientMarkerStore markerStore;
        private final List<MinimapWireMessage> sent;
        private final MinimapDiskCache diskCache;
        private final MinimapClientSyncManager sync;
        private final ClientMinimapS2CDispatcher dispatcher;
        private RuntimeWireMessage.Subscribe subscribe;

        private Fixture(
                ClientMinimapRuntime runtime,
                ClientMarkerStore markerStore,
                List<MinimapWireMessage> sent,
                MinimapDiskCache diskCache,
                MinimapClientSyncManager sync,
                ClientMinimapS2CDispatcher dispatcher
        ) {
            this.runtime = runtime;
            this.markerStore = markerStore;
            this.sent = sent;
            this.diskCache = diskCache;
            this.sync = sync;
            this.dispatcher = dispatcher;
        }

        private RuntimeWireMessage.Subscribe subscribe(UUID requestId) {
            return subscribe(requestId, TARGET);
        }

        private RuntimeWireMessage.Subscribe subscribe(
                UUID requestId,
                WireIdentity.MapTarget target
        ) {
            MinimapScopeLease lease = runtime.acquirePending(
                    WireIdentity.Scope.MATCH_HUD
            );
            return new RuntimeWireMessage.Subscribe(
                    requestId, lease.toWire(), target, Optional.empty()
            );
        }
    }

    private record RuntimeFixture(
            WireIdentity.RuntimeIdentity identity,
            byte[] manifestBytes,
            Map<ContainerPath, byte[]> entries,
            Map<ContainerPath, RuntimeEntryDescriptor> descriptors,
            ContainerPath tilePath
    ) {
        private RuntimeFixture {
            manifestBytes = manifestBytes.clone();
        }

        @Override
        public byte[] manifestBytes() {
            return manifestBytes.clone();
        }
    }
}
