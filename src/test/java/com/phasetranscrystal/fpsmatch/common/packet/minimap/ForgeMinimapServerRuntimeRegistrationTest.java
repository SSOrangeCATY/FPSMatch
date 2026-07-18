package com.phasetranscrystal.fpsmatch.common.packet.minimap;

import com.phasetranscrystal.fpsmatch.common.minimap.server.sync.ServerMinimapRuntimeBootstrap;
import com.phasetranscrystal.fpsmatch.core.minimap.model.MapKey;
import com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.RuntimeWireMessage;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.WireIdentity;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForgeMinimapServerRuntimeRegistrationTest {
    @Test
    void installsRuntimeHandlerAndBindsLifecycleOnce() {
        RecordingEvents events = new RecordingEvents();
        AtomicReference<MinimapC2SRequestHandler> handler = new AtomicReference<>();
        AtomicInteger dispatches = new AtomicInteger();
        AtomicInteger closes = new AtomicInteger();

        ForgeMinimapServerRuntimeRegistration.install(
                events,
                handler::set,
                server -> new ServerMinimapRuntimeBootstrap.ActiveRuntime() {
                    @Override
                    public void dispatch(
                            UUID actorId,
                            com.phasetranscrystal.fpsmatch.core.minimap.wire.MinimapWireMessage message
                    ) {
                        dispatches.incrementAndGet();
                    }

                    @Override
                    public void onPlayerLogout(UUID actorId) {
                    }

                    @Override
                    public void close() {
                        closes.incrementAndGet();
                    }
                }
        );

        UUID actor = new UUID(0L, 1L);
        assertFalse(handler.get().handle(actor, subscribe()));
        events.start.accept(new Object());
        assertTrue(handler.get().handle(actor, subscribe()));
        events.stop.run();
        assertFalse(handler.get().handle(actor, subscribe()));
        assertTrue(dispatches.get() == 1);
        assertTrue(closes.get() == 1);
        assertTrue(events.bindCount == 1);
    }

    @Test
    void loadsSharedBuiltinCatalogBeforeRuntimeAndRefreshesItOnlyGlobally() {
        RecordingForgeEvents forgeEvents = new RecordingForgeEvents();
        ForgeMinimapServerLifecycleEventSource events =
                new ForgeMinimapServerLifecycleEventSource(forgeEvents);
        AtomicReference<MinimapC2SRequestHandler> handler = new AtomicReference<>();
        ArrayList<String> calls = new ArrayList<>();

        ForgeMinimapServerRuntimeRegistration.install(
                events,
                handler::set,
                server -> {
                    calls.add("runtime");
                    return runtime(calls);
                },
                server -> calls.add("catalog"),
                () -> calls.add("clear")
        );
        Object server = new Object();

        forgeEvents.earlyStart.accept(server);
        forgeEvents.start.accept(server);
        forgeEvents.datapackSync.accept(server, new Object());
        forgeEvents.datapackSync.accept(server, null);
        forgeEvents.stopping.accept(server);
        forgeEvents.stopped.accept(server);

        org.junit.jupiter.api.Assertions.assertEquals(
                List.of("catalog", "runtime", "catalog", "close", "clear"),
                calls
        );
        assertFalse(handler.get().handle(new UUID(0L, 7L), subscribe()));
    }

    private static ServerMinimapRuntimeBootstrap.ActiveRuntime runtime(
            List<String> calls
    ) {
        return new ServerMinimapRuntimeBootstrap.ActiveRuntime() {
            @Override
            public void dispatch(
                    UUID actorId,
                    com.phasetranscrystal.fpsmatch.core.minimap.wire.MinimapWireMessage message
            ) {
            }

            @Override
            public void onPlayerLogout(UUID actorId) {
            }

            @Override
            public void close() {
                calls.add("close");
            }
        };
    }

    private static RuntimeWireMessage.Subscribe subscribe() {
        return new RuntimeWireMessage.Subscribe(
                new UUID(0L, 2L),
                new WireIdentity.ScopeLease(
                        WireIdentity.Scope.MATCH_HUD, 1L, 1L
                ),
                new WireIdentity.MapTarget(
                        new MapKey("cs", "dust2"),
                        NamespacedId.parse("minecraft:overworld")
                ),
                Optional.empty()
        );
    }

    private static final class RecordingEvents
            implements ServerMinimapRuntimeBootstrap.EventSource {
        private Consumer<Object> start;
        private Runnable stop;
        private int bindCount;

        @Override
        public void bind(
                Consumer<Object> onStart,
                Consumer<UUID> onLogout,
                Runnable onStop
        ) {
            bindCount++;
            start = onStart;
            stop = onStop;
        }
    }

    private static final class RecordingForgeEvents
            implements ForgeMinimapServerLifecycleEventSource.EventRegistrar {
        private Consumer<Object> start;
        private Consumer<Object> earlyStart;
        private BiConsumer<Object, Object> datapackSync;
        private Consumer<Object> stopping;
        private Consumer<Object> stopped;

        @Override
        public void onServerStarted(Consumer<Object> listener) {
            start = listener;
        }

        @Override
        public void onServerStartedEarly(Consumer<Object> listener) {
            earlyStart = listener;
        }

        @Override
        public void onDatapackSync(BiConsumer<Object, Object> listener) {
            datapackSync = listener;
        }

        @Override
        public void onConnectionOpened(BiConsumer<Object, Object> listener) {
        }

        @Override
        public void onConnectionClosed(Consumer<Object> listener) {
        }

        @Override
        public void onPlayerLoggedOut(Consumer<UUID> listener) {
        }

        @Override
        public void onServerStopping(Consumer<Object> listener) {
            stopping = listener;
        }

        @Override
        public void onServerStopped(Consumer<Object> listener) {
            stopped = listener;
        }
    }
}
