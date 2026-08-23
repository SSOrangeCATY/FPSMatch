package com.ptcrys.fpsmatch.common.packet.minimap;

import com.ptcrys.fpsmatch.core.minimap.contract.MinimapErrorCode;
import com.ptcrys.fpsmatch.core.minimap.contract.MinimapHardLimits;
import com.ptcrys.fpsmatch.core.minimap.model.Sha256;
import com.ptcrys.fpsmatch.core.minimap.wire.MinimapWireCodec;
import com.ptcrys.fpsmatch.core.minimap.wire.MinimapWireError;
import net.minecraft.network.FriendlyByteBuf;

import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

final class MinimapEnvelopeBody {
    private MinimapEnvelopeBody() {
    }

    static MinimapFrameSegment read(
            FriendlyByteBuf buffer,
            MinimapEnvelopeDirection direction
    ) {
        Objects.requireNonNull(buffer, "buffer");
        Objects.requireNonNull(direction, "direction");
        requireReadable(buffer, 16);
        UUID frameId = new UUID(buffer.readLong(), buffer.readLong());
        int frameLength = readUnsignedVarInt(
                buffer, MinimapHardLimits.MAX_WIRE_FRAME_BYTES
        );
        requireReadable(buffer, 32);
        byte[] hashBytes = new byte[32];
        buffer.readBytes(hashBytes);
        Sha256 frameHash = new Sha256(HexFormat.of().formatHex(hashBytes));
        int segmentIndex = readUnsignedVarInt(buffer, maximumSegments() - 1);
        int segmentCount = readUnsignedVarInt(buffer, maximumSegments());
        int dataLength = readUnsignedVarInt(
                buffer, MinimapHardLimits.MAX_WIRE_FRAME_BYTES
        );
        validateGeometry(
                direction,
                frameLength,
                segmentIndex,
                segmentCount,
                dataLength
        );
        if (buffer.readableBytes() != dataLength) {
            throw malformed("Segment data length does not match its envelope");
        }
        byte[] data = new byte[dataLength];
        buffer.readBytes(data);
        return new MinimapFrameSegment(
                frameId,
                frameLength,
                frameHash,
                segmentIndex,
                segmentCount,
                data
        );
    }

    static void write(
            MinimapFrameSegment segment,
            FriendlyByteBuf buffer,
            MinimapEnvelopeDirection direction
    ) {
        Objects.requireNonNull(segment, "segment");
        Objects.requireNonNull(buffer, "buffer");
        Objects.requireNonNull(direction, "direction");
        byte[] data = segment.segmentData();
        validateGeometry(
                direction,
                segment.frameLength(),
                segment.segmentIndex(),
                segment.segmentCount(),
                data.length
        );
        buffer.writeLong(segment.frameId().getMostSignificantBits());
        buffer.writeLong(segment.frameId().getLeastSignificantBits());
        writeUnsignedVarInt(buffer, segment.frameLength());
        buffer.writeBytes(HexFormat.of().parseHex(segment.frameHash().value()));
        writeUnsignedVarInt(buffer, segment.segmentIndex());
        writeUnsignedVarInt(buffer, segment.segmentCount());
        writeUnsignedVarInt(buffer, data.length);
        buffer.writeBytes(data);
    }

    static void writeMarker(
            UUID frameId,
            MinimapWireCodec.PreparedMarkerFrame frame,
            FriendlyByteBuf buffer
    ) {
        Objects.requireNonNull(frameId, "frameId");
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(buffer, "buffer");
        validateGeometry(
                MinimapEnvelopeDirection.PLAY_TO_CLIENT,
                frame.length(),
                0,
                1,
                frame.length()
        );
        buffer.writeLong(frameId.getMostSignificantBits());
        buffer.writeLong(frameId.getLeastSignificantBits());
        writeUnsignedVarInt(buffer, frame.length());
        int hashIndex = buffer.writerIndex();
        buffer.writeZero(32);
        writeUnsignedVarInt(buffer, 0);
        writeUnsignedVarInt(buffer, 1);
        writeUnsignedVarInt(buffer, frame.length());
        MessageDigest digest = sha256();
        frame.writeTo(new MinimapWireCodec.FrameSink() {
            @Override
            public void writeByte(int value) {
                buffer.writeByte(value);
                digest.update((byte) value);
            }

            @Override
            public void writeBytes(byte[] value) {
                buffer.writeBytes(value);
                digest.update(value);
            }
        });
        buffer.setBytes(hashIndex, digest.digest());
    }

    private static void validateGeometry(
            MinimapEnvelopeDirection direction,
            int frameLength,
            int segmentIndex,
            int segmentCount,
            int dataLength
    ) {
        if (frameLength <= 0 || frameLength > MinimapHardLimits.MAX_WIRE_FRAME_BYTES
                || segmentCount <= 0 || segmentCount > maximumSegments()
                || segmentIndex < 0 || segmentIndex >= segmentCount
                || dataLength <= 0 || dataLength > frameLength) {
            throw malformed("Segment envelope geometry is invalid");
        }
        if (direction == MinimapEnvelopeDirection.PLAY_TO_SERVER) {
            int segmentBytes = MinimapHardLimits.MAX_FORGE_C2S_SEGMENT_BYTES;
            int expectedCount = (frameLength - 1) / segmentBytes + 1;
            int expectedLength = segmentIndex + 1 < expectedCount
                    ? segmentBytes
                    : frameLength - (expectedCount - 1) * segmentBytes;
            if (segmentCount != expectedCount || dataLength != expectedLength) {
                throw malformed("C2S segment envelope geometry is invalid");
            }
            return;
        }
        if (direction == MinimapEnvelopeDirection.PLAY_TO_CLIENT) {
            if (segmentIndex != 0 || segmentCount != 1 || dataLength != frameLength) {
                throw malformed("S2C segment envelope geometry is invalid");
            }
            return;
        }
        throw new MinimapWireError(
                MinimapErrorCode.WRONG_DIRECTION,
                "Invalid minimap envelope direction"
        );
    }

    private static int readUnsignedVarInt(FriendlyByteBuf buffer, int maximum) {
        int value = 0;
        for (int index = 0; index < 5; index++) {
            requireReadable(buffer, 1);
            int next = buffer.readUnsignedByte();
            if (index == 4 && (next & 0xf0) != 0) {
                throw malformed("Unsigned VarInt overflows");
            }
            value |= (next & 0x7f) << (index * 7);
            if ((next & 0x80) == 0) {
                if (unsignedVarIntSize(value) != index + 1) {
                    throw malformed("Unsigned VarInt is not canonical");
                }
                if (value > maximum) {
                    throw new MinimapWireError(
                            MinimapErrorCode.QUOTA_EXCEEDED,
                            "Envelope integer exceeds its hard limit"
                    );
                }
                return value;
            }
        }
        throw malformed("Unsigned VarInt overflows");
    }

    private static void writeUnsignedVarInt(FriendlyByteBuf buffer, int value) {
        int remaining = value;
        while ((remaining & ~0x7f) != 0) {
            buffer.writeByte((remaining & 0x7f) | 0x80);
            remaining >>>= 7;
        }
        buffer.writeByte(remaining);
    }

    private static int unsignedVarIntSize(int value) {
        int size = 1;
        while ((value >>>= 7) != 0) {
            size++;
        }
        return size;
    }

    private static int maximumSegments() {
        return (MinimapHardLimits.MAX_WIRE_FRAME_BYTES - 1)
                / MinimapHardLimits.MAX_FORGE_C2S_SEGMENT_BYTES + 1;
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 is unavailable", unavailable);
        }
    }

    private static void requireReadable(FriendlyByteBuf buffer, int bytes) {
        if (buffer.readableBytes() < bytes) {
            throw malformed("Truncated minimap segment envelope");
        }
    }

    private static MinimapWireError malformed(String message) {
        return new MinimapWireError(MinimapErrorCode.MALFORMED_MESSAGE, message);
    }
}
