package com.phasetranscrystal.fpsmatch.common.minimap.server.sync;

import com.phasetranscrystal.fpsmatch.core.minimap.format.MinimapContainerFixtures;
import com.phasetranscrystal.fpsmatch.core.minimap.format.RuntimeMapReader;
import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapErrorCode;
import com.phasetranscrystal.fpsmatch.core.minimap.extension.MinimapExtensionRegistry;
import com.phasetranscrystal.fpsmatch.core.minimap.extension.MinimapGameplayExtension;
import com.phasetranscrystal.fpsmatch.core.minimap.extension.MarkerPresentation;
import com.phasetranscrystal.fpsmatch.core.minimap.marker.MarkerCandidate;
import com.phasetranscrystal.fpsmatch.core.minimap.marker.MinimapMarkerProvider;
import com.phasetranscrystal.fpsmatch.core.minimap.marker.MinimapViewerContext;
import com.phasetranscrystal.fpsmatch.core.minimap.marker.MinimapVisibilityPolicy;
import com.phasetranscrystal.fpsmatch.core.minimap.marker.ViewerRole;
import com.phasetranscrystal.fpsmatch.core.minimap.model.ContainerPath;
import com.phasetranscrystal.fpsmatch.core.minimap.model.DisplayLabel;
import com.phasetranscrystal.fpsmatch.core.minimap.model.BuiltinRuntimeBinding;
import com.phasetranscrystal.fpsmatch.core.minimap.model.MapKey;
import com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId;
import com.phasetranscrystal.fpsmatch.core.minimap.storage.MinimapRepository;
import com.phasetranscrystal.fpsmatch.core.minimap.storage.MinimapStorageFixtures;
import com.phasetranscrystal.fpsmatch.core.minimap.storage.PublishTransaction;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.MinimapWireMessage;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.MarkerWireMessage;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.PublishWireMessage;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.RuntimeWireMessage;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.WireIdentity;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.WireTransfer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerMinimapRuntimeFactoryTest {
    private static final UUID PLAYER = new UUID(0L, 1L);

    @TempDir
    Path temp;

    @AfterEach
    void clearExtensions() {
        MinimapExtensionRegistry.clearForTests();
    }

    @Test
    void createsWorldScopedRuntimeAndRevalidatesAuthorityForEveryOperation() throws Exception {
        Path repositoryRoot = temp.resolve("world-a").resolve("fpsmatch/minimaps");
        MinimapRepository repository = new MinimapRepository(repositoryRoot);
        MinimapStorageFixtures.Pair pair = MinimapStorageFixtures.validPair(1L);
        ContainerPath regionsPath = ContainerPath.parse("regions-runtime.json");
        com.phasetranscrystal.fpsmatch.core.minimap.model.Sha256 regionsHash;
        try (var runtimeMap = RuntimeMapReader.read(pair.runtime())) {
            regionsHash = runtimeMap.manifest().entries().stream()
                    .filter(entry -> entry.path().equals(regionsPath))
                    .findFirst().orElseThrow().sha256();
        }
        MapKey mapKey = MinimapContainerFixtures.sourceDefinition().manifest().binding();
        NamespacedId dimension = MinimapContainerFixtures.sourceDefinition()
                .manifest().dimension();
        NamespacedId documentId = MinimapContainerFixtures.sourceDefinition()
                .manifest().documentId();
        PublishTransaction prepared = repository.prepare(
                repository.reserve(mapKey, dimension, documentId, 0L),
                pair.source(), pair.runtime()
        );
        assertTrue(repository.commit(prepared).committed());

        Object server = new Object();
        WireIdentity.MapTarget target = new WireIdentity.MapTarget(mapKey, dimension);
        AtomicReference<Optional<RuntimeAuthority>> authority = new AtomicReference<>(
                Optional.of(new RuntimeAuthority(
                        target, documentId, 1L,
                        prepared.descriptor().sourceHash(),
                        prepared.descriptor().runtimeHash()
                ))
        );
        List<MinimapWireMessage> sent = new ArrayList<>();
        ServerMinimapRuntimeFactory.ServerAccess<Object> access =
                new ServerMinimapRuntimeFactory.ServerAccess<>() {
                    @Override
                    public Path repositoryRoot(Object actualServer) {
                        assertTrue(actualServer == server);
                        return repositoryRoot;
                    }

                    @Override
                    public Optional<RuntimeAuthority> resolveAuthority(
                            Object actualServer,
                            UUID actorId,
                            WireIdentity.MapTarget requested
                    ) {
                        return actorId.equals(PLAYER) && requested.equals(target)
                                ? authority.get()
                                : Optional.empty();
                    }

                    @Override
                    public void send(
                            Object actualServer,
                            UUID actorId,
                            MinimapWireMessage message
                    ) {
                        if (actorId.equals(PLAYER)) {
                            sent.add(message);
                        }
                    }
                };
        ServerMinimapRuntimeFactory<Object> factory =
                new ServerMinimapRuntimeFactory<>(access, () -> new UUID(0L, 99L));

        ServerMinimapRuntimeBootstrap.ActiveRuntime runtime = factory.create(server);
        WireIdentity.ScopeLease lease = new WireIdentity.ScopeLease(
                WireIdentity.Scope.MATCH_HUD, 1L, 1L
        );
        runtime.dispatch(PLAYER, new RuntimeWireMessage.Subscribe(
                new UUID(0L, 2L), lease, target, Optional.empty()
        ));
        RuntimeWireMessage.ScopeAck ack = assertInstanceOf(
                RuntimeWireMessage.ScopeAck.class, sent.get(0)
        );
        RuntimeWireMessage.Manifest manifest = assertInstanceOf(
                RuntimeWireMessage.Manifest.class, sent.get(1)
        );

        authority.set(Optional.empty());
        sent.clear();
        runtime.dispatch(PLAYER, new RuntimeWireMessage.RequestEntries(
                new UUID(0L, 3L), lease, ack.runtime(),
                List.of(new WireTransfer.EntryRequest(
                        regionsPath, regionsHash
                ))
        ));

        assertInstanceOf(PublishWireMessage.ErrorMessage.class, sent.get(0));
        assertTrue(manifest.transfer().fragmentData().length > 0);
        runtime.close();
    }

    @Test
    void selectsBuiltinOnlyForRevisionZeroAndNeverFallsBackAcrossSources()
            throws Exception {
        Path repositoryRoot = temp.resolve("world-b").resolve("fpsmatch/minimaps");
        MinimapStorageFixtures.Pair builtinPair = MinimapStorageFixtures.validPair(0L);
        Path builtinPath = temp.resolve("builtin.fpsmapc");
        Files.write(builtinPath, builtinPair.runtime());
        BuiltinRuntimeBinding declaration;
        com.phasetranscrystal.fpsmatch.core.minimap.model.Sha256 sourceHash;
        try (var runtimeMap = RuntimeMapReader.read(builtinPair.runtime())) {
            declaration = new BuiltinRuntimeBinding(
                    runtimeMap.manifest().binding(),
                    MinimapContainerFixtures.sourceDefinition().manifest().dimension(),
                    runtimeMap.manifest().documentId(),
                    runtimeMap.runtimeHash()
            );
            sourceHash = runtimeMap.manifest().sourceHash();
        }
        BuiltinRuntimeMapRegistry builtins = BuiltinRuntimeMapRegistry.builder()
                .register(NamespacedId.parse("fpsmatch:builtin"), declaration, builtinPath)
                .build();
        AtomicReference<BuiltinRuntimeMapRegistry> builtinSnapshot =
                new AtomicReference<>(BuiltinRuntimeMapRegistry.builder().build());
        WireIdentity.MapTarget target = new WireIdentity.MapTarget(
                declaration.binding(), declaration.dimension()
        );
        AtomicReference<RuntimeAuthority> authority = new AtomicReference<>(
                new RuntimeAuthority(
                        target, declaration.documentId(), 0L,
                        sourceHash, declaration.runtimeHash()
                )
        );
        List<MinimapWireMessage> sent = new ArrayList<>();
        Object server = new Object();
        ServerMinimapRuntimeFactory.ServerAccess<Object> access = access(
                repositoryRoot, target, authority, sent, server
        );
        ServerMinimapRuntimeFactory<Object> factory =
                new ServerMinimapRuntimeFactory<>(
                        access, builtinSnapshot::get, () -> new UUID(0L, 98L)
                );
        ServerMinimapRuntimeBootstrap.ActiveRuntime runtime = factory.create(server);
        WireIdentity.ScopeLease lease = new WireIdentity.ScopeLease(
                WireIdentity.Scope.MATCH_HUD, 1L, 1L
        );

        runtime.dispatch(PLAYER, new RuntimeWireMessage.Subscribe(
                new UUID(0L, 4L), lease, target, Optional.empty()
        ));
        assertInstanceOf(PublishWireMessage.ErrorMessage.class, sent.get(0));

        builtinSnapshot.set(builtins);
        sent.clear();
        runtime.dispatch(PLAYER, new RuntimeWireMessage.Subscribe(
                new UUID(0L, 6L), lease, target, Optional.empty()
        ));
        RuntimeWireMessage.ScopeAck ack = assertInstanceOf(
                RuntimeWireMessage.ScopeAck.class, sent.get(0)
        );
        assertTrue(ack.runtime().revision() == 0L);

        authority.set(new RuntimeAuthority(
                target, declaration.documentId(), 1L,
                sourceHash, declaration.runtimeHash()
        ));
        sent.clear();
        runtime.dispatch(PLAYER, new RuntimeWireMessage.Subscribe(
                new UUID(0L, 5L),
                new WireIdentity.ScopeLease(
                        WireIdentity.Scope.MATCH_HUD, 2L, 2L
                ),
                target, Optional.empty()
        ));
        assertInstanceOf(PublishWireMessage.ErrorMessage.class, sent.get(0));
        runtime.close();
    }

    @Test
    void resolvesServerFilteredMarkersThroughRegisteredGameplayExtensions()
            throws Exception {
        Path repositoryRoot = temp.resolve("world-markers").resolve("fpsmatch/minimaps");
        MinimapRepository repository = new MinimapRepository(repositoryRoot);
        MinimapStorageFixtures.Pair pair = MinimapStorageFixtures.validPair(1L);
        MapKey mapKey = MinimapContainerFixtures.sourceDefinition().manifest().binding();
        NamespacedId dimension = MinimapContainerFixtures.sourceDefinition()
                .manifest().dimension();
        NamespacedId documentId = MinimapContainerFixtures.sourceDefinition()
                .manifest().documentId();
        PublishTransaction prepared = repository.prepare(
                repository.reserve(mapKey, dimension, documentId, 0L),
                pair.source(), pair.runtime()
        );
        assertTrue(repository.commit(prepared).committed());
        WireIdentity.MapTarget target = new WireIdentity.MapTarget(mapKey, dimension);
        RuntimeAuthority authority = new RuntimeAuthority(
                target, documentId, 1L,
                prepared.descriptor().sourceHash(), prepared.descriptor().runtimeHash()
        );
        NamespacedId markerId = NamespacedId.parse("fpsmatch:player/test");
        AtomicBoolean providerFails = new AtomicBoolean();
        AtomicBoolean policyFails = new AtomicBoolean();
        AtomicReference<Double> markerX = new AtomicReference<>(1.0);
        AtomicReference<NamespacedId> markerStyle = new AtomicReference<>(
                NamespacedId.parse("fpsmatch:style/default")
        );
        MinimapExtensionRegistry.register(new MinimapGameplayExtension() {
            @Override
            public String id() {
                return "test:runtime-markers";
            }

            @Override
            public boolean supports(MapKey requested) {
                return mapKey.equals(requested);
            }

            @Override
            public Optional<MinimapViewerContext> viewerContext(
                    MapKey requested,
                    UUID actorId
            ) {
                return Optional.of(new MinimapViewerContext(
                        ViewerRole.ACTIVE_PLAYER, "ct", Optional.of(markerId), true, false
                ));
            }

            @Override
            public List<MinimapMarkerProvider> markerProviders(MapKey requested) {
                return List.of(context -> {
                    if (providerFails.get()) {
                        throw new IllegalStateException("provider unavailable");
                    }
                    return List.of(new MarkerCandidate(
                            markerId,
                            NamespacedId.parse("fpsmatch:type/player"),
                            markerStyle.get(),
                            markerX.get(), 2, 3, 45f, 10L,
                            Optional.empty(), Optional.of("ground"),
                            "ct", false, false
                    ));
                });
            }

            @Override
            public List<MarkerPresentation> markerPresentations(MapKey requested) {
                return List.of(new MarkerPresentation(
                        NamespacedId.parse("fpsmatch:type/player"),
                        NamespacedId.parse("fpsmatch:style/default"),
                        NamespacedId.parse("fpsmatch:textures/minimap/markers/default.png"),
                        DisplayLabel.translation("fpsmatch.minimap.marker.player"),
                        1.0
                ));
            }

            @Override
            public Optional<MinimapVisibilityPolicy> visibilityPolicy(MapKey requested) {
                return Optional.of((context, candidates) -> {
                    if (policyFails.get()) {
                        throw new IllegalStateException("policy unavailable");
                    }
                    return candidates.stream().map(MarkerCandidate::toMarker).toList();
                });
            }
        });
        List<MinimapWireMessage> sent = new ArrayList<>();
        Object server = new Object();
        ServerMinimapRuntimeFactory.ServerAccess<Object> access = access(
                repositoryRoot, target, new AtomicReference<>(authority), sent, server
        );
        ServerMinimapRuntimeBootstrap.ActiveRuntime runtime =
                new ServerMinimapRuntimeFactory<>(
                        access,
                        () -> BuiltinRuntimeMapRegistry.builder().build(),
                        () -> new UUID(0L, 97L),
                        () -> 20
                ).create(server);
        WireIdentity.ScopeLease lease = new WireIdentity.ScopeLease(
                WireIdentity.Scope.MATCH_HUD, 1L, 1L
        );
        runtime.dispatch(PLAYER, new RuntimeWireMessage.Subscribe(
                new UUID(0L, 7L), lease, target, Optional.empty()
        ));
        RuntimeWireMessage.ScopeAck ack = assertInstanceOf(
                RuntimeWireMessage.ScopeAck.class, sent.get(0)
        );
        sent.clear();

        runtime.dispatch(PLAYER, new RuntimeWireMessage.RequestMarkerReset(
                new UUID(0L, 8L), lease, ack.runtime(), Optional.empty()
        ));

        MarkerWireMessage.Reset reset = assertInstanceOf(
                MarkerWireMessage.Reset.class, sent.get(0)
        );
        assertEquals(1, reset.markers().size());
        assertEquals(markerId, reset.markers().get(0).markerId());

        markerX.set(4.0);
        sent.clear();
        runtime.tick(1L);
        MarkerWireMessage.Delta configuredDelta = assertInstanceOf(
                MarkerWireMessage.Delta.class, sent.get(0)
        );
        assertEquals(1L, configuredDelta.sequence());

        providerFails.set(true);
        sent.clear();
        runtime.dispatch(PLAYER, new RuntimeWireMessage.RequestMarkerReset(
                new UUID(0L, 9L), lease, ack.runtime(), Optional.empty()
        ));
        assertEquals(
                MinimapErrorCode.MAP_UNAVAILABLE,
                assertInstanceOf(PublishWireMessage.ErrorMessage.class, sent.get(0))
                        .error().knownCode()
        );

        providerFails.set(false);
        policyFails.set(true);
        sent.clear();
        runtime.dispatch(PLAYER, new RuntimeWireMessage.RequestMarkerReset(
                new UUID(0L, 10L), lease, ack.runtime(), Optional.empty()
        ));
        assertEquals(
                MinimapErrorCode.MAP_UNAVAILABLE,
                assertInstanceOf(PublishWireMessage.ErrorMessage.class, sent.get(0))
                        .error().knownCode()
        );

        policyFails.set(false);
        markerStyle.set(NamespacedId.parse("fpsmatch:style/undeclared"));
        sent.clear();
        runtime.dispatch(PLAYER, new RuntimeWireMessage.RequestMarkerReset(
                new UUID(0L, 11L), lease, ack.runtime(), Optional.empty()
        ));
        assertEquals(
                MinimapErrorCode.MAP_UNAVAILABLE,
                assertInstanceOf(PublishWireMessage.ErrorMessage.class, sent.get(0))
                        .error().knownCode()
        );
        runtime.close();
    }

    private static ServerMinimapRuntimeFactory.ServerAccess<Object> access(
            Path repositoryRoot,
            WireIdentity.MapTarget target,
            AtomicReference<RuntimeAuthority> authority,
            List<MinimapWireMessage> sent,
            Object server
    ) {
        return new ServerMinimapRuntimeFactory.ServerAccess<>() {
            @Override
            public Path repositoryRoot(Object actualServer) {
                assertTrue(actualServer == server);
                return repositoryRoot;
            }

            @Override
            public Optional<RuntimeAuthority> resolveAuthority(
                    Object actualServer,
                    UUID actorId,
                    WireIdentity.MapTarget requested
            ) {
                return actorId.equals(PLAYER) && requested.equals(target)
                        ? Optional.of(authority.get())
                        : Optional.empty();
            }

            @Override
            public void send(
                    Object actualServer,
                    UUID actorId,
                    MinimapWireMessage message
            ) {
                if (actorId.equals(PLAYER)) {
                    sent.add(message);
                }
            }
        };
    }
}
