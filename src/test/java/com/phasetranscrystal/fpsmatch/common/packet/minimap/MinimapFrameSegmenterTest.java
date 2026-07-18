package com.phasetranscrystal.fpsmatch.common.packet.minimap;

import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapHardLimits;
import com.phasetranscrystal.fpsmatch.core.minimap.format.Sha256Digest;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinimapFrameSegmenterTest {
    private static final int MAX_ENVELOPE_METADATA_BYTES = 56;
    private static final int MAX_SIMPLE_CHANNEL_DISCRIMINATOR_BYTES = 5;

    @Test
    void maximumFrameUsesElevenForgeSafeC2SSegmentsAndOneS2CSegment() {
        byte[] frame = new byte[MinimapHardLimits.MAX_WIRE_FRAME_BYTES];
        for (int index = 0; index < frame.length; index++) {
            frame[index] = (byte) index;
        }
        UUID frameId = new UUID(0, 1);

        List<MinimapFrameSegment> c2s = MinimapFrameSegmenter.forC2S(frameId, frame);

        assertEquals(11, c2s.size());
        ByteArrayOutputStream reconstructed = new ByteArrayOutputStream(frame.length);
        for (int index = 0; index < c2s.size(); index++) {
            MinimapFrameSegment segment = c2s.get(index);
            assertEquals(frameId, segment.frameId());
            assertEquals(frame.length, segment.frameLength());
            assertEquals(Sha256Digest.of(frame), segment.frameHash());
            assertEquals(index, segment.segmentIndex());
            assertEquals(c2s.size(), segment.segmentCount());
            assertTrue(segment.segmentData().length
                    <= MinimapHardLimits.MAX_FORGE_C2S_SEGMENT_BYTES);
            assertTrue(segment.segmentData().length
                    + MAX_ENVELOPE_METADATA_BYTES
                    + MAX_SIMPLE_CHANNEL_DISCRIMINATOR_BYTES < 32_767);
            reconstructed.writeBytes(segment.segmentData());
        }
        assertArrayEquals(frame, reconstructed.toByteArray());

        List<MinimapFrameSegment> s2c = MinimapFrameSegmenter.forS2C(frameId, frame);

        assertEquals(1, s2c.size());
        assertArrayEquals(frame, s2c.get(0).segmentData());
        assertTrue(s2c.get(0).segmentData().length
                + MAX_ENVELOPE_METADATA_BYTES
                + MAX_SIMPLE_CHANNEL_DISCRIMINATOR_BYTES < 1024 * 1024);
    }

    @Test
    void segmentGeometryAndOwnershipAreValidatedAtConstruction() {
        UUID frameId = new UUID(0, 2);
        byte[] frame = new byte[]{1, 2, 3};
        MinimapFrameSegment segment = MinimapFrameSegmenter.forC2S(frameId, frame).get(0);

        frame[0] = 9;
        assertArrayEquals(new byte[]{1, 2, 3}, segment.segmentData());
        byte[] exposed = segment.segmentData();
        exposed[1] = 9;
        assertArrayEquals(new byte[]{1, 2, 3}, segment.segmentData());
        assertEquals(
                segment,
                new MinimapFrameSegment(
                        segment.frameId(), segment.frameLength(), segment.frameHash(),
                        segment.segmentIndex(), segment.segmentCount(), segment.segmentData()
                )
        );
        assertThrows(UnsupportedOperationException.class, () ->
                MinimapFrameSegmenter.forC2S(frameId, new byte[]{1}).add(segment)
        );

        assertThrows(IllegalArgumentException.class, () ->
                new MinimapFrameSegment(frameId, 0, segment.frameHash(), 0, 1, new byte[]{1})
        );
        assertThrows(IllegalArgumentException.class, () ->
                new MinimapFrameSegment(
                        frameId, MinimapHardLimits.MAX_WIRE_FRAME_BYTES + 1,
                        segment.frameHash(), 0, 1, new byte[]{1}
                )
        );
        assertThrows(IllegalArgumentException.class, () ->
                new MinimapFrameSegment(frameId, 1, segment.frameHash(), -1, 1, new byte[]{1})
        );
        assertThrows(IllegalArgumentException.class, () ->
                new MinimapFrameSegment(frameId, 1, segment.frameHash(), 1, 1, new byte[]{1})
        );
        assertThrows(IllegalArgumentException.class, () ->
                new MinimapFrameSegment(frameId, 1, segment.frameHash(), 0, 0, new byte[]{1})
        );
        assertThrows(IllegalArgumentException.class, () ->
                new MinimapFrameSegment(frameId, 1, segment.frameHash(), 0, 12, new byte[]{1})
        );
        assertThrows(IllegalArgumentException.class, () ->
                new MinimapFrameSegment(frameId, 1, segment.frameHash(), 0, 1, new byte[0])
        );
        assertThrows(IllegalArgumentException.class, () ->
                new MinimapFrameSegment(
                        frameId, 1, segment.frameHash(), 0, 1,
                        new byte[MinimapHardLimits.MAX_WIRE_FRAME_BYTES + 1]
                )
        );
        assertThrows(IllegalArgumentException.class, () ->
                MinimapFrameSegmenter.forC2S(frameId, new byte[0])
        );
        assertThrows(IllegalArgumentException.class, () ->
                MinimapFrameSegmenter.forS2C(
                        frameId,
                        new byte[MinimapHardLimits.MAX_WIRE_FRAME_BYTES + 1]
                )
        );
    }
}
