package com.phasetranscrystal.fpsmatch.common.client.minimap;

import com.phasetranscrystal.fpsmatch.common.client.minimap.cache.MinimapCacheKey;
import com.phasetranscrystal.fpsmatch.common.client.minimap.cache.MinimapDiskCache;
import com.phasetranscrystal.fpsmatch.common.client.minimap.tactical.MinimapClientScreens;
import com.phasetranscrystal.fpsmatch.common.client.minimap.tactical.TacticalMapController;
import com.phasetranscrystal.fpsmatch.common.client.minimap.tactical.TacticalOpenRequest;
import com.phasetranscrystal.fpsmatch.core.minimap.format.Sha256Digest;
import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapErrorCode;
import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapOpcode;
import com.phasetranscrystal.fpsmatch.core.minimap.model.ContainerPath;
import com.phasetranscrystal.fpsmatch.core.minimap.model.MapKey;
import com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId;
import com.phasetranscrystal.fpsmatch.core.minimap.model.Sha256;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.MarkerWireMessage;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.MinimapWireMessage;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.PublishWireMessage;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.RuntimeWireMessage;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.WireIdentity;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.WireMarker;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.WireStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientMinimapServicesTest {
    @TempDir
    Path temp;

    @Test
    void connectSubscribeAckAndDisconnectClearMemoryButKeepDiskCache() {
        ArrayList<MinimapWireMessage> sent = new ArrayList<>();
        AtomicInteger ids = new AtomicInteger(1);
        ClientMinimapServices services = ClientMinimapServices.create(
                new MinimapDiskCache(temp.resolve("cache"), 32L * 1024 * 1024),
                sent::add,
                () -> 0L,
                () -> uuid(ids.getAndIncrement())
        );
        WireIdentity.MapTarget target = target("dust2", "minecraft:overworld");
        WireIdentity.RuntimeIdentity identity = identity(
                target, "fpsmatch:dust2", 1L, "runtime-a"
        );

        services.connect("server-a");
        UUID requestId = services.subscribe(
                WireIdentity.Scope.MATCH_HUD,
                target,
                List.of(ContainerPath.parse("floors/ground/tiles/0/0_0.png")),
                Optional.empty()
        ).orElseThrow();
        RuntimeWireMessage.Subscribe subscribe = assertInstanceOf(
                RuntimeWireMessage.Subscribe.class, sent.get(0)
        );
        assertEquals(requestId, subscribe.requestId());
        services.dispatcher().dispatch(new RuntimeWireMessage.ScopeAck(
                requestId, subscribe.lease(), identity
        ));
        assertTrue(services.hasActiveScope(WireIdentity.Scope.MATCH_HUD));

        services.dispatcher().dispatch(new MarkerWireMessage.Reset(
                Optional.empty(), subscribe.lease(), identity, uuid(50), 0L,
                uuid(51), 0, 1, List.of(marker("fpsmatch:self"))
        ));
        assertEquals(1, services.markerStore().markers().size());

        byte[] cached = "cached".getBytes(StandardCharsets.UTF_8);
        MinimapCacheKey cacheKey = new MinimapCacheKey(
                "server-a", target.dimension(), target.mapKey(),
                identity.binding().documentId(), identity.revision(),
                identity.runtimeHash(), Sha256Digest.of(cached), "cached.bin"
        );
        assertTrue(services.diskCache().put(cacheKey, cached));

        services.disconnect();

        assertTrue(services.runtime().currentGeneration().isEmpty());
        assertTrue(services.markerStore().markers().isEmpty());
        assertFalse(services.hasActiveScope(WireIdentity.Scope.MATCH_HUD));
        assertTrue(services.diskCache().get(cacheKey).isPresent());
    }

    @Test
    void switchingTargetsInvalidatesOldLeaseBeforeSendingTheReplacementSubscribe() {
        ArrayList<MinimapWireMessage> sent = new ArrayList<>();
        AtomicInteger ids = new AtomicInteger(100);
        ClientMinimapServices services = ClientMinimapServices.create(
                new MinimapDiskCache(temp.resolve("cache"), 32L * 1024 * 1024),
                sent::add,
                () -> 0L,
                () -> uuid(ids.getAndIncrement())
        );
        WireIdentity.MapTarget dust2 = target("dust2", "minecraft:overworld");
        services.connect("server-a");
        UUID firstRequest = services.subscribe(
                WireIdentity.Scope.MATCH_HUD, dust2, List.of(), Optional.empty()
        ).orElseThrow();
        RuntimeWireMessage.Subscribe first = assertInstanceOf(
                RuntimeWireMessage.Subscribe.class, sent.get(0)
        );
        services.dispatcher().dispatch(new RuntimeWireMessage.ScopeAck(
                firstRequest, first.lease(),
                identity(dust2, "fpsmatch:dust2", 1L, "runtime-a")
        ));
        RuntimeGeneration old = services.runtime().currentGeneration().orElseThrow();
        MinimapScopeLease oldLease = new MinimapScopeLease(
                first.lease().scope(), first.lease().scopeEpoch(),
                first.lease().runtimeGeneration()
        );

        WireIdentity.MapTarget inferno = target("inferno", "minecraft:the_nether");
        services.subscribe(
                WireIdentity.Scope.MATCH_HUD, inferno, List.of(), Optional.empty()
        ).orElseThrow();

        assertFalse(services.runtime().canCommit(old, oldLease));
        assertEquals(old, services.runtime().currentGeneration().orElseThrow());
        assertInstanceOf(RuntimeWireMessage.Unsubscribe.class, sent.get(1));
        RuntimeWireMessage.Subscribe replacement = assertInstanceOf(
                RuntimeWireMessage.Subscribe.class, sent.get(2)
        );
        assertEquals(inferno, replacement.target());
        assertTrue(replacement.lease().runtimeGeneration() > old.localGeneration());
    }

    @Test
    void tacticalScopeJoinsCurrentTargetWithoutInvalidatingHud() {
        ArrayList<MinimapWireMessage> sent = new ArrayList<>();
        AtomicInteger ids = new AtomicInteger(200);
        ClientMinimapServices services = ClientMinimapServices.create(
                new MinimapDiskCache(temp.resolve("cache"), 32L * 1024 * 1024),
                sent::add,
                () -> 0L,
                () -> uuid(ids.getAndIncrement())
        );
        WireIdentity.MapTarget target = target("dust2", "minecraft:overworld");
        WireIdentity.RuntimeIdentity identity = identity(
                target, "fpsmatch:dust2", 1L, "runtime-a"
        );
        services.connect("server-a");
        UUID hudRequest = services.subscribe(
                WireIdentity.Scope.MATCH_HUD, target, List.of(), Optional.empty()
        ).orElseThrow();
        RuntimeWireMessage.Subscribe hud = assertInstanceOf(
                RuntimeWireMessage.Subscribe.class, sent.get(0)
        );
        services.dispatcher().dispatch(new RuntimeWireMessage.ScopeAck(
                hudRequest, hud.lease(), identity
        ));
        RuntimeGeneration generation = services.runtime().currentGeneration().orElseThrow();
        MinimapScopeLease hudLease = new MinimapScopeLease(
                hud.lease().scope(), hud.lease().scopeEpoch(),
                hud.lease().runtimeGeneration()
        );

        UUID tacticalRequest = services.subscribe(
                WireIdentity.Scope.TACTICAL_SCREEN,
                target,
                List.of(),
                Optional.of(new WireIdentity.RuntimeHint(
                        identity.binding().documentId(),
                        identity.revision(),
                        identity.runtimeHash()
                ))
        ).orElseThrow();

        assertEquals(2, sent.size());
        RuntimeWireMessage.Subscribe tactical = assertInstanceOf(
                RuntimeWireMessage.Subscribe.class, sent.get(1)
        );
        assertEquals(tacticalRequest, tactical.requestId());
        assertEquals(generation.localGeneration(), tactical.lease().runtimeGeneration());
        assertTrue(services.runtime().canCommit(generation, hudLease));

        services.dispatcher().dispatch(new RuntimeWireMessage.ScopeAck(
                tacticalRequest, tactical.lease(), identity
        ));
        assertTrue(services.hasActiveScope(WireIdentity.Scope.MATCH_HUD));
        assertTrue(services.hasActiveScope(WireIdentity.Scope.TACTICAL_SCREEN));
    }

    @Test
    void connectedResetKeepsThePendingConnectionAvailableForMapSubscription() {
        ArrayList<MinimapWireMessage> sent = new ArrayList<>();
        AtomicInteger ids = new AtomicInteger(300);
        ClientMinimapServices services = ClientMinimapServices.create(
                new MinimapDiskCache(temp.resolve("cache"), 32L * 1024 * 1024),
                sent::add,
                () -> 0L,
                () -> uuid(ids.getAndIncrement())
        );
        services.connect("server-a");

        services.reset();

        UUID requestId = services.subscribe(
                WireIdentity.Scope.MATCH_HUD,
                target("dust2", "minecraft:overworld"),
                List.of(),
                Optional.empty()
        ).orElseThrow();
        RuntimeWireMessage.Subscribe subscribe = assertInstanceOf(
                RuntimeWireMessage.Subscribe.class, sent.get(0)
        );
        assertEquals(requestId, subscribe.requestId());
    }

    @Test
    void matchEntryProbeIsDeduplicatedAndCapabilityBeginsOnlyAfterAck() {
        ArrayList<MinimapWireMessage> sent = new ArrayList<>();
        AtomicInteger ids = new AtomicInteger(400);
        ClientMinimapServices services = ClientMinimapServices.create(
                new MinimapDiskCache(temp.resolve("cache"), 32L * 1024 * 1024),
                sent::add,
                () -> 0L,
                () -> uuid(ids.getAndIncrement())
        );
        WireIdentity.MapTarget target = target("dust2", "minecraft:overworld");
        services.connect("server-a");

        UUID requestId = services.subscriptions().enterMatch(target).orElseThrow();
        assertTrue(services.subscriptions().enterMatch(target).isEmpty());
        assertEquals(1, sent.size());
        assertFalse(services.subscriptions().matchHudAvailable());

        RuntimeWireMessage.Subscribe subscribe = assertInstanceOf(
                RuntimeWireMessage.Subscribe.class, sent.get(0)
        );
        services.dispatcher().dispatch(new RuntimeWireMessage.ScopeAck(
                requestId,
                subscribe.lease(),
                identity(target, "fpsmatch:dust2", 1L, "runtime-a")
        ));

        assertTrue(services.subscriptions().matchHudAvailable());
        assertTrue(services.subscriptions().enterMatch(target).isEmpty());
        assertEquals(1, sent.size());
    }

    @Test
    void mapOrDimensionChangeReplacesHudProbeAndResetAllowsTheTargetAgain() {
        ArrayList<MinimapWireMessage> sent = new ArrayList<>();
        AtomicInteger ids = new AtomicInteger(500);
        ClientMinimapServices services = ClientMinimapServices.create(
                new MinimapDiskCache(temp.resolve("cache"), 32L * 1024 * 1024),
                sent::add,
                () -> 0L,
                () -> uuid(ids.getAndIncrement())
        );
        WireIdentity.MapTarget overworld = target("dust2", "minecraft:overworld");
        WireIdentity.MapTarget nether = target("dust2", "minecraft:the_nether");
        services.connect("server-a");
        UUID firstRequest = services.subscriptions().enterMatch(overworld).orElseThrow();
        RuntimeWireMessage.Subscribe first = assertInstanceOf(
                RuntimeWireMessage.Subscribe.class, sent.get(0)
        );
        services.dispatcher().dispatch(new RuntimeWireMessage.ScopeAck(
                firstRequest,
                first.lease(),
                identity(overworld, "fpsmatch:dust2", 1L, "runtime-a")
        ));

        assertTrue(services.subscriptions().enterMatch(nether).isPresent());
        assertInstanceOf(RuntimeWireMessage.Unsubscribe.class, sent.get(1));
        RuntimeWireMessage.Subscribe replacement = assertInstanceOf(
                RuntimeWireMessage.Subscribe.class, sent.get(2)
        );
        assertEquals(nether, replacement.target());
        assertFalse(services.subscriptions().matchHudAvailable());

        services.reset();

        assertTrue(services.subscriptions().enterMatch(nether).isPresent());
        assertEquals(4, sent.size());
    }

    @Test
    void subscribeErrorRejectsCapabilityAndReleasesThePendingLease() {
        ArrayList<MinimapWireMessage> sent = new ArrayList<>();
        AtomicInteger ids = new AtomicInteger(600);
        ClientMinimapServices services = ClientMinimapServices.create(
                new MinimapDiskCache(temp.resolve("cache"), 32L * 1024 * 1024),
                sent::add,
                () -> 0L,
                () -> uuid(ids.getAndIncrement())
        );
        WireIdentity.MapTarget target = target("dust2", "minecraft:overworld");
        services.connect("server-a");
        UUID requestId = services.subscriptions().enterMatch(target).orElseThrow();
        RuntimeWireMessage.Subscribe subscribe = assertInstanceOf(
                RuntimeWireMessage.Subscribe.class, sent.get(0)
        );

        services.dispatcher().dispatch(new PublishWireMessage.ErrorMessage(
                Optional.of(requestId),
                Optional.of(subscribe.lease()),
                Optional.empty(),
                Optional.of(MinimapOpcode.C2S_SUBSCRIBE.code()),
                new WireStatus.ErrorInfo(
                        MinimapErrorCode.MAP_UNAVAILABLE.code(),
                        WireStatus.RetryDisposition.DO_NOT_RETRY,
                        "Runtime map is unavailable"
                )
        ));

        MinimapScopeLease pendingLease = new MinimapScopeLease(
                subscribe.lease().scope(),
                subscribe.lease().scopeEpoch(),
                subscribe.lease().runtimeGeneration()
        );
        assertFalse(services.runtime().isPending(pendingLease));
        assertFalse(services.subscriptions().matchHudAvailable());
        assertTrue(services.subscriptions().enterMatch(target).isEmpty());
        assertEquals(1, sent.size());
    }

    @Test
    void tacticalScreenOpensOnlyAfterAckAndClosingKeepsTheHudScope() {
        ArrayList<MinimapWireMessage> sent = new ArrayList<>();
        AtomicInteger ids = new AtomicInteger(700);
        ClientMinimapServices services = ClientMinimapServices.create(
                new MinimapDiskCache(temp.resolve("cache"), 32L * 1024 * 1024),
                sent::add,
                () -> 0L,
                () -> uuid(ids.getAndIncrement())
        );
        WireIdentity.MapTarget target = target("dust2", "minecraft:overworld");
        WireIdentity.RuntimeIdentity identity = identity(
                target, "fpsmatch:dust2", 1L, "runtime-a"
        );
        services.connect("server-a");
        UUID hudRequestId = services.subscriptions().enterMatch(target).orElseThrow();
        RuntimeWireMessage.Subscribe hudSubscribe = assertInstanceOf(
                RuntimeWireMessage.Subscribe.class, sent.get(0)
        );
        services.dispatcher().dispatch(new RuntimeWireMessage.ScopeAck(
                hudRequestId, hudSubscribe.lease(), identity
        ));
        TacticalMapController controller = new TacticalMapController(services.runtime());
        MinimapClientScreens screens = new MinimapClientScreens(
                controller, services.subscriptions()
        );

        assertTrue(screens.openIfAllowed(
                new TacticalOpenRequest(true, true, false, false)
        ));
        assertFalse(controller.isOpen());
        RuntimeWireMessage.Subscribe tacticalSubscribe = assertInstanceOf(
                RuntimeWireMessage.Subscribe.class, sent.get(1)
        );
        assertEquals(WireIdentity.Scope.TACTICAL_SCREEN, tacticalSubscribe.lease().scope());

        services.dispatcher().dispatch(new RuntimeWireMessage.ScopeAck(
                tacticalSubscribe.requestId(), tacticalSubscribe.lease(), identity
        ));
        assertTrue(controller.isOpen());
        assertTrue(services.hasActiveScope(WireIdentity.Scope.MATCH_HUD));
        assertTrue(services.hasActiveScope(WireIdentity.Scope.TACTICAL_SCREEN));

        screens.close();

        assertFalse(controller.isOpen());
        assertTrue(services.hasActiveScope(WireIdentity.Scope.MATCH_HUD));
        assertFalse(services.hasActiveScope(WireIdentity.Scope.TACTICAL_SCREEN));
        RuntimeWireMessage.Unsubscribe unsubscribe = assertInstanceOf(
                RuntimeWireMessage.Unsubscribe.class, sent.get(2)
        );
        assertEquals(WireIdentity.Scope.TACTICAL_SCREEN, unsubscribe.lease().scope());
    }

    @Test
    void subscriptionResetClosesAnOpenPlatformScreen() {
        ArrayList<MinimapWireMessage> sent = new ArrayList<>();
        AtomicInteger ids = new AtomicInteger(750);
        AtomicInteger platformCloses = new AtomicInteger();
        ClientMinimapServices services = ClientMinimapServices.create(
                new MinimapDiskCache(temp.resolve("cache"), 32L * 1024 * 1024),
                sent::add,
                () -> 0L,
                () -> uuid(ids.getAndIncrement())
        );
        WireIdentity.MapTarget target = target("dust2", "minecraft:overworld");
        WireIdentity.RuntimeIdentity identity = identity(
                target, "fpsmatch:dust2", 1L, "runtime-a"
        );
        services.connect("server-a");
        UUID hudRequestId = services.subscriptions().enterMatch(target).orElseThrow();
        RuntimeWireMessage.Subscribe hudSubscribe = assertInstanceOf(
                RuntimeWireMessage.Subscribe.class, sent.get(0)
        );
        services.dispatcher().dispatch(new RuntimeWireMessage.ScopeAck(
                hudRequestId, hudSubscribe.lease(), identity
        ));
        TacticalMapController controller = new TacticalMapController(services.runtime());
        MinimapClientScreens screens = new MinimapClientScreens(
                controller,
                services.subscriptions(),
                new MinimapClientScreens.ScreenOpener() {
                    @Override
                    public void open(
                            TacticalMapController ignored,
                            Runnable onClose
                    ) {
                    }

                    @Override
                    public void close() {
                        platformCloses.incrementAndGet();
                    }
                }
        );
        assertTrue(screens.openIfAllowed(
                new TacticalOpenRequest(true, true, false, false)
        ));
        RuntimeWireMessage.Subscribe tacticalSubscribe = assertInstanceOf(
                RuntimeWireMessage.Subscribe.class, sent.get(1)
        );
        services.dispatcher().dispatch(new RuntimeWireMessage.ScopeAck(
                tacticalSubscribe.requestId(), tacticalSubscribe.lease(), identity
        ));

        services.reset();

        assertEquals(1, platformCloses.get());
        assertFalse(controller.isOpen());
    }

    @Test
    void closingPendingTacticalSendsCancellationAndLateAckCannotOpen() {
        ArrayList<MinimapWireMessage> sent = new ArrayList<>();
        AtomicInteger ids = new AtomicInteger(800);
        ClientMinimapServices services = ClientMinimapServices.create(
                new MinimapDiskCache(temp.resolve("cache"), 32L * 1024 * 1024),
                sent::add,
                () -> 0L,
                () -> uuid(ids.getAndIncrement())
        );
        WireIdentity.MapTarget target = target("dust2", "minecraft:overworld");
        WireIdentity.RuntimeIdentity identity = identity(
                target, "fpsmatch:dust2", 1L, "runtime-a"
        );
        services.connect("server-a");
        UUID hudRequestId = services.subscriptions().enterMatch(target).orElseThrow();
        RuntimeWireMessage.Subscribe hudSubscribe = assertInstanceOf(
                RuntimeWireMessage.Subscribe.class, sent.get(0)
        );
        services.dispatcher().dispatch(new RuntimeWireMessage.ScopeAck(
                hudRequestId, hudSubscribe.lease(), identity
        ));
        TacticalMapController controller = new TacticalMapController(services.runtime());
        MinimapClientScreens screens = new MinimapClientScreens(
                controller, services.subscriptions()
        );
        assertTrue(screens.openIfAllowed(
                new TacticalOpenRequest(true, true, false, false)
        ));
        RuntimeWireMessage.Subscribe tacticalSubscribe = assertInstanceOf(
                RuntimeWireMessage.Subscribe.class, sent.get(1)
        );

        screens.close();

        RuntimeWireMessage.Unsubscribe cancellation = assertInstanceOf(
                RuntimeWireMessage.Unsubscribe.class, sent.get(2)
        );
        assertEquals(tacticalSubscribe.lease(), cancellation.lease());
        services.dispatcher().dispatch(new RuntimeWireMessage.ScopeAck(
                tacticalSubscribe.requestId(), tacticalSubscribe.lease(), identity
        ));
        assertFalse(controller.isOpen());
        assertFalse(services.hasActiveScope(WireIdentity.Scope.TACTICAL_SCREEN));
        assertTrue(services.hasActiveScope(WireIdentity.Scope.MATCH_HUD));
    }

    @Test
    void tacticalSubscribeErrorClearsPendingOpenAndNeverCreatesTheScreen() {
        ArrayList<MinimapWireMessage> sent = new ArrayList<>();
        AtomicInteger ids = new AtomicInteger(900);
        ClientMinimapServices services = ClientMinimapServices.create(
                new MinimapDiskCache(temp.resolve("cache"), 32L * 1024 * 1024),
                sent::add,
                () -> 0L,
                () -> uuid(ids.getAndIncrement())
        );
        WireIdentity.MapTarget target = target("dust2", "minecraft:overworld");
        WireIdentity.RuntimeIdentity identity = identity(
                target, "fpsmatch:dust2", 1L, "runtime-a"
        );
        services.connect("server-a");
        UUID hudRequestId = services.subscriptions().enterMatch(target).orElseThrow();
        RuntimeWireMessage.Subscribe hudSubscribe = assertInstanceOf(
                RuntimeWireMessage.Subscribe.class, sent.get(0)
        );
        services.dispatcher().dispatch(new RuntimeWireMessage.ScopeAck(
                hudRequestId, hudSubscribe.lease(), identity
        ));
        TacticalMapController controller = new TacticalMapController(services.runtime());
        MinimapClientScreens screens = new MinimapClientScreens(
                controller, services.subscriptions()
        );
        assertTrue(screens.openIfAllowed(
                new TacticalOpenRequest(true, true, false, false)
        ));
        assertTrue(screens.isOpenPending());
        RuntimeWireMessage.Subscribe tacticalSubscribe = assertInstanceOf(
                RuntimeWireMessage.Subscribe.class, sent.get(1)
        );

        services.dispatcher().dispatch(new PublishWireMessage.ErrorMessage(
                Optional.of(tacticalSubscribe.requestId()),
                Optional.of(tacticalSubscribe.lease()),
                Optional.empty(),
                Optional.of(MinimapOpcode.C2S_SUBSCRIBE.code()),
                new WireStatus.ErrorInfo(
                        MinimapErrorCode.UNAUTHORIZED.code(),
                        WireStatus.RetryDisposition.DO_NOT_RETRY,
                        "Runtime map is unavailable"
                )
        ));

        assertFalse(screens.isOpenPending());
        assertFalse(controller.isOpen());
        services.dispatcher().dispatch(new RuntimeWireMessage.ScopeAck(
                tacticalSubscribe.requestId(), tacticalSubscribe.lease(), identity
        ));
        assertFalse(controller.isOpen());
        assertTrue(services.hasActiveScope(WireIdentity.Scope.MATCH_HUD));
    }

    @Test
    void resourceReloadPreservesDiskAndReplacesTheHudLease() {
        ArrayList<MinimapWireMessage> sent = new ArrayList<>();
        AtomicInteger ids = new AtomicInteger(1000);
        ClientMinimapServices services = ClientMinimapServices.create(
                new MinimapDiskCache(temp.resolve("cache"), 32L * 1024 * 1024),
                sent::add,
                () -> 0L,
                () -> uuid(ids.getAndIncrement())
        );
        WireIdentity.MapTarget target = target(
                "dust2", "minecraft:overworld"
        );
        services.connect("server-a");
        UUID requestId = services.subscriptions().enterMatch(target).orElseThrow();
        RuntimeWireMessage.Subscribe subscribe = assertInstanceOf(
                RuntimeWireMessage.Subscribe.class, sent.get(0)
        );
        WireIdentity.RuntimeIdentity identity = identity(
                target, "fpsmatch:dust2", 1L, "runtime-a"
        );
        services.dispatcher().dispatch(new RuntimeWireMessage.ScopeAck(
                requestId, subscribe.lease(), identity
        ));
        RuntimeGeneration before = services.runtime()
                .currentGeneration().orElseThrow();
        byte[] cached = "cached".getBytes(StandardCharsets.UTF_8);
        MinimapCacheKey cacheKey = new MinimapCacheKey(
                "server-a",
                target.dimension(),
                target.mapKey(),
                identity.binding().documentId(),
                identity.revision(),
                identity.runtimeHash(),
                Sha256Digest.of(cached),
                "cached.bin"
        );
        assertTrue(services.diskCache().put(cacheKey, cached));

        services.reloadResources();

        assertEquals(3, sent.size());
        RuntimeWireMessage.Unsubscribe unsubscribe = assertInstanceOf(
                RuntimeWireMessage.Unsubscribe.class, sent.get(1)
        );
        assertEquals(subscribe.lease(), unsubscribe.lease());
        RuntimeWireMessage.Subscribe replacement = assertInstanceOf(
                RuntimeWireMessage.Subscribe.class, sent.get(2)
        );
        assertEquals(target, replacement.target());
        assertTrue(
                replacement.lease().runtimeGeneration()
                        > before.localGeneration()
        );
        assertFalse(services.hasActiveScope(WireIdentity.Scope.MATCH_HUD));
        assertTrue(services.diskCache().get(cacheKey).isPresent());
    }

    private static WireIdentity.MapTarget target(String mapName, String dimension) {
        return new WireIdentity.MapTarget(
                new MapKey("cs", mapName), NamespacedId.parse(dimension)
        );
    }

    private static WireIdentity.RuntimeIdentity identity(
            WireIdentity.MapTarget target,
            String document,
            long revision,
            String runtimeHash
    ) {
        return new WireIdentity.RuntimeIdentity(
                new WireIdentity.DocumentBinding(target, NamespacedId.parse(document)),
                revision, hash(runtimeHash), Optional.empty()
        );
    }

    private static WireMarker.Marker marker(String id) {
        return new WireMarker.Marker(
                NamespacedId.parse(id),
                NamespacedId.parse("fpsmatch:type/player"),
                NamespacedId.parse("fpsmatch:style/default"),
                0, 0, 0, 0f, 0L, Optional.empty(), Optional.of("ground"),
                List.of()
        );
    }

    private static Sha256 hash(String value) {
        return Sha256Digest.of(value.getBytes(StandardCharsets.UTF_8));
    }

    private static UUID uuid(int value) {
        return new UUID(0L, value);
    }
}
