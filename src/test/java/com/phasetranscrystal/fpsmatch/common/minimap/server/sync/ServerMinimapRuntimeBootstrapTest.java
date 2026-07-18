package com.phasetranscrystal.fpsmatch.common.minimap.server.sync;

import com.phasetranscrystal.fpsmatch.common.packet.minimap.MinimapC2SRequestHandler;
import com.phasetranscrystal.fpsmatch.core.minimap.model.MapKey;
import com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.RuntimeWireMessage;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.WireIdentity;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerMinimapRuntimeBootstrapTest {
    @Test
    void installsOnceStartsRoutesLogoutCleanupAndStopsFailClosed() {
        RecordingEvents events = new RecordingEvents();
        AtomicReference<MinimapC2SRequestHandler> installed = new AtomicReference<>();
        AtomicInteger dispatches = new AtomicInteger();
        AtomicInteger logouts = new AtomicInteger();
        AtomicInteger ticks = new AtomicInteger();
        ServerMinimapRuntimeBootstrap bootstrap = new ServerMinimapRuntimeBootstrap(
                installed::set,
                server -> new ServerMinimapRuntimeBootstrap.ActiveRuntime() {
                    @Override
                    public void dispatch(UUID actorId, com.phasetranscrystal.fpsmatch.core.minimap.wire.MinimapWireMessage message) {
                        dispatches.incrementAndGet();
                    }

                    @Override
                    public void onPlayerLogout(UUID actorId) {
                        logouts.incrementAndGet();
                    }

                    @Override
                    public void tick(long nowTick) {
                        ticks.addAndGet(Math.toIntExact(nowTick));
                    }

                    @Override
                    public void close() {
                    }
                }
        );
        bootstrap.install(events);
        bootstrap.install(events);
        UUID actor = new UUID(0L, 1L);

        assertFalse(installed.get().handle(actor, subscribe()));
        events.start.accept(new Object());
        assertTrue(installed.get().handle(actor, subscribe()));
        events.tick.accept(4L);
        events.logout.accept(actor);
        events.stop.run();
        events.tick.accept(8L);
        assertFalse(installed.get().handle(actor, subscribe()));

        org.junit.jupiter.api.Assertions.assertEquals(1, events.bindCount);
        org.junit.jupiter.api.Assertions.assertEquals(1, dispatches.get());
        org.junit.jupiter.api.Assertions.assertEquals(1, logouts.get());
        org.junit.jupiter.api.Assertions.assertEquals(4, ticks.get());
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
        private Consumer<UUID> logout;
        private Runnable stop;
        private LongConsumer tick;
        private int bindCount;

        @Override
        public void bind(
                Consumer<Object> onStart,
                Consumer<UUID> onLogout,
                Runnable onStop
        ) {
            bindCount++;
            start = onStart;
            logout = onLogout;
            stop = onStop;
        }

        @Override
        public void bindTicks(LongConsumer onTick) {
            tick = onTick;
        }
    }
}
