package com.phasetranscrystal.fpsmatch.core.minimap.format;

import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapHardLimits;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.zip.Adler32;
import java.util.zip.CRC32;

public final class CanonicalPngCodecV1 {
    static final byte[] SIGNATURE = {
            (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a
    };
    static final int IDAT_CHUNK_BYTES = 1024 * 1024;

    private static final int DEFLATE_BLOCK_BYTES = 32_768;
    private static final int DEFLATE_WINDOW_BYTES = 32_768;
    private static final int MAX_MATCH_BYTES = 258;
    private static final int MAX_HASH_SIZE = 1 << 20;

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

    private CanonicalPngCodecV1() {
    }

    public static byte[] encode(int width, int height, byte[] rgba) {
        Objects.requireNonNull(rgba, "rgba");
        int pixelBytes = checkedPixelBytes(width, height);
        if (rgba.length != pixelBytes) {
            throw new PngValidationException("RGBA byte length does not match PNG dimensions");
        }

        int rowBytes = Math.multiplyExact(width, 4);
        byte[] filtered = new byte[Math.addExact(pixelBytes, height)];
        int source = 0;
        int target = 0;
        for (int row = 0; row < height; row++) {
            filtered[target++] = 0;
            for (int columnByte = 0; columnByte < rowBytes; columnByte += 4) {
                byte red = rgba[source++];
                byte green = rgba[source++];
                byte blue = rgba[source++];
                byte alpha = rgba[source++];
                if (alpha == 0) {
                    red = 0;
                    green = 0;
                    blue = 0;
                }
                filtered[target++] = red;
                filtered[target++] = green;
                filtered[target++] = blue;
                filtered[target++] = alpha;
            }
        }

        byte[] zlib = encodeZlib(filtered);
        if (zlib.length > MinimapHardLimits.MAX_CANONICAL_PNG_COMPRESSED_BYTES) {
            throw new PngValidationException("Canonical PNG compressed stream exceeds the hard limit");
        }

        ByteArrayOutputStream png = new ByteArrayOutputStream(zlib.length + 128);
        png.writeBytes(SIGNATURE);
        byte[] ihdr = new byte[13];
        writeInt(ihdr, 0, width);
        writeInt(ihdr, 4, height);
        ihdr[8] = 8;
        ihdr[9] = 6;
        writeChunk(png, "IHDR", ihdr, 0, ihdr.length);
        for (int offset = 0; offset < zlib.length; offset += IDAT_CHUNK_BYTES) {
            int length = Math.min(IDAT_CHUNK_BYTES, zlib.length - offset);
            writeChunk(png, "IDAT", zlib, offset, length);
        }
        writeChunk(png, "IEND", new byte[0], 0, 0);
        return png.toByteArray();
    }

    static byte[] encodeZlib(byte[] input) {
        Objects.requireNonNull(input, "input");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(input.length / 2 + 16);
        bytes.write(0x78);
        bytes.write(0x01);
        BitWriter bits = new BitWriter(bytes);
        int[] heads = new int[hashTableSizeForInput(input.length)];
        int[] previous = new int[input.length];
        Arrays.fill(heads, -1);
        Arrays.fill(previous, -1);

        int position = 0;
        if (input.length == 0) {
            writeBlockHeader(bits, true);
            writeFixedSymbol(bits, 256);
        }
        while (position < input.length) {
            int blockEnd = Math.min(input.length,
                    Math.addExact(position - position % DEFLATE_BLOCK_BYTES, DEFLATE_BLOCK_BYTES));
            writeBlockHeader(bits, blockEnd == input.length);
            while (position < blockEnd) {
                int maxLength = Math.min(MAX_MATCH_BYTES, blockEnd - position);
                long match = findMatch(input, position, maxLength, heads, previous);
                int length = (int) (match >>> 32);
                int distance = (int) match;
                int consumed;
                if (length >= 3) {
                    writeLengthDistance(bits, length, distance);
                    consumed = length;
                } else {
                    writeFixedSymbol(bits, input[position] & 0xff);
                    consumed = 1;
                }
                for (int index = position; index < position + consumed; index++) {
                    addHistory(input, index, heads, previous);
                }
                position += consumed;
            }
            writeFixedSymbol(bits, 256);
        }
        bits.finish();

        Adler32 adler = new Adler32();
        adler.update(input);
        writeInt(bytes, (int) adler.getValue());
        return bytes.toByteArray();
    }

    private static int checkedPixelBytes(int width, int height) {
        if (width <= 0 || height <= 0
                || width > MinimapHardLimits.MAX_TILE_EDGE
                || height > MinimapHardLimits.MAX_TILE_EDGE) {
            throw new PngValidationException("PNG dimensions exceed the tile hard limit");
        }
        long pixels = Math.multiplyExact((long) width, height);
        long bytes = Math.multiplyExact(pixels, 4L);
        if (bytes > MinimapHardLimits.MAX_DECODED_TILE_BYTES || bytes > Integer.MAX_VALUE) {
            throw new PngValidationException("PNG decoded bytes exceed the hard limit");
        }
        return (int) bytes;
    }

    static int hashTableSizeForInput(int inputLength) {
        if (inputLength < 0) {
            throw new IllegalArgumentException("Input length cannot be negative");
        }
        int tableSize = 16;
        while (tableSize < inputLength && tableSize < MAX_HASH_SIZE) {
            tableSize <<= 1;
        }
        return tableSize;
    }

    static long maxZlibBytesForFilteredInput(long filteredBytes) {
        if (filteredBytes < 0) {
            throw new IllegalArgumentException("Filtered byte length cannot be negative");
        }
        long blocks = Math.max(1L, (filteredBytes + DEFLATE_BLOCK_BYTES - 1) / DEFLATE_BLOCK_BYTES);
        long deflateBits = Math.addExact(Math.multiplyExact(filteredBytes, 9L),
                Math.multiplyExact(blocks, 10L));
        long deflateBytes = Math.floorDiv(Math.addExact(deflateBits, 7L), 8L);
        return Math.addExact(deflateBytes, 6L);
    }

    private static long findMatch(
            byte[] input,
            int position,
            int maxLength,
            int[] heads,
            int[] previous
    ) {
        if (maxLength < 3 || position + 2 >= input.length) {
            return 0;
        }
        int candidate = heads[hash(input, position, heads.length)];
        int bestLength = 0;
        int bestDistance = 0;
        while (candidate >= 0) {
            int distance = position - candidate;
            if (distance > DEFLATE_WINDOW_BYTES) {
                break;
            }
            if (input[candidate] == input[position]
                    && input[candidate + 1] == input[position + 1]
                    && input[candidate + 2] == input[position + 2]) {
                int length = 3;
                while (length < maxLength && input[candidate + length] == input[position + length]) {
                    length++;
                }
                if (length > bestLength) {
                    bestLength = length;
                    bestDistance = distance;
                    if (length == maxLength) {
                        break;
                    }
                }
            }
            candidate = previous[candidate];
        }
        return (long) bestLength << 32 | bestDistance & 0xffffffffL;
    }

    private static void addHistory(byte[] input, int position, int[] heads, int[] previous) {
        if (position + 2 >= input.length) {
            return;
        }
        int hash = hash(input, position, heads.length);
        previous[position] = heads[hash];
        heads[hash] = position;
    }

    private static int hash(byte[] input, int position, int tableSize) {
        int value = (input[position] & 0xff) << 16
                | (input[position + 1] & 0xff) << 8
                | input[position + 2] & 0xff;
        int hashBits = Integer.numberOfTrailingZeros(tableSize);
        return value * 0x1e35a7bd >>> 32 - hashBits;
    }

    private static void writeBlockHeader(BitWriter bits, boolean finalBlock) {
        bits.writeBits(finalBlock ? 1 : 0, 1);
        bits.writeBits(1, 2);
    }

    private static void writeLengthDistance(BitWriter bits, int length, int distance) {
        int lengthIndex;
        if (length == 258) {
            lengthIndex = 28;
        } else {
            lengthIndex = findCode(length, LENGTH_BASE, LENGTH_EXTRA, 28);
        }
        writeFixedSymbol(bits, 257 + lengthIndex);
        bits.writeBits(length - LENGTH_BASE[lengthIndex], LENGTH_EXTRA[lengthIndex]);

        int distanceIndex = findCode(distance, DISTANCE_BASE, DISTANCE_EXTRA, DISTANCE_BASE.length);
        bits.writeBits(reverseBits(distanceIndex, 5), 5);
        bits.writeBits(distance - DISTANCE_BASE[distanceIndex], DISTANCE_EXTRA[distanceIndex]);
    }

    private static int findCode(int value, int[] bases, int[] extras, int count) {
        for (int index = 0; index < count; index++) {
            int maximum = bases[index] + (1 << extras[index]) - 1;
            if (value >= bases[index] && value <= maximum) {
                return index;
            }
        }
        throw new IllegalArgumentException("Value cannot be represented by DEFLATE code tables");
    }

    private static void writeFixedSymbol(BitWriter bits, int symbol) {
        int code;
        int length;
        if (symbol <= 143) {
            code = 0x30 + symbol;
            length = 8;
        } else if (symbol <= 255) {
            code = 0x190 + symbol - 144;
            length = 9;
        } else if (symbol <= 279) {
            code = symbol - 256;
            length = 7;
        } else {
            code = 0xc0 + symbol - 280;
            length = 8;
        }
        bits.writeBits(reverseBits(code, length), length);
    }

    private static int reverseBits(int value, int length) {
        return Integer.reverse(value) >>> 32 - length;
    }

    private static void writeChunk(
            ByteArrayOutputStream output,
            String type,
            byte[] data,
            int offset,
            int length
    ) {
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        writeInt(output, length);
        output.writeBytes(typeBytes);
        output.write(data, offset, length);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data, offset, length);
        writeInt(output, (int) crc.getValue());
    }

    private static void writeInt(byte[] output, int offset, int value) {
        output[offset] = (byte) (value >>> 24);
        output[offset + 1] = (byte) (value >>> 16);
        output[offset + 2] = (byte) (value >>> 8);
        output[offset + 3] = (byte) value;
    }

    private static void writeInt(ByteArrayOutputStream output, int value) {
        output.write(value >>> 24);
        output.write(value >>> 16);
        output.write(value >>> 8);
        output.write(value);
    }

    private static final class BitWriter {
        private final ByteArrayOutputStream output;
        private int currentByte;
        private int bitCount;

        private BitWriter(ByteArrayOutputStream output) {
            this.output = output;
        }

        private void writeBits(int value, int count) {
            for (int bit = 0; bit < count; bit++) {
                currentByte |= (value >>> bit & 1) << bitCount;
                bitCount++;
                if (bitCount == 8) {
                    output.write(currentByte);
                    currentByte = 0;
                    bitCount = 0;
                }
            }
        }

        private void finish() {
            if (bitCount > 0) {
                output.write(currentByte);
                currentByte = 0;
                bitCount = 0;
            }
        }
    }
}
