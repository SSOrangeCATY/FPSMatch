package com.ptcrys.fpsmatch.common.minimap.server.sync;

import com.ptcrys.fpsmatch.common.packet.minimap.MinimapC2SRequestHandler;
import com.ptcrys.fpsmatch.core.minimap.wire.MinimapWireMessage;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongConsumer;

public final class ServerMinimapRuntimeBootstrap {
    private final Consumer<MinimapC2SRequestHandler> handlerInstaller;
    private final Function<Object, ActiveRuntime> runtimeFactory;
    private ActiveRuntime active;
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
                this::dispatch
        ));
        events.bind(this::start, this::logout, this::stop);
        events.bindTicks(this::tick);
    }

    private synchronized void start(Object server) {
        closeActive();
        active = Objects.requireNonNull(
                runtimeFactory.apply(Objects.requireNonNull(server, "server")),
                "active runtime"
        );
    }

    private synchronized void dispatch(UUID actorId, MinimapWireMessage message) {
        if (active != null) {
            active.dispatch(actorId, message);
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

    private synchronized void logout(UUID actorId) {
        if (active != null) {
            active.onPlayerLogout(actorId);
        }
    }

    private synchronized void stop() {
        closeActive();
    }

    private void closeActive() {
        if (active == null) {
            return;
        }
        active.close();
        active = null;
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
    }

    public interface ActiveRuntime extends AutoCloseable {
        void dispatch(UUID actorId, MinimapWireMessage message);

        void onPlayerLogout(UUID actorId);

        default void tick(long nowTick) {
        }

        /**
         * Fail closed by default. Runtime factories that own editor sessions override this.
         */
        default boolean allowEditor(UUID actorId, MinimapWireMessage message) {
            return false;
        }

        @Override
        void close();
    }
}
