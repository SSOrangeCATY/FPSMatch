package com.phasetranscrystal.fpsmatch.core.minimap.format;

import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapHardLimits;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

public final class BoundedPngReader {
    private BoundedPngReader() {
    }

    public static DecodedPng decode(byte[] png) {
        Objects.requireNonNull(png, "png");
        ParsedPng parsed = parse(png);
        byte[] filtered = inflate(png, parsed.idatRanges(), parsed.filteredBytes());
        byte[] rgba = removeFilters(filtered, parsed.width(), parsed.height());
        byte[] canonical = CanonicalPngCodecV1.encode(parsed.width(), parsed.height(), rgba);
        if (!Arrays.equals(canonical, png)) {
            throw new PngValidationException("PNG bytes are not CanonicalPngCodecV1 output");
        }
        return new DecodedPng(parsed.width(), parsed.height(), rgba);
    }

    private static ParsedPng parse(byte[] png) {
        if (png.length > MinimapHardLimits.MAX_CANONICAL_PNG_COMPRESSED_BYTES
                || png.length < CanonicalPngCodecV1.SIGNATURE.length + 12) {
            throw new PngValidationException("PNG byte length is outside the hard limit");
        }
        for (int index = 0; index < CanonicalPngCodecV1.SIGNATURE.length; index++) {
            if (png[index] != CanonicalPngCodecV1.SIGNATURE[index]) {
                throw new PngValidationException("PNG signature is invalid");
            }
        }

        int offset = CanonicalPngCodecV1.SIGNATURE.length;
        int chunkCount = 0;
        int width = -1;
        int height = -1;
        boolean seenIhdr = false;
        boolean seenIdat = false;
        boolean seenIend = false;
        long compressedBytes = 0;
        long maxCompressedBytes = -1;
        int maxIdatChunks = -1;
        List<Range> idatRanges = new ArrayList<>();
        while (offset < png.length) {
            if (++chunkCount > MinimapHardLimits.MAX_ZIP_ENTRIES || png.length - offset < 12) {
                throw new PngValidationException("PNG chunk table is truncated or excessive");
            }
            long unsignedLength = Integer.toUnsignedLong(readInt(png, offset));
            if (unsignedLength > Integer.MAX_VALUE || unsignedLength > png.length - offset - 12L) {
                throw new PngValidationException("PNG chunk length exceeds the remaining input");
            }
            int length = (int) unsignedLength;
            int typeOffset = offset + 4;
            int dataOffset = offset + 8;
            int crcOffset = dataOffset + length;
            String type = new String(png, typeOffset, 4, StandardCharsets.US_ASCII);
            switch (type) {
                case "IHDR" -> {
                    if (seenIhdr || seenIdat || chunkCount != 1 || length != 13) {
                        throw new PngValidationException("PNG IHDR position or length is invalid");
                    }
                    seenIhdr = true;
                    width = readInt(png, dataOffset);
                    height = readInt(png, dataOffset + 4);
                    validateIhdr(png, dataOffset, width, height);
                    int filteredBytes = Math.addExact(checkedPixelBytes(width, height), height);
                    maxCompressedBytes = CanonicalPngCodecV1.maxZlibBytesForFilteredInput(filteredBytes);
                    maxIdatChunks = Math.toIntExact(Math.floorDiv(
                            maxCompressedBytes + CanonicalPngCodecV1.IDAT_CHUNK_BYTES - 1,
                            CanonicalPngCodecV1.IDAT_CHUNK_BYTES
                    ));
                }
                case "IDAT" -> {
                    if (!seenIhdr || seenIend) {
                        throw new PngValidationException("PNG IDAT position is invalid");
                    }
                    if (length <= 0 || length > CanonicalPngCodecV1.IDAT_CHUNK_BYTES) {
                        throw new PngValidationException("PNG IDAT chunk length is not canonical");
                    }
                    if (seenIdat
                            && idatRanges.get(idatRanges.size() - 1).length()
                            != CanonicalPngCodecV1.IDAT_CHUNK_BYTES) {
                        throw new PngValidationException(
                                "PNG non-final IDAT chunks must be exactly 1 MiB"
                        );
                    }
                    if (idatRanges.size() >= maxIdatChunks
                            || compressedBytes > maxCompressedBytes - length) {
                        throw new PngValidationException(
                                "PNG IDAT bytes exceed the canonical size bound"
                        );
                    }
                    seenIdat = true;
                    compressedBytes += length;
                    idatRanges.add(new Range(dataOffset, length));
                }
                case "IEND" -> {
                    if (!seenIhdr || !seenIdat || seenIend || length != 0) {
                        throw new PngValidationException("PNG IEND position or length is invalid");
                    }
                    seenIend = true;
                }
                default -> throw new PngValidationException("PNG contains a forbidden chunk type");
            }
            verifyCrc(png, typeOffset, dataOffset, length, crcOffset);
            offset = crcOffset + 4;
            if (seenIend && offset != png.length) {
                throw new PngValidationException("PNG contains bytes after IEND");
            }
        }
        if (!seenIend) {
            throw new PngValidationException("PNG is missing IEND");
        }
        int pixelBytes = checkedPixelBytes(width, height);
        int filteredBytes = Math.addExact(pixelBytes, height);
        if (compressedBytes < 2 || compressedByte(png, idatRanges, 0) != 0x78
                || compressedByte(png, idatRanges, 1) != 0x01) {
            throw new PngValidationException("PNG zlib header is not canonical");
        }
        return new ParsedPng(width, height, filteredBytes, List.copyOf(idatRanges));
    }

    private static void validateIhdr(byte[] png, int offset, int width, int height) {
        checkedPixelBytes(width, height);
        if ((png[offset + 8] & 0xff) != 8
                || (png[offset + 9] & 0xff) != 6
                || png[offset + 10] != 0
                || png[offset + 11] != 0
                || png[offset + 12] != 0) {
            throw new PngValidationException("PNG must be non-interlaced 8-bit RGBA");
        }
    }

    private static int checkedPixelBytes(int width, int height) {
        if (width <= 0 || height <= 0
                || width > MinimapHardLimits.MAX_TILE_EDGE
                || height > MinimapHardLimits.MAX_TILE_EDGE) {
            throw new PngValidationException("PNG dimensions exceed the tile hard limit");
        }
        long bytes = Math.multiplyExact(Math.multiplyExact((long) width, height), 4L);
        if (bytes > MinimapHardLimits.MAX_DECODED_TILE_BYTES || bytes > Integer.MAX_VALUE) {
            throw new PngValidationException("PNG decoded bytes exceed the hard limit");
        }
        return (int) bytes;
    }

    private static byte[] inflate(byte[] png, List<Range> ranges, int expectedBytes) {
        byte[] output = new byte[Math.addExact(expectedBytes, 1)];
        Inflater inflater = new Inflater();
        int outputOffset = 0;
        int rangeIndex = 0;
        try {
            while (!inflater.finished()) {
                if (inflater.needsDictionary()) {
                    throw new PngValidationException("PNG zlib stream requires a dictionary");
                }
                if (inflater.needsInput()) {
                    if (rangeIndex >= ranges.size()) {
                        break;
                    }
                    Range range = ranges.get(rangeIndex++);
                    inflater.setInput(png, range.offset(), range.length());
                }
                int count = inflater.inflate(output, outputOffset, output.length - outputOffset);
                outputOffset += count;
                if (outputOffset > expectedBytes) {
                    throw new PngValidationException("PNG zlib stream expands beyond declared dimensions");
                }
                if (count == 0 && !inflater.finished() && !inflater.needsInput()
                        && !inflater.needsDictionary()) {
                    throw new PngValidationException("PNG zlib stream made no progress");
                }
            }
            if (!inflater.finished() || outputOffset != expectedBytes
                    || inflater.getRemaining() != 0 || rangeIndex != ranges.size()) {
                throw new PngValidationException("PNG zlib stream is truncated or contains extra data");
            }
            return Arrays.copyOf(output, expectedBytes);
        } catch (DataFormatException exception) {
            throw new PngValidationException("PNG zlib stream is invalid", exception);
        } finally {
            inflater.end();
        }
    }

    private static byte[] removeFilters(byte[] filtered, int width, int height) {
        int rowBytes = Math.multiplyExact(width, 4);
        byte[] rgba = new byte[Math.multiplyExact(rowBytes, height)];
        int source = 0;
        int target = 0;
        for (int row = 0; row < height; row++) {
            if (filtered[source++] != 0) {
                throw new PngValidationException("Canonical PNG rows must use filter 0");
            }
            System.arraycopy(filtered, source, rgba, target, rowBytes);
            source += rowBytes;
            target += rowBytes;
        }
        return rgba;
    }

    private static void verifyCrc(byte[] png, int typeOffset, int dataOffset, int length, int crcOffset) {
        CRC32 crc = new CRC32();
        crc.update(png, typeOffset, 4);
        crc.update(png, dataOffset, length);
        if (crc.getValue() != Integer.toUnsignedLong(readInt(png, crcOffset))) {
            throw new PngValidationException("PNG chunk CRC does not match");
        }
    }

    private static int compressedByte(byte[] png, List<Range> ranges, int requestedIndex) {
        int index = requestedIndex;
        for (Range range : ranges) {
            if (index < range.length()) {
                return png[range.offset() + index] & 0xff;
            }
            index -= range.length();
        }
        throw new PngValidationException("PNG IDAT stream is truncated");
    }

    private static int readInt(byte[] value, int offset) {
        return (value[offset] & 0xff) << 24
                | (value[offset + 1] & 0xff) << 16
                | (value[offset + 2] & 0xff) << 8
                | value[offset + 3] & 0xff;
    }

    public record DecodedPng(int width, int height, byte[] rgba) {
        public DecodedPng {
            rgba = rgba.clone();
        }

        @Override
        public byte[] rgba() {
            return rgba.clone();
        }
    }

    private record Range(int offset, int length) {
    }

    private record ParsedPng(int width, int height, int filteredBytes, List<Range> idatRanges) {
    }
}
