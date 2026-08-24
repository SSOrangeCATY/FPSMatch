package com.ptcrys.fpsmatch.common.minimap.server.sync;

import com.ptcrys.fpsmatch.common.minimap.server.DefaultMinimapPermissionPolicy;
import com.ptcrys.fpsmatch.common.minimap.server.EditorSessionManager;
import com.ptcrys.fpsmatch.common.minimap.server.DraftAncestorPins;
import com.ptcrys.fpsmatch.common.minimap.server.DraftStore;
import com.ptcrys.fpsmatch.common.minimap.server.MinimapBindingCoordinator;
import com.ptcrys.fpsmatch.common.minimap.server.ServerEditorContextAuthority;
import com.ptcrys.fpsmatch.common.minimap.server.ServerEditorPublishService;
import com.ptcrys.fpsmatch.common.minimap.server.MinimapPermissionPolicy;
import com.ptcrys.fpsmatch.common.minimap.server.UploadLimits;
import com.ptcrys.fpsmatch.common.minimap.server.UploadManager;
import com.ptcrys.fpsmatch.config.FPSMConfig;
import com.ptcrys.fpsmatch.core.minimap.contract.MinimapHardLimits;
import com.ptcrys.fpsmatch.core.minimap.format.ContainerLimits;
import com.ptcrys.fpsmatch.core.minimap.format.SourceMap;
import com.ptcrys.fpsmatch.core.minimap.format.SourceMapReader;
import com.ptcrys.fpsmatch.core.minimap.extension.MinimapExtensionRegistry;
import com.ptcrys.fpsmatch.core.minimap.extension.MarkerPresentation;
import com.ptcrys.fpsmatch.core.minimap.marker.MarkerCandidate;
import com.ptcrys.fpsmatch.core.minimap.marker.MarkerSnapshot;
import com.ptcrys.fpsmatch.core.minimap.marker.MinimapMarkerProvider;
import com.ptcrys.fpsmatch.core.minimap.marker.MinimapViewerContext;
import com.ptcrys.fpsmatch.core.minimap.marker.MinimapVisibilityPolicy;
import com.ptcrys.fpsmatch.core.minimap.model.MapKey;
import com.ptcrys.fpsmatch.core.minimap.storage.CurrentPublication;
import com.ptcrys.fpsmatch.core.minimap.storage.MinimapRepository;
import com.ptcrys.fpsmatch.core.minimap.wire.MinimapWireMessage;
import com.ptcrys.fpsmatch.core.minimap.wire.WireIdentity;

import java.io.IOException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.function.IntSupplier;

public final class ServerMinimapRuntimeFactory<S> {
    private static final long MAINTENANCE_SWEEP_INTERVAL_TICKS = 20L;
    private final ServerAccess<S> access;
    private final Supplier<BuiltinRuntimeMapRegistry> builtins;
    private final Supplier<UUID> transferIds;
    private final IntSupplier markerHz;

    public ServerMinimapRuntimeFactory(
            ServerAccess<S> access,
            Supplier<UUID> transferIds
    ) {
        this(
                access,
                () -> BuiltinRuntimeMapRegistry.builder().build(),
                transferIds,
                () -> 5
        );
    }

    public ServerMinimapRuntimeFactory(
            ServerAccess<S> access,
            Supplier<BuiltinRuntimeMapRegistry> builtins,
            Supplier<UUID> transferIds
    ) {
        this(access, builtins, transferIds, () -> 5);
    }

    public ServerMinimapRuntimeFactory(
            ServerAccess<S> access,
            Supplier<BuiltinRuntimeMapRegistry> builtins,
            Supplier<UUID> transferIds,
            IntSupplier markerHz
    ) {
        this.access = Objects.requireNonNull(access, "access");
        this.builtins = Objects.requireNonNull(builtins, "builtins");
        this.transferIds = Objects.requireNonNull(transferIds, "transferIds");
        this.markerHz = Objects.requireNonNull(markerHz, "markerHz");
    }

    public ServerMinimapRuntimeBootstrap.ActiveRuntime create(S server) {
        Objects.requireNonNull(server, "server");
        MinimapRepository repository = new MinimapRepository(
                access.repositoryRoot(server)
        );
        RepositoryRuntimeMapResolver resolver = new RepositoryRuntimeMapResolver(
                repository,
                (actorId, target) -> access.resolveAuthority(
                        server, actorId, target
                )
        );
        MinimapPermissionPolicy editorPermissions =
                DefaultMinimapPermissionPolicy.onlinePlayers(
                        ServerMinimapRuntimeFactory::editorPermissionLevel
                );
        EditorSessionManager editorSessions = new EditorSessionManager(
                editorPermissions,
                () -> editorSessionIdleTtl(),
                Clock.systemUTC()
        );
        Clock clock = Clock.systemUTC();
        DraftStore drafts = new DraftStore(
                access.repositoryRoot(server).resolve("drafts"),
                editorDraftTtl(), 16, clock, DraftAncestorPins.repository(repository)
        );
        long uploadBytes = MinimapHardLimits.MAX_SOURCE_ENTRY_UPLOAD_BYTES;
        UploadManager uploads = new UploadManager(
                access.repositoryRoot(server).resolve("uploads"),
                new UploadLimits(uploadBytes, 64, 8,
                        Math.multiplyExact(uploadBytes, 64),
                        Math.multiplyExact(uploadBytes, 8)),
                editorUploadTtl(), clock
        );
        MinimapBindingCoordinator bindings = new MinimapBindingCoordinator(
                new ServerEditorPublishService.CapabilityBindingStore()
        );
        ServerEditorContextAuthority contextAuthority =
                new ServerEditorContextAuthority(editorSessions, drafts, bindings);
        java.util.concurrent.atomic.AtomicReference<ServerMinimapRuntimeRouter> routerRef =
                new java.util.concurrent.atomic.AtomicReference<>();
        ServerEditorPublishService editorPublish = new ServerEditorPublishService(
                repository,
                editorSessions,
                editorPermissions,
                drafts,
                bindings,
                (actorId, message) -> access.send(server, actorId, message),
                mapKey -> {
                    Throwable failure = null;
                    ServerMinimapRuntimeRouter active = routerRef.get();
                    if (active != null) {
                        try {
                            active.invalidateMap(mapKey);
                        } catch (RuntimeException | Error next) {
                            failure = LifecycleFailures.merge(failure, next);
                        }
                    }
                    try {
                        access.onMapPublished(server, mapKey);
                    } catch (RuntimeException | Error next) {
                        failure = LifecycleFailures.merge(failure, next);
                    }
                    LifecycleFailures.rethrow(failure);
                }
        );
        ServerMinimapRuntimeRouter.RuntimeResolver runtimeResolver =
                new GameplayRegionRuntimeResolver(
                        (actorId, target) -> access.resolveAuthority(server, actorId, target)
                                .flatMap(authority -> authority.revision() == 0L
                                        ? new BuiltinRuntimeMapResolver(
                                        Objects.requireNonNull(
                                                builtins.get(), "builtin registry"
                                        ),
                                        (requestedActor, requestedTarget) ->
                                                access.resolveAuthority(
                                                        server,
                                                        requestedActor,
                                                        requestedTarget
                                                )
                                ).resolve(actorId, target)
                                        : resolver.resolve(actorId, target)),
                        MinimapExtensionRegistry::regionProviders,
                        MinimapExtensionRegistry::regionPresentations
                );
        ServerMinimapRuntimeRouter router = new ServerMinimapRuntimeRouter(
                runtimeResolver,
                ServerMinimapRuntimeFactory::resolveMarkers,
                (actorId, message) -> access.send(server, actorId, message),
                transferIds,
                markerHz,
                editorSessions,
                editorPermissions,
                editorPublish
        );
        routerRef.set(router);
        ServerMinimapEditorRouter editorRouter = new ServerMinimapEditorRouter(
                editorSessions, editorPermissions, drafts, uploads, bindings,
                editorPublish, (actorId, message) -> access.send(server, actorId, message),
                (actorId, context) -> resolveEditorSource(repository, context),
                transferIds,
                contextAuthority
        );
        router.installEditorRouter(editorRouter);
        ServerMinimapRuntimeMaintenance maintenance =
                new ServerMinimapRuntimeMaintenance(
                        MAINTENANCE_SWEEP_INTERVAL_TICKS,
                        List.of(
                                editorSessions::removeExpired,
                                drafts::removeExpired,
                                uploads::removeExpired,
                                editorRouter::pruneStaleUploadScopes,
                                editorPublish::recoverBindings
                        ),
                        List.of(
                                contextAuthority::clearAll,
                                editorSessions::invalidateAll,
                                uploads::closeAll
                        )
                );
        return new ActiveRuntime(
                router,
                (actorId, message) -> access.send(server, actorId, message),
                contextAuthority,
                maintenance
        );
    }

    private static Optional<RuntimeMarkerSnapshot> resolveMarkers(
            UUID actorId,
            WireIdentity.MapTarget target
    ) {
        try {
            MapKey mapKey = target.mapKey();
            Optional<MinimapViewerContext> viewer =
                    MinimapExtensionRegistry.viewerContext(mapKey, actorId);
            Optional<MinimapVisibilityPolicy> policy =
                    MinimapExtensionRegistry.visibilityPolicy(mapKey);
            if (viewer.isEmpty() || policy.isEmpty()) {
                return Optional.empty();
            }
            List<MinimapMarkerProvider> providers =
                    MinimapExtensionRegistry.markerProviders(mapKey);
            ArrayList<MarkerCandidate> candidates = new ArrayList<>();
            for (MinimapMarkerProvider provider : providers) {
                candidates.addAll(Objects.requireNonNull(
                        provider.collect(viewer.orElseThrow()),
                        "marker candidates"
                ));
            }
            List<MarkerSnapshot.Marker> visible = Objects.requireNonNull(
                    policy.orElseThrow().filter(viewer.orElseThrow(), candidates),
                    "visible markers"
            );
            List<MarkerPresentation> presentations =
                    MinimapExtensionRegistry.markerPresentations(mapKey);
            for (MarkerSnapshot.Marker marker : visible) {
                boolean declared = presentations.stream().anyMatch(presentation ->
                        presentation.typeId().equals(marker.typeId())
                                && presentation.styleId().equals(marker.styleId())
                );
                if (!declared) {
                    throw new IllegalStateException(
                            "Undeclared marker presentation: "
                                    + marker.typeId() + "|" + marker.styleId()
                    );
                }
            }
            return Optional.of(new RuntimeMarkerSnapshot(
                    viewer.orElseThrow(), visible
            ));
        } catch (RuntimeException unavailable) {
            throw new RuntimeMarkerUnavailableException(
                    "Gameplay marker extension failed", unavailable
            );
        }
    }


    private static int editorPermissionLevel() {
        try {
            return FPSMConfig.Server.minimapEditorPermissionLevel.get();
        } catch (Throwable unavailable) {
            return 2;
        }
    }

    private static java.time.Duration editorSessionIdleTtl() {
        try {
            return java.time.Duration.ofMinutes(
                    FPSMConfig.Server.minimapEditorSessionTtlMinutes.get()
            );
        } catch (Throwable unavailable) {
            return java.time.Duration.ofMinutes(10);
        }
    }

    /** Opens only the currently bound, fully committed source revision. */
    private static Optional<SourceMap> resolveEditorSource(
            MinimapRepository repository,
            WireIdentity.EditorContext context
    ) {
        MapKey mapKey = context.binding().target().mapKey();
        Optional<CurrentPublication> current = repository.currentPublication(mapKey);
        if (current.isEmpty()) {
            return Optional.empty();
        }
        var record = current.orElseThrow().record();
        var descriptor = record.descriptor();
        if (!record.target().mapKey().equals(mapKey)
                || !record.target().dimension().equals(context.binding().target().dimension())
                || !record.target().documentId().equals(context.binding().documentId())
                || descriptor.publishRevision() != context.baseRevision()
                || !descriptor.sourceHash().equals(context.baseSourceHash())) {
            return Optional.empty();
        }
        Path sourcePath = repository.mapDirectory(mapKey)
                .resolve("revisions")
                .resolve(Long.toString(context.baseRevision()))
                .resolve("source.fpsmap");
        SeekableByteChannel channel = null;
        SourceMap source = null;
        try {
            channel = Files.newByteChannel(
                    sourcePath,
                    java.util.Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)
            );
            long size = channel.size();
            if (size < 0 || size > ContainerLimits.sourceHardLimits()
                    .maxCanonicalContainerBytes()) {
                throw new IOException("Editor source container exceeds its hard limit");
            }
            source = SourceMapReader.open(channel, size);
            channel = null;
            if (!source.sourceHash().equals(descriptor.sourceHash())
                    || !source.manifest().binding().equals(mapKey)
                    || !source.manifest().dimension().equals(
                    context.binding().target().dimension())
                    || !source.manifest().documentId().equals(context.binding().documentId())
                    || source.manifest().revision() != context.baseRevision()) {
                source.close();
                return Optional.empty();
            }
            return Optional.of(source);
        } catch (IOException | RuntimeException failure) {
            closeSourceAfterFailure(source, channel, failure);
            throw new IllegalStateException("Unable to open authoritative editor source", failure);
        }
    }

    private static void closeSourceAfterFailure(
            SourceMap source,
            SeekableByteChannel channel,
            Throwable failure
    ) {
        try {
            if (source != null) {
                source.close();
            } else if (channel != null) {
                channel.close();
            }
        } catch (IOException closeFailure) {
            failure.addSuppressed(closeFailure);
        }
    }

    private static java.time.Duration editorDraftTtl() {
        try {
            return java.time.Duration.ofDays(FPSMConfig.Server.minimapDraftTtlDays.get());
        } catch (Throwable unavailable) {
            return java.time.Duration.ofDays(7);
        }
    }

    private static java.time.Duration editorUploadTtl() {
        try {
            return java.time.Duration.ofMinutes(FPSMConfig.Server.minimapUploadTtlMinutes.get());
        } catch (Throwable unavailable) {
            return java.time.Duration.ofMinutes(30);
        }
    }

    public interface ServerAccess<S> {
        Path repositoryRoot(S server);

        Optional<RuntimeAuthority> resolveAuthority(
                S server,
                UUID actorId,
                WireIdentity.MapTarget target
        );

        void send(S server, UUID actorId, MinimapWireMessage message);

        default void onMapPublished(S server, MapKey mapKey) {
        }
    }

    private static final class ActiveRuntime
            implements ServerMinimapRuntimeBootstrap.ActiveRuntime {
        private final ServerMinimapRuntimeRouter router;
        private final ServerMinimapRuntimeRouter.Sender sender;
        private final ServerEditorContextAuthority contextAuthority;
        private final ServerMinimapRuntimeMaintenance maintenance;
        private boolean closed;

        private ActiveRuntime(
                ServerMinimapRuntimeRouter router,
                ServerMinimapRuntimeRouter.Sender sender,
                ServerEditorContextAuthority contextAuthority,
                ServerMinimapRuntimeMaintenance maintenance
        ) {
            this.router = router;
            this.sender = sender;
            this.contextAuthority = contextAuthority;
            this.maintenance = maintenance;
        }

        @Override
        public synchronized void dispatch(UUID actorId, MinimapWireMessage message) {
            if (!closed) {
                router.dispatch(actorId, message);
            }
        }

        @Override
        public synchronized void onPlayerLogout(UUID actorId) {
            if (!closed) {
                router.onPlayerLogout(actorId);
            }
        }

        @Override
        public synchronized void invalidateActor(UUID actorId) {
            if (!closed) {
                router.onPlayerLogout(actorId);
            }
        }

        @Override
        public synchronized void invalidateMap(MapKey mapKey) {
            if (!closed) {
                router.invalidateMap(mapKey);
            }
        }

        @Override
        public synchronized void send(UUID actorId, MinimapWireMessage message) {
            if (!closed) {
                sender.send(actorId, message);
            }
        }

        @Override
        public synchronized void tick(long nowTick) {
            if (!closed) {
                maintenance.tick(nowTick);
                router.tick(nowTick);
            }
        }

        @Override
        public synchronized void onCatalogReload() {
            if (!closed) {
                router.onCatalogReload();
            }
        }

        @Override
        public synchronized boolean allowEditor(UUID actorId, MinimapWireMessage message) {
            return !closed && router.allowEditor(actorId, message);
        }

        @Override
        public synchronized boolean matchesActiveEditorContext(
                UUID actorId,
                WireIdentity.EditorContext context
        ) {
            return !closed
                    && contextAuthority.matchesActiveEditorContext(actorId, context);
        }

        @Override
        public synchronized void close() {
            if (closed) {
                return;
            }
            closed = true;
            maintenance.close();
        }
    }
}
