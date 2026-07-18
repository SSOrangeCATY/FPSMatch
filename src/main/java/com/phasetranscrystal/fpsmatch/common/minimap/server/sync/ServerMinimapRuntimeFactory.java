package com.phasetranscrystal.fpsmatch.common.minimap.server.sync;

import com.phasetranscrystal.fpsmatch.core.minimap.extension.MinimapExtensionRegistry;
import com.phasetranscrystal.fpsmatch.core.minimap.extension.MarkerPresentation;
import com.phasetranscrystal.fpsmatch.core.minimap.marker.MarkerCandidate;
import com.phasetranscrystal.fpsmatch.core.minimap.marker.MarkerSnapshot;
import com.phasetranscrystal.fpsmatch.core.minimap.marker.MinimapMarkerProvider;
import com.phasetranscrystal.fpsmatch.core.minimap.marker.MinimapViewerContext;
import com.phasetranscrystal.fpsmatch.core.minimap.marker.MinimapVisibilityPolicy;
import com.phasetranscrystal.fpsmatch.core.minimap.model.MapKey;
import com.phasetranscrystal.fpsmatch.core.minimap.storage.MinimapRepository;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.MinimapWireMessage;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.WireIdentity;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.function.IntSupplier;

public final class ServerMinimapRuntimeFactory<S> {
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
        ServerMinimapRuntimeRouter router = new ServerMinimapRuntimeRouter(
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
                ServerMinimapRuntimeFactory::resolveMarkers,
                (actorId, message) -> access.send(server, actorId, message),
                transferIds,
                markerHz
        );
        return new ActiveRuntime(router);
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

    public interface ServerAccess<S> {
        Path repositoryRoot(S server);

        Optional<RuntimeAuthority> resolveAuthority(
                S server,
                UUID actorId,
                WireIdentity.MapTarget target
        );

        void send(S server, UUID actorId, MinimapWireMessage message);
    }

    private static final class ActiveRuntime
            implements ServerMinimapRuntimeBootstrap.ActiveRuntime {
        private final ServerMinimapRuntimeRouter router;
        private boolean closed;

        private ActiveRuntime(ServerMinimapRuntimeRouter router) {
            this.router = router;
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
        public synchronized void tick(long nowTick) {
            if (!closed) {
                router.tick(nowTick);
            }
        }

        @Override
        public synchronized void close() {
            closed = true;
        }
    }
}
