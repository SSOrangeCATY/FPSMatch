package com.phasetranscrystal.fpsmatch.core.minimap.format;

import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapHardLimits;
import com.phasetranscrystal.fpsmatch.core.minimap.model.ContainerPath;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.AbstractMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.CRC32;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CanonicalZipWriterTest {
    private static final String FIXTURE_ROOT =
            "/com/phasetranscrystal/fpsmatch/minimap/contract/v1/zip/";
    private static final ContainerLimits LIMITS = new ContainerLimits(8, 1024, 4096, 1);

    @Test
    void writesTheCanonicalSingleEntryGoldenBytesExactly() throws IOException {
        byte[] expected = HexFormat.of().parseHex(new String(
                resource("single-a-canonical.hex"), StandardCharsets.US_ASCII
        ).trim());

        byte[] actual = CanonicalZipWriter.write(List.of(
                entry("a.txt", new byte[]{'A'})
        ), LIMITS);
        ByteArrayOutputStream streamed = new ByteArrayOutputStream();
        CanonicalZipWriter.write(streamed, List.of(
                entry("a.txt", new byte[]{'A'})
        ), LIMITS);

        assertArrayEquals(expected, actual);
        assertArrayEquals(expected, streamed.toByteArray());
        assertEquals(109, actual.length);
        assertEquals("237b8cde0cf2748f095e9d965651584bd60f73ef977a65f95b9ac69dddaf2dfc",
                sha256(actual));
    }

    @Test
    void sortsEntriesByCanonicalPathAndDoesNotDependOnCallerOrder() {
        CanonicalZipWriter.Entry first = entry("a.txt", new byte[]{1});
        CanonicalZipWriter.Entry second = entry("b.bin", new byte[]{2, 3});

        byte[] sorted = CanonicalZipWriter.write(List.of(first, second), LIMITS);
        byte[] reversed = CanonicalZipWriter.write(List.of(second, first), LIMITS);

        assertArrayEquals(sorted, reversed);
    }

    @Test
    void matchesTheIndependentTwoEntryOracleAcrossEmptyAndNonEmptyPayloads() {
        byte[] zip = CanonicalZipWriter.write(List.of(
                entry("z/empty.png", new byte[0]),
                entry("a.json", "{}".getBytes(StandardCharsets.UTF_8))
        ), LIMITS);
        int eocd = zip.length - 22;

        assertEquals(210, zip.length);
        assertEquals(109, readInt(zip, eocd + 12));
        assertEquals(79, readInt(zip, eocd + 16));
        assertEquals("3acc2a3cb89f38f01e5ea63c93d71cb3284e3750bac9934bbbe3460014789a2f",
                sha256(zip));
    }

    @Test
    void hardLimitProfilesFreezeSourceAndRuntimeContainerBounds() {
        ContainerLimits source = ContainerLimits.sourceHardLimits();
        ContainerLimits runtime = ContainerLimits.runtimeHardLimits();

        assertEquals(MinimapHardLimits.MAX_ZIP_ENTRIES, source.maxEntries());
        assertEquals(MinimapHardLimits.MAX_ZIP_ENTRY_BYTES, source.maxEntryBytes());
        assertEquals(MinimapHardLimits.MAX_SOURCE_EXPANDED_BYTES, source.maxExpandedBytes());
        assertEquals(1, source.maxCompressionRatio());
        assertEquals(1_109_786_646L, source.maxCanonicalContainerBytes());
        assertEquals(MinimapHardLimits.MAX_RUNTIME_EXPANDED_BYTES, runtime.maxExpandedBytes());
        assertEquals(572_915_734L, runtime.maxCanonicalContainerBytes());
    }

    @Test
    void snapshotsEntryBytesAndRejectsDuplicatePathsOrWriterLimits() {
        byte[] mutable = {1};
        CanonicalZipWriter.Entry snapshotted = entry("a.bin", mutable);
        mutable[0] = 9;
        byte[] exposed = snapshotted.bytes();
        exposed[0] = 8;

        byte[] zip = CanonicalZipWriter.write(List.of(snapshotted), LIMITS);
        assertArrayEquals(new byte[]{1}, CanonicalZipReader.read(zip, LIMITS)
                .entryBytes(ContainerPath.parse("a.bin")));

        assertThrows(ContainerValidationException.class, () -> CanonicalZipWriter.write(
                List.of(entry("a.bin", new byte[0]), entry("a.bin", new byte[0])), LIMITS
        ));
        assertThrows(ContainerValidationException.class, () -> CanonicalZipWriter.write(
                List.of(entry("a.bin", new byte[0]), entry("b.bin", new byte[0])),
                new ContainerLimits(1, 1024, 4096, 1)
        ));
        assertThrows(ContainerValidationException.class, () -> CanonicalZipWriter.write(
                List.of(entry("a.bin", new byte[2])),
                new ContainerLimits(8, 1, 4096, 1)
        ));
        assertThrows(ContainerValidationException.class, () -> CanonicalZipWriter.write(
                List.of(entry("a.bin", new byte[1]), entry("b.bin", new byte[1])),
                new ContainerLimits(8, 1024, 1, 1)
        ));
    }

    @Test
    void streamsRepeatableEntrySourcesInTwoPassesAndRechecksTheSecondPass() throws IOException {
        RepeatableSource source = new RepeatableSource("a.bin", new byte[]{1, 2, 3});
        ByteArrayOutputStream streamed = new ByteArrayOutputStream();

        CanonicalZipWriter.write(streamed, List.of(source), LIMITS);

        assertEquals(2, source.openCount);
        assertArrayEquals(
                CanonicalZipWriter.write(List.of(entry("a.bin", new byte[]{1, 2, 3})), LIMITS),
                streamed.toByteArray()
        );

        CanonicalZipWriter.EntrySource changing = new CanonicalZipWriter.EntrySource() {
            private int opens;

            @Override
            public ContainerPath path() {
                return ContainerPath.parse("changing.bin");
            }

            @Override
            public long size() {
                return 1;
            }

            @Override
            public InputStream openStream() {
                return new ByteArrayInputStream(new byte[]{(byte) ++opens});
            }
        };
        assertThrows(ContainerValidationException.class, () -> CanonicalZipWriter.write(
                new ByteArrayOutputStream(), List.of(changing), LIMITS
        ));
    }

    @Test
    void inMemoryConvenienceRejectsLargeSourcesBeforeOpeningThem() {
        CanonicalZipWriter.EntrySource unopened = new CanonicalZipWriter.EntrySource() {
            @Override
            public ContainerPath path() {
                return ContainerPath.parse("large.bin");
            }

            @Override
            public long size() {
                return CanonicalZipWriter.MAX_IN_MEMORY_CONTAINER_BYTES;
            }

            @Override
            public InputStream openStream() {
                throw new AssertionError("Large source must be rejected before it is opened");
            }
        };

        assertThrows(ContainerValidationException.class,
                () -> CanonicalZipWriter.writeSources(List.of(unopened),
                        ContainerLimits.sourceHardLimits()));
    }

    @Test
    void twoPassConsistencyCannotBeBypassedWithARepeatedCrc32Value() {
        byte[] first = HexFormat.of().parseHex("9fc3c332f13410ac");
        byte[] second = HexFormat.of().parseHex("b4dfd34b139d04ad");
        assertEquals(crc32(first), crc32(second));
        assertEquals(false, java.util.Arrays.equals(first, second));
        CanonicalZipWriter.EntrySource collision = new CanonicalZipWriter.EntrySource() {
            private int opens;

            @Override
            public ContainerPath path() {
                return ContainerPath.parse("collision.bin");
            }

            @Override
            public long size() {
                return first.length;
            }

            @Override
            public InputStream openStream() {
                return new ByteArrayInputStream(opens++ == 0 ? first : second);
            }
        };

        assertThrows(ContainerValidationException.class, () -> CanonicalZipWriter.write(
                new ByteArrayOutputStream(), List.of(collision), LIMITS
        ));
    }

    @Test
    void mapCountLimitIsCheckedBeforeAllocatingOrIteratingEntries() {
        Map<ContainerPath, byte[]> oversizedLazyMap = new AbstractMap<>() {
            @Override
            public Set<Map.Entry<ContainerPath, byte[]>> entrySet() {
                throw new AssertionError("Map entries must not be visited after the count gate fails");
            }

            @Override
            public int size() {
                return 100_000;
            }
        };

        assertThrows(ContainerValidationException.class,
                () -> CanonicalZipWriter.write(oversizedLazyMap, LIMITS));
    }

    private static CanonicalZipWriter.Entry entry(String path, byte[] bytes) {
        return new CanonicalZipWriter.Entry(ContainerPath.parse(path), bytes);
    }

    private static byte[] resource(String name) {
        try (InputStream stream = CanonicalZipWriterTest.class.getResourceAsStream(FIXTURE_ROOT + name)) {
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

    private static int readInt(byte[] value, int offset) {
        return value[offset] & 0xff
                | (value[offset + 1] & 0xff) << 8
                | (value[offset + 2] & 0xff) << 16
                | (value[offset + 3] & 0xff) << 24;
    }

    private static long crc32(byte[] value) {
        CRC32 crc = new CRC32();
        crc.update(value);
        return crc.getValue();
    }

    private static final class RepeatableSource implements CanonicalZipWriter.EntrySource {
        private final ContainerPath path;
        private final byte[] bytes;
        private int openCount;

        private RepeatableSource(String path, byte[] bytes) {
            this.path = ContainerPath.parse(path);
            this.bytes = bytes.clone();
        }

        @Override
        public ContainerPath path() {
            return path;
        }

        @Override
        public long size() {
            return bytes.length;
        }

        @Override
        public InputStream openStream() {
            openCount++;
            return new ByteArrayInputStream(bytes);
        }
    }
}
