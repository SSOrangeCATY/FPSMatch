package com.phasetranscrystal.fpsmatch.common.packet.minimap;

import com.phasetranscrystal.fpsmatch.common.minimap.server.sync.ServerMinimapRuntimeBootstrap;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

class ForgeMinimapServerLifecycleEventSourceRuntimeTest {
    @Test
    void forwardsServerRuntimeStartPlayerLogoutAndStop() {
        RecordingEvents events = new RecordingEvents();
        ForgeMinimapServerLifecycleEventSource source =
                new ForgeMinimapServerLifecycleEventSource(events);
        ServerMinimapRuntimeBootstrap.EventSource runtimeEvents = assertInstanceOf(
                ServerMinimapRuntimeBootstrap.EventSource.class, source
        );
        ArrayList<Object> calls = new ArrayList<>();
        runtimeEvents.bind(
                server -> calls.add(server),
                playerId -> calls.add(playerId),
                () -> calls.add("stop")
        );
        runtimeEvents.bindTicks(tick -> calls.add(List.of("tick", tick)));
        Object server = new Object();
        UUID player = new UUID(0L, 1L);

        events.start.accept(server);
        events.tick.accept(4L);
        events.logout.accept(player);
        assertNull(events.stopped);

        org.junit.jupiter.api.Assertions.assertEquals(
                List.of(server, List.of("tick", 4L), player), calls
        );
        events.stopping.accept(server);
        events.stopping.accept(server);

        org.junit.jupiter.api.Assertions.assertEquals(
                List.of(server, List.of("tick", 4L), player, "stop", "stop"), calls
        );
    }

    @Test
    void loadsBuiltinCatalogEarlyReloadsOnlyGloballyAndClearsAfterStop() {
        RecordingEvents events = new RecordingEvents();
        ForgeMinimapServerLifecycleEventSource source =
                new ForgeMinimapServerLifecycleEventSource(events);
        ArrayList<Object> calls = new ArrayList<>();
        source.bindBuiltinCatalog(
                server -> calls.add(List.of("load", server)),
                server -> calls.add(List.of("reload", server)),
                () -> calls.add("clear")
        );
        Object server = new Object();

        events.earlyStart.accept(server);
        events.datapackSync.accept(server, new Object());
        events.datapackSync.accept(server, null);
        events.stopped.accept(server);

        org.junit.jupiter.api.Assertions.assertEquals(
                List.of(
                        List.of("load", server),
                        List.of("reload", server),
                        "clear"
                ),
                calls
        );
    }

    private static final class RecordingEvents
            implements ForgeMinimapServerLifecycleEventSource.EventRegistrar {
        private Consumer<Object> start;
        private Consumer<Object> earlyStart;
        private BiConsumer<Object, Object> datapackSync;
        private BiConsumer<Object, Object> connectionOpened;
        private Consumer<Object> connectionClosed;
        private Consumer<UUID> logout;
        private Consumer<Object> stopping;
        private Consumer<Object> stopped;
        private LongConsumer tick;

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
            connectionOpened = listener;
        }

        @Override
        public void onConnectionClosed(Consumer<Object> listener) {
            connectionClosed = listener;
        }

        @Override
        public void onPlayerLoggedOut(Consumer<UUID> listener) {
            logout = listener;
        }

        @Override
        public void onServerStopping(Consumer<Object> listener) {
            stopping = listener;
        }

        @Override
        public void onServerStopped(Consumer<Object> listener) {
            stopped = listener;
        }

        @Override
        public void onServerTick(LongConsumer listener) {
            tick = listener;
        }
    }
}
