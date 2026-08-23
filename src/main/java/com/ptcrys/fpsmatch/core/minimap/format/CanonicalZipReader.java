package com.ptcrys.fpsmatch.core.minimap.format;

import com.ptcrys.fpsmatch.core.minimap.contract.MinimapHardLimits;
import com.ptcrys.fpsmatch.core.minimap.model.ContainerPath;
import com.ptcrys.fpsmatch.core.minimap.model.Sha256;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.zip.CRC32;

public final class CanonicalZipReader {
    static final long MAX_IN_MEMORY_CONTAINER_BYTES = 64L * 1024 * 1024;

    private static final long LOCAL_SIGNATURE = 0x04034b50L;
    private static final long CENTRAL_SIGNATURE = 0x02014b50L;
    private static final long EOCD_SIGNATURE = 0x06054b50L;
    private static final int VERSION_NEEDED = 20;
    private static final int VERSION_MADE_BY = 0x031e;
    private static final int UTF8_FLAG = 0x0800;
    private static final int STORED_METHOD = 0;
    private static final int DOS_TIME = 0;
    private static final int DOS_DATE = 0x0021;
    private static final long EXTERNAL_ATTRIBUTES = 0x81a40000L;
    private static final int LOCAL_HEADER_BYTES = 30;
    private static final int CENTRAL_HEADER_BYTES = 46;
    private static final int EOCD_BYTES = 22;

    private CanonicalZipReader() {
    }

    public static Archive read(byte[] container, ContainerLimits limits) {
        Objects.requireNonNull(container, "container");
        Objects.requireNonNull(limits, "limits");
        if (container.length < EOCD_BYTES
                || container.length > limits.maxCanonicalContainerBytes()
                || container.length > MAX_IN_MEMORY_CONTAINER_BYTES) {
            throw new ContainerValidationException("ZIP container size is outside the limit");
        }
        return parse(new ByteArraySource(container.clone()), limits);
    }

    public static Archive open(
            SeekableByteChannel channel,
            long containerSize,
            ContainerLimits limits
    ) {
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(limits, "limits");
        ChannelSource source = null;
        try {
            if (containerSize < 0 || channel.size() != containerSize) {
                throw new ContainerValidationException("ZIP channel size does not match the declared size");
            }
            source = new ChannelSource(channel, containerSize);
            return parse(source, limits);
        } catch (IOException exception) {
            closeAfterFailure(source, channel, exception);
            throw new ContainerValidationException("Failed to read ZIP container", exception);
        } catch (RuntimeException exception) {
            closeAfterFailure(source, channel, exception);
            throw exception;
        }
    }

    private static Archive parse(RandomAccessSource source, ContainerLimits limits) {
        try {
            long size = source.size();
            if (size < EOCD_BYTES || size > limits.maxCanonicalContainerBytes()) {
                throw new ContainerValidationException("ZIP container size is outside the limit");
            }
            Sha256 containerSha256 = digestSource(source, size);
            long eocdOffset = size - EOCD_BYTES;
            byte[] eocd = readBytes(source, eocdOffset, EOCD_BYTES);
            if (u32(eocd, 0) != EOCD_SIGNATURE) {
                throw new ContainerValidationException("ZIP EOCD is missing from the exact end of input");
            }
            int diskNumber = u16(eocd, 4);
            int centralDisk = u16(eocd, 6);
            int entriesOnDisk = u16(eocd, 8);
            int entryCount = u16(eocd, 10);
            long centralSize = u32(eocd, 12);
            long centralOffset = u32(eocd, 16);
            int commentLength = u16(eocd, 20);
            if (diskNumber != 0 || centralDisk != 0 || entriesOnDisk != entryCount) {
                throw new ContainerValidationException("ZIP must use one disk with matching entry counts");
            }
            if (entryCount == 0xffff || centralSize == 0xffffffffL
                    || centralOffset == 0xffffffffL) {
                throw new ContainerValidationException("ZIP64 sentinels are forbidden");
            }
            if (entryCount > limits.maxEntries()) {
                throw new ContainerValidationException("ZIP entry count exceeds the limit");
            }
            if (commentLength != 0) {
                throw new ContainerValidationException("ZIP container comments are forbidden");
            }
            if (checkedAdd(centralOffset, centralSize, "ZIP central directory bounds overflow")
                    != eocdOffset) {
                throw new ContainerValidationException(
                        "ZIP central directory must end immediately before EOCD"
                );
            }
            if ((long) entryCount * (CENTRAL_HEADER_BYTES + 1L) > centralSize) {
                throw new ContainerValidationException("ZIP central directory is too small for its entry count");
            }

            List<CentralEntry> centralEntries = new ArrayList<>(entryCount);
            long centralCursor = centralOffset;
            byte[] previousPath = null;
            long totalExpandedBytes = 0;
            for (int index = 0; index < entryCount; index++) {
                requireRange(centralCursor, CENTRAL_HEADER_BYTES,
                        checkedAdd(centralOffset, centralSize, "ZIP central directory bounds overflow"),
                        "ZIP central header is truncated");
                byte[] header = readBytes(source, centralCursor, CENTRAL_HEADER_BYTES);
                if (u32(header, 0) != CENTRAL_SIGNATURE) {
                    throw new ContainerValidationException("ZIP central header signature is invalid");
                }
                int madeBy = u16(header, 4);
                int needed = u16(header, 6);
                int flags = u16(header, 8);
                int method = u16(header, 10);
                int time = u16(header, 12);
                int date = u16(header, 14);
                long crc = u32(header, 16);
                long compressedSize = u32(header, 20);
                long expandedSize = u32(header, 24);
                int nameLength = u16(header, 28);
                int extraLength = u16(header, 30);
                int entryCommentLength = u16(header, 32);
                int startDisk = u16(header, 34);
                int internalAttributes = u16(header, 36);
                long externalAttributes = u32(header, 38);
                long localOffset = u32(header, 42);

                validateSizes(compressedSize, expandedSize, limits);
                if (madeBy != VERSION_MADE_BY) {
                    throw new ContainerValidationException("ZIP version-made-by is not canonical Unix 3.0");
                }
                if (needed != VERSION_NEEDED || flags != UTF8_FLAG || method != STORED_METHOD
                        || time != DOS_TIME || date != DOS_DATE) {
                    throw new ContainerValidationException("ZIP central entry metadata is not canonical");
                }
                if (compressedSize != expandedSize) {
                    throw new ContainerValidationException("ZIP STORED entry sizes must match");
                }
                if (nameLength <= 0 || nameLength > MinimapHardLimits.MAX_ENTRY_PATH_UTF8_BYTES) {
                    throw new ContainerValidationException("ZIP entry path length is outside the limit");
                }
                if (extraLength != 0 || entryCommentLength != 0 || startDisk != 0
                        || internalAttributes != 0 || externalAttributes != EXTERNAL_ATTRIBUTES) {
                    throw new ContainerValidationException("ZIP central entry attributes are not canonical");
                }
                if (localOffset == 0xffffffffL) {
                    throw new ContainerValidationException("ZIP64 local offsets are forbidden");
                }

                long nameOffset = checkedAdd(centralCursor, CENTRAL_HEADER_BYTES,
                        "ZIP central path offset overflow");
                requireRange(nameOffset, nameLength,
                        checkedAdd(centralOffset, centralSize, "ZIP central directory bounds overflow"),
                        "ZIP central path is truncated");
                byte[] rawPath = readBytes(source, nameOffset, nameLength);
                ContainerPath path = parsePath(rawPath);
                if (previousPath != null) {
                    int comparison = compareUnsigned(previousPath, rawPath);
                    if (comparison == 0) {
                        throw new ContainerValidationException("ZIP contains a duplicate canonical path");
                    }
                    if (comparison > 0) {
                        throw new ContainerValidationException("ZIP entries are not in canonical path order");
                    }
                }
                previousPath = rawPath;
                totalExpandedBytes = checkedAdd(totalExpandedBytes, expandedSize,
                        "ZIP expanded byte count overflow");
                if (totalExpandedBytes > limits.maxExpandedBytes()) {
                    throw new ContainerValidationException("ZIP expanded bytes exceed the limit");
                }
                centralEntries.add(new CentralEntry(
                        path, rawPath, crc, compressedSize, expandedSize, localOffset
                ));
                centralCursor = checkedAdd(nameOffset, nameLength, "ZIP central cursor overflow");
            }
            if (centralCursor != checkedAdd(centralOffset, centralSize,
                    "ZIP central directory bounds overflow")) {
                throw new ContainerValidationException("ZIP central directory contains hidden bytes");
            }

            LinkedHashMap<ContainerPath, EntryMetadata> entries = new LinkedHashMap<>();
            long localCursor = 0;
            byte[] buffer = new byte[8192];
            for (CentralEntry central : centralEntries) {
                if (central.localOffset() != localCursor) {
                    throw new ContainerValidationException("ZIP local entries are not contiguous and ordered");
                }
                requireRange(localCursor, LOCAL_HEADER_BYTES, centralOffset,
                        "ZIP local header is truncated");
                byte[] header = readBytes(source, localCursor, LOCAL_HEADER_BYTES);
                if (u32(header, 0) != LOCAL_SIGNATURE) {
                    throw new ContainerValidationException("ZIP local header signature is invalid");
                }
                int nameLength = u16(header, 26);
                int extraLength = u16(header, 28);
                if (u16(header, 4) != VERSION_NEEDED
                        || u16(header, 6) != UTF8_FLAG
                        || u16(header, 8) != STORED_METHOD
                        || u16(header, 10) != DOS_TIME
                        || u16(header, 12) != DOS_DATE
                        || u32(header, 14) != central.crc32()
                        || u32(header, 18) != central.compressedSize()
                        || u32(header, 22) != central.expandedSize()
                        || nameLength != central.rawPath().length
                        || extraLength != 0) {
                    throw new ContainerValidationException(
                            "ZIP local header does not match canonical central metadata"
                    );
                }
                long nameOffset = checkedAdd(localCursor, LOCAL_HEADER_BYTES,
                        "ZIP local path offset overflow");
                requireRange(nameOffset, nameLength, centralOffset, "ZIP local path is truncated");
                byte[] rawPath = readBytes(source, nameOffset, nameLength);
                if (!Arrays.equals(rawPath, central.rawPath())) {
                    throw new ContainerValidationException("ZIP local and central paths do not match");
                }
                long dataOffset = checkedAdd(nameOffset, nameLength, "ZIP data offset overflow");
                requireRange(dataOffset, central.compressedSize(), centralOffset,
                        "ZIP entry payload is truncated");
                PayloadDigest digest = digestPayload(
                        source, dataOffset, central.compressedSize(), buffer
                );
                if (digest.crc32() != central.crc32()) {
                    throw new ContainerValidationException("ZIP entry CRC32 does not match");
                }
                entries.put(central.path(), new EntryMetadata(
                        dataOffset, central.expandedSize(), digest.crc32(), digest.sha256()
                ));
                localCursor = checkedAdd(dataOffset, central.compressedSize(),
                        "ZIP local cursor overflow");
            }
            if (localCursor != centralOffset) {
                throw new ContainerValidationException("ZIP contains hidden bytes before the central directory");
            }
            Sha256 finalContainerSha256 = digestSource(source, size);
            if (!containerSha256.equals(finalContainerSha256)) {
                throw new ContainerValidationException(
                        "ZIP container changed while it was being validated"
                );
            }
            source.verifyStable();
            return new Archive(source, entries, containerSha256);
        } catch (IOException exception) {
            try {
                source.close();
            } catch (IOException closeException) {
                exception.addSuppressed(closeException);
            }
            throw new ContainerValidationException("Failed to read ZIP container", exception);
        } catch (RuntimeException exception) {
            try {
                source.close();
            } catch (IOException closeException) {
                exception.addSuppressed(closeException);
            }
            throw exception;
        }
    }

    private static void validateSizes(long compressed, long expanded, ContainerLimits limits) {
        if (compressed == 0xffffffffL || expanded == 0xffffffffL) {
            throw new ContainerValidationException("ZIP64 entry sizes are forbidden");
        }
        if (compressed > limits.maxEntryBytes() || expanded > limits.maxEntryBytes()) {
            throw new ContainerValidationException("ZIP entry bytes exceed the limit");
        }
        if (expanded > 0 && (compressed == 0
                || productLessThan(compressed, limits.maxCompressionRatio(), expanded))) {
            throw new ContainerValidationException("ZIP entry compression ratio exceeds the limit");
        }
    }

    private static boolean productLessThan(long left, long right, long target) {
        if (left > Long.MAX_VALUE / right) {
            return false;
        }
        return left * right < target;
    }

    private static PayloadDigest digestPayload(
            RandomAccessSource source,
            long offset,
            long length,
            byte[] buffer
    ) throws IOException {
        CRC32 crc = new CRC32();
        MessageDigest sha = sha256Digest();
        long position = offset;
        long remaining = length;
        while (remaining > 0) {
            int count = (int) Math.min(buffer.length, remaining);
            source.readFully(position, buffer, 0, count);
            crc.update(buffer, 0, count);
            sha.update(buffer, 0, count);
            position += count;
            remaining -= count;
        }
        return new PayloadDigest(
                crc.getValue(),
                Sha256.parse(HexFormat.of().formatHex(sha.digest()))
        );
    }

    private static PayloadDigest digestBytes(byte[] value) {
        CRC32 crc = new CRC32();
        crc.update(value);
        MessageDigest sha = sha256Digest();
        sha.update(value);
        return new PayloadDigest(
                crc.getValue(),
                Sha256.parse(HexFormat.of().formatHex(sha.digest()))
        );
    }

    private static Sha256 digestSource(RandomAccessSource source, long length) throws IOException {
        MessageDigest digest = sha256Digest();
        byte[] buffer = new byte[8192];
        long position = 0;
        while (position < length) {
            int count = (int) Math.min(buffer.length, length - position);
            source.readFully(position, buffer, 0, count);
            digest.update(buffer, 0, count);
            position += count;
        }
        return Sha256.parse(HexFormat.of().formatHex(digest.digest()));
    }

    private static ContainerPath parsePath(byte[] rawPath) {
        try {
            String decoded = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(rawPath))
                    .toString();
            return ContainerPath.parse(decoded);
        } catch (CharacterCodingException | IllegalArgumentException exception) {
            throw new ContainerValidationException("ZIP entry path is not canonical", exception);
        }
    }

    private static int compareUnsigned(byte[] left, byte[] right) {
        int common = Math.min(left.length, right.length);
        for (int index = 0; index < common; index++) {
            int comparison = Integer.compare(left[index] & 0xff, right[index] & 0xff);
            if (comparison != 0) {
                return comparison;
            }
        }
        return Integer.compare(left.length, right.length);
    }

    private static byte[] readBytes(RandomAccessSource source, long offset, int length)
            throws IOException {
        byte[] value = new byte[length];
        source.readFully(offset, value, 0, length);
        return value;
    }

    private static void requireRange(long offset, long length, long limit, String message) {
        if (offset < 0 || length < 0 || offset > limit
                || length > limit - offset) {
            throw new ContainerValidationException(message);
        }
    }

    private static long checkedAdd(long left, long right, String message) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException exception) {
            throw new ContainerValidationException(message, exception);
        }
    }

    private static int u16(byte[] value, int offset) {
        return value[offset] & 0xff | (value[offset + 1] & 0xff) << 8;
    }

    private static long u32(byte[] value, int offset) {
        return Integer.toUnsignedLong(
                value[offset] & 0xff
                        | (value[offset + 1] & 0xff) << 8
                        | (value[offset + 2] & 0xff) << 16
                        | (value[offset + 3] & 0xff) << 24
        );
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void closeAfterFailure(
            ChannelSource source,
            SeekableByteChannel channel,
            Throwable failure
    ) {
        try {
            if (source != null) {
                source.close();
            } else {
                channel.close();
            }
        } catch (IOException closeException) {
            failure.addSuppressed(closeException);
        }
    }

    public static final class Archive implements Closeable {
        private final RandomAccessSource source;
        private final LinkedHashMap<ContainerPath, EntryMetadata> entries;
        private final Set<ContainerPath> paths;
        private final Sha256 containerSha256;
        private boolean closed;

        private Archive(
                RandomAccessSource source,
                LinkedHashMap<ContainerPath, EntryMetadata> entries,
                Sha256 containerSha256
        ) {
            this.source = source;
            this.entries = new LinkedHashMap<>(entries);
            this.paths = Collections.unmodifiableSet(new LinkedHashSet<>(entries.keySet()));
            this.containerSha256 = Objects.requireNonNull(containerSha256, "containerSha256");
        }

        public int size() {
            ensureOpen();
            ensureSourceStable();
            return entries.size();
        }

        public Set<ContainerPath> paths() {
            ensureOpen();
            ensureSourceStable();
            return paths;
        }

        public long entryLength(ContainerPath path) {
            Objects.requireNonNull(path, "path");
            ensureOpen();
            ensureSourceStable();
            return requireEntry(path).length();
        }

        public Sha256 entrySha256(ContainerPath path) {
            Objects.requireNonNull(path, "path");
            ensureOpen();
            EntryMetadata entry = requireEntry(path);
            verifyCurrentEntry(entry, new byte[8192]);
            return entry.sha256();
        }

        public Sha256 containerSha256() {
            verifyCurrentContainer();
            return containerSha256;
        }

        public void verifyCurrentContainer() {
            ensureOpen();
            ensureSourceStable();
            try {
                Sha256 current = digestSource(source, source.size());
                source.verifyStable();
                if (!containerSha256.equals(current)) {
                    throw new ContainerValidationException(
                            "ZIP container changed after validation"
                    );
                }
            } catch (IOException exception) {
                throw new ContainerValidationException(
                        "Failed to revalidate ZIP container", exception
                );
            }
        }

        public byte[] entryBytes(ContainerPath path) {
            Objects.requireNonNull(path, "path");
            ensureOpen();
            EntryMetadata entry = requireEntry(path);
            int length = Math.toIntExact(entry.length());
            byte[] value = new byte[length];
            try {
                source.readFully(entry.dataOffset(), value, 0, length);
            } catch (IOException exception) {
                throw new ContainerValidationException("Failed to read validated ZIP entry", exception);
            }
            verifyDigest(entry, digestBytes(value));
            return value;
        }

        public Map<ContainerPath, byte[]> entries() {
            ensureOpen();
            long totalBytes = 0;
            for (EntryMetadata entry : entries.values()) {
                totalBytes = checkedAdd(totalBytes, entry.length(),
                        "ZIP in-memory entry byte count overflow");
                if (totalBytes > MAX_IN_MEMORY_CONTAINER_BYTES) {
                    throw new ContainerValidationException(
                            "ZIP entries exceed the aggregate in-memory result limit"
                    );
                }
            }
            LinkedHashMap<ContainerPath, byte[]> copy = new LinkedHashMap<>();
            for (ContainerPath path : entries.keySet()) {
                copy.put(path, entryBytes(path));
            }
            return Collections.unmodifiableMap(copy);
        }

        public InputStream openBounded(ContainerPath path) {
            Objects.requireNonNull(path, "path");
            ensureOpen();
            ensureSourceStable();
            return new VerifiedEntryInputStream(this, requireEntry(path));
        }

        public void verifyManifestEntries(
                ContainerPath manifestPath,
                List<ExpectedEntry> expectedEntries
        ) {
            Objects.requireNonNull(manifestPath, "manifestPath");
            Objects.requireNonNull(expectedEntries, "expectedEntries");
            ensureOpen();
            if (!entries.containsKey(manifestPath)) {
                throw new ContainerValidationException("ZIP manifest entry is missing");
            }
            if (expectedEntries.size() != entries.size() - 1) {
                throw new ContainerValidationException(
                        "ZIP manifest entry count does not match the container"
                );
            }
            byte[] validationBuffer = new byte[8192];
            verifyCurrentEntry(entries.get(manifestPath), validationBuffer);
            HashSet<ContainerPath> declared = new HashSet<>();
            for (ExpectedEntry expected : expectedEntries) {
                Objects.requireNonNull(expected, "expected entry");
                if (expected.path().equals(manifestPath)) {
                    throw new ContainerValidationException("ZIP manifest must exclude itself");
                }
                if (!declared.add(expected.path())) {
                    throw new ContainerValidationException("ZIP manifest declares a duplicate path");
                }
                EntryMetadata actual = entries.get(expected.path());
                if (actual == null) {
                    throw new ContainerValidationException("ZIP manifest declares a missing entry");
                }
                verifyCurrentEntry(actual, validationBuffer);
                if (actual.length() != expected.byteLength()) {
                    throw new ContainerValidationException("ZIP manifest entry length does not match");
                }
                if (!actual.sha256().equals(expected.sha256())) {
                    throw new ContainerValidationException("ZIP manifest entry SHA-256 does not match");
                }
            }
            if (entries.size() != declared.size() + 1) {
                throw new ContainerValidationException("ZIP contains an entry absent from the manifest");
            }
        }

        @Override
        public synchronized void close() throws IOException {
            if (!closed) {
                closed = true;
                source.close();
            }
        }

        private synchronized void readEntryRange(
                EntryMetadata entry,
                long relativeOffset,
                byte[] output,
                int offset,
                int length
        ) throws IOException {
            ensureEntryStreamOpen();
            source.readFully(entry.dataOffset() + relativeOffset, output, offset, length);
        }

        private synchronized void verifyEntryStreamSourceStable() throws IOException {
            ensureEntryStreamOpen();
            source.verifyStable();
        }

        private synchronized void ensureEntryStreamOpen() throws IOException {
            if (closed) {
                throw new IOException("ZIP archive is closed");
            }
        }

        private EntryMetadata requireEntry(ContainerPath path) {
            EntryMetadata entry = entries.get(path);
            if (entry == null) {
                throw new ContainerValidationException("ZIP entry does not exist: " + path);
            }
            return entry;
        }

        private void ensureOpen() {
            if (closed) {
                throw new ContainerValidationException("ZIP archive is closed");
            }
        }

        private void ensureSourceStable() {
            try {
                source.verifyStable();
            } catch (IOException exception) {
                throw new ContainerValidationException(
                        "ZIP channel changed after validation", exception
                );
            }
        }

        private void verifyCurrentEntry(EntryMetadata entry, byte[] buffer) {
            try {
                verifyDigest(entry, digestPayload(
                        source, entry.dataOffset(), entry.length(), buffer
                ));
            } catch (IOException exception) {
                throw new ContainerValidationException("Failed to revalidate ZIP entry", exception);
            }
        }

        private static void verifyDigest(EntryMetadata entry, PayloadDigest digest) {
            if (digest.crc32() != entry.crc32() || !digest.sha256().equals(entry.sha256())) {
                throw new ContainerValidationException(
                        "ZIP entry changed after container validation"
                );
            }
        }
    }

    public record ExpectedEntry(ContainerPath path, long byteLength, Sha256 sha256) {
        public ExpectedEntry {
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(sha256, "sha256");
            if (byteLength < 0 || byteLength > MinimapHardLimits.MAX_ZIP_ENTRY_BYTES) {
                throw new IllegalArgumentException("Expected ZIP entry length exceeds the hard limit");
            }
        }
    }

    private static final class VerifiedEntryInputStream extends InputStream {
        private static final int DRAIN_BUFFER_BYTES = 8192;

        private final Archive owner;
        private final EntryMetadata entry;
        private final byte[] oneByte = new byte[1];
        private final CRC32 crc32 = new CRC32();
        private final MessageDigest sha256 = sha256Digest();
        private long position;
        private boolean verified;
        private boolean failed;
        private boolean closed;

        private VerifiedEntryInputStream(Archive owner, EntryMetadata entry) {
            this.owner = owner;
            this.entry = entry;
            if (entry.length() == 0) {
                Archive.verifyDigest(entry, new PayloadDigest(
                        crc32.getValue(),
                        Sha256.parse(HexFormat.of().formatHex(sha256.digest()))
                ));
                verified = true;
            }
        }

        @Override
        public synchronized int read() throws IOException {
            int count = read(oneByte, 0, 1);
            return count < 0 ? -1 : oneByte[0] & 0xff;
        }

        @Override
        public synchronized int read(byte[] output, int offset, int length) throws IOException {
            Objects.checkFromIndexSize(offset, length, output.length);
            ensureOpen();
            if (length == 0) {
                return 0;
            }
            if (position >= entry.length()) {
                return -1;
            }
            int count = (int) Math.min(length, entry.length() - position);
            owner.readEntryRange(entry, position, output, offset, count);
            crc32.update(output, offset, count);
            sha256.update(output, offset, count);
            position += count;
            if (position == entry.length()) {
                verifyCompleteEntry();
            }
            return count;
        }

        @Override
        public synchronized int available() throws IOException {
            ensureOpen();
            return (int) Math.min(Integer.MAX_VALUE, entry.length() - position);
        }

        @Override
        public synchronized void close() throws IOException {
            if (closed) {
                return;
            }
            try {
                if (!verified && !failed) {
                    byte[] discard = new byte[DRAIN_BUFFER_BYTES];
                    while (read(discard, 0, discard.length) >= 0) {
                        // Drain through the authenticated read path.
                    }
                }
            } finally {
                closed = true;
            }
        }

        private void ensureOpen() throws IOException {
            if (closed) {
                throw new IOException("ZIP entry stream is closed");
            }
            if (failed) {
                throw new IOException("ZIP entry stream validation failed");
            }
            owner.ensureEntryStreamOpen();
        }

        private void verifyCompleteEntry() throws IOException {
            try {
                owner.verifyEntryStreamSourceStable();
                Archive.verifyDigest(entry, new PayloadDigest(
                        crc32.getValue(),
                        Sha256.parse(HexFormat.of().formatHex(sha256.digest()))
                ));
                verified = true;
            } catch (IOException | RuntimeException failure) {
                failed = true;
                throw failure;
            }
        }
    }

    private interface RandomAccessSource extends Closeable {
        long size();

        void readFully(long position, byte[] output, int offset, int length) throws IOException;

        default void verifyStable() throws IOException {
        }
    }

    private static final class ByteArraySource implements RandomAccessSource {
        private final byte[] value;

        private ByteArraySource(byte[] value) {
            this.value = value;
        }

        @Override
        public long size() {
            return value.length;
        }

        @Override
        public void readFully(long position, byte[] output, int offset, int length)
                throws IOException {
            if (position < 0 || position > value.length || length > value.length - position) {
                throw new IOException("Read exceeds in-memory ZIP bounds");
            }
            System.arraycopy(value, Math.toIntExact(position), output, offset, length);
        }

        @Override
        public void close() {
        }
    }

    private static final class ChannelSource implements RandomAccessSource {
        private final SeekableByteChannel channel;
        private final long size;

        private ChannelSource(SeekableByteChannel channel, long size) {
            this.channel = channel;
            this.size = size;
        }

        @Override
        public long size() {
            return size;
        }

        @Override
        public synchronized void readFully(
                long position,
                byte[] output,
                int offset,
                int length
        ) throws IOException {
            verifyStable();
            if (position < 0 || position > size || length > size - position) {
                throw new IOException("Read exceeds ZIP channel bounds");
            }
            channel.position(position);
            ByteBuffer target = ByteBuffer.wrap(output, offset, length);
            while (target.hasRemaining()) {
                int count = channel.read(target);
                if (count < 0) {
                    throw new IOException("ZIP channel ended before the validated size");
                }
                if (count == 0) {
                    throw new IOException("ZIP channel read made no progress");
                }
            }
            verifyStable();
        }

        @Override
        public synchronized void verifyStable() throws IOException {
            if (!channel.isOpen()) {
                throw new IOException("ZIP channel is closed");
            }
            if (channel.size() != size) {
                throw new IOException("ZIP channel size changed after validation");
            }
        }

        @Override
        public void close() throws IOException {
            channel.close();
        }
    }

    private record CentralEntry(
            ContainerPath path,
            byte[] rawPath,
            long crc32,
            long compressedSize,
            long expandedSize,
            long localOffset
    ) {
    }

    private record EntryMetadata(long dataOffset, long length, long crc32, Sha256 sha256) {
    }

    private record PayloadDigest(long crc32, Sha256 sha256) {
    }
}
