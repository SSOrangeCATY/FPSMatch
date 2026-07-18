package com.phasetranscrystal.fpsmatch.common.packet.minimap;

import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapErrorCode;
import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapHardLimits;
import com.phasetranscrystal.fpsmatch.core.minimap.format.Sha256Digest;
import com.phasetranscrystal.fpsmatch.core.minimap.model.Sha256;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.MinimapWireError;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinimapFrameReassemblerTest {

    @Test
    void outOfOrderSegmentsAndIdenticalDuplicatesReassembleExactlyOnce() {
        MutableClock clock = new MutableClock();
        MinimapFrameReassembler reassembler = new MinimapFrameReassembler(clock);
        Object connection = new Object();
        byte[] frame = frame(70_000, 3);
        List<MinimapFrameSegment> segments = MinimapFrameSegmenter.forC2S(uuid(1), frame);

        assertEquals(Optional.empty(), reassembler.accept(
                connection, MinimapEnvelopeDirection.PLAY_TO_SERVER, segments.get(2)
        ));
        assertEquals(Optional.empty(), reassembler.accept(
                connection, MinimapEnvelopeDirection.PLAY_TO_SERVER, segments.get(0)
        ));
        assertEquals(Optional.empty(), reassembler.accept(
                connection, MinimapEnvelopeDirection.PLAY_TO_SERVER, segments.get(0)
        ));

        Optional<byte[]> completed = reassembler.accept(
                connection, MinimapEnvelopeDirection.PLAY_TO_SERVER, segments.get(1)
        );

        assertTrue(completed.isPresent());
        assertArrayEquals(frame, completed.orElseThrow());
        assertEquals(0, reassembler.inFlightFrames(connection));
        assertEquals(0, reassembler.declaredBytes(connection));
    }

    @Test
    void conflictingDuplicatesMetadataAndFrameHashesFailClosed() {
        MinimapFrameReassembler reassembler =
                new MinimapFrameReassembler(Clock.systemUTC());
        Object connection = new Object();
        byte[] frame = frame(40_000, 4);
        List<MinimapFrameSegment> segments = MinimapFrameSegmenter.forC2S(uuid(2), frame);
        MinimapFrameSegment first = segments.get(0);
        reassembler.accept(connection, MinimapEnvelopeDirection.PLAY_TO_SERVER, first);

        byte[] differentData = first.segmentData();
        differentData[0] ^= 1;
        MinimapFrameSegment conflicting = new MinimapFrameSegment(
                first.frameId(), first.frameLength(), first.frameHash(),
                first.segmentIndex(), first.segmentCount(), differentData
        );
        assertWireError(MinimapErrorCode.FRAGMENT_CONFLICT, () ->
                reassembler.accept(
                        connection, MinimapEnvelopeDirection.PLAY_TO_SERVER, conflicting
                )
        );

        MinimapFrameSegment metadataConflict = new MinimapFrameSegment(
                first.frameId(), first.frameLength() + 1, hash("11"),
                1, first.segmentCount(), new byte[9_281]
        );
        assertWireError(MinimapErrorCode.FRAGMENT_CONFLICT, () ->
                reassembler.accept(
                        connection, MinimapEnvelopeDirection.PLAY_TO_SERVER,
                        metadataConflict
                )
        );

        Object badHashConnection = new Object();
        List<MinimapFrameSegment> badHash = new ArrayList<>();
        for (MinimapFrameSegment segment : segments) {
            badHash.add(new MinimapFrameSegment(
                    uuid(3), segment.frameLength(), hash("00"),
                    segment.segmentIndex(), segment.segmentCount(), segment.segmentData()
            ));
        }
        reassembler.accept(
                badHashConnection, MinimapEnvelopeDirection.PLAY_TO_SERVER, badHash.get(0)
        );
        assertWireError(MinimapErrorCode.HASH_MISMATCH, () ->
                reassembler.accept(
                        badHashConnection,
                        MinimapEnvelopeDirection.PLAY_TO_SERVER,
                        badHash.get(1)
                )
        );
        assertEquals(0, reassembler.inFlightFrames(badHashConnection));
    }

    @Test
    void connectionIdentityAndPerConnectionBudgetsAreIndependent() {
        MinimapFrameReassembler reassembler =
                new MinimapFrameReassembler(Clock.systemUTC());
        Object firstConnection = new String("connection");
        Object equalButDistinctConnection = new String("connection");
        byte[] frame = frame(40_000, 5);
        List<MinimapFrameSegment> segments = MinimapFrameSegmenter.forC2S(uuid(4), frame);

        reassembler.accept(
                firstConnection, MinimapEnvelopeDirection.PLAY_TO_SERVER, segments.get(0)
        );
        reassembler.accept(
                equalButDistinctConnection,
                MinimapEnvelopeDirection.PLAY_TO_SERVER,
                segments.get(0)
        );
        assertArrayEquals(frame, reassembler.accept(
                firstConnection, MinimapEnvelopeDirection.PLAY_TO_SERVER, segments.get(1)
        ).orElseThrow());
        assertArrayEquals(frame, reassembler.accept(
                equalButDistinctConnection,
                MinimapEnvelopeDirection.PLAY_TO_SERVER,
                segments.get(1)
        ).orElseThrow());

        Object budgeted = new Object();
        for (int index = 0;
             index < MinimapHardLimits.MAX_REASSEMBLY_FRAMES_PER_CONNECTION;
             index++) {
            byte[] maximum = frame(MinimapHardLimits.MAX_WIRE_FRAME_BYTES, index);
            MinimapFrameSegment head = MinimapFrameSegmenter
                    .forC2S(uuid(10 + index), maximum).get(0);
            reassembler.accept(
                    budgeted, MinimapEnvelopeDirection.PLAY_TO_SERVER, head
            );
        }
        assertEquals(4, reassembler.inFlightFrames(budgeted));
        assertEquals(
                MinimapHardLimits.MAX_REASSEMBLY_BYTES_PER_CONNECTION,
                reassembler.declaredBytes(budgeted)
        );
        MinimapFrameSegment fifth = MinimapFrameSegmenter.forC2S(
                uuid(20), frame(40_000, 20)
        ).get(0);
        assertWireError(MinimapErrorCode.QUOTA_EXCEEDED, () ->
                reassembler.accept(
                        budgeted, MinimapEnvelopeDirection.PLAY_TO_SERVER, fifth
                )
        );
        assertEquals(4, reassembler.inFlightFrames(budgeted));
    }

    @Test
    void ttlTracksRealProgressAndConnectionCloseReleasesState() {
        MutableClock clock = new MutableClock();
        MinimapFrameReassembler reassembler = new MinimapFrameReassembler(clock);
        Object expiring = new Object();
        List<MinimapFrameSegment> segments = MinimapFrameSegmenter.forC2S(
                uuid(30), frame(70_000, 30)
        );
        reassembler.accept(
                expiring, MinimapEnvelopeDirection.PLAY_TO_SERVER, segments.get(0)
        );
        clock.advance(Duration.ofSeconds(20));
        reassembler.accept(
                expiring, MinimapEnvelopeDirection.PLAY_TO_SERVER, segments.get(0)
        );
        clock.advance(Duration.ofSeconds(10));

        assertEquals(1, reassembler.discardExpired());
        assertEquals(0, reassembler.inFlightFrames(expiring));

        Object retained = new Object();
        reassembler.accept(
                retained, MinimapEnvelopeDirection.PLAY_TO_SERVER, segments.get(0)
        );
        clock.advance(Duration.ofSeconds(20));
        reassembler.accept(
                retained, MinimapEnvelopeDirection.PLAY_TO_SERVER, segments.get(1)
        );
        clock.advance(Duration.ofSeconds(20));
        assertEquals(0, reassembler.discardExpired());
        assertEquals(1, reassembler.inFlightFrames(retained));

        reassembler.closeConnection(retained);
        assertEquals(0, reassembler.inFlightFrames(retained));
        reassembler.closeConnection(retained);
        reassembler.close();
        assertThrows(IllegalStateException.class, () -> reassembler.accept(
                new Object(), MinimapEnvelopeDirection.PLAY_TO_SERVER, segments.get(0)
        ));
    }

    @Test
    void directionSpecificGeometryIsRejectedBeforeStateReservation() {
        MinimapFrameReassembler reassembler =
                new MinimapFrameReassembler(Clock.systemUTC());
        Object connection = new Object();
        Sha256 hash = Sha256Digest.of(new byte[]{1});

        MinimapFrameSegment shortNonFinal = new MinimapFrameSegment(
                uuid(40), 40_000, hash, 0, 2, new byte[]{1}
        );
        MinimapFrameSegment wrongC2SCount = new MinimapFrameSegment(
                uuid(41), 40_000, hash, 0, 1, new byte[]{1}
        );
        MinimapFrameSegment segmentedS2C = new MinimapFrameSegment(
                uuid(42), 2, hash, 0, 2, new byte[]{1}
        );
        MinimapFrameSegment shortS2C = new MinimapFrameSegment(
                uuid(43), 2, hash, 0, 1, new byte[]{1}
        );

        for (MinimapFrameSegment invalid : List.of(shortNonFinal, wrongC2SCount)) {
            assertWireError(MinimapErrorCode.MALFORMED_MESSAGE, () ->
                    reassembler.accept(
                            connection,
                            MinimapEnvelopeDirection.PLAY_TO_SERVER,
                            invalid
                    )
            );
        }
        for (MinimapFrameSegment invalid : List.of(segmentedS2C, shortS2C)) {
            assertWireError(MinimapErrorCode.MALFORMED_MESSAGE, () ->
                    reassembler.accept(
                            connection,
                            MinimapEnvelopeDirection.PLAY_TO_CLIENT,
                            invalid
                    )
            );
        }
        assertWireError(MinimapErrorCode.WRONG_DIRECTION, () ->
                reassembler.accept(
                        connection, MinimapEnvelopeDirection.OTHER, shortS2C
                )
        );
        assertEquals(0, reassembler.inFlightFrames(connection));
        assertEquals(0, reassembler.declaredBytes(connection));
    }

    private static void assertWireError(MinimapErrorCode expected, Runnable action) {
        MinimapWireError error = assertThrows(MinimapWireError.class, action::run);
        assertEquals(expected, error.code());
    }

    private static byte[] frame(int length, int salt) {
        byte[] frame = new byte[length];
        for (int index = 0; index < frame.length; index++) {
            frame[index] = (byte) (index * 31 + salt);
        }
        return frame;
    }

    private static Sha256 hash(String byteHex) {
        return new Sha256(byteHex.repeat(32));
    }

    private static UUID uuid(long lowBits) {
        return new UUID(0, lowBits);
    }

    private static final class MutableClock extends Clock {
        private Instant instant = Instant.parse("2026-07-16T00:00:00Z");

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
