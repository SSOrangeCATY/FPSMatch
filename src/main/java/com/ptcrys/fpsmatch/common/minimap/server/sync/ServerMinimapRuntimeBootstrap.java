package com.ptcrys.fpsmatch.common.minimap.server.sync;

import com.ptcrys.fpsmatch.common.packet.minimap.MinimapC2SRequestHandler;
import com.ptcrys.fpsmatch.core.minimap.model.MapKey;
import com.ptcrys.fpsmatch.core.minimap.wire.MinimapWireMessage;
import com.ptcrys.fpsmatch.core.minimap.wire.WireIdentity;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongConsumer;

public final class ServerMinimapRuntimeBootstrap {
    private final Consumer<MinimapC2SRequestHandler> handlerInstaller;
    private final Function<Object, ActiveRuntime> runtimeFactory;
    private ActiveRuntime active;
    private Object activeServer;
    private boolean installed;

    public ServerMinimapRuntimeBootstrap(
            Consumer<MinimapC2SRequestHandler> handlerInstaller,
            Function<Object, ActiveRuntime> runtimeFactory
    ) {
        this.handlerInstaller = Objects.requireNonNull(
                handlerInstaller, "handlerInstaller"
        );
        this.runtimeFactory = Objects.requireNonNull(runtimeFactory, "runtimeFactory");
    }

    public synchronized void install(EventSource events) {
        Objects.requireNonNull(events, "events");
        if (installed) {
            return;
        }
        installed = true;
        handlerInstaller.accept(new MinimapC2SRequestHandler(
                (actorId, message) -> hasActiveRuntime(),
                this::allowEditor,
                this::dispatch,
                this::send
        ));
        events.bind(this::start, this::logout, this::stop);
        events.bindTicks(this::tick);
        events.bindInvalidations(this::invalidateMap, this::invalidateActor);
    }

    private synchronized void start(Object server) {
        Object suppliedServer = Objects.requireNonNull(server, "server");
        closeActive();
        ActiveRuntime created = Objects.requireNonNull(
                runtimeFactory.apply(suppliedServer),
                "active runtime"
        );
        active = created;
        activeServer = suppliedServer;
    }

    private synchronized void dispatch(UUID actorId, MinimapWireMessage message) {
        if (active != null) {
            active.dispatch(actorId, message);
        }
    }

    private synchronized void send(UUID actorId, MinimapWireMessage message) {
        if (active != null) {
            active.send(actorId, message);
        }
    }

    private synchronized boolean allowEditor(UUID actorId, MinimapWireMessage message) {
        return active != null && active.allowEditor(actorId, message);
    }

    private synchronized boolean hasActiveRuntime() {
        return active != null;
    }

    private synchronized void tick(long nowTick) {
        if (active != null) {
            active.tick(nowTick);
        }
    }

    public synchronized void onCatalogReload() {
        if (active != null) {
            active.onCatalogReload();
        }
    }

    public synchronized boolean matchesActiveEditorContext(
            Object suppliedServer,
            UUID actorId,
            WireIdentity.EditorContext context
    ) {
        if (active == null || activeServer != suppliedServer
                || actorId == null || context == null) {
            return false;
        }
        try {
            return active.matchesActiveEditorContext(actorId, context);
        } catch (RuntimeException failure) {
            return false;
        }
    }

    /**
     * Lets an in-process publisher reuse the same map invalidation path as the
     * lifecycle event source without allowing a stale server instance to touch
     * the current runtime.
     */
    public synchronized void invalidateMap(
            Object suppliedServer,
            MapKey mapKey
    ) {
        Objects.requireNonNull(suppliedServer, "suppliedServer");
        Objects.requireNonNull(mapKey, "mapKey");
        if (active != null && activeServer == suppliedServer) {
            active.invalidateMap(mapKey);
        }
    }

    private synchronized void logout(UUID actorId) {
        if (active != null) {
            active.onPlayerLogout(actorId);
        }
    }

    private synchronized void invalidateMap(MapKey mapKey) {
        if (active != null) {
            active.invalidateMap(mapKey);
        }
    }

    private synchronized void invalidateActor(UUID actorId) {
        if (active != null) {
            active.invalidateActor(actorId);
        }
    }

    private synchronized void stop() {
        closeActive();
    }

    private void closeActive() {
        ActiveRuntime closing = active;
        active = null;
        activeServer = null;
        if (closing != null) {
            closing.close();
        }
    }

    @FunctionalInterface
    public interface EventSource {
        void bind(
                Consumer<Object> onStart,
                Consumer<UUID> onLogout,
                Runnable onStop
        );

        default void bindTicks(LongConsumer onTick) {
        }

        default void bindInvalidations(
                Consumer<MapKey> onMapInvalidated,
                Consumer<UUID> onActorInvalidated
        ) {
        }
    }

    public interface ActiveRuntime extends AutoCloseable {
        void dispatch(UUID actorId, MinimapWireMessage message);

        /** Sends a server-authored response without entering the request dispatcher. */
        default void send(UUID actorId, MinimapWireMessage message) {
        }

        void onPlayerLogout(UUID actorId);

        default void invalidateMap(MapKey mapKey) {
        }

        default void invalidateActor(UUID actorId) {
            onPlayerLogout(actorId);
        }

        default void tick(long nowTick) {
        }

        default void onCatalogReload() {
        }

        /**
         * Fail closed by default. Runtime factories that own editor sessions override this.
         */
        default boolean allowEditor(UUID actorId, MinimapWireMessage message) {
            return false;
        }

        default boolean matchesActiveEditorContext(
                UUID actorId,
                WireIdentity.EditorContext context
        ) {
            return false;
        }

        @Override
        void close();
    }
}
