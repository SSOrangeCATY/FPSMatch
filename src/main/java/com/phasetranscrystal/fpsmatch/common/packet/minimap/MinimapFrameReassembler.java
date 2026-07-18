package com.phasetranscrystal.fpsmatch.common.packet.minimap;

import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapErrorCode;
import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapHardLimits;
import com.phasetranscrystal.fpsmatch.core.minimap.format.Sha256Digest;
import com.phasetranscrystal.fpsmatch.core.minimap.model.Sha256;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.MinimapWireError;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

final class MinimapFrameReassembler implements AutoCloseable {
    private final Clock clock;
    private final IdentityHashMap<Object, ConnectionState> connections =
            new IdentityHashMap<>();
    private boolean closed;

    MinimapFrameReassembler(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    synchronized Optional<byte[]> accept(
            Object connectionToken,
            MinimapEnvelopeDirection direction,
            MinimapFrameSegment segment
    ) {
        ensureOpen();
        Objects.requireNonNull(connectionToken, "connectionToken");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(segment, "segment");

        byte[] segmentData = segment.segmentData();
        validateGeometry(direction, segment, segmentData.length);
        Instant now = clock.instant();
        discardExpired(now);

        ConnectionState connection = connections.get(connectionToken);
        Assembly assembly = connection == null
                ? null
                : connection.frames.get(segment.frameId());
        if (assembly == null) {
            int frameCount = connection == null ? 0 : connection.frames.size();
            long declaredBytes = connection == null ? 0 : connection.declaredBytes;
            if (frameCount >= MinimapHardLimits.MAX_REASSEMBLY_FRAMES_PER_CONNECTION
                    || segment.frameLength()
                    > MinimapHardLimits.MAX_REASSEMBLY_BYTES_PER_CONNECTION - declaredBytes) {
                throw error(
                        MinimapErrorCode.QUOTA_EXCEEDED,
                        "Connection reassembly budget exceeded"
                );
            }
            if (connection == null) {
                connection = new ConnectionState();
                connections.put(connectionToken, connection);
            }
            assembly = new Assembly(direction, segment, now);
            connection.frames.put(segment.frameId(), assembly);
            connection.declaredBytes += segment.frameLength();
        } else if (!assembly.matches(direction, segment)) {
            throw error(
                    MinimapErrorCode.FRAGMENT_CONFLICT,
                    "Frame segment metadata conflicts with its assembly"
            );
        }

        byte[] existing = assembly.segments[segment.segmentIndex()];
        if (existing != null) {
            if (Arrays.equals(existing, segmentData)) {
                return Optional.empty();
            }
            throw error(
                    MinimapErrorCode.FRAGMENT_CONFLICT,
                    "Frame segment index has conflicting bytes"
            );
        }

        assembly.segments[segment.segmentIndex()] = segmentData;
        assembly.receivedSegments++;
        assembly.lastProgress = now;
        if (assembly.receivedSegments != assembly.segments.length) {
            return Optional.empty();
        }

        byte[] frame = assemble(assembly);
        removeAssembly(connectionToken, connection, segment.frameId(), assembly.frameLength);
        if (!Sha256Digest.of(frame).equals(assembly.frameHash)) {
            throw error(MinimapErrorCode.HASH_MISMATCH, "Reassembled frame hash mismatch");
        }
        return Optional.of(frame);
    }

    synchronized int discardExpired() {
        ensureOpen();
        return discardExpired(clock.instant());
    }

    synchronized void closeConnection(Object connectionToken) {
        if (connectionToken != null) {
            connections.remove(connectionToken);
        }
    }

    synchronized int inFlightFrames(Object connectionToken) {
        ConnectionState connection = connections.get(connectionToken);
        return connection == null ? 0 : connection.frames.size();
    }

    synchronized long declaredBytes(Object connectionToken) {
        ConnectionState connection = connections.get(connectionToken);
        return connection == null ? 0 : connection.declaredBytes;
    }

    @Override
    public synchronized void close() {
        if (!closed) {
            closed = true;
            connections.clear();
        }
    }

    private int discardExpired(Instant now) {
        int removed = 0;
        Iterator<Map.Entry<Object, ConnectionState>> connectionsIterator =
                connections.entrySet().iterator();
        while (connectionsIterator.hasNext()) {
            ConnectionState connection = connectionsIterator.next().getValue();
            Iterator<Map.Entry<UUID, Assembly>> framesIterator =
                    connection.frames.entrySet().iterator();
            while (framesIterator.hasNext()) {
                Assembly assembly = framesIterator.next().getValue();
                Instant expiresAt = assembly.lastProgress.plus(
                        MinimapHardLimits.REASSEMBLY_TTL
                );
                if (!now.isBefore(expiresAt)) {
                    framesIterator.remove();
                    connection.declaredBytes -= assembly.frameLength;
                    removed++;
                }
            }
            if (connection.frames.isEmpty()) {
                connectionsIterator.remove();
            }
        }
        return removed;
    }

    private void removeAssembly(
            Object connectionToken,
            ConnectionState connection,
            UUID frameId,
            int frameLength
    ) {
        connection.frames.remove(frameId);
        connection.declaredBytes -= frameLength;
        if (connection.frames.isEmpty()) {
            connections.remove(connectionToken);
        }
    }

    private static byte[] assemble(Assembly assembly) {
        byte[] frame = new byte[assembly.frameLength];
        int offset = 0;
        for (byte[] segment : assembly.segments) {
            if (segment == null || segment.length > frame.length - offset) {
                throw error(
                        MinimapErrorCode.MALFORMED_MESSAGE,
                        "Reassembled frame geometry is inconsistent"
                );
            }
            System.arraycopy(segment, 0, frame, offset, segment.length);
            offset += segment.length;
        }
        if (offset != frame.length) {
            throw error(
                    MinimapErrorCode.MALFORMED_MESSAGE,
                    "Reassembled frame length is inconsistent"
            );
        }
        return frame;
    }

    private static void validateGeometry(
            MinimapEnvelopeDirection direction,
            MinimapFrameSegment segment,
            int dataLength
    ) {
        if (direction == MinimapEnvelopeDirection.PLAY_TO_SERVER) {
            int segmentBytes = MinimapHardLimits.MAX_FORGE_C2S_SEGMENT_BYTES;
            int expectedCount = (segment.frameLength() - 1) / segmentBytes + 1;
            int expectedLength = segment.segmentIndex() + 1 < expectedCount
                    ? segmentBytes
                    : segment.frameLength() - (expectedCount - 1) * segmentBytes;
            if (segment.segmentCount() != expectedCount || dataLength != expectedLength) {
                throw error(
                        MinimapErrorCode.MALFORMED_MESSAGE,
                        "C2S frame segment geometry is invalid"
                );
            }
            return;
        }
        if (direction == MinimapEnvelopeDirection.PLAY_TO_CLIENT) {
            if (segment.segmentIndex() != 0
                    || segment.segmentCount() != 1
                    || dataLength != segment.frameLength()) {
                throw error(
                        MinimapErrorCode.MALFORMED_MESSAGE,
                        "S2C frame segment geometry is invalid"
                );
            }
            return;
        }
        throw error(MinimapErrorCode.WRONG_DIRECTION, "Invalid minimap envelope direction");
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("Frame reassembler is closed");
        }
    }

    private static MinimapWireError error(MinimapErrorCode code, String message) {
        return new MinimapWireError(code, message);
    }

    private static final class ConnectionState {
        private final Map<UUID, Assembly> frames = new HashMap<>();
        private long declaredBytes;
    }

    private static final class Assembly {
        private final MinimapEnvelopeDirection direction;
        private final int frameLength;
        private final Sha256 frameHash;
        private final byte[][] segments;
        private int receivedSegments;
        private Instant lastProgress;

        private Assembly(
                MinimapEnvelopeDirection direction,
                MinimapFrameSegment first,
                Instant now
        ) {
            this.direction = direction;
            this.frameLength = first.frameLength();
            this.frameHash = first.frameHash();
            this.segments = new byte[first.segmentCount()][];
            this.lastProgress = now;
        }

        private boolean matches(
                MinimapEnvelopeDirection actualDirection,
                MinimapFrameSegment segment
        ) {
            return direction == actualDirection
                    && frameLength == segment.frameLength()
                    && frameHash.equals(segment.frameHash())
                    && segments.length == segment.segmentCount();
        }
    }
}
