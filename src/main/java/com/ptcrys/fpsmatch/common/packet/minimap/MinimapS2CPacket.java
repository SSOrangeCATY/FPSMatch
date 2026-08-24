package com.ptcrys.fpsmatch.common.packet.minimap;

import com.ptcrys.fpsmatch.core.minimap.contract.MinimapMessageDirection;
import com.ptcrys.fpsmatch.core.minimap.wire.MinimapWireCodec;
import com.ptcrys.fpsmatch.core.minimap.wire.MinimapWireError;
import com.ptcrys.fpsmatch.core.minimap.wire.MinimapWireMessage;
import com.ptcrys.fpsmatch.core.minimap.wire.MarkerWireMessage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/** The single Forge PLAY_TO_CLIENT envelope for all stable minimap S2C opcodes. */
public final class MinimapS2CPacket {
    private static volatile MinimapS2CDispatcher dispatcher = message -> {
    };

    private final MinimapFrameSegment segment;
    private final UUID frameId;
    private final MinimapWireCodec.PreparedMarkerFrame markerFrame;

    public MinimapS2CPacket(MinimapFrameSegment segment) {
        this.segment = Objects.requireNonNull(segment, "segment");
        this.frameId = null;
        this.markerFrame = null;
    }

    private MinimapS2CPacket(
            UUID frameId,
            MinimapWireCodec.PreparedMarkerFrame markerFrame
    ) {
        this.segment = null;
        this.frameId = Objects.requireNonNull(frameId, "frameId");
        this.markerFrame = Objects.requireNonNull(markerFrame, "markerFrame");
    }

    public static MinimapS2CPacket fromMessage(
            UUID frameId,
            MinimapWireMessage message
    ) {
        Objects.requireNonNull(frameId, "frameId");
        Objects.requireNonNull(message, "message");
        if (message.opcode().direction() != MinimapMessageDirection.S2C) {
            throw new MinimapWireError(
                    com.ptcrys.fpsmatch.core.minimap.contract
                            .MinimapErrorCode.WRONG_DIRECTION,
                    "Wrong minimap envelope direction"
            );
        }
        if (message instanceof MarkerWireMessage marker) {
            return new MinimapS2CPacket(
                    frameId,
                    MinimapWireCodec.prepareMarkerFrame(marker)
            );
        }
        MinimapFrameSegment segment = MinimapFrameSegmenter.forS2C(
                frameId,
                MinimapWireCodec.encode(message)
        ).get(0);
        return new MinimapS2CPacket(segment);
    }

    public MinimapFrameSegment segment() {
        if (segment != null) {
            return segment;
        }
        return MinimapFrameSegmenter.forS2C(
                frameId,
                MinimapWireCodec.encode(markerFrame.message())
        ).get(0);
    }

    public static void installDispatcher(MinimapS2CDispatcher newDispatcher) {
        dispatcher = Objects.requireNonNull(newDispatcher, "newDispatcher");
    }

    public static void encode(MinimapS2CPacket packet, FriendlyByteBuf buffer) {
        Objects.requireNonNull(packet, "packet");
        if (packet.markerFrame != null) {
            MinimapEnvelopeBody.writeMarker(
                    packet.frameId,
                    packet.markerFrame,
                    buffer
            );
            return;
        }
        MinimapEnvelopeBody.write(
                packet.segment,
                buffer,
                MinimapEnvelopeDirection.PLAY_TO_CLIENT
        );
    }

    public static MinimapS2CPacket decode(FriendlyByteBuf buffer) {
        return new MinimapS2CPacket(MinimapEnvelopeBody.read(
                buffer,
                MinimapEnvelopeDirection.PLAY_TO_CLIENT
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
                dispatcher,
                MinimapPacketEndpointRuntime.REGISTRY,
                MinimapPacketEndpointRuntime.REASSEMBLER
        );
    }

    static void handleOnNetworkThread(
            MinimapS2CPacket packet,
            MinimapEnvelopeContext context,
            MinimapS2CDispatcher target,
            MinimapPacketEndpointRegistry endpoints,
            MinimapFrameReassembler reassembler
    ) {
        Objects.requireNonNull(packet, "packet");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(endpoints, "endpoints");
        Objects.requireNonNull(reassembler, "reassembler");
        if (context.direction() != MinimapEnvelopeDirection.PLAY_TO_CLIENT) {
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
                    MinimapEnvelopeDirection.PLAY_TO_CLIENT,
                    packet.segment
            );
            if (frame.isEmpty()) {
                context.markHandled();
                return;
            }
            message = MinimapWireCodec.decode(
                    MinimapMessageDirection.S2C,
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
                target.dispatch(message);
            }
        });
        context.markHandled();
    }
}
