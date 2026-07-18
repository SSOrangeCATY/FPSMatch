package com.phasetranscrystal.fpsmatch.common.packet.minimap;

import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapMessageDirection;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.MinimapWireCodec;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.MinimapWireError;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.MinimapWireMessage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.Objects;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/** The single Forge PLAY_TO_SERVER envelope for all stable minimap C2S opcodes. */
public final class MinimapC2SPacket {
    private static volatile MinimapC2SRequestHandler requestHandler =
            MinimapC2SRequestHandler.disabled();

    private final MinimapFrameSegment segment;

    public MinimapC2SPacket(MinimapFrameSegment segment) {
        this.segment = Objects.requireNonNull(segment, "segment");
    }

    public static List<MinimapC2SPacket> fromMessage(
            UUID frameId,
            MinimapWireMessage message
    ) {
        Objects.requireNonNull(frameId, "frameId");
        Objects.requireNonNull(message, "message");
        if (message.opcode().direction() != MinimapMessageDirection.C2S) {
            throw new MinimapWireError(
                    com.phasetranscrystal.fpsmatch.core.minimap.contract
                            .MinimapErrorCode.WRONG_DIRECTION,
                    "Wrong minimap envelope direction"
            );
        }
        List<MinimapFrameSegment> segments = MinimapFrameSegmenter.forC2S(
                frameId,
                MinimapWireCodec.encode(message)
        );
        ArrayList<MinimapC2SPacket> packets = new ArrayList<>(segments.size());
        segments.forEach(segment -> packets.add(new MinimapC2SPacket(segment)));
        return List.copyOf(packets);
    }

    public MinimapFrameSegment segment() {
        return segment;
    }

    public static void installHandler(MinimapC2SRequestHandler handler) {
        requestHandler = Objects.requireNonNull(handler, "handler");
    }

    public static void encode(MinimapC2SPacket packet, FriendlyByteBuf buffer) {
        Objects.requireNonNull(packet, "packet");
        MinimapEnvelopeBody.write(
                packet.segment,
                buffer,
                MinimapEnvelopeDirection.PLAY_TO_SERVER
        );
    }

    public static MinimapC2SPacket decode(FriendlyByteBuf buffer) {
        return new MinimapC2SPacket(MinimapEnvelopeBody.read(
                buffer,
                MinimapEnvelopeDirection.PLAY_TO_SERVER
        ));
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = Objects.requireNonNull(
                contextSupplier,
                "contextSupplier"
        ).get();
        handleOnNetworkThread(
                this,
                MinimapEnvelopeContext.forge(context),
                requestHandler,
                MinimapPacketEndpointRuntime.REGISTRY,
                MinimapPacketEndpointRuntime.REASSEMBLER
        );
    }

    static void handleOnNetworkThread(
            MinimapC2SPacket packet,
            MinimapEnvelopeContext context,
            MinimapC2SRequestHandler handler,
            MinimapPacketEndpointRegistry endpoints,
            MinimapFrameReassembler reassembler
    ) {
        Objects.requireNonNull(packet, "packet");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(handler, "handler");
        Objects.requireNonNull(endpoints, "endpoints");
        Objects.requireNonNull(reassembler, "reassembler");
        if (context.direction() != MinimapEnvelopeDirection.PLAY_TO_SERVER) {
            context.markHandled();
            return;
        }
        UUID actorId = context.senderId();
        if (actorId == null) {
            context.markHandled();
            return;
        }

        Object connectionToken = context.connectionToken();
        Optional<MinimapPacketEndpointRegistry.EndpointGeneration> endpoint =
                endpoints.current(connectionToken);
        if (connectionToken == null || endpoint.isEmpty()) {
            context.markHandled();
            return;
        }

        MinimapWireMessage message;
        try {
            Optional<byte[]> frame = reassembler.accept(
                    connectionToken,
                    MinimapEnvelopeDirection.PLAY_TO_SERVER,
                    packet.segment
            );
            if (frame.isEmpty()) {
                context.markHandled();
                return;
            }
            message = MinimapWireCodec.decode(
                    MinimapMessageDirection.C2S,
                    frame.orElseThrow()
            );
        } catch (MinimapWireError rejected) {
            context.markHandled();
            return;
        }
        MinimapPacketEndpointRegistry.EndpointGeneration generation =
                endpoint.orElseThrow();
        if (!endpoints.isCurrent(generation)) {
            context.markHandled();
            return;
        }
        context.enqueueWork(() -> {
            if (endpoints.isCurrent(generation)) {
                handler.handle(actorId, message);
            }
        });
        context.markHandled();
    }
}
