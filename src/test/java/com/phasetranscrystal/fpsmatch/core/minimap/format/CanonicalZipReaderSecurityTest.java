package com.phasetranscrystal.fpsmatch.core.minimap.format;

import com.phasetranscrystal.fpsmatch.core.minimap.model.ContainerPath;
import com.phasetranscrystal.fpsmatch.core.minimap.model.Sha256;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CanonicalZipReaderSecurityTest {
    private static final ContainerLimits LIMITS = new ContainerLimits(16, 1024, 4096, 1);

    @TempDir
    Path tempDirectory;

    @Test
    void readsCanonicalEntriesAndReturnsOnlyDefensiveCopies() {
        byte[] zip = CanonicalZipWriter.write(Map.of(
                path("b.bin"), new byte[]{2, 3},
                path("a.bin"), new byte[]{1}
        ), LIMITS);

        CanonicalZipReader.Archive archive = CanonicalZipReader.read(zip, LIMITS);
        byte[] first = archive.entryBytes(path("a.bin"));
        first[0] = 9;
        Map<ContainerPath, byte[]> exposed = archive.entries();
        exposed.get(path("b.bin"))[0] = 8;

        assertEquals(List.of(path("a.bin"), path("b.bin")), List.copyOf(archive.paths()));
        assertArrayEquals(new byte[]{1}, archive.entryBytes(path("a.bin")));
        assertArrayEquals(new byte[]{2, 3}, archive.entryBytes(path("b.bin")));
        assertThrows(UnsupportedOperationException.class,
                () -> exposed.put(path("c.bin"), new byte[0]));
        assertThrows(ContainerValidationException.class,
                () -> archive.entryBytes(path("missing.bin")));
    }

    @Test
    void exposesOnlyValidatedEntryMetadataAndCompleteContainerHashWithoutPayloadCopies() {
        byte[] zip = rawStoredZip(List.of(new RawEntry(utf8("a.txt"), new byte[]{'A'})));

        CanonicalZipReader.Archive archive = CanonicalZipReader.read(zip, LIMITS);

        assertEquals(1, archive.entryLength(path("a.txt")));
        assertEquals(sha256(new byte[]{'A'}), archive.entrySha256(path("a.txt")));
        assertEquals(
                Sha256.parse("237b8cde0cf2748f095e9d965651584bd60f73ef977a65f95b9ac69dddaf2dfc"),
                archive.containerSha256()
        );
        assertThrows(ContainerValidationException.class,
                () -> archive.entryLength(path("missing.bin")));
    }

    @Test
    void opensAChannelBackedArchiveAndBoundsEachEntryStream() throws Exception {
        byte[] zip = CanonicalZipWriter.write(Map.of(
                path("a.bin"), new byte[]{1, 2, 3},
                path("b.bin"), new byte[]{4}
        ), LIMITS);
        Path file = tempDirectory.resolve("map.fpsmap");
        Files.write(file, zip);
        FileChannel channel = FileChannel.open(file, StandardOpenOption.READ);

        try (CanonicalZipReader.Archive archive = CanonicalZipReader.open(
                channel, Files.size(file), LIMITS
        ); InputStream entry = archive.openBounded(path("a.bin"))) {
            assertArrayEquals(new byte[]{1, 2, 3}, entry.readAllBytes());
            assertEquals(0, entry.read(new byte[1], 0, 0));
            assertEquals(-1, entry.read());

            InputStream closed = archive.openBounded(path("b.bin"));
            closed.close();
            assertThrows(java.io.IOException.class, closed::read);
        }

        assertEquals(false, channel.isOpen());
    }

    @Test
    void openBoundedDoesNotAllocateAnEntrySizedSnapshot() throws Exception {
        int entryBytes = 8 * 1024 * 1024;
        ContainerLimits limits = new ContainerLimits(4, entryBytes, entryBytes, 1);
        byte[] zip = CanonicalZipWriter.write(Map.of(
                path("large.bin"), new byte[entryBytes]
        ), limits);
        Path file = tempDirectory.resolve("large-entry.fpsmap");
        Files.write(file, zip);
        com.sun.management.ThreadMXBean allocations =
                (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        assertTrue(allocations.isThreadAllocatedMemorySupported());
        allocations.setThreadAllocatedMemoryEnabled(true);

        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ);
             CanonicalZipReader.Archive archive = CanonicalZipReader.open(
                     channel, Files.size(file), limits
             )) {
            long threadId = Thread.currentThread().getId();
            long before = allocations.getThreadAllocatedBytes(threadId);

            try (InputStream ignored = archive.openBounded(path("large.bin"))) {
                // Closing an unread authenticated stream must remain bounded too.
            }

            long allocated = allocations.getThreadAllocatedBytes(threadId) - before;
            assertTrue(allocated < 1024 * 1024,
                    "Opening the bounded stream allocated " + allocated + " bytes");
        }
    }

    @Test
    void channelEntryStreamRejectsBytesChangedBeforeItOpensAtStreamEnd() throws Exception {
        byte[] zip = CanonicalZipWriter.write(Map.of(
                path("a.bin"), new byte[]{1, 2, 3}
        ), LIMITS);
        Path file = tempDirectory.resolve("mutable.fpsmap");
        Files.write(file, zip);
        FileChannel channel = FileChannel.open(file, StandardOpenOption.READ);

        try (CanonicalZipReader.Archive archive = CanonicalZipReader.open(
                channel, Files.size(file), LIMITS
        )) {
            try (FileChannel mutator = FileChannel.open(file, StandardOpenOption.WRITE)) {
                mutator.position(30L + "a.bin".length());
                mutator.write(ByteBuffer.wrap(new byte[]{9}));
                mutator.force(true);
            }

            assertThrows(ContainerValidationException.class,
                    () -> archive.entryBytes(path("a.bin")));
            try (InputStream entry = archive.openBounded(path("a.bin"))) {
                assertThrows(ContainerValidationException.class, entry::readAllBytes);
            }
        }
    }

    @Test
    void sameLengthReplacementAfterStreamOpenFailsBeforeFinalReadReturns() throws Exception {
        byte[] zip = CanonicalZipWriter.write(Map.of(
                path("a.bin"), new byte[]{1, 2, 3}
        ), LIMITS);
        Path file = tempDirectory.resolve("stream-replaced.fpsmap");
        Files.write(file, zip);

        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ);
             CanonicalZipReader.Archive archive = CanonicalZipReader.open(
                     channel, Files.size(file), LIMITS
             ); InputStream entry = archive.openBounded(path("a.bin"))) {
            try (FileChannel mutator = FileChannel.open(file, StandardOpenOption.WRITE)) {
                mutator.position(30L + "a.bin".length());
                mutator.write(ByteBuffer.wrap(new byte[]{9}));
                mutator.force(true);
            }

            byte[] output = new byte[3];
            assertThrows(ContainerValidationException.class, () -> entry.read(output));
        }
    }

    @Test
    void partialReadThenCloseDrainsAndDetectsReplacement() throws Exception {
        byte[] zip = CanonicalZipWriter.write(Map.of(
                path("a.bin"), new byte[]{1, 2, 3, 4}
        ), LIMITS);
        Path file = tempDirectory.resolve("close-replaced.fpsmap");
        Files.write(file, zip);

        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ);
             CanonicalZipReader.Archive archive = CanonicalZipReader.open(
                     channel, Files.size(file), LIMITS
             )) {
            InputStream entry = archive.openBounded(path("a.bin"));
            assertEquals(1, entry.read());
            try (FileChannel mutator = FileChannel.open(file, StandardOpenOption.WRITE)) {
                mutator.position(30L + "a.bin".length() + 2);
                mutator.write(ByteBuffer.wrap(new byte[]{9}));
                mutator.force(true);
            }

            assertThrows(ContainerValidationException.class, entry::close);
        }
    }

    @Test
    void closingArchiveInvalidatesOpenEntryStream() throws Exception {
        CanonicalZipReader.Archive archive = CanonicalZipReader.read(
                CanonicalZipWriter.write(Map.of(
                        path("a.bin"), new byte[]{1, 2, 3}
                ), LIMITS),
                LIMITS
        );
        InputStream entry = archive.openBounded(path("a.bin"));

        archive.close();

        assertThrows(java.io.IOException.class, entry::read);
        assertThrows(java.io.IOException.class, entry::close);
    }

    @Test
    void closingOneEntryLeavesArchiveAndSiblingEntryUsable() throws Exception {
        try (CanonicalZipReader.Archive archive = CanonicalZipReader.read(
                CanonicalZipWriter.write(Map.of(
                        path("a.bin"), new byte[]{1, 2, 3},
                        path("b.bin"), new byte[]{4, 5}
                ), LIMITS),
                LIMITS
        )) {
            InputStream first = archive.openBounded(path("a.bin"));
            InputStream sibling = archive.openBounded(path("b.bin"));

            first.close();

            assertEquals(2, archive.size());
            assertArrayEquals(new byte[]{4, 5}, sibling.readAllBytes());
            sibling.close();
        }
    }

    @Test
    void skipZeroLengthAvailableAndMarkResetPreserveAuthentication() throws Exception {
        byte[] zip = CanonicalZipWriter.write(Map.of(
                path("a.bin"), new byte[]{1, 2, 3, 4}
        ), LIMITS);
        Path file = tempDirectory.resolve("skipped-replaced.fpsmap");
        Files.write(file, zip);

        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ);
             CanonicalZipReader.Archive archive = CanonicalZipReader.open(
                     channel, Files.size(file), LIMITS
             ); InputStream entry = archive.openBounded(path("a.bin"))) {
            assertEquals(4, entry.available());
            assertEquals(0, entry.read(new byte[2], 0, 0));
            assertEquals(4, entry.available());
            assertEquals(false, entry.markSupported());
            entry.mark(4);
            assertThrows(java.io.IOException.class, entry::reset);

            try (FileChannel mutator = FileChannel.open(file, StandardOpenOption.WRITE)) {
                mutator.position(30L + "a.bin".length());
                mutator.write(ByteBuffer.wrap(new byte[]{9}));
                mutator.force(true);
            }

            assertEquals(2, entry.skip(2));
            assertEquals(2, entry.available());
            assertThrows(ContainerValidationException.class, entry::readAllBytes);
        }
    }

    @Test
    void emptyEntryIsVerifiedWithoutAllocatingADrainBuffer() throws Exception {
        try (CanonicalZipReader.Archive archive = CanonicalZipReader.read(
                CanonicalZipWriter.write(Map.of(
                        path("empty.bin"), new byte[0]
                ), LIMITS),
                LIMITS
        )) {
            InputStream entry = archive.openBounded(path("empty.bin"));
            assertEquals(0, entry.available());
            assertEquals(0, entry.read(new byte[1], 0, 0));
            assertEquals(-1, entry.read());
            com.sun.management.ThreadMXBean allocations =
                    (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
            allocations.setThreadAllocatedMemoryEnabled(true);
            long threadId = Thread.currentThread().getId();
            long before = allocations.getThreadAllocatedBytes(threadId);

            entry.close();

            long allocated = allocations.getThreadAllocatedBytes(threadId) - before;
            assertTrue(allocated < 4096,
                    "Closing an empty entry allocated " + allocated + " bytes");
            assertThrows(java.io.IOException.class, entry::available);
        }
    }

    @Test
    void channelArchiveRejectsContainerGrowthAfterInitialValidation() throws Exception {
        byte[] zip = CanonicalZipWriter.write(Map.of(
                path("a.bin"), new byte[]{1, 2, 3}
        ), LIMITS);
        Path file = tempDirectory.resolve("grown.fpsmap");
        Files.write(file, zip);
        FileChannel channel = FileChannel.open(file, StandardOpenOption.READ);

        try (CanonicalZipReader.Archive archive = CanonicalZipReader.open(
                channel, Files.size(file), LIMITS
        ); FileChannel mutator = FileChannel.open(file, StandardOpenOption.WRITE)) {
            mutator.position(Files.size(file));
            mutator.write(ByteBuffer.wrap(new byte[]{9}));
            mutator.force(true);

            assertThrows(ContainerValidationException.class, archive::containerSha256);
            assertThrows(ContainerValidationException.class,
                    () -> archive.entryBytes(path("a.bin")));
        }
    }

    @Test
    void channelHashAccessorsRejectSameLengthPayloadReplacement() throws Exception {
        byte[] zip = CanonicalZipWriter.write(Map.of(
                path("a.bin"), new byte[]{1, 2, 3}
        ), LIMITS);
        Path file = tempDirectory.resolve("replaced.fpsmap");
        Files.write(file, zip);
        FileChannel channel = FileChannel.open(file, StandardOpenOption.READ);

        try (CanonicalZipReader.Archive archive = CanonicalZipReader.open(
                channel, Files.size(file), LIMITS
        )) {
            try (FileChannel mutator = FileChannel.open(file, StandardOpenOption.WRITE)) {
                mutator.position(30L + "a.bin".length());
                mutator.write(ByteBuffer.wrap(new byte[]{9}));
                mutator.force(true);
            }

            assertThrows(ContainerValidationException.class,
                    () -> archive.entrySha256(path("a.bin")));
            assertThrows(ContainerValidationException.class, archive::containerSha256);
        }
    }

    @Test
    void byteArraySizeGateRunsBeforeTheReaderSnapshotsInput() {
        com.sun.management.ThreadMXBean allocations =
                (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        assertTrue(allocations.isThreadAllocatedMemorySupported());
        allocations.setThreadAllocatedMemoryEnabled(true);
        byte[] oversized = new byte[2 * 1024 * 1024];
        ContainerLimits tiny = new ContainerLimits(1, 1, 1, 1);
        long threadId = Thread.currentThread().getId();
        long before = allocations.getThreadAllocatedBytes(threadId);

        assertThrows(ContainerValidationException.class,
                () -> CanonicalZipReader.read(oversized, tiny));

        long allocated = allocations.getThreadAllocatedBytes(threadId) - before;
        assertTrue(allocated < oversized.length / 2L,
                "Rejected input allocated " + allocated + " bytes before its size gate");
    }

    @Test
    void byteArrayConvenienceHasAFixedPreSnapshotMemoryLimit() {
        com.sun.management.ThreadMXBean allocations =
                (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        allocations.setThreadAllocatedMemoryEnabled(true);
        byte[] oversized = new byte[Math.toIntExact(
                CanonicalZipReader.MAX_IN_MEMORY_CONTAINER_BYTES + 1
        )];
        long threadId = Thread.currentThread().getId();
        long before = allocations.getThreadAllocatedBytes(threadId);

        assertThrows(ContainerValidationException.class, () -> CanonicalZipReader.read(
                oversized, ContainerLimits.sourceHardLimits()
        ));

        long allocated = allocations.getThreadAllocatedBytes(threadId) - before;
        assertTrue(allocated < 1024 * 1024,
                "Oversized in-memory read allocated " + allocated + " bytes");
    }

    @Test
    void rejectsNonCanonicalTraversalAbsoluteAndDuplicatePaths() {
        List<byte[]> invalidPaths = List.of(
                utf8("../x"),
                utf8("a/../x"),
                utf8("/x"),
                utf8("c:/x"),
                utf8("a\\b"),
                utf8("a//b"),
                utf8("a/"),
                utf8("A"),
                new byte[]{(byte) 0xc3, 0x28},
                repeatedPathByte(513)
        );
        for (byte[] invalidPath : invalidPaths) {
            assertRejected(rawStoredZip(List.of(new RawEntry(invalidPath, new byte[]{1}))), LIMITS);
        }

        assertRejected(rawStoredZip(List.of(
                raw("a.bin", 1),
                raw("a.bin", 2)
        )), LIMITS);
        assertRejected(rawStoredZip(List.of(
                raw("b.bin", 1),
                raw("a.bin", 2)
        )), LIMITS);
    }

    @Test
    void rejectsEveryTruncatedPrefixOfAnOtherwiseCanonicalArchive() {
        byte[] valid = rawStoredZip(List.of(raw("a", 1), raw("b", 2)));

        for (int length = 0; length < valid.length; length++) {
            assertRejected(Arrays.copyOf(valid, length), LIMITS);
        }
    }

    @Test
    void rejectsEveryNonCanonicalZipMetadataField() {
        byte[] valid = rawStoredZip(List.of(raw("a", 1)));
        int central = centralOffset(valid);
        int eocd = valid.length - 22;
        List<byte[]> invalid = new ArrayList<>();

        invalid.add(withShort(valid, 4, 21));
        invalid.add(withShort(valid, 6, 0));
        invalid.add(withShort(valid, 6, 0x0808));
        invalid.add(withShort(valid, 8, 8));
        invalid.add(withShort(valid, 10, 1));
        invalid.add(withShort(valid, 12, 0));
        invalid.add(withShort(valid, 28, 1));
        invalid.add(withShort(valid, central + 4, 0x0314));
        invalid.add(withShort(valid, central + 6, 21));
        invalid.add(withShort(valid, central + 8, 0));
        invalid.add(withShort(valid, central + 8, 0x0808));
        invalid.add(withShort(valid, central + 10, 8));
        invalid.add(withShort(valid, central + 12, 1));
        invalid.add(withShort(valid, central + 14, 0));
        invalid.add(withShort(valid, central + 30, 1));
        invalid.add(withShort(valid, central + 32, 1));
        invalid.add(withShort(valid, central + 34, 1));
        invalid.add(withShort(valid, central + 36, 1));
        invalid.add(withInt(valid, central + 38, 0));
        invalid.add(withInt(valid, central + 42, 1));
        invalid.add(withShort(valid, eocd + 4, 1));
        invalid.add(withShort(valid, eocd + 6, 1));
        invalid.add(withShort(valid, eocd + 8, 0));
        invalid.add(withShort(valid, eocd + 20, 1));
        invalid.add(prepend(valid, (byte) 0));
        invalid.add(append(valid, (byte) 0));
        invalid.add(withInt(valid, central + 20, -1));

        for (byte[] bytes : invalid) {
            assertRejected(bytes, LIMITS);
        }
    }

    @Test
    void rejectsCrcLocalCentralAndDeclaredSizeMismatches() {
        byte[] valid = rawStoredZip(List.of(raw("a", 1)));
        int central = centralOffset(valid);

        byte[] badDataCrc = valid.clone();
        badDataCrc[31] ^= 1;
        byte[] badLocalCrc = valid.clone();
        badLocalCrc[14] ^= 1;
        byte[] differentLocalName = valid.clone();
        differentLocalName[30] = 'b';

        for (byte[] bytes : List.of(
                badDataCrc,
                badLocalCrc,
                differentLocalName,
                withInt(valid, central + 24, 2),
                withInt(valid, 22, 2)
        )) {
            assertRejected(bytes, LIMITS);
        }
    }

    @Test
    void appliesCountEntryTotalAndCompressionRatioLimitsBeforeCopyingEntries() {
        byte[] twoEntries = rawStoredZip(List.of(raw("a", 1), raw("b", 2)));
        assertRejected(twoEntries, new ContainerLimits(1, 1024, 4096, 1));
        assertRejected(twoEntries, new ContainerLimits(16, 1024, 1, 1));
        assertRejected(rawStoredZip(List.of(new RawEntry(utf8("a"), new byte[]{1, 2}))),
                new ContainerLimits(16, 1, 4096, 1));

        byte[] ratio = rawStoredZip(List.of(raw("a", 1)));
        int central = centralOffset(ratio);
        writeShort(ratio, 8, 8);
        writeShort(ratio, central + 10, 8);
        writeInt(ratio, 22, 100);
        writeInt(ratio, central + 24, 100);

        ContainerValidationException exception = assertThrows(
                ContainerValidationException.class,
                () -> CanonicalZipReader.read(ratio, new ContainerLimits(16, 1024, 4096, 10))
        );
        assertEquals("ZIP entry compression ratio exceeds the limit", exception.getMessage());
    }

    @Test
    void verifiesManifestEntrySetLengthsAndHashesWhileExcludingTheManifestItself() {
        byte[] manifest = "{}".getBytes(StandardCharsets.UTF_8);
        byte[] tile = {1, 2, 3};
        CanonicalZipReader.Archive archive = CanonicalZipReader.read(
                CanonicalZipWriter.write(Map.of(
                        path("manifest.json"), manifest,
                        path("tiles/0.png"), tile
                ), LIMITS),
                LIMITS
        );
        CanonicalZipReader.ExpectedEntry expected = new CanonicalZipReader.ExpectedEntry(
                path("tiles/0.png"), tile.length, sha256(tile)
        );

        archive.verifyManifestEntries(path("manifest.json"), List.of(expected));

        assertThrows(ContainerValidationException.class, () -> archive.verifyManifestEntries(
                path("manifest.json"), List.of(new CanonicalZipReader.ExpectedEntry(
                        expected.path(), expected.byteLength() + 1, expected.sha256()
                ))
        ));
        assertThrows(ContainerValidationException.class, () -> archive.verifyManifestEntries(
                path("manifest.json"), List.of(new CanonicalZipReader.ExpectedEntry(
                        expected.path(), expected.byteLength(), Sha256.parse("0".repeat(64))
                ))
        ));
        assertThrows(ContainerValidationException.class,
                () -> archive.verifyManifestEntries(path("manifest.json"), List.of()));
        assertThrows(ContainerValidationException.class, () -> archive.verifyManifestEntries(
                path("manifest.json"), List.of(expected, expected)
        ));
        assertThrows(ContainerValidationException.class, () -> archive.verifyManifestEntries(
                path("manifest.json"), List.of(new CanonicalZipReader.ExpectedEntry(
                        path("manifest.json"), manifest.length, sha256(manifest)
                ))
        ));

        List<CanonicalZipReader.ExpectedEntry> oversizedLazyList = new AbstractList<>() {
            @Override
            public CanonicalZipReader.ExpectedEntry get(int index) {
                throw new AssertionError("Inventory count must be rejected before element access");
            }

            @Override
            public int size() {
                return Integer.MAX_VALUE;
            }
        };
        assertThrows(ContainerValidationException.class, () -> archive.verifyManifestEntries(
                path("manifest.json"), oversizedLazyList
        ));
    }

    @Test
    void manifestVerificationReusesOnePayloadBufferAcrossLargeInventories() {
        int entryCount = 1_000;
        ContainerLimits limits = new ContainerLimits(2_000, 1024, 4096, 1);
        List<CanonicalZipWriter.Entry> entries = new ArrayList<>();
        List<CanonicalZipReader.ExpectedEntry> expected = new ArrayList<>();
        entries.add(new CanonicalZipWriter.Entry(path("manifest.json"), utf8("{}")));
        Sha256 emptyHash = sha256(new byte[0]);
        for (int index = 0; index < entryCount; index++) {
            ContainerPath entryPath = path("tiles/" + index + ".bin");
            entries.add(new CanonicalZipWriter.Entry(entryPath, new byte[0]));
            expected.add(new CanonicalZipReader.ExpectedEntry(entryPath, 0, emptyHash));
        }
        CanonicalZipReader.Archive archive = CanonicalZipReader.read(
                CanonicalZipWriter.write(entries, limits), limits
        );
        com.sun.management.ThreadMXBean allocations =
                (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        allocations.setThreadAllocatedMemoryEnabled(true);
        long threadId = Thread.currentThread().getId();
        long before = allocations.getThreadAllocatedBytes(threadId);

        archive.verifyManifestEntries(path("manifest.json"), expected);

        long allocated = allocations.getThreadAllocatedBytes(threadId) - before;
        assertTrue(allocated < 2L * 1024 * 1024,
                "Inventory verification allocated " + allocated + " bytes");
    }

    private static void assertRejected(byte[] zip, ContainerLimits limits) {
        assertThrows(ContainerValidationException.class, () -> CanonicalZipReader.read(zip, limits));
    }

    private static ContainerPath path(String value) {
        return ContainerPath.parse(value);
    }

    private static RawEntry raw(String path, int value) {
        return new RawEntry(utf8(path), new byte[]{(byte) value});
    }

    private static byte[] utf8(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] repeatedPathByte(int length) {
        byte[] value = new byte[length];
        Arrays.fill(value, (byte) 'a');
        return value;
    }

    private static byte[] rawStoredZip(List<RawEntry> entries) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        List<Integer> localOffsets = new ArrayList<>();
        List<Long> crcs = new ArrayList<>();
        for (RawEntry entry : entries) {
            localOffsets.add(output.size());
            CRC32 crc = new CRC32();
            crc.update(entry.data());
            crcs.add(crc.getValue());
            writeInt(output, 0x04034b50);
            writeShort(output, 20);
            writeShort(output, 0x0800);
            writeShort(output, 0);
            writeShort(output, 0);
            writeShort(output, 0x0021);
            writeInt(output, crc.getValue());
            writeInt(output, entry.data().length);
            writeInt(output, entry.data().length);
            writeShort(output, entry.path().length);
            writeShort(output, 0);
            output.writeBytes(entry.path());
            output.writeBytes(entry.data());
        }

        int centralOffset = output.size();
        for (int index = 0; index < entries.size(); index++) {
            RawEntry entry = entries.get(index);
            writeInt(output, 0x02014b50);
            writeShort(output, 0x031e);
            writeShort(output, 20);
            writeShort(output, 0x0800);
            writeShort(output, 0);
            writeShort(output, 0);
            writeShort(output, 0x0021);
            writeInt(output, crcs.get(index));
            writeInt(output, entry.data().length);
            writeInt(output, entry.data().length);
            writeShort(output, entry.path().length);
            writeShort(output, 0);
            writeShort(output, 0);
            writeShort(output, 0);
            writeShort(output, 0);
            writeInt(output, 0x81a40000L);
            writeInt(output, localOffsets.get(index));
            output.writeBytes(entry.path());
        }
        int centralSize = output.size() - centralOffset;
        writeInt(output, 0x06054b50);
        writeShort(output, 0);
        writeShort(output, 0);
        writeShort(output, entries.size());
        writeShort(output, entries.size());
        writeInt(output, centralSize);
        writeInt(output, centralOffset);
        writeShort(output, 0);
        return output.toByteArray();
    }

    private static int centralOffset(byte[] zip) {
        return readInt(zip, zip.length - 22 + 16);
    }

    private static byte[] withShort(byte[] value, int offset, int number) {
        byte[] copy = value.clone();
        writeShort(copy, offset, number);
        return copy;
    }

    private static byte[] withInt(byte[] value, int offset, int number) {
        byte[] copy = value.clone();
        writeInt(copy, offset, number);
        return copy;
    }

    private static byte[] prepend(byte[] value, byte prefix) {
        byte[] copy = new byte[value.length + 1];
        copy[0] = prefix;
        System.arraycopy(value, 0, copy, 1, value.length);
        return copy;
    }

    private static byte[] append(byte[] value, byte suffix) {
        byte[] copy = Arrays.copyOf(value, value.length + 1);
        copy[copy.length - 1] = suffix;
        return copy;
    }

    private static Sha256 sha256(byte[] value) {
        try {
            return Sha256.parse(HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value)
            ));
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

    private static void writeShort(ByteArrayOutputStream output, int value) {
        output.write(value);
        output.write(value >>> 8);
    }

    private static void writeInt(ByteArrayOutputStream output, long value) {
        output.write((int) value);
        output.write((int) (value >>> 8));
        output.write((int) (value >>> 16));
        output.write((int) (value >>> 24));
    }

    private static void writeShort(byte[] output, int offset, int value) {
        output[offset] = (byte) value;
        output[offset + 1] = (byte) (value >>> 8);
    }

    private static void writeInt(byte[] output, int offset, int value) {
        output[offset] = (byte) value;
        output[offset + 1] = (byte) (value >>> 8);
        output[offset + 2] = (byte) (value >>> 16);
        output[offset + 3] = (byte) (value >>> 24);
    }

    private record RawEntry(byte[] path, byte[] data) {
    }
}
