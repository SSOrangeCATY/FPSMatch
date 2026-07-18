package com.phasetranscrystal.fpsmatch.common.packet.minimap;

import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapHardLimits;
import com.phasetranscrystal.fpsmatch.core.minimap.format.Sha256Digest;
import com.phasetranscrystal.fpsmatch.core.minimap.model.Sha256;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class MinimapFrameSegmenter {
    private MinimapFrameSegmenter() {
    }

    public static List<MinimapFrameSegment> forC2S(UUID frameId, byte[] frame) {
        byte[] ownedFrame = requireFrame(frame);
        int segmentBytes = MinimapHardLimits.MAX_FORGE_C2S_SEGMENT_BYTES;
        int segmentCount = (ownedFrame.length - 1) / segmentBytes + 1;
        Sha256 frameHash = Sha256Digest.of(ownedFrame);
        ArrayList<MinimapFrameSegment> segments = new ArrayList<>(segmentCount);
        for (int index = 0; index < segmentCount; index++) {
            int offset = index * segmentBytes;
            int length = Math.min(segmentBytes, ownedFrame.length - offset);
            byte[] data = new byte[length];
            System.arraycopy(ownedFrame, offset, data, 0, length);
            segments.add(new MinimapFrameSegment(
                    frameId,
                    ownedFrame.length,
                    frameHash,
                    index,
                    segmentCount,
                    data
            ));
        }
        return List.copyOf(segments);
    }

    public static List<MinimapFrameSegment> forS2C(UUID frameId, byte[] frame) {
        byte[] ownedFrame = requireFrame(frame);
        return List.of(new MinimapFrameSegment(
                frameId,
                ownedFrame.length,
                Sha256Digest.of(ownedFrame),
                0,
                1,
                ownedFrame
        ));
    }

    private static byte[] requireFrame(byte[] frame) {
        Objects.requireNonNull(frame, "frame");
        if (frame.length == 0 || frame.length > MinimapHardLimits.MAX_WIRE_FRAME_BYTES) {
            throw new IllegalArgumentException("Wire frame length is outside its hard limit");
        }
        return frame.clone();
    }
}
