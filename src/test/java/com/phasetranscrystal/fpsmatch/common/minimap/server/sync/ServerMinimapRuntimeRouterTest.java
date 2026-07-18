package com.phasetranscrystal.fpsmatch.common.minimap.server.sync;

import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapErrorCode;
import com.phasetranscrystal.fpsmatch.core.minimap.format.Sha256Digest;
import com.phasetranscrystal.fpsmatch.core.minimap.marker.MarkerSnapshot;
import com.phasetranscrystal.fpsmatch.core.minimap.marker.MinimapViewerContext;
import com.phasetranscrystal.fpsmatch.core.minimap.marker.ViewerRole;
import com.phasetranscrystal.fpsmatch.core.minimap.model.ContainerPath;
import com.phasetranscrystal.fpsmatch.core.minimap.model.MapKey;
import com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId;
import com.phasetranscrystal.fpsmatch.core.minimap.model.RuntimeEntryDescriptor;
import com.phasetranscrystal.fpsmatch.core.minimap.model.Sha256;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.MinimapWireMessage;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.MarkerWireMessage;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.PublishWireMessage;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.RuntimeWireMessage;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.WireIdentity;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.WireTransfer;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerMinimapRuntimeRouterTest {
    private static final UUID PLAYER = new UUID(0L, 1L);
    private static final WireIdentity.MapTarget TARGET = new WireIdentity.MapTarget(
            new MapKey("cs", "dust2"),
            NamespacedId.parse("minecraft:overworld")
    );
    private static final WireIdentity.ScopeLease LEASE =
            new WireIdentity.ScopeLease(WireIdentity.Scope.MATCH_HUD, 2L, 3L);

    @Test
    void subscribeSendsAckAndManifestThenEveryEntryRequestReauthorizes() {
        RuntimeFixture fixture = runtimeFixture();
        AtomicBoolean authorized = new AtomicBoolean(true);
        AtomicInteger resolutions = new AtomicInteger();
        ArrayList<MinimapWireMessage> sent = new ArrayList<>();
        ServerMinimapRuntimeRouter router = new ServerMinimapRuntimeRouter(
                (actorId, target) -> {
                    resolutions.incrementAndGet();
                    return authorized.get() && actorId.equals(PLAYER)
                            && target.equals(TARGET)
                            ? Optional.of(fixture.source())
                            : Optional.empty();
                },
                (actorId, message) -> sent.add(message),
                () -> new UUID(0L, 100L)
        );
        UUID subscribeId = new UUID(0L, 10L);

        router.dispatch(PLAYER, new RuntimeWireMessage.Subscribe(
                subscribeId, LEASE, TARGET, Optional.empty()
        ));

        assertEquals(2, sent.size());
        RuntimeWireMessage.ScopeAck ack = assertInstanceOf(
                RuntimeWireMessage.ScopeAck.class, sent.get(0)
        );
        RuntimeWireMessage.Manifest manifest = assertInstanceOf(
                RuntimeWireMessage.Manifest.class, sent.get(1)
        );
        assertEquals(subscribeId, ack.requestId());
        assertEquals(fixture.identity(), ack.runtime());
        assertEquals(Optional.of(subscribeId), manifest.requestId());
        assertEquals(fixture.identity(), manifest.runtime());

        authorized.set(false);
        ContainerPath path = fixture.entries().keySet().iterator().next();
        router.dispatch(PLAYER, new RuntimeWireMessage.RequestEntries(
                new UUID(0L, 11L), LEASE, fixture.identity(),
                List.of(new WireTransfer.EntryRequest(
                        path, Sha256Digest.of(fixture.entries().get(path))
                ))
        ));

        assertEquals(3, sent.size());
        PublishWireMessage.ErrorMessage error = assertInstanceOf(
                PublishWireMessage.ErrorMessage.class, sent.get(2)
        );
        assertEquals(MinimapErrorCode.UNAUTHORIZED, error.error().knownCode());
        assertEquals(2, resolutions.get());
        assertEquals(1, fixture.source().closeCount());
    }

    @Test
    void requestRequiresCurrentLeaseRuntimeAndDeclaredHash() {
        RuntimeFixture fixture = runtimeFixture();
        ArrayList<MinimapWireMessage> sent = new ArrayList<>();
        ServerMinimapRuntimeRouter router = new ServerMinimapRuntimeRouter(
                (actorId, target) -> Optional.of(fixture.source()),
                (actorId, message) -> sent.add(message),
                () -> new UUID(0L, 101L)
        );
        router.dispatch(PLAYER, new RuntimeWireMessage.Subscribe(
                new UUID(0L, 20L), LEASE, TARGET, Optional.empty()
        ));
        sent.clear();
        ContainerPath path = fixture.entries().keySet().iterator().next();

        router.dispatch(PLAYER, new RuntimeWireMessage.RequestEntries(
                new UUID(0L, 21L), LEASE, fixture.identity(),
                List.of(new WireTransfer.EntryRequest(path, hash("wrong")))
        ));

        PublishWireMessage.ErrorMessage error = assertInstanceOf(
                PublishWireMessage.ErrorMessage.class, sent.get(0)
        );
        assertEquals(MinimapErrorCode.HASH_MISMATCH, error.error().knownCode());
        assertTrue(sent.stream().noneMatch(RuntimeWireMessage.EntryFragment.class::isInstance));
    }

    @Test
    void subscribeDistinguishesUnavailableContentFromUnauthorizedAccess() {
        ArrayList<MinimapWireMessage> sent = new ArrayList<>();
        ServerMinimapRuntimeRouter unavailable = new ServerMinimapRuntimeRouter(
                (actorId, target) -> {
                    throw new RuntimeMapUnavailableException(
                            "runtime content is unavailable"
                    );
                },
                (actorId, message) -> sent.add(message),
                () -> new UUID(0L, 102L)
        );

        unavailable.dispatch(PLAYER, new RuntimeWireMessage.Subscribe(
                new UUID(0L, 22L), LEASE, TARGET, Optional.empty()
        ));

        PublishWireMessage.ErrorMessage unavailableError = assertInstanceOf(
                PublishWireMessage.ErrorMessage.class, sent.get(0)
        );
        assertEquals(
                MinimapErrorCode.MAP_UNAVAILABLE,
                unavailableError.error().knownCode()
        );
        assertEquals(
                com.phasetranscrystal.fpsmatch.core.minimap.wire.WireStatus
                        .RetryDisposition.RETRY_NEW_REQUEST,
                unavailableError.error().retryDisposition()
        );

        sent.clear();
        ServerMinimapRuntimeRouter unauthorized = new ServerMinimapRuntimeRouter(
                (actorId, target) -> Optional.empty(),
                (actorId, message) -> sent.add(message),
                () -> new UUID(0L, 103L)
        );
        unauthorized.dispatch(PLAYER, new RuntimeWireMessage.Subscribe(
                new UUID(0L, 23L), LEASE, TARGET, Optional.empty()
        ));
        assertEquals(
                MinimapErrorCode.UNAUTHORIZED,
                assertInstanceOf(PublishWireMessage.ErrorMessage.class, sent.get(0))
                        .error().knownCode()
        );
    }

    @Test
    void initialMarkerFailureSendsOnlyErrorAndDoesNotCreateTheScope() {
        RuntimeFixture fixture = runtimeFixture();
        ArrayList<MinimapWireMessage> sent = new ArrayList<>();
        ServerMinimapRuntimeRouter router = new ServerMinimapRuntimeRouter(
                (actorId, target) -> Optional.of(fixture.source()),
                (actorId, target) -> {
                    throw new RuntimeMarkerUnavailableException("failed", null);
                },
                (actorId, message) -> sent.add(message),
                () -> new UUID(0L, 104L)
        );
        UUID requestId = new UUID(0L, 24L);

        router.dispatch(PLAYER, new RuntimeWireMessage.Subscribe(
                requestId, LEASE, TARGET, Optional.empty()
        ));

        assertEquals(1, sent.size());
        PublishWireMessage.ErrorMessage error = assertInstanceOf(
                PublishWireMessage.ErrorMessage.class, sent.get(0)
        );
        assertEquals(Optional.of(requestId), error.requestId());
        assertEquals(MinimapErrorCode.MAP_UNAVAILABLE, error.error().knownCode());
        assertTrue(sent.stream().noneMatch(RuntimeWireMessage.ScopeAck.class::isInstance));
        assertTrue(sent.stream().noneMatch(RuntimeWireMessage.Manifest.class::isInstance));

        sent.clear();
        ContainerPath path = fixture.entries().keySet().iterator().next();
        router.dispatch(PLAYER, new RuntimeWireMessage.RequestEntries(
                new UUID(0L, 25L), LEASE, fixture.identity(),
                List.of(new WireTransfer.EntryRequest(
                        path, Sha256Digest.of(fixture.entries().get(path))
                ))
        ));
        assertEquals(
                MinimapErrorCode.SCOPE_MISMATCH,
                assertInstanceOf(PublishWireMessage.ErrorMessage.class, sent.get(0))
                        .error().knownCode()
        );
    }

    @Test
    void validEntryRequestStreamsCanonicalFragmentsAndUnsubscribeRevokesLease() {
        RuntimeFixture fixture = runtimeFixture();
        ArrayList<MinimapWireMessage> sent = new ArrayList<>();
        AtomicInteger ids = new AtomicInteger(200);
        ServerMinimapRuntimeRouter router = new ServerMinimapRuntimeRouter(
                (actorId, target) -> Optional.of(fixture.source()),
                (actorId, message) -> sent.add(message),
                () -> new UUID(0L, ids.getAndIncrement())
        );
        router.dispatch(PLAYER, new RuntimeWireMessage.Subscribe(
                new UUID(0L, 30L), LEASE, TARGET, Optional.empty()
        ));
        sent.clear();
        ContainerPath path = fixture.entries().keySet().iterator().next();
        byte[] payload = fixture.entries().get(path);

        router.dispatch(PLAYER, new RuntimeWireMessage.RequestEntries(
                new UUID(0L, 31L), LEASE, fixture.identity(),
                List.of(new WireTransfer.EntryRequest(path, Sha256Digest.of(payload)))
        ));

        RuntimeWireMessage.EntryFragment fragment = assertInstanceOf(
                RuntimeWireMessage.EntryFragment.class, sent.get(0)
        );
        assertEquals(new UUID(0L, 31L), fragment.requestId());
        assertEquals(path, fragment.path());
        assertEquals(Sha256Digest.of(payload), fragment.transfer().objectHash());

        router.dispatch(PLAYER, new RuntimeWireMessage.Unsubscribe(
                new UUID(0L, 32L), LEASE, TARGET
        ));
        sent.clear();
        router.dispatch(PLAYER, new RuntimeWireMessage.RequestEntries(
                new UUID(0L, 33L), LEASE, fixture.identity(),
                List.of(new WireTransfer.EntryRequest(path, Sha256Digest.of(payload)))
        ));
        assertEquals(
                MinimapErrorCode.SCOPE_MISMATCH,
                assertInstanceOf(PublishWireMessage.ErrorMessage.class, sent.get(0))
                        .error().knownCode()
        );
    }

    @Test
    void markerResetReauthorizesAndPagesOnlyTheServerFilteredSnapshot() {
        RuntimeFixture fixture = runtimeFixture();
        AtomicBoolean authorized = new AtomicBoolean(true);
        AtomicInteger markerResolutions = new AtomicInteger();
        ArrayList<MinimapWireMessage> sent = new ArrayList<>();
        List<MarkerSnapshot.Marker> markers = java.util.stream.IntStream
                .range(0, 33)
                .mapToObj(ServerMinimapRuntimeRouterTest::marker)
                .toList();
        ServerMinimapRuntimeRouter router = new ServerMinimapRuntimeRouter(
                (actorId, target) -> authorized.get()
                        ? Optional.of(fixture.source())
                        : Optional.empty(),
                (actorId, target) -> {
                    markerResolutions.incrementAndGet();
                    return Optional.of(new RuntimeMarkerSnapshot(
                            new MinimapViewerContext(
                                    ViewerRole.ACTIVE_PLAYER,
                                    "ct",
                                    Optional.of(NamespacedId.parse("fpsmatch:player/self")),
                                    true,
                                    false
                            ),
                            markers
                    ));
                },
                (actorId, message) -> sent.add(message),
                () -> new UUID(0L, 300L)
        );
        router.dispatch(PLAYER, new RuntimeWireMessage.Subscribe(
                new UUID(0L, 40L), LEASE, TARGET, Optional.empty()
        ));
        sent.clear();
        UUID resetRequest = new UUID(0L, 41L);

        router.dispatch(PLAYER, new RuntimeWireMessage.RequestMarkerReset(
                resetRequest,
                LEASE,
                fixture.identity(),
                Optional.empty()
        ));

        assertEquals(2, sent.size());
        MarkerWireMessage.Reset first = assertInstanceOf(
                MarkerWireMessage.Reset.class, sent.get(0)
        );
        MarkerWireMessage.Reset second = assertInstanceOf(
                MarkerWireMessage.Reset.class, sent.get(1)
        );
        assertEquals(Optional.of(resetRequest), first.requestId());
        assertEquals(first.streamEpoch(), second.streamEpoch());
        assertEquals(first.resetId(), second.resetId());
        assertEquals(0, first.pageIndex());
        assertEquals(1, second.pageIndex());
        assertEquals(2, first.pageCount());
        assertEquals(32, first.markers().size());
        assertEquals(1, second.markers().size());
        assertEquals(2, markerResolutions.get());

        sent.clear();
        WireIdentity.ScopeLease stale = new WireIdentity.ScopeLease(
                LEASE.scope(), LEASE.scopeEpoch() + 1L, LEASE.runtimeGeneration()
        );
        router.dispatch(PLAYER, new RuntimeWireMessage.RequestMarkerReset(
                new UUID(0L, 42L), stale, fixture.identity(), Optional.empty()
        ));
        assertEquals(MinimapErrorCode.SCOPE_MISMATCH,
                assertInstanceOf(PublishWireMessage.ErrorMessage.class, sent.get(0))
                        .error().knownCode());
        assertEquals(2, markerResolutions.get());

        sent.clear();
        authorized.set(false);
        router.dispatch(PLAYER, new RuntimeWireMessage.RequestMarkerReset(
                new UUID(0L, 43L), LEASE, fixture.identity(), Optional.empty()
        ));
        assertEquals(MinimapErrorCode.UNAUTHORIZED,
                assertInstanceOf(PublishWireMessage.ErrorMessage.class, sent.get(0))
                        .error().knownCode());
        assertEquals(2, markerResolutions.get());
    }

    @Test
    void markerTicksRunOnlyForSubscribersAndStartWithAResetAtConfiguredFrequency() {
        RuntimeFixture fixture = runtimeFixture();
        AtomicInteger markerResolutions = new AtomicInteger();
        AtomicReference<List<MarkerSnapshot.Marker>> markers = new AtomicReference<>(
                List.of(marker(1))
        );
        ArrayList<MinimapWireMessage> sent = new ArrayList<>();
        ServerMinimapRuntimeRouter router = new ServerMinimapRuntimeRouter(
                (actorId, target) -> Optional.of(fixture.source()),
                (actorId, target) -> {
                    markerResolutions.incrementAndGet();
                    return Optional.of(new RuntimeMarkerSnapshot(viewer(), markers.get()));
                },
                (actorId, message) -> sent.add(message),
                () -> new UUID(0L, 301L)
        );

        router.tick(0L);
        assertEquals(0, markerResolutions.get());
        router.dispatch(PLAYER, new RuntimeWireMessage.Subscribe(
                new UUID(0L, 44L), LEASE, TARGET, Optional.empty()
        ));

        assertEquals(1, markerResolutions.get());
        MarkerWireMessage.Reset initial = assertInstanceOf(
                MarkerWireMessage.Reset.class, sent.get(2)
        );
        assertEquals(Optional.of(new UUID(0L, 44L)), initial.requestId());
        assertEquals(1, initial.markers().size());

        sent.clear();
        markers.set(List.of(marker(1, 99.0, 11L)));
        router.tick(1L);
        router.tick(2L);
        router.tick(3L);
        assertTrue(sent.isEmpty());
        assertEquals(1, markerResolutions.get());

        router.tick(4L);

        MarkerWireMessage.Delta delta = assertInstanceOf(
                MarkerWireMessage.Delta.class, sent.get(0)
        );
        assertEquals(1L, delta.sequence());
        assertEquals(1, delta.operations().size());
        assertEquals(2, markerResolutions.get());
    }

    @Test
    void markerUpdatesArePhasedAcrossServerTicksWithoutReducingPerPlayerFrequency() {
        RuntimeFixture fixture = runtimeFixture();
        AtomicInteger markerResolutions = new AtomicInteger();
        AtomicReference<List<MarkerSnapshot.Marker>> markers =
                new AtomicReference<>(List.of(marker(1)));
        ArrayList<UUID> resolvedActors = new ArrayList<>();
        ArrayList<UUID> sentActors = new ArrayList<>();
        ServerMinimapRuntimeRouter router = new ServerMinimapRuntimeRouter(
                (actorId, target) -> Optional.of(fixture.source()),
                (actorId, target) -> {
                    markerResolutions.incrementAndGet();
                    resolvedActors.add(actorId);
                    return Optional.of(new RuntimeMarkerSnapshot(
                            viewer(), markers.get()
                    ));
                },
                (actorId, message) -> {
                    if (message instanceof MarkerWireMessage.Delta) {
                        sentActors.add(actorId);
                    }
                },
                () -> new UUID(0L, 303L)
        );
        router.tick(0L);
        for (int index = 0; index < 4; index++) {
            UUID actor = new UUID(0L, index + 1L);
            router.dispatch(actor, new RuntimeWireMessage.Subscribe(
                    new UUID(0L, 50L + index),
                    new WireIdentity.ScopeLease(
                            WireIdentity.Scope.MATCH_HUD, 2L, index + 1L
                    ),
                    TARGET,
                    Optional.empty()
            ));
        }
        markerResolutions.set(0);
        resolvedActors.clear();
        markers.set(List.of(marker(1, 99.0, 11L)));

        for (long tick = 1L; tick <= 4L; tick++) {
            router.tick(tick);
            assertEquals(tick, markerResolutions.get());
            assertEquals(tick, sentActors.size());
        }
        assertEquals(4, new java.util.HashSet<>(sentActors).size());
        UUID firstPhaseActor = resolvedActors.get(0);

        router.tick(5L);
        assertEquals(5, markerResolutions.get());
        assertEquals(firstPhaseActor, resolvedActors.get(4));
        assertEquals(4, sentActors.size());
    }

    @Test
    void markerStreamUsesOneLiveScopeAndFallsBackToPagedResetAboveDeltaLimit() {
        RuntimeFixture fixture = runtimeFixture();
        AtomicReference<List<MarkerSnapshot.Marker>> markers =
                new AtomicReference<>(List.of());
        ArrayList<MinimapWireMessage> sent = new ArrayList<>();
        ServerMinimapRuntimeRouter router = new ServerMinimapRuntimeRouter(
                (actorId, target) -> Optional.of(fixture.source()),
                (actorId, target) -> Optional.of(new RuntimeMarkerSnapshot(
                        viewer(), markers.get()
                )),
                (actorId, message) -> sent.add(message),
                () -> new UUID(0L, 302L)
        );
        WireIdentity.ScopeLease tactical = new WireIdentity.ScopeLease(
                WireIdentity.Scope.TACTICAL_SCREEN, 4L, 3L
        );
        router.dispatch(PLAYER, new RuntimeWireMessage.Subscribe(
                new UUID(0L, 45L), LEASE, TARGET, Optional.empty()
        ));
        router.dispatch(PLAYER, new RuntimeWireMessage.Subscribe(
                new UUID(0L, 46L), tactical, TARGET, Optional.empty()
        ));
        sent.clear();
        markers.set(java.util.stream.IntStream.range(0, 33)
                .mapToObj(ServerMinimapRuntimeRouterTest::marker)
                .toList());

        router.tick(4L);

        assertEquals(2, sent.size());
        MarkerWireMessage.Reset first = assertInstanceOf(
                MarkerWireMessage.Reset.class, sent.get(0)
        );
        MarkerWireMessage.Reset second = assertInstanceOf(
                MarkerWireMessage.Reset.class, sent.get(1)
        );
        assertEquals(LEASE, first.lease());
        assertEquals(first.streamEpoch(), second.streamEpoch());
        assertEquals(first.resetId(), second.resetId());
        assertEquals(32, first.markers().size());
        assertEquals(1, second.markers().size());

        sent.clear();
        markers.set(java.util.stream.IntStream.range(0, 33)
                .mapToObj(index -> index == 0
                        ? marker(0, 100.0, 12L)
                        : marker(index))
                .toList());
        router.tick(8L);
        MarkerWireMessage.Delta hudDelta = assertInstanceOf(
                MarkerWireMessage.Delta.class, sent.get(0)
        );
        assertEquals(LEASE, hudDelta.lease());

        router.dispatch(PLAYER, new RuntimeWireMessage.Unsubscribe(
                new UUID(0L, 47L), LEASE, TARGET
        ));
        sent.clear();
        markers.set(java.util.stream.IntStream.range(0, 33)
                .mapToObj(index -> index == 0
                        ? marker(0, 101.0, 13L)
                        : marker(index))
                .toList());
        router.tick(12L);

        MarkerWireMessage.Delta tacticalDelta = assertInstanceOf(
                MarkerWireMessage.Delta.class, sent.get(0)
        );
        assertEquals(tactical, tacticalDelta.lease());
        assertEquals(1, sent.size());
    }

    @Test
    void markerTickReauthorizesBeforeResolvingAndRevokesTheStreamOnFailure() {
        RuntimeFixture fixture = runtimeFixture();
        AtomicBoolean authorized = new AtomicBoolean(true);
        AtomicInteger markerResolutions = new AtomicInteger();
        ArrayList<MinimapWireMessage> sent = new ArrayList<>();
        ServerMinimapRuntimeRouter router = new ServerMinimapRuntimeRouter(
                (actorId, target) -> authorized.get()
                        ? Optional.of(fixture.source())
                        : Optional.empty(),
                (actorId, target) -> {
                    markerResolutions.incrementAndGet();
                    return Optional.of(new RuntimeMarkerSnapshot(
                            viewer(), List.of(marker(1))
                    ));
                },
                (actorId, message) -> sent.add(message),
                () -> UUID.randomUUID()
        );
        router.dispatch(PLAYER, new RuntimeWireMessage.Subscribe(
                new UUID(0L, 48L), LEASE, TARGET, Optional.empty()
        ));
        assertEquals(1, markerResolutions.get());
        sent.clear();
        authorized.set(false);

        router.tick(4L);

        assertEquals(1, markerResolutions.get());
        MarkerWireMessage.Reset reset = assertInstanceOf(
                MarkerWireMessage.Reset.class, sent.get(0)
        );
        assertTrue(reset.markers().isEmpty());
        assertEquals(
                MinimapErrorCode.UNAUTHORIZED,
                assertInstanceOf(PublishWireMessage.ErrorMessage.class, sent.get(1))
                        .error().knownCode()
        );
        sent.clear();
        router.tick(8L);
        assertTrue(sent.isEmpty());
        assertEquals(1, markerResolutions.get());
    }

    @Test
    void markerExtensionFailureClearsTheOldStreamAndRecoversWithANewReset() {
        RuntimeFixture fixture = runtimeFixture();
        AtomicBoolean fails = new AtomicBoolean();
        ArrayList<MinimapWireMessage> sent = new ArrayList<>();
        ServerMinimapRuntimeRouter router = new ServerMinimapRuntimeRouter(
                (actorId, target) -> Optional.of(fixture.source()),
                (actorId, target) -> {
                    if (fails.get()) {
                        throw new RuntimeMarkerUnavailableException("failed", null);
                    }
                    return Optional.of(new RuntimeMarkerSnapshot(
                            viewer(), List.of(marker(1))
                    ));
                },
                (actorId, message) -> sent.add(message),
                UUID::randomUUID
        );
        router.dispatch(PLAYER, new RuntimeWireMessage.Subscribe(
                new UUID(0L, 49L), LEASE, TARGET, Optional.empty()
        ));
        sent.clear();
        fails.set(true);

        assertDoesNotThrow(() -> router.tick(4L));

        assertTrue(assertInstanceOf(
                MarkerWireMessage.Reset.class, sent.get(0)
        ).markers().isEmpty());
        assertEquals(
                MinimapErrorCode.MAP_UNAVAILABLE,
                assertInstanceOf(PublishWireMessage.ErrorMessage.class, sent.get(1))
                        .error().knownCode()
        );
        fails.set(false);
        sent.clear();
        router.tick(8L);
        MarkerWireMessage.Reset recovered = assertInstanceOf(
                MarkerWireMessage.Reset.class, sent.get(0)
        );
        assertEquals(1, recovered.markers().size());
        assertEquals(0L, recovered.sequence());
    }

    private static RuntimeFixture runtimeFixture() {
        byte[] manifest = "manifest".getBytes(StandardCharsets.UTF_8);
        ContainerPath path = ContainerPath.parse("regions-runtime.json");
        byte[] entry = "runtime-entry".getBytes(StandardCharsets.UTF_8);
        WireIdentity.RuntimeIdentity identity = new WireIdentity.RuntimeIdentity(
                new WireIdentity.DocumentBinding(
                        TARGET, NamespacedId.parse("fpsmatch:dust2")
                ),
                1L,
                Sha256Digest.of(manifest),
                Optional.of(hash("container"))
        );
        LinkedHashMap<ContainerPath, byte[]> entries = new LinkedHashMap<>();
        entries.put(path, entry);
        MemoryRuntimeSource source = new MemoryRuntimeSource(
                identity,
                manifest,
                Map.copyOf(entries)
        );
        return new RuntimeFixture(identity, source, Map.copyOf(entries));
    }

    private static Sha256 hash(String value) {
        return Sha256Digest.of(value.getBytes(StandardCharsets.UTF_8));
    }

    private static MarkerSnapshot.Marker marker(int index) {
        return marker(index, index, 10L);
    }

    private static MarkerSnapshot.Marker marker(int index, double x, long updatedTick) {
        return new MarkerSnapshot.Marker(
                NamespacedId.parse("fpsmatch:marker/" + index),
                NamespacedId.parse("fpsmatch:type/player"),
                NamespacedId.parse("fpsmatch:style/player"),
                x,
                64.0,
                index,
                0.0f,
                updatedTick,
                Optional.empty(),
                Optional.empty()
        );
    }

    private static MinimapViewerContext viewer() {
        return new MinimapViewerContext(
                ViewerRole.ACTIVE_PLAYER,
                "ct",
                Optional.of(NamespacedId.parse("fpsmatch:player/self")),
                true,
                false
        );
    }

    private record RuntimeFixture(
            WireIdentity.RuntimeIdentity identity,
            MemoryRuntimeSource source,
            Map<ContainerPath, byte[]> entries
    ) {
    }

    private static final class MemoryRuntimeSource implements RuntimeMapSource {
        private final WireIdentity.RuntimeIdentity identity;
        private final byte[] manifest;
        private final Map<ContainerPath, byte[]> entries;
        private int closeCount;

        private MemoryRuntimeSource(
                WireIdentity.RuntimeIdentity identity,
                byte[] manifest,
                Map<ContainerPath, byte[]> entries
        ) {
            this.identity = identity;
            this.manifest = manifest.clone();
            this.entries = entries;
        }

        @Override
        public WireIdentity.RuntimeIdentity identity() {
            return identity;
        }

        @Override
        public byte[] manifestBytes() {
            return manifest.clone();
        }

        @Override
        public Optional<RuntimeEntryDescriptor> descriptor(ContainerPath path) {
            byte[] payload = entries.get(path);
            return payload == null ? Optional.empty() : Optional.of(
                    new RuntimeEntryDescriptor(
                            path, payload.length, Sha256Digest.of(payload)
                    )
            );
        }

        @Override
        public InputStream openEntry(ContainerPath path) {
            return new ByteArrayInputStream(entries.get(path));
        }

        @Override
        public void close() {
            closeCount++;
        }

        private int closeCount() {
            return closeCount;
        }
    }
}
