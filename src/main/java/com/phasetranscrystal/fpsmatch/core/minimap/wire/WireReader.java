package com.phasetranscrystal.fpsmatch.core.minimap.wire;

import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapErrorCode;
import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapHardLimits;
import com.phasetranscrystal.fpsmatch.core.minimap.model.Sha256;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Bounded, canonical primitives for future opcode-specific typed payload codecs. */
public final class WireReader {
    private final ByteBuffer input;

    public WireReader(byte[] input) {
        if (input == null) {
            throw malformed("wire input is null");
        }
        this.input = ByteBuffer.wrap(input).asReadOnlyBuffer();
    }

    public WireReader(ByteBuffer input) {
        if (input == null) {
            throw malformed("wire input is null");
        }
        this.input = input.slice().asReadOnlyBuffer();
    }

    public int readUnsignedVarInt(int maximum) {
        if (maximum < 0) {
            throw new IllegalArgumentException("maximum must be non-negative");
        }
        int value = 0;
        for (int index = 0; index < 5; index++) {
            int next = readUnsignedByte();
            if (index == 4 && (next & 0xf0) != 0) {
                throw malformed("overflowing unsigned VarInt");
            }
            value |= (next & 0x7f) << (index * 7);
            if ((next & 0x80) == 0) {
                if (value < 0 || unsignedVarIntSize(value) != index + 1) {
                    throw malformed("non-canonical unsigned VarInt");
                }
                if (value > maximum) {
                    throw quota("unsigned VarInt exceeds its limit");
                }
                return value;
            }
        }
        throw malformed("overflowing unsigned VarInt");
    }

    public int readUnsignedByte() {
        if (!input.hasRemaining()) {
            throw malformed("truncated wire value");
        }
        return Byte.toUnsignedInt(input.get());
    }

    public int readUnsignedShort() {
        requireRemaining(2, "truncated unsigned short");
        return (readUnsignedByte() << 8) | readUnsignedByte();
    }

    public long readNonNegativeVarLong() {
        long value = 0;
        for (int index = 0; index < 9; index++) {
            int next = readUnsignedByte();
            value |= (long) (next & 0x7f) << (index * 7);
            if ((next & 0x80) == 0) {
                if (unsignedVarLongSize(value) != index + 1) {
                    throw malformed("non-canonical non-negative VarLong");
                }
                return value;
            }
        }
        throw malformed("negative or overflowing VarLong");
    }

    public int readSignedVarInt() {
        long encoded = readUnsignedBits(5, 0x0f, "signed VarInt");
        return (int) ((encoded >>> 1) ^ -(encoded & 1L));
    }

    public long readSignedVarLong() {
        long encoded = readUnsignedBits(10, 0x01, "signed VarLong");
        return (encoded >>> 1) ^ -(encoded & 1L);
    }

    public boolean readBoolean() {
        int value = readUnsignedByte();
        if (value == 0) {
            return false;
        }
        if (value == 1) {
            return true;
        }
        throw malformed("wire boolean tag is invalid");
    }

    public UUID readUuid() {
        requireRemaining(16, "truncated wire UUID");
        return new UUID(input.getLong(), input.getLong());
    }

    public Sha256 readHash() {
        requireRemaining(32, "truncated wire hash");
        byte[] bytes = new byte[32];
        input.get(bytes);
        return new Sha256(java.util.HexFormat.of().formatHex(bytes));
    }

    public float readFloat() {
        requireRemaining(4, "truncated wire float");
        float value = Float.intBitsToFloat(input.getInt());
        if (!Float.isFinite(value)) {
            throw malformed("wire float must be finite");
        }
        return value;
    }

    public double readDouble() {
        requireRemaining(8, "truncated wire double");
        double value = Double.longBitsToDouble(input.getLong());
        if (!Double.isFinite(value)) {
            throw malformed("wire double must be finite");
        }
        return value;
    }

    public int readCount(int maximum) {
        return readUnsignedVarInt(maximum);
    }

    public static int checkedCountTotal(int current, int additional, int maximum) {
        if (current < 0 || additional < 0 || maximum < 0) {
            throw malformed("negative count");
        }
        long total = (long) current + additional;
        if (total > maximum) {
            throw quota("aggregate count exceeds its limit");
        }
        return (int) total;
    }

    public byte[] readByteArray(int maximumLength) {
        int hardMaximum = boundedLimit(maximumLength, MinimapHardLimits.MAX_WIRE_BODY_BYTES);
        int length = readUnsignedVarInt(hardMaximum);
        if (input.remaining() < length) {
            throw malformed("truncated length-prefixed bytes");
        }
        byte[] value = new byte[length];
        input.get(value);
        return value;
    }

    public String readUtf8(int maximumBytes) {
        int hardMaximum = boundedLimit(maximumBytes, MinimapHardLimits.MAX_WIRE_STRING_UTF8_BYTES);
        byte[] value = readByteArray(hardMaximum);
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(value))
                    .toString();
        } catch (CharacterCodingException error) {
            throw new MinimapWireError(
                    MinimapErrorCode.MALFORMED_MESSAGE,
                    "invalid UTF-8 wire string",
                    error
            );
        }
    }

    public int remaining() {
        return input.remaining();
    }

    int consumedBytes() {
        return input.position();
    }

    public void requireFinished() {
        if (input.hasRemaining()) {
            throw malformed("trailing wire bytes");
        }
    }

    private long readUnsignedBits(int maximumBytes, int finalByteMask, String label) {
        long value = 0;
        for (int index = 0; index < maximumBytes; index++) {
            int next = readUnsignedByte();
            if (index == maximumBytes - 1 && (next & ~finalByteMask) != 0) {
                throw malformed("overflowing " + label);
            }
            value |= (long) (next & 0x7f) << (index * 7);
            if ((next & 0x80) == 0) {
                int canonicalSize = value < 0 ? 10 : unsignedVarLongSize(value);
                if (canonicalSize != index + 1) {
                    throw malformed("non-canonical " + label);
                }
                return value;
            }
        }
        throw malformed("overflowing " + label);
    }

    private void requireRemaining(int required, String message) {
        if (input.remaining() < required) {
            throw malformed(message);
        }
    }

    private static int boundedLimit(int requested, int hardMaximum) {
        if (requested < 0) {
            throw new IllegalArgumentException("limit must be non-negative");
        }
        return Math.min(requested, hardMaximum);
    }

    private static int unsignedVarIntSize(int value) {
        int size = 1;
        while ((value >>>= 7) != 0) {
            size++;
        }
        return size;
    }

    private static int unsignedVarLongSize(long value) {
        int size = 1;
        while ((value >>>= 7) != 0) {
            size++;
        }
        return size;
    }

    private static MinimapWireError malformed(String message) {
        return new MinimapWireError(MinimapErrorCode.MALFORMED_MESSAGE, message);
    }

    private static MinimapWireError quota(String message) {
        return new MinimapWireError(MinimapErrorCode.QUOTA_EXCEEDED, message);
    }
}
