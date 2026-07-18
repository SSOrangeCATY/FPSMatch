package com.phasetranscrystal.fpsmatch.core.minimap.format;

import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapHardLimits;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CanonicalPngCodecV1Test {
    private static final String FIXTURE_ROOT =
            "/com/phasetranscrystal/fpsmatch/minimap/contract/v1/png/";

    @Test
    void encodesTheOnePixelGoldenPngExactlyAndRoundTrips() {
        byte[] expected = HexFormat.of().parseHex(new String(
                resource("rgba-1x1-canonical.hex"), StandardCharsets.US_ASCII
        ).trim());

        byte[] encoded = CanonicalPngCodecV1.encode(1, 1, new byte[]{1, 2, 3, 4});

        assertArrayEquals(expected, encoded);
        assertEquals("b84195a7deb0f986f35512763dfde7b3265b295cf9f9b5d975d7f18a57d36f2e",
                sha256(encoded));
        assertEquals(List.of("IHDR", "IDAT", "IEND"), chunkTypes(encoded));
        BoundedPngReader.DecodedPng decoded = BoundedPngReader.decode(encoded);
        assertEquals(1, decoded.width());
        assertEquals(1, decoded.height());
        assertArrayEquals(new byte[]{1, 2, 3, 4}, decoded.rgba());
    }

    @Test
    void transparentPixelsHaveTheirRgbChannelsCanonicalizedToZero() {
        byte[] transparentColor = CanonicalPngCodecV1.encode(1, 1, new byte[]{12, 34, 56, 0});
        byte[] transparentBlack = CanonicalPngCodecV1.encode(1, 1, new byte[]{0, 0, 0, 0});

        assertArrayEquals(transparentBlack, transparentColor);
        assertEquals(67, transparentColor.length);
        assertEquals("f19fe636a57e6c5076413efab51d1c36e87686c79f461b7ad8913fe7de4c964e",
                sha256(transparentColor));
        assertArrayEquals(new byte[]{0, 0, 0, 0}, BoundedPngReader.decode(transparentColor).rgba());
    }

    @Test
    void encoderRejectsInvalidDimensionsAndPixelBuffersBeforeProcessing() {
        assertThrows(PngValidationException.class,
                () -> CanonicalPngCodecV1.encode(0, 1, new byte[0]));
        assertThrows(PngValidationException.class,
                () -> CanonicalPngCodecV1.encode(MinimapHardLimits.MAX_TILE_EDGE + 1, 1, new byte[4]));
        assertThrows(PngValidationException.class,
                () -> CanonicalPngCodecV1.encode(1, 1, new byte[3]));
    }

    @Test
    void deflateHashTableScalesWithInputInsteadOfAllocatingFourMebibytesPerSmallImage() {
        assertEquals(16, CanonicalPngCodecV1.hashTableSizeForInput(5));
        assertEquals(65_536, CanonicalPngCodecV1.hashTableSizeForInput(32_769));
        assertEquals(1 << 20, CanonicalPngCodecV1.hashTableSizeForInput(Integer.MAX_VALUE));
    }

    @Test
    void canonicalZlibWorstCaseBoundScalesWithFilteredInputBytes() {
        assertEquals(13, CanonicalPngCodecV1.maxZlibBytesForFilteredInput(5));
        assertEquals(36_872, CanonicalPngCodecV1.maxZlibBytesForFilteredInput(32_768));
        assertEquals(36_874, CanonicalPngCodecV1.maxZlibBytesForFilteredInput(32_769));
    }

    @Test
    void deflateUsesFixedBlocksAt32768BytesAndGreedyLongestMatches() {
        byte[] raw = new byte[32_769];

        DeflateProbe probe = DeflateProbe.read(CanonicalPngCodecV1.encodeZlib(raw));

        assertEquals(List.of(32_768, 1), probe.blockOutputBytes());
        assertEquals(List.of(false, true), probe.finalBlocks());
        assertEquals(new Match(1, 258, 1), probe.matches().get(0));
    }

    @Test
    void deflateHistoryContinuesAcrossBlockBoundaries() {
        byte[] raw = new byte[32_800];
        java.util.Arrays.fill(raw, (byte) 0x41);

        DeflateProbe probe = DeflateProbe.read(CanonicalPngCodecV1.encodeZlib(raw));

        assertEquals(List.of(32_768, 32), probe.blockOutputBytes());
        assertEquals(new Match(32_768, 32, 1), probe.matches().get(probe.matches().size() - 1));
    }

    @Test
    void deflateWindowIncludesTheMaximumDistanceAcrossBlockBoundaries() {
        byte[] raw = new byte[32_771];
        java.util.Arrays.fill(raw, (byte) 'X');
        raw[0] = 'A';
        raw[1] = 'B';
        raw[2] = 'C';
        raw[32_768] = 'A';
        raw[32_769] = 'B';
        raw[32_770] = 'C';

        DeflateProbe probe = DeflateProbe.read(CanonicalPngCodecV1.encodeZlib(raw));

        assertEquals(
                new Match(32_768, 3, 32_768),
                probe.matches().get(probe.matches().size() - 1)
        );
    }

    @Test
    void equalLengthMatchesChooseTheSmallestDistance() {
        byte[] raw = "abcXabcYabcZ".getBytes(StandardCharsets.US_ASCII);

        DeflateProbe probe = DeflateProbe.read(CanonicalPngCodecV1.encodeZlib(raw));

        assertEquals(List.of(new Match(4, 3, 4), new Match(8, 3, 4)), probe.matches());
    }

    @Test
    void longestMatchWinsEvenWhenItUsesAFartherDistance() {
        byte[] rgba = "ABCDEFXABCQRABCDEFZW".getBytes(StandardCharsets.US_ASCII);
        byte[] filtered = new byte[rgba.length + 1];
        System.arraycopy(rgba, 0, filtered, 1, rgba.length);

        DeflateProbe probe = DeflateProbe.read(CanonicalPngCodecV1.encodeZlib(filtered));
        byte[] png = CanonicalPngCodecV1.encode(5, 1, rgba);

        assertEquals(List.of(new Match(8, 3, 7), new Match(13, 6, 12)), probe.matches());
        assertArrayEquals(HexFormat.of().parseHex(
                "89504e470d0a1a0a0000000d494844520000000500000001080600000016fe64f3"
                        + "00000017494441547801637074727671758b0052814110765438003982059d76f3dbad"
                        + "0000000049454e44ae426082"
        ), png);
        assertEquals(80, png.length);
        assertEquals("42d5c97bc4f4c2d59151703c9cd94809b48dd3c415c80bfcdf1aada7382ba8f3",
                sha256(png));
    }

    @Test
    void matchLength258UsesTheDedicatedLengthCode() {
        byte[] rgba = new byte[75 * 4];
        java.util.Arrays.fill(rgba, (byte) 0x41);
        byte[] filtered = new byte[rgba.length + 1];
        System.arraycopy(rgba, 0, filtered, 1, rgba.length);

        byte[] zlib = CanonicalPngCodecV1.encodeZlib(filtered);
        DeflateProbe probe = DeflateProbe.read(zlib);
        byte[] png = CanonicalPngCodecV1.encode(75, 1, rgba);

        assertArrayEquals(HexFormat.of().parseHex("780163701c05440300cb9f4c2d"), zlib);
        assertEquals(List.of(new Match(2, 258, 1), new Match(260, 41, 1)), probe.matches());
        assertArrayEquals(HexFormat.of().parseHex(
                "89504e470d0a1a0a0000000d494844520000004b000000010806000000968d53e0"
                        + "0000000d49444154780163701c05440300cb9f4c2d7c0e8eae"
                        + "0000000049454e44ae426082"
        ), png);
        assertEquals(70, png.length);
        assertEquals("ca082b521d9a16c43edb17fe6451c60b730781e1f13a4b11611568329135bee7",
                sha256(png));
    }

    @Test
    void idatChunksSplitTheZlibStreamAtOneMebibyte() {
        int width = MinimapHardLimits.MAX_TILE_EDGE;
        int height = 300;
        byte[] rgba = incompressibleRgba(width, height);

        byte[] first = CanonicalPngCodecV1.encode(width, height, rgba);
        byte[] second = CanonicalPngCodecV1.encode(width, height, rgba);
        List<Integer> idatLengths = chunkLengths(first, "IDAT");

        assertArrayEquals(first, second);
        assertEquals(true, idatLengths.size() >= 2);
        for (int index = 0; index < idatLengths.size() - 1; index++) {
            assertEquals(1_048_576, idatLengths.get(index));
        }
        assertEquals(true, idatLengths.get(idatLengths.size() - 1) > 0);
        assertEquals(true, idatLengths.get(idatLengths.size() - 1) <= 1_048_576);
        assertArrayEquals(rgba, BoundedPngReader.decode(first).rgba());
    }

    @Test
    void acceptsTheExactDecodedTileByteLimit() {
        int edge = MinimapHardLimits.MAX_TILE_EDGE;
        byte[] rgba = new byte[Math.toIntExact(MinimapHardLimits.MAX_DECODED_TILE_BYTES)];

        BoundedPngReader.DecodedPng decoded = BoundedPngReader.decode(
                CanonicalPngCodecV1.encode(edge, edge, rgba)
        );

        assertEquals(edge, decoded.width());
        assertEquals(edge, decoded.height());
        assertArrayEquals(rgba, decoded.rgba());
    }

    private static List<String> chunkTypes(byte[] png) {
        List<String> types = new ArrayList<>();
        int offset = 8;
        while (offset < png.length) {
            int length = readInt(png, offset);
            types.add(new String(png, offset + 4, 4, StandardCharsets.US_ASCII));
            offset += 12 + length;
        }
        return types;
    }

    private static List<Integer> chunkLengths(byte[] png, String expectedType) {
        List<Integer> lengths = new ArrayList<>();
        int offset = 8;
        while (offset < png.length) {
            int length = readInt(png, offset);
            String type = new String(png, offset + 4, 4, StandardCharsets.US_ASCII);
            if (type.equals(expectedType)) {
                lengths.add(length);
            }
            offset += 12 + length;
        }
        return lengths;
    }

    private static byte[] incompressibleRgba(int width, int height) {
        byte[] rgba = new byte[Math.multiplyExact(Math.multiplyExact(width, height), 4)];
        int state = 0x6d2b79f5;
        for (int pixel = 0; pixel < width * height; pixel++) {
            for (int channel = 0; channel < 3; channel++) {
                state ^= state << 13;
                state ^= state >>> 17;
                state ^= state << 5;
                rgba[pixel * 4 + channel] = (byte) state;
            }
            rgba[pixel * 4 + 3] = (byte) 0xff;
        }
        return rgba;
    }

    private static int readInt(byte[] value, int offset) {
        return (value[offset] & 0xff) << 24
                | (value[offset + 1] & 0xff) << 16
                | (value[offset + 2] & 0xff) << 8
                | value[offset + 3] & 0xff;
    }

    private static byte[] resource(String name) {
        try (InputStream stream = CanonicalPngCodecV1Test.class.getResourceAsStream(FIXTURE_ROOT + name)) {
            if (stream == null) {
                throw new AssertionError("Missing test fixture: " + name);
            }
            return stream.readAllBytes();
        } catch (IOException exception) {
            throw new AssertionError("Failed to read test fixture: " + name, exception);
        }
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }

    private record Match(int outputOffset, int length, int distance) {
    }

    private record DeflateProbe(
            List<Integer> blockOutputBytes,
            List<Boolean> finalBlocks,
            List<Match> matches
    ) {
        private static final int[] LENGTH_BASE = {
                3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 15, 17, 19, 23, 27, 31,
                35, 43, 51, 59, 67, 83, 99, 115, 131, 163, 195, 227, 258
        };
        private static final int[] LENGTH_EXTRA = {
                0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2,
                3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 0
        };
        private static final int[] DISTANCE_BASE = {
                1, 2, 3, 4, 5, 7, 9, 13, 17, 25, 33, 49, 65, 97, 129, 193,
                257, 385, 513, 769, 1025, 1537, 2049, 3073, 4097, 6145, 8193,
                12289, 16385, 24577
        };
        private static final int[] DISTANCE_EXTRA = {
                0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6,
                7, 7, 8, 8, 9, 9, 10, 10, 11, 11, 12, 12, 13, 13
        };

        private static DeflateProbe read(byte[] zlib) {
            assertEquals(0x78, zlib[0] & 0xff);
            assertEquals(0x01, zlib[1] & 0xff);
            BitReader bits = new BitReader(zlib, 2, zlib.length - 4);
            List<Integer> blockSizes = new ArrayList<>();
            List<Boolean> finals = new ArrayList<>();
            List<Match> matches = new ArrayList<>();
            int outputOffset = 0;
            boolean finalBlock;
            do {
                finalBlock = bits.readBits(1) == 1;
                finals.add(finalBlock);
                assertEquals(1, bits.readBits(2), "BTYPE must be fixed-Huffman");
                int blockStart = outputOffset;
                while (true) {
                    int symbol = bits.readFixedSymbol();
                    if (symbol < 256) {
                        outputOffset++;
                    } else if (symbol == 256) {
                        break;
                    } else {
                        int lengthIndex = symbol - 257;
                        int length = LENGTH_BASE[lengthIndex]
                                + bits.readBits(LENGTH_EXTRA[lengthIndex]);
                        int distanceCode = reverseBits(bits.readBits(5), 5);
                        int distance = DISTANCE_BASE[distanceCode]
                                + bits.readBits(DISTANCE_EXTRA[distanceCode]);
                        matches.add(new Match(outputOffset, length, distance));
                        outputOffset += length;
                    }
                }
                blockSizes.add(outputOffset - blockStart);
            } while (!finalBlock);
            return new DeflateProbe(List.copyOf(blockSizes), List.copyOf(finals), List.copyOf(matches));
        }

        private static int reverseBits(int value, int length) {
            return Integer.reverse(value) >>> 32 - length;
        }
    }

    private static final class BitReader {
        private final byte[] value;
        private final int limit;
        private int byteOffset;
        private int bitOffset;

        private BitReader(byte[] value, int offset, int limit) {
            this.value = value;
            this.byteOffset = offset;
            this.limit = limit;
        }

        private int readBits(int count) {
            int result = 0;
            for (int bit = 0; bit < count; bit++) {
                if (byteOffset >= limit) {
                    throw new AssertionError("Truncated DEFLATE stream");
                }
                result |= (value[byteOffset] >>> bitOffset & 1) << bit;
                bitOffset++;
                if (bitOffset == 8) {
                    bitOffset = 0;
                    byteOffset++;
                }
            }
            return result;
        }

        private int readFixedSymbol() {
            int transmitted = 0;
            for (int length = 1; length <= 9; length++) {
                transmitted |= readBits(1) << length - 1;
                for (int symbol = 0; symbol <= 287; symbol++) {
                    int symbolLength = fixedLength(symbol);
                    if (symbolLength == length
                            && Integer.reverse(fixedCode(symbol)) >>> 32 - length == transmitted) {
                        return symbol;
                    }
                }
            }
            throw new AssertionError("Invalid fixed-Huffman symbol");
        }

        private static int fixedLength(int symbol) {
            if (symbol <= 143) {
                return 8;
            }
            if (symbol <= 255) {
                return 9;
            }
            if (symbol <= 279) {
                return 7;
            }
            return 8;
        }

        private static int fixedCode(int symbol) {
            if (symbol <= 143) {
                return 0x30 + symbol;
            }
            if (symbol <= 255) {
                return 0x190 + symbol - 144;
            }
            if (symbol <= 279) {
                return symbol - 256;
            }
            return 0xc0 + symbol - 280;
        }
    }
}
