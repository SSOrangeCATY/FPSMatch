package com.phasetranscrystal.fpsmatch.common.packet.minimap;

import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapOpcode;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.MinimapWireGoldenFixtures;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.MinimapWireMessage;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinimapPacketEndpointLifecycleTest {

    @Test
    void serverStartConnectionStopTwiceAdvancesGenerationAndClearsState() {
        LifecycleHarness harness = new LifecycleHarness();
        RecordingServerEvents events = new RecordingServerEvents();
        harness.lifecycle.bindServer(events);
        Object server = new Object();
        Object connection = new Object();

        events.start(server);
        events.connect(server, connection);
        MinimapPacketEndpointRegistry.EndpointGeneration first =
                harness.registry.current(connection).orElseThrow();
        acceptPartialFrame(harness.reassembler, connection, new UUID(0, 1));
        assertEquals(1, harness.reassembler.inFlightFrames(connection));

        RecordingContext queued = new RecordingContext(
                MinimapEnvelopeDirection.PLAY_TO_SERVER,
                UUID.randomUUID(),
                connection
        );
        AtomicInteger dispatches = new AtomicInteger();
        MinimapC2SPacket.handleOnNetworkThread(
                c2sPacket(),
                queued,
                new MinimapC2SRequestHandler(
                        (actor, message) -> true,
                        (actor, message) -> dispatches.incrementAndGet()
                ),
                harness.registry,
                harness.reassembler
        );
        assertEquals(1, queued.queued.size());

        events.stop(server);
        events.stop(server);
        queued.queued.get(0).run();
        assertTrue(harness.registry.current(connection).isEmpty());
        assertEquals(0, harness.reassembler.inFlightFrames(connection));
        assertEquals(0, dispatches.get());

        events.start(server);
        events.connect(server, connection);
        MinimapPacketEndpointRegistry.EndpointGeneration second =
                harness.registry.current(connection).orElseThrow();
        assertTrue(second.value() > first.value());
        events.disconnect(connection);
        events.disconnect(connection);
        assertTrue(harness.registry.current(connection).isEmpty());
        events.stop(server);
    }

    @Test
    void serverDisconnectAndStopCloseOnlyTheirOwnedConnectionLeases() {
        LifecycleHarness harness = new LifecycleHarness();
        RecordingServerEvents events = new RecordingServerEvents();
        harness.lifecycle.bindServer(events);
        Object server = new Object();
        Object firstConnection = new Object();
        Object secondConnection = new Object();

        events.start(server);
        events.connect(server, firstConnection);
        events.connect(server, secondConnection);
        events.disconnect(firstConnection);

        assertTrue(harness.registry.current(firstConnection).isEmpty());
        assertTrue(harness.registry.current(secondConnection).isPresent());
        events.stop(server);
        assertTrue(harness.registry.current(secondConnection).isEmpty());
    }

    @Test
    void clientLoginLogoutAndResetOwnTheClientLeaseAndRejectLateWork() {
        LifecycleHarness harness = new LifecycleHarness();
        RecordingClientEvents events = new RecordingClientEvents();
        harness.lifecycle.bindClient(events);
        Object connection = new Object();

        events.login(connection);
        MinimapPacketEndpointRegistry.EndpointGeneration first =
                harness.registry.current(connection).orElseThrow();
        acceptPartialFrame(harness.reassembler, connection, new UUID(0, 2));
        events.logout(connection);
        events.logout(connection);
        assertTrue(harness.registry.current(connection).isEmpty());
        assertEquals(0, harness.reassembler.inFlightFrames(connection));

        events.login(connection);
        MinimapPacketEndpointRegistry.EndpointGeneration second =
                harness.registry.current(connection).orElseThrow();
        assertTrue(second.value() > first.value());
        RecordingContext queued = new RecordingContext(
                MinimapEnvelopeDirection.PLAY_TO_CLIENT,
                null,
                connection
        );
        AtomicInteger dispatches = new AtomicInteger();
        MinimapS2CPacket.handleOnNetworkThread(
                s2cPacket(),
                queued,
                message -> dispatches.incrementAndGet(),
                harness.registry,
                harness.reassembler
        );
        assertEquals(1, queued.queued.size());

        events.reset();
        queued.queued.get(0).run();
        MinimapPacketEndpointRegistry.EndpointGeneration afterReset =
                harness.registry.current(connection).orElseThrow();
        assertTrue(afterReset.value() > second.value());
        assertEquals(0, dispatches.get());

        events.logout(null);
        assertTrue(harness.registry.current(connection).isEmpty());
        Object replacementConnection = new Object();
        events.login(replacementConnection);
        events.logout(null);
        assertTrue(harness.registry.current(replacementConnection).isEmpty());
    }

    private static void acceptPartialFrame(
            MinimapFrameReassembler reassembler,
            Object connection,
            UUID frameId
    ) {
        List<MinimapFrameSegment> segments = MinimapFrameSegmenter.forC2S(
                frameId, new byte[40_000]
        );
        Optional<byte[]> completed = reassembler.accept(
                connection,
                MinimapEnvelopeDirection.PLAY_TO_SERVER,
                segments.get(0)
        );
        assertTrue(completed.isEmpty());
    }

    private static MinimapC2SPacket c2sPacket() {
        MinimapWireMessage message = MinimapWireGoldenFixtures.message(
                MinimapOpcode.C2S_EDITOR_RESERVE_PUBLISH
        );
        return MinimapC2SPacket.fromMessage(new UUID(0, 3), message).get(0);
    }

    private static MinimapS2CPacket s2cPacket() {
        MinimapWireMessage message = MinimapWireGoldenFixtures.message(
                MinimapOpcode.S2C_PUBLISH_RESERVATION
        );
        return MinimapS2CPacket.fromMessage(new UUID(0, 4), message);
    }

    private static final class LifecycleHarness {
        private final MinimapFrameReassembler reassembler =
                new MinimapFrameReassembler(Clock.systemUTC());
        private final MinimapPacketEndpointRegistry registry =
                new MinimapPacketEndpointRegistry(reassembler);
        private final MinimapPacketEndpointLifecycle lifecycle =
                new MinimapPacketEndpointLifecycle(registry);
    }

    private static final class RecordingServerEvents
            implements MinimapPacketLifecycle.ServerEventSource {
        private Consumer<Object> serverStarted;
        private BiConsumer<Object, Object> connectionOpened;
        private Consumer<Object> connectionClosed;
        private Consumer<Object> serverStopped;

        @Override
        public void onServerStarted(Consumer<Object> listener) {
            serverStarted = listener;
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
        public void onServerStopped(Consumer<Object> listener) {
            serverStopped = listener;
        }

        private void start(Object server) {
            serverStarted.accept(server);
        }

        private void connect(Object server, Object connection) {
            connectionOpened.accept(server, connection);
        }

        private void disconnect(Object connection) {
            connectionClosed.accept(connection);
        }

        private void stop(Object server) {
            serverStopped.accept(server);
        }
    }

    private static final class RecordingClientEvents
            implements MinimapPacketLifecycle.ClientEventSource {
        private Consumer<Object> loggedIn;
        private Consumer<Object> loggedOut;
        private Runnable reset;

        @Override
        public void onLoggedIn(Consumer<Object> listener) {
            loggedIn = listener;
        }

        @Override
        public void onLoggedOut(Consumer<Object> listener) {
            loggedOut = listener;
        }

        @Override
        public void onReset(Runnable listener) {
            reset = listener;
        }

        private void login(Object connection) {
            loggedIn.accept(connection);
        }

        private void logout(Object connection) {
            loggedOut.accept(connection);
        }

        private void reset() {
            reset.run();
        }
    }

    private static final class RecordingContext implements MinimapEnvelopeContext {
        private final MinimapEnvelopeDirection direction;
        private final UUID senderId;
        private final Object connection;
        private final List<Runnable> queued = new ArrayList<>();

        private RecordingContext(
                MinimapEnvelopeDirection direction,
                UUID senderId,
                Object connection
        ) {
            this.direction = direction;
            this.senderId = senderId;
            this.connection = connection;
        }

        @Override
        public MinimapEnvelopeDirection direction() {
            return direction;
        }

        @Override
        public UUID senderId() {
            return senderId;
        }

        @Override
        public Object connectionToken() {
            return connection;
        }

        @Override
        public void enqueueWork(Runnable work) {
            queued.add(work);
        }

        @Override
        public void markHandled() {
        }
    }
}
