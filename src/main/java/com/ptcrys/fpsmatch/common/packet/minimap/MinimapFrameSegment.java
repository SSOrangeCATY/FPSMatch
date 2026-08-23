package com.ptcrys.fpsmatch.common.packet.minimap;

import com.ptcrys.fpsmatch.core.minimap.model.Sha256;
import com.ptcrys.fpsmatch.core.minimap.contract.MinimapHardLimits;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

public record MinimapFrameSegment(
        UUID frameId,
        int frameLength,
        Sha256 frameHash,
        int segmentIndex,
        int segmentCount,
        byte[] segmentData
) {
    public MinimapFrameSegment {
        Objects.requireNonNull(frameId, "frameId");
        Objects.requireNonNull(frameHash, "frameHash");
        segmentData = Objects.requireNonNull(segmentData, "segmentData").clone();
        int maximumSegments = (MinimapHardLimits.MAX_WIRE_FRAME_BYTES - 1)
                / MinimapHardLimits.MAX_FORGE_C2S_SEGMENT_BYTES + 1;
        if (frameLength <= 0 || frameLength > MinimapHardLimits.MAX_WIRE_FRAME_BYTES) {
            throw new IllegalArgumentException("Frame length is outside its hard limit");
        }
        if (segmentCount <= 0 || segmentCount > maximumSegments
                || segmentIndex < 0 || segmentIndex >= segmentCount) {
            throw new IllegalArgumentException("Segment index or count is invalid");
        }
        if (segmentData.length == 0
                || segmentData.length > MinimapHardLimits.MAX_WIRE_FRAME_BYTES
                || segmentData.length > frameLength) {
            throw new IllegalArgumentException("Segment data length is invalid");
        }
    }

    @Override
    public byte[] segmentData() {
        return segmentData.clone();
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof MinimapFrameSegment segment
                && frameLength == segment.frameLength
                && segmentIndex == segment.segmentIndex
                && segmentCount == segment.segmentCount
                && frameId.equals(segment.frameId)
                && frameHash.equals(segment.frameHash)
                && Arrays.equals(segmentData, segment.segmentData);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(
                frameId, frameLength, frameHash, segmentIndex, segmentCount
        );
        return 31 * result + Arrays.hashCode(segmentData);
    }
}
