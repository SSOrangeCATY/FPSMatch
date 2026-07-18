package com.phasetranscrystal.fpsmatch.core.minimap.format;

import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapHardLimits;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PngBombTest {
    private static final byte[] SIGNATURE = {
            (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a
    };

    @Test
    void rejectsBadSignatureCrcTruncationTrailerAndIllegalChunks() {
        byte[] valid = validPng();
        byte[] signature = valid.clone();
        signature[0] ^= 1;
        byte[] truncated = Arrays.copyOf(valid, valid.length - 1);
        byte[] trailer = Arrays.copyOf(valid, valid.length + 1);

        for (byte[] invalid : List.of(
                signature,
                withCorruptChunkCrc(valid, "IHDR"),
                withCorruptChunkCrc(valid, "IDAT"),
                withCorruptChunkCrc(valid, "IEND"),
                truncated,
                trailer,
                insertAfterIhdr(valid, "tEXt", new byte[]{1}),
                insertAfterIhdr(valid, "acTL", new byte[8]),
                insertAfterIhdr(valid, "ABCD", new byte[0])
        )) {
            assertRejected(invalid);
        }
    }

    @Test
    void rejectsUnsupportedOrOversizedIhdrBeforePixelAllocation() {
        byte[] valid = validPng();
        for (byte[] invalid : List.of(
                withIhdrInt(valid, 0, 0),
                withIhdrInt(valid, 0, MinimapHardLimits.MAX_TILE_EDGE + 1),
                withIhdrInt(valid, 0, Integer.MAX_VALUE),
                withIhdrInt(valid, 4, 0),
                withIhdrInt(valid, 4, MinimapHardLimits.MAX_TILE_EDGE + 1),
                withIhdrByte(valid, 8, 16),
                withIhdrByte(valid, 9, 3),
                withIhdrByte(valid, 10, 1),
                withIhdrByte(valid, 11, 1),
                withIhdrByte(valid, 12, 1)
        )) {
            assertRejected(invalid);
        }
    }

    @Test
    void rejectsOversizedChunkLengthBeforeReadingItsPayload() {
        byte[] invalid = validPng();
        writeInt(invalid, 33, Integer.MAX_VALUE);
        assertRejected(invalid);

        invalid = validPng();
        writeInt(invalid, 33, -1);
        assertRejected(invalid);
    }

    @Test
    void rejectsForbiddenChunkTypeBeforeScanningItsPayloadCrc() {
        byte[] invalid = insertAfterIhdr(validPng(), "tEXt", new byte[]{1});
        invalid[33 + 8 + 1] ^= 1;

        PngValidationException exception = assertThrows(
                PngValidationException.class,
                () -> BoundedPngReader.decode(invalid)
        );

        assertEquals("PNG contains a forbidden chunk type", exception.getMessage());
    }

    @Test
    void rejectsZeroLengthAndShortNonFinalIdatChunksBeforeInflating() {
        byte[] zlib = CanonicalPngCodecV1.encodeZlib(new byte[]{0, 1, 2, 3, 4});
        byte[] zeroLength = pngWithIdatChunks(new byte[0], zlib);
        byte[] shortFirst = pngWithIdatChunks(
                Arrays.copyOfRange(zlib, 0, 1),
                Arrays.copyOfRange(zlib, 1, zlib.length)
        );

        PngValidationException zeroLengthException = assertThrows(
                PngValidationException.class,
                () -> BoundedPngReader.decode(zeroLength)
        );
        PngValidationException shortFirstException = assertThrows(
                PngValidationException.class,
                () -> BoundedPngReader.decode(shortFirst)
        );

        assertEquals("PNG IDAT chunk length is not canonical", zeroLengthException.getMessage());
        assertEquals("PNG non-final IDAT chunks must be exactly 1 MiB", shortFirstException.getMessage());
    }

    @Test
    void rejectsDimensionDerivedIdatLimitBeforeScanningItsPayloadCrc() {
        byte[] invalid = pngWithIdatChunks(new byte[14]);
        invalid[33 + 8 + 14] ^= 1;

        PngValidationException exception = assertThrows(
                PngValidationException.class,
                () -> BoundedPngReader.decode(invalid)
        );

        assertEquals("PNG IDAT bytes exceed the canonical size bound", exception.getMessage());
    }

    @Test
    void rejectsSemanticallyValidPngBytesThatAreNotCanonicalV1Output() {
        byte[] transparentRgb = pngWithZlib(CanonicalPngCodecV1.encodeZlib(
                new byte[]{0, 12, 34, 56, 0}
        ));
        byte[] literalOnlyDeflate = pngWithZlib(HexFormat.of().parseHex(
                "78016360606060000000050001"
        ));

        for (byte[] invalid : List.of(transparentRgb, literalOnlyDeflate)) {
            PngValidationException exception = assertThrows(
                    PngValidationException.class,
                    () -> BoundedPngReader.decode(invalid)
            );
            assertEquals("PNG bytes are not CanonicalPngCodecV1 output", exception.getMessage());
        }
    }

    @Test
    void rejectsDeflateBombExtraOutputInvalidFilterAndBrokenStreams() {
        byte[] bombRaw = new byte[1024 * 1024];
        byte[] bombZlib = withCanonicalHeader(deflate(bombRaw, Deflater.BEST_COMPRESSION));
        byte[] extraOutput = withCanonicalHeader(deflate(new byte[]{0, 1, 2, 3, 4, 5}, 9));
        byte[] invalidFilter = withCanonicalHeader(deflate(new byte[]{1, 1, 2, 3, 4}, 9));
        byte[] brokenAdler = withCanonicalHeader(deflate(new byte[]{0, 1, 2, 3, 4}, 9));
        brokenAdler[brokenAdler.length - 1] ^= 1;
        byte[] truncated = Arrays.copyOf(brokenAdler, brokenAdler.length - 2);
        byte[] alternateHeader = deflate(new byte[]{0, 1, 2, 3, 4}, Deflater.DEFAULT_COMPRESSION);

        for (byte[] invalid : List.of(
                pngWithZlib(bombZlib),
                pngWithZlib(extraOutput),
                pngWithZlib(invalidFilter),
                pngWithZlib(brokenAdler),
                pngWithZlib(truncated),
                pngWithZlib(alternateHeader)
        )) {
            assertRejected(invalid);
        }
    }

    private static byte[] validPng() {
        return CanonicalPngCodecV1.encode(1, 1, new byte[]{1, 2, 3, 4});
    }

    private static void assertRejected(byte[] png) {
        assertThrows(PngValidationException.class, () -> BoundedPngReader.decode(png));
    }

    private static byte[] withIhdrInt(byte[] png, int dataOffset, int value) {
        byte[] copy = png.clone();
        writeInt(copy, 16 + dataOffset, value);
        rewriteChunkCrc(copy, 8);
        return copy;
    }

    private static byte[] withIhdrByte(byte[] png, int dataOffset, int value) {
        byte[] copy = png.clone();
        copy[16 + dataOffset] = (byte) value;
        rewriteChunkCrc(copy, 8);
        return copy;
    }

    private static byte[] withCorruptChunkCrc(byte[] png, String expectedType) {
        byte[] copy = png.clone();
        int offset = SIGNATURE.length;
        while (offset < copy.length) {
            int length = readInt(copy, offset);
            String type = new String(copy, offset + 4, 4, StandardCharsets.US_ASCII);
            if (type.equals(expectedType)) {
                copy[offset + 8 + length] ^= 1;
                return copy;
            }
            offset += 12 + length;
        }
        throw new AssertionError("Missing PNG chunk: " + expectedType);
    }

    private static byte[] insertAfterIhdr(byte[] png, String type, byte[] data) {
        byte[] chunk = chunk(type, data);
        byte[] result = new byte[png.length + chunk.length];
        System.arraycopy(png, 0, result, 0, 33);
        System.arraycopy(chunk, 0, result, 33, chunk.length);
        System.arraycopy(png, 33, result, 33 + chunk.length, png.length - 33);
        return result;
    }

    private static byte[] pngWithZlib(byte[] zlib) {
        return pngWithIdatChunks(zlib);
    }

    private static byte[] pngWithIdatChunks(byte[]... chunks) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            bytes.write(SIGNATURE);
            ByteArrayOutputStream ihdrBytes = new ByteArrayOutputStream();
            DataOutputStream ihdr = new DataOutputStream(ihdrBytes);
            ihdr.writeInt(1);
            ihdr.writeInt(1);
            ihdr.write(new byte[]{8, 6, 0, 0, 0});
            bytes.write(chunk("IHDR", ihdrBytes.toByteArray()));
            for (byte[] data : chunks) {
                bytes.write(chunk("IDAT", data));
            }
            bytes.write(chunk("IEND", new byte[0]));
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new AssertionError(exception);
        }
    }

    private static byte[] chunk(String type, byte[] data) {
        try {
            byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
            CRC32 crc = new CRC32();
            crc.update(typeBytes);
            crc.update(data);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(data.length);
            output.write(typeBytes);
            output.write(data);
            output.writeInt((int) crc.getValue());
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new AssertionError(exception);
        }
    }

    private static byte[] deflate(byte[] value, int level) {
        Deflater deflater = new Deflater(level);
        try {
            deflater.setInput(value);
            deflater.finish();
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            while (!deflater.finished()) {
                int count = deflater.deflate(buffer);
                if (count == 0) {
                    throw new AssertionError("Deflater stopped before finishing");
                }
                bytes.write(buffer, 0, count);
            }
            return bytes.toByteArray();
        } finally {
            deflater.end();
        }
    }

    private static byte[] withCanonicalHeader(byte[] zlib) {
        byte[] copy = zlib.clone();
        copy[0] = 0x78;
        copy[1] = 0x01;
        return copy;
    }

    private static void rewriteChunkCrc(byte[] png, int chunkOffset) {
        int length = readInt(png, chunkOffset);
        CRC32 crc = new CRC32();
        crc.update(png, chunkOffset + 4, 4 + length);
        writeInt(png, chunkOffset + 8 + length, (int) crc.getValue());
    }

    private static int readInt(byte[] value, int offset) {
        return (value[offset] & 0xff) << 24
                | (value[offset + 1] & 0xff) << 16
                | (value[offset + 2] & 0xff) << 8
                | value[offset + 3] & 0xff;
    }

    private static void writeInt(byte[] value, int offset, int number) {
        value[offset] = (byte) (number >>> 24);
        value[offset + 1] = (byte) (number >>> 16);
        value[offset + 2] = (byte) (number >>> 8);
        value[offset + 3] = (byte) number;
    }
}
