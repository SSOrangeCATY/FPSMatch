package com.phasetranscrystal.fpsmatch.core.minimap.wire;

import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapErrorCode;
import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapHardLimits;
import com.phasetranscrystal.fpsmatch.core.minimap.model.Sha256;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.CoderResult;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;

/** A writer whose backing storage can never grow beyond its fixed frame budget. */
public final class WireWriter {
    private final int maximumBytes;
    private byte[] output;
    private final MinimapWireCodec.FrameSink sink;
    private int size;

    public WireWriter(int maximumBytes) {
        this(maximumBytes, 32);
    }

    public WireWriter(int maximumBytes, int initialBytes) {
        this(maximumBytes, initialBytes, false, null);
    }

    private WireWriter(
            int maximumBytes,
            int initialBytes,
            boolean counting,
            MinimapWireCodec.FrameSink sink
    ) {
        if (maximumBytes < 0) {
            throw new IllegalArgumentException("maximumBytes must be non-negative");
        }
        if (initialBytes < 0) {
            throw new IllegalArgumentException("initialBytes must be non-negative");
        }
        this.maximumBytes = Math.min(maximumBytes, MinimapHardLimits.MAX_WIRE_FRAME_BYTES);
        this.output = counting
                ? null
                : new byte[Math.min(this.maximumBytes, initialBytes)];
        this.sink = sink;
    }

    static WireWriter counting(int maximumBytes) {
        return new WireWriter(maximumBytes, 0, true, null);
    }

    static WireWriter streaming(
            int maximumBytes,
            MinimapWireCodec.FrameSink sink
    ) {
        return new WireWriter(
                maximumBytes,
                0,
                true,
                java.util.Objects.requireNonNull(sink, "sink")
        );
    }

    public void writeUnsignedByte(int value) {
        if (value < 0 || value > 0xff) {
            throw malformed("unsigned byte is outside its range");
        }
        writeRawByte(value);
    }

    public void writeUnsignedShort(int value) {
        if (value < 0 || value > 0xffff) {
            throw malformed("unsigned short is outside its range");
        }
        ensureCapacity(2);
        writeRawByteUnchecked(value >>> 8);
        writeRawByteUnchecked(value);
    }

    public void writeUnsignedVarInt(int value) {
        if (value < 0) {
            throw malformed("negative unsigned VarInt");
        }
        int remaining = value;
        do {
            int next = remaining & 0x7f;
            remaining >>>= 7;
            if (remaining != 0) {
                next |= 0x80;
            }
            writeRawByte(next);
        } while (remaining != 0);
    }

    public void writeNonNegativeVarLong(long value) {
        if (value < 0) {
            throw malformed("negative VarLong");
        }
        long remaining = value;
        do {
            int next = (int) (remaining & 0x7f);
            remaining >>>= 7;
            if (remaining != 0) {
                next |= 0x80;
            }
            writeRawByte(next);
        } while (remaining != 0);
    }

    public void writeSignedVarInt(int value) {
        long encoded = (((long) value << 1) ^ (value >> 31)) & 0xffff_ffffL;
        writeUnsignedBits(encoded);
    }

    public void writeSignedVarLong(long value) {
        long encoded = (value << 1) ^ (value >> 63);
        do {
            int next = (int) (encoded & 0x7f);
            encoded >>>= 7;
            if (encoded != 0) {
                next |= 0x80;
            }
            writeRawByte(next);
        } while (encoded != 0);
    }

    public void writeBoolean(boolean value) {
        writeRawByte(value ? 1 : 0);
    }

    public void writeUuid(UUID value) {
        if (value == null) {
            throw malformed("wire UUID is null");
        }
        ensureCapacity(16);
        writeLong(value.getMostSignificantBits());
        writeLong(value.getLeastSignificantBits());
    }

    public void writeHash(Sha256 value) {
        if (value == null) {
            throw malformed("wire hash is null");
        }
        String hex = value.value();
        ensureCapacity(32);
        for (int index = 0; index < hex.length(); index += 2) {
            int high = Character.digit(hex.charAt(index), 16);
            int low = Character.digit(hex.charAt(index + 1), 16);
            writeRawByteUnchecked(high << 4 | low);
        }
    }

    public void writeFloat(float value) {
        if (!Float.isFinite(value)) {
            throw malformed("wire float must be finite");
        }
        writeInt(Float.floatToRawIntBits(value));
    }

    public void writeDouble(double value) {
        if (!Double.isFinite(value)) {
            throw malformed("wire double must be finite");
        }
        writeLong(Double.doubleToRawLongBits(value));
    }

    public void writeByteArray(byte[] value, int maximumLength) {
        if (value == null) {
            throw malformed("wire byte array is null");
        }
        int hardMaximum = boundedLimit(maximumLength, MinimapHardLimits.MAX_WIRE_BODY_BYTES);
        if (value.length > hardMaximum) {
            throw quota("byte array exceeds its field limit");
        }
        int prefixBytes = unsignedVarIntSize(value.length);
        ensureCapacity((long) prefixBytes + value.length);
        writeUnsignedVarInt(value.length);
        writeRaw(value);
    }

    public void writeUtf8(String value, int maximumBytes) {
        if (value == null) {
            throw malformed("wire string is null");
        }
        int hardMaximum = boundedLimit(maximumBytes, MinimapHardLimits.MAX_WIRE_STRING_UTF8_BYTES);
        if (isAscii(value)) {
            writeAsciiUtf8(value, hardMaximum);
            return;
        }
        byte[] encoded = strictUtf8(value, hardMaximum);
        writeByteArray(encoded, hardMaximum);
    }

    private void writeAsciiUtf8(String value, int maximumBytes) {
        int length = value.length();
        if (length > maximumBytes) {
            throw quota("UTF-8 wire string exceeds its byte limit");
        }
        ensureCapacity((long) unsignedVarIntSize(length) + length);
        writeUnsignedVarInt(length);
        for (int index = 0; index < length; index++) {
            writeRawByteUnchecked(value.charAt(index));
        }
    }

    private static boolean isAscii(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (value.charAt(index) > 0x7f) {
                return false;
            }
        }
        return true;
    }

    public byte[] toByteArray() {
        if (output == null) {
            throw new IllegalStateException("counting wire writer has no byte array");
        }
        return Arrays.copyOf(output, size);
    }

    byte[] takeExactByteArray() {
        if (output == null || size != output.length) {
            throw new IllegalStateException("wire writer backing array is not exactly filled");
        }
        return output;
    }

    void writeRawBytes(byte[] value) {
        if (value == null) {
            throw malformed("raw wire bytes are null");
        }
        writeRaw(value);
    }

    int writtenBytes() {
        return size;
    }

    private static byte[] strictUtf8(String value, int maximumBytes) {
        int estimatedBytes = (int) Math.min((long) maximumBytes, 3L * value.length());
        ByteBuffer encoded = ByteBuffer.allocate(estimatedBytes);
        var encoder = StandardCharsets.UTF_8.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            CoderResult result = encoder.encode(CharBuffer.wrap(value), encoded, true);
            if (result.isError()) {
                result.throwException();
            }
            if (result.isOverflow()) {
                throw quota("UTF-8 wire string exceeds its byte limit");
            }
            result = encoder.flush(encoded);
            if (result.isError()) {
                result.throwException();
            }
            if (result.isOverflow()) {
                throw quota("UTF-8 wire string exceeds its byte limit");
            }
        } catch (CharacterCodingException error) {
            throw new MinimapWireError(
                    MinimapErrorCode.MALFORMED_MESSAGE,
                    "wire string is not a valid Unicode scalar sequence",
                    error
            );
        }

        byte[] bytes = new byte[encoded.position()];
        encoded.flip();
        encoded.get(bytes);
        return bytes;
    }

    private void writeUnsignedBits(long value) {
        long remaining = value;
        do {
            int next = (int) (remaining & 0x7f);
            remaining >>>= 7;
            if (remaining != 0) {
                next |= 0x80;
            }
            writeRawByte(next);
        } while (remaining != 0);
    }

    private void writeInt(int value) {
        ensureCapacity(4);
        writeRawByteUnchecked(value >>> 24);
        writeRawByteUnchecked(value >>> 16);
        writeRawByteUnchecked(value >>> 8);
        writeRawByteUnchecked(value);
    }

    private void writeLong(long value) {
        ensureCapacity(8);
        for (int shift = 56; shift >= 0; shift -= 8) {
            writeRawByteUnchecked((int) (value >>> shift));
        }
    }

    private void writeRawByte(int value) {
        ensureCapacity(1);
        writeRawByteUnchecked(value);
    }

    private void writeRawByteUnchecked(int value) {
        if (output != null) {
            output[size] = (byte) value;
        } else if (sink != null) {
            sink.writeByte(value);
        }
        size++;
    }

    private void writeRaw(byte[] value) {
        ensureCapacity(value.length);
        if (output != null) {
            System.arraycopy(value, 0, output, size, value.length);
        } else if (sink != null) {
            sink.writeBytes(value);
        }
        size += value.length;
    }

    private void ensureCapacity(long additionalBytes) {
        long required = (long) size + additionalBytes;
        if (additionalBytes < 0 || required > maximumBytes) {
            throw quota("wire writer exceeds its fixed byte budget");
        }
        if (output == null || required <= output.length) {
            return;
        }
        int doubled = output.length == 0 ? 1 : output.length * 2;
        int nextLength = (int) Math.min(maximumBytes, Math.max(required, (long) doubled));
        output = Arrays.copyOf(output, nextLength);
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

    private static MinimapWireError malformed(String message) {
        return new MinimapWireError(MinimapErrorCode.MALFORMED_MESSAGE, message);
    }

    private static MinimapWireError quota(String message) {
        return new MinimapWireError(MinimapErrorCode.QUOTA_EXCEEDED, message);
    }
}
