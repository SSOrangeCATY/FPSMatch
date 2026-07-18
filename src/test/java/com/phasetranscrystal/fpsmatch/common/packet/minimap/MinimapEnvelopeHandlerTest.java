package com.phasetranscrystal.fpsmatch.common.packet.minimap;

import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapMessageDirection;
import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapOpcode;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.MinimapWireMessage;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.MinimapWireGoldenFixtures;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.PublishWireMessage;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.RuntimeWireMessage;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.WireIdentity;
import com.phasetranscrystal.fpsmatch.core.minimap.model.MapKey;
import com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId;
import com.phasetranscrystal.fpsmatch.core.minimap.model.Sha256;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.time.Clock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinimapEnvelopeHandlerTest {

    @Test
    void queuedWorkFromAClosedOrReplacedGenerationNeverDispatches() {
        MinimapFrameReassembler reassembler =
                new MinimapFrameReassembler(Clock.systemUTC());
        MinimapPacketEndpointRegistry registry =
                new MinimapPacketEndpointRegistry(reassembler);
        Object connection = new Object();
        MinimapPacketEndpointRegistry.EndpointLease first = registry.install(connection);
        RecordingContext context = new RecordingContext(
                MinimapEnvelopeDirection.PLAY_TO_SERVER,
                UUID.randomUUID(),
                connection
        );
        AtomicInteger gateCalls = new AtomicInteger();
        AtomicInteger dispatches = new AtomicInteger();
        MinimapC2SRequestHandler handler = new MinimapC2SRequestHandler(
                (actorId, message) -> {
                    gateCalls.incrementAndGet();
                    return true;
                },
                (actorId, message) -> dispatches.incrementAndGet()
        );
        MinimapC2SPacket packet = MinimapC2SPacket.fromMessage(
                new UUID(0, 99), reservePublish()
        ).get(0);

        MinimapC2SPacket.handleOnNetworkThread(
                packet, context, handler, registry, reassembler
        );

        assertEquals(1, context.queued.size());
        first.close();
        registry.install(connection);
        context.queued.get(0).run();
        assertEquals(0, gateCalls.get());
        assertEquals(0, dispatches.get());
    }

    @Test
    void queuedWorkAfterDisconnectStopOrClientResetNeverDispatches() {
        for (int closeMode = 0; closeMode < 2; closeMode++) {
            MinimapFrameReassembler reassembler =
                    new MinimapFrameReassembler(Clock.systemUTC());
            MinimapPacketEndpointRegistry registry =
                    new MinimapPacketEndpointRegistry(reassembler);
            Object connection = new Object();
            MinimapPacketEndpointRegistry.EndpointLease lease =
                    registry.install(connection);
            RecordingContext context = new RecordingContext(
                    MinimapEnvelopeDirection.PLAY_TO_SERVER,
                    UUID.randomUUID(),
                    connection
            );
            AtomicInteger dispatches = new AtomicInteger();
            MinimapC2SPacket.handleOnNetworkThread(
                    c2sPacket(reservePublish()),
                    context,
                    new MinimapC2SRequestHandler(
                            (actorId, message) -> true,
                            (actorId, message) -> dispatches.incrementAndGet()
                    ),
                    registry,
                    reassembler
            );
            assertEquals(1, context.queued.size());

            if (closeMode == 0) {
                lease.close();
            } else {
                registry.closeAll();
            }
            context.queued.get(0).run();
            assertEquals(0, dispatches.get());
        }

        EndpointHarness endpoints = new EndpointHarness();
        RecordingContext clientContext = new RecordingContext(
                MinimapEnvelopeDirection.PLAY_TO_CLIENT,
                null,
                endpoints.connection
        );
        AtomicInteger clientDispatches = new AtomicInteger();
        MinimapS2CPacket.handleOnNetworkThread(
                MinimapS2CPacket.fromMessage(new UUID(0, 102), publishReservation()),
                clientContext,
                message -> clientDispatches.incrementAndGet(),
                endpoints.registry,
                endpoints.reassembler
        );
        endpoints.registry.closeAll();
        clientContext.queued.get(0).run();
        assertEquals(0, clientDispatches.get());
    }

    @Test
    void wrongForgeDirectionIsRejectedBeforeDecodeOrEnqueue() {
        EndpointHarness endpoints = new EndpointHarness();
        RecordingContext context = new RecordingContext(
                MinimapEnvelopeDirection.PLAY_TO_CLIENT,
                UUID.randomUUID(),
                endpoints.connection
        );
        MinimapC2SRequestHandler handler = new MinimapC2SRequestHandler(
                (actorId, message) -> {
                    throw new AssertionError("gate must not run");
                },
                (actorId, message) -> {
                    throw new AssertionError("dispatcher must not run");
                }
        );
        MinimapFrameSegment firstSegment = MinimapFrameSegmenter.forC2S(
                new UUID(0, 103), new byte[40_000]
        ).get(0);
        FriendlyByteBuf encoded = new FriendlyByteBuf(Unpooled.buffer());
        MinimapC2SPacket.encode(new MinimapC2SPacket(firstSegment), encoded);
        MinimapC2SPacket decoded = MinimapC2SPacket.decode(encoded);

        MinimapC2SPacket.handleOnNetworkThread(
                decoded,
                context,
                handler,
                endpoints.registry,
                endpoints.reassembler
        );

        assertTrue(context.handled);
        assertTrue(context.queued.isEmpty());
        assertEquals(0, endpoints.reassembler.inFlightFrames(endpoints.connection));
        assertEquals(0, encoded.readableBytes());
        encoded.release();
    }

    @Test
    void validC2SIsDecodedThenQueuedBeforeDispatch() {
        EndpointHarness endpoints = new EndpointHarness();
        UUID actorId = UUID.randomUUID();
        RecordingContext context = new RecordingContext(
                MinimapEnvelopeDirection.PLAY_TO_SERVER,
                actorId,
                endpoints.connection
        );
        AtomicInteger dispatches = new AtomicInteger();
        MinimapC2SRequestHandler handler = new MinimapC2SRequestHandler(
                (ignoredActor, ignoredMessage) -> true,
                (actualActor, message) -> {
                    assertEquals(actorId, actualActor);
                    assertEquals(
                            MinimapOpcode.C2S_EDITOR_RESERVE_PUBLISH,
                            message.opcode()
                    );
                    dispatches.incrementAndGet();
                }
        );

        MinimapC2SPacket.handleOnNetworkThread(
                c2sPacket(reservePublish()),
                context,
                handler,
                endpoints.registry,
                endpoints.reassembler
        );

        assertTrue(context.handled);
        assertEquals(1, context.queued.size());
        assertEquals(0, dispatches.get());

        context.queued.get(0).run();
        assertEquals(1, dispatches.get());
    }

    @Test
    void missingC2SSenderFailsClosedWithoutEnqueue() {
        EndpointHarness endpoints = new EndpointHarness();
        RecordingContext context = new RecordingContext(
                MinimapEnvelopeDirection.PLAY_TO_SERVER,
                null,
                endpoints.connection
        );

        MinimapC2SPacket.handleOnNetworkThread(
                c2sPacket(reservePublish()),
                context,
                MinimapC2SRequestHandler.disabled(),
                endpoints.registry,
                endpoints.reassembler
        );

        assertTrue(context.handled);
        assertTrue(context.queued.isEmpty());
    }

    @Test
    void validS2CIsDecodedThenQueuedBeforeClientDispatch() {
        EndpointHarness endpoints = new EndpointHarness();
        RecordingContext context = new RecordingContext(
                MinimapEnvelopeDirection.PLAY_TO_CLIENT,
                null,
                endpoints.connection
        );
        AtomicInteger dispatches = new AtomicInteger();

        MinimapS2CPacket.handleOnNetworkThread(
                MinimapS2CPacket.fromMessage(new UUID(0, 101), publishReservation()),
                context,
                message -> {
                    assertEquals(
                            MinimapOpcode.S2C_PUBLISH_RESERVATION,
                            message.opcode()
                    );
                    dispatches.incrementAndGet();
                },
                endpoints.registry,
                endpoints.reassembler
        );

        assertTrue(context.handled);
        assertEquals(1, context.queued.size());
        assertEquals(0, dispatches.get());

        context.queued.get(0).run();
        assertEquals(1, dispatches.get());
    }

    @Test
    void everyEditorC2SRevalidatesBeforeDispatcher() {
        UUID actorId = UUID.randomUUID();
        for (MinimapOpcode opcode : MinimapOpcode.values()) {
            if (opcode.direction() != MinimapMessageDirection.C2S || opcode.code() < 0x10) {
                continue;
            }
            EndpointHarness endpoints = new EndpointHarness();
            RecordingContext context = new RecordingContext(
                    MinimapEnvelopeDirection.PLAY_TO_SERVER,
                    actorId,
                    endpoints.connection
            );
            List<String> calls = new ArrayList<>();
            MinimapC2SRequestHandler handler = new MinimapC2SRequestHandler(
                    (actualActor, message) -> {
                        assertEquals(actorId, actualActor);
                        assertEquals(opcode, message.opcode());
                        calls.add("revalidate");
                        return true;
                    },
                    (actualActor, message) -> calls.add("dispatch")
            );

            MinimapC2SPacket.handleOnNetworkThread(
                    c2sPacket(MinimapWireGoldenFixtures.message(opcode)),
                    context,
                    handler,
                    endpoints.registry,
                    endpoints.reassembler
            );

            assertTrue(calls.isEmpty(), opcode.name());
            assertEquals(1, context.queued.size(), opcode.name());
            context.queued.get(0).run();
            assertEquals(List.of("revalidate", "dispatch"), calls, opcode.name());
        }
    }

    @Test
    void editorDenialAndGateFailureBothFailClosed() {
        UUID actorId = UUID.randomUUID();
        AtomicInteger dispatches = new AtomicInteger();
        MinimapWireMessage message = reservePublish();
        MinimapC2SRequestHandler denied = new MinimapC2SRequestHandler(
                (ignoredActor, ignoredMessage) -> false,
                (ignoredActor, ignoredMessage) -> dispatches.incrementAndGet()
        );
        MinimapC2SRequestHandler failed = new MinimapC2SRequestHandler(
                (ignoredActor, ignoredMessage) -> {
                    throw new IllegalStateException("permission backend unavailable");
                },
                (ignoredActor, ignoredMessage) -> dispatches.incrementAndGet()
        );

        assertFalse(denied.handle(actorId, message));
        assertFalse(failed.handle(actorId, message));
        assertEquals(0, dispatches.get());
    }

    @Test
    void runtimeC2SDoesNotRequireAnEditorGate() {
        UUID actorId = UUID.randomUUID();
        AtomicInteger dispatches = new AtomicInteger();
        MinimapC2SRequestHandler handler = new MinimapC2SRequestHandler(
                (ignoredActor, ignoredMessage) -> {
                    throw new AssertionError("runtime request must not use the editor gate");
                },
                (ignoredActor, ignoredMessage) -> dispatches.incrementAndGet()
        );

        assertTrue(handler.handle(actorId, runtimeRequest()));
        assertEquals(1, dispatches.get());
    }

    private static RuntimeWireMessage.RequestMarkerReset runtimeRequest() {
        WireIdentity.MapTarget target = new WireIdentity.MapTarget(
                new MapKey("fpsmatch:test", "Map A"),
                NamespacedId.parse("minecraft:overworld")
        );
        WireIdentity.RuntimeIdentity runtime = new WireIdentity.RuntimeIdentity(
                new WireIdentity.DocumentBinding(
                        target, NamespacedId.parse("fpsmatch:test_map")
                ),
                0,
                new Sha256("00".repeat(32)),
                Optional.empty()
        );
        return new RuntimeWireMessage.RequestMarkerReset(
                UUID.randomUUID(),
                new WireIdentity.ScopeLease(WireIdentity.Scope.MATCH_HUD, 0, 0),
                runtime,
                Optional.empty()
        );
    }

    private static PublishWireMessage.ReservePublish reservePublish() {
        return new PublishWireMessage.ReservePublish(
                new UUID(0, 1), editorContext()
        );
    }

    private static MinimapC2SPacket c2sPacket(MinimapWireMessage message) {
        List<MinimapC2SPacket> packets = MinimapC2SPacket.fromMessage(
                new UUID(0, 100), message
        );
        assertEquals(1, packets.size());
        return packets.get(0);
    }

    private static PublishWireMessage.PublishReservation publishReservation() {
        return new PublishWireMessage.PublishReservation(
                new UUID(0, 1), editorContext(), "t", 0, 1, 2
        );
    }

    private static WireIdentity.EditorContext editorContext() {
        WireIdentity.DocumentBinding binding = new WireIdentity.DocumentBinding(
                new WireIdentity.MapTarget(
                        new MapKey("g", "m"), NamespacedId.parse("a:d")
                ),
                NamespacedId.parse("a:o")
        );
        return new WireIdentity.EditorContext(
                new WireIdentity.ScopeLease(WireIdentity.Scope.EDITOR, 1, 2),
                binding,
                new UUID(0, 2),
                new UUID(0, 3),
                0,
                new Sha256("11".repeat(32)),
                new Sha256("22".repeat(32)),
                0
        );
    }

    private static final class RecordingContext implements MinimapEnvelopeContext {
        private final MinimapEnvelopeDirection direction;
        private final UUID senderId;
        private final Object connectionToken;
        private final List<Runnable> queued = new ArrayList<>();
        private boolean handled;

        private RecordingContext(
                MinimapEnvelopeDirection direction,
                UUID senderId,
                Object connectionToken
        ) {
            this.direction = direction;
            this.senderId = senderId;
            this.connectionToken = connectionToken;
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
            return connectionToken;
        }

        @Override
        public void enqueueWork(Runnable work) {
            queued.add(work);
        }

        @Override
        public void markHandled() {
            handled = true;
        }
    }

    private static final class EndpointHarness {
        private final MinimapFrameReassembler reassembler =
                new MinimapFrameReassembler(Clock.systemUTC());
        private final MinimapPacketEndpointRegistry registry =
                new MinimapPacketEndpointRegistry(reassembler);
        private final Object connection = new Object();

        private EndpointHarness() {
            registry.install(connection);
        }
    }
}
