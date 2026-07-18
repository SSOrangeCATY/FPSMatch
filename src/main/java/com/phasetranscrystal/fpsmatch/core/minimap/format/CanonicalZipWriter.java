package com.phasetranscrystal.fpsmatch.core.minimap.format;

import com.phasetranscrystal.fpsmatch.core.minimap.model.ContainerPath;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.zip.CRC32;

public final class CanonicalZipWriter {
    static final long MAX_IN_MEMORY_CONTAINER_BYTES = 64L * 1024 * 1024;

    private static final long UINT32_ZIP64_SENTINEL = 0xffff_ffffL;
    private static final int LOCAL_FILE_HEADER_SIGNATURE = 0x04034b50;
    private static final int CENTRAL_DIRECTORY_SIGNATURE = 0x02014b50;
    private static final int END_OF_CENTRAL_DIRECTORY_SIGNATURE = 0x06054b50;
    private static final int VERSION_NEEDED = 20;
    private static final int VERSION_MADE_BY = 0x031e;
    private static final int UTF8_FLAG = 0x0800;
    private static final int STORED_METHOD = 0;
    private static final int DOS_TIME = 0;
    private static final int DOS_DATE = 0x0021;
    private static final long EXTERNAL_ATTRIBUTES = 0x81a4_0000L;
    private static final int LOCAL_HEADER_BYTES = 30;
    private static final int CENTRAL_HEADER_BYTES = 46;
    private static final int END_RECORD_BYTES = 22;

    private CanonicalZipWriter() {
    }

    public static byte[] write(List<Entry> entries, ContainerLimits limits) {
        return writeSources(entries, limits);
    }

    public static byte[] writeSources(
            List<? extends EntrySource> entries,
            ContainerLimits limits
    ) {
        try {
            PreparedArchive archive = prepare(
                    entries, limits, MAX_IN_MEMORY_CONTAINER_BYTES
            );
            ByteArrayOutputStream output = new ByteArrayOutputStream(
                    Math.toIntExact(archive.totalBytes())
            );
            writePrepared(output, archive);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new ContainerValidationException("Failed to read a repeatable ZIP entry source", exception);
        }
    }

    public static byte[] write(Map<ContainerPath, byte[]> entries, ContainerLimits limits) {
        return writeSources(mapSources(entries, limits), limits);
    }

    public static void write(
            OutputStream output,
            List<? extends EntrySource> entries,
            ContainerLimits limits
    ) throws IOException {
        Objects.requireNonNull(output, "output");
        writePrepared(output, prepare(entries, limits, Long.MAX_VALUE));
    }

    public static void write(
            OutputStream output,
            Map<ContainerPath, byte[]> entries,
            ContainerLimits limits
    ) throws IOException {
        write(output, mapSources(entries, limits), limits);
    }

    private static List<EntrySource> mapSources(
            Map<ContainerPath, byte[]> entries,
            ContainerLimits limits
    ) {
        Objects.requireNonNull(entries, "entries");
        Objects.requireNonNull(limits, "limits");
        int declaredSize = entries.size();
        if (declaredSize < 0 || declaredSize > limits.maxEntries()) {
            throw new ContainerValidationException("ZIP entry count exceeds the limit");
        }
        List<EntrySource> sources = new ArrayList<>(declaredSize);
        for (Map.Entry<ContainerPath, byte[]> entry : entries.entrySet()) {
            if (sources.size() >= limits.maxEntries()) {
                throw new ContainerValidationException("ZIP entry count exceeds the limit");
            }
            sources.add(new ByteArrayEntrySource(entry.getKey(), entry.getValue()));
        }
        return sources;
    }

    private static PreparedArchive prepare(
            List<? extends EntrySource> entries,
            ContainerLimits limits,
            long resultByteLimit
    ) throws IOException {
        Objects.requireNonNull(entries, "entries");
        Objects.requireNonNull(limits, "limits");
        if (entries.size() > limits.maxEntries()) {
            throw new ContainerValidationException("ZIP entry count exceeds the limit");
        }

        List<DeclaredEntry> declared = new ArrayList<>(entries.size());
        for (EntrySource source : entries) {
            Objects.requireNonNull(source, "entry source");
            ContainerPath path = Objects.requireNonNull(source.path(), "entry path");
            long size = source.size();
            if (size < 0 || size > limits.maxEntryBytes()) {
                throw new ContainerValidationException("ZIP entry bytes exceed the limit");
            }
            declared.add(new DeclaredEntry(
                    source,
                    path,
                    path.value().getBytes(StandardCharsets.UTF_8),
                    size
            ));
        }
        declared.sort(Comparator.comparing(entry -> entry.path().value()));

        String previousPath = null;
        long expandedBytes = 0;
        long localBytes = 0;
        for (int index = 0; index < declared.size(); index++) {
            DeclaredEntry entry = declared.get(index);
            String path = entry.path().value();
            if (path.equals(previousPath)) {
                throw new ContainerValidationException("ZIP entry path is duplicated");
            }
            previousPath = path;
            if (expandedBytes > limits.maxExpandedBytes() - entry.size()) {
                throw new ContainerValidationException("ZIP expanded bytes exceed the limit");
            }
            expandedBytes += entry.size();
            declared.set(index, entry.withLocalOffset(localBytes));
            localBytes = Math.addExact(
                    localBytes,
                    LOCAL_HEADER_BYTES + (long) entry.name().length + entry.size()
            );
        }

        long centralBytes = 0;
        for (DeclaredEntry entry : declared) {
            centralBytes = Math.addExact(
                    centralBytes,
                    CENTRAL_HEADER_BYTES + (long) entry.name().length
            );
        }
        long totalBytes = Math.addExact(
                Math.addExact(localBytes, centralBytes),
                END_RECORD_BYTES
        );
        if (totalBytes > limits.maxCanonicalContainerBytes()) {
            throw new ContainerValidationException("ZIP canonical container bytes exceed the limit");
        }
        if (totalBytes > resultByteLimit) {
            throw new ContainerValidationException(
                    "ZIP container exceeds the in-memory result limit; use the OutputStream API"
            );
        }
        requireClassicUnsignedInt(localBytes,
                "ZIP central directory offset exceeds the classic format");
        requireClassicUnsignedInt(centralBytes,
                "ZIP central directory size exceeds the classic format");

        List<PreparedEntry> prepared = new ArrayList<>(declared.size());
        byte[] buffer = new byte[8192];
        for (DeclaredEntry entry : declared) {
            requireClassicUnsignedInt(entry.localOffset(),
                    "ZIP local header offset exceeds the classic format");
            SourceDigest digest = scan(entry.source(), entry.size(), null, buffer);
            prepared.add(new PreparedEntry(
                    entry.source(), entry.name(), entry.size(), digest.crc32(),
                    digest.sha256(), entry.localOffset()
            ));
        }
        return new PreparedArchive(List.copyOf(prepared), localBytes, centralBytes, totalBytes);
    }

    private static void writePrepared(OutputStream output, PreparedArchive archive) throws IOException {
        byte[] buffer = new byte[8192];
        for (PreparedEntry entry : archive.entries()) {
            writeInt(output, LOCAL_FILE_HEADER_SIGNATURE);
            writeShort(output, VERSION_NEEDED);
            writeShort(output, UTF8_FLAG);
            writeShort(output, STORED_METHOD);
            writeShort(output, DOS_TIME);
            writeShort(output, DOS_DATE);
            writeInt(output, entry.crc());
            writeInt(output, entry.size());
            writeInt(output, entry.size());
            writeShort(output, entry.name().length);
            writeShort(output, 0);
            output.write(entry.name());
            SourceDigest secondPass = scan(entry.source(), entry.size(), output, buffer);
            if (secondPass.crc32() != entry.crc()
                    || !Arrays.equals(secondPass.sha256(), entry.sha256())) {
                throw new ContainerValidationException(
                        "ZIP entry source changed between the validation and write passes"
                );
            }
        }

        for (PreparedEntry entry : archive.entries()) {
            writeInt(output, CENTRAL_DIRECTORY_SIGNATURE);
            writeShort(output, VERSION_MADE_BY);
            writeShort(output, VERSION_NEEDED);
            writeShort(output, UTF8_FLAG);
            writeShort(output, STORED_METHOD);
            writeShort(output, DOS_TIME);
            writeShort(output, DOS_DATE);
            writeInt(output, entry.crc());
            writeInt(output, entry.size());
            writeInt(output, entry.size());
            writeShort(output, entry.name().length);
            writeShort(output, 0);
            writeShort(output, 0);
            writeShort(output, 0);
            writeShort(output, 0);
            writeInt(output, EXTERNAL_ATTRIBUTES);
            writeInt(output, entry.localOffset());
            output.write(entry.name());
        }

        writeInt(output, END_OF_CENTRAL_DIRECTORY_SIGNATURE);
        writeShort(output, 0);
        writeShort(output, 0);
        writeShort(output, archive.entries().size());
        writeShort(output, archive.entries().size());
        writeInt(output, archive.centralBytes());
        writeInt(output, archive.centralOffset());
        writeShort(output, 0);
    }

    private static SourceDigest scan(
            EntrySource source,
            long expectedBytes,
            OutputStream copyTarget,
            byte[] buffer
    ) throws IOException {
        CRC32 crc = new CRC32();
        MessageDigest sha256 = sha256Digest();
        try (InputStream input = Objects.requireNonNull(
                source.openStream(), "entry source stream"
        )) {
            long remaining = expectedBytes;
            while (remaining > 0) {
                int count = input.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                if (count < 0) {
                    throw new ContainerValidationException(
                            "ZIP entry source ended before its declared byte length"
                    );
                }
                if (count == 0) {
                    throw new ContainerValidationException("ZIP entry source made no read progress");
                }
                crc.update(buffer, 0, count);
                sha256.update(buffer, 0, count);
                if (copyTarget != null) {
                    copyTarget.write(buffer, 0, count);
                }
                remaining -= count;
            }
            if (input.read() != -1) {
                throw new ContainerValidationException(
                        "ZIP entry source exceeds its declared byte length"
                );
            }
        }
        return new SourceDigest(crc.getValue(), sha256.digest());
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void requireClassicUnsignedInt(long value, String message) {
        if (value < 0 || value >= UINT32_ZIP64_SENTINEL) {
            throw new ContainerValidationException(message);
        }
    }

    private static void writeShort(OutputStream output, int value) throws IOException {
        output.write(value);
        output.write(value >>> 8);
    }

    private static void writeInt(OutputStream output, long value) throws IOException {
        output.write((int) value);
        output.write((int) (value >>> 8));
        output.write((int) (value >>> 16));
        output.write((int) (value >>> 24));
    }

    public interface EntrySource {
        ContainerPath path();

        long size();

        InputStream openStream() throws IOException;
    }

    public record Entry(ContainerPath path, byte[] bytes) implements EntrySource {
        public Entry {
            Objects.requireNonNull(path, "path");
            bytes = Objects.requireNonNull(bytes, "bytes").clone();
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }

        @Override
        public long size() {
            return bytes.length;
        }

        @Override
        public InputStream openStream() {
            return new ByteArrayInputStream(bytes);
        }
    }

    private record ByteArrayEntrySource(ContainerPath path, byte[] bytes) implements EntrySource {
        private ByteArrayEntrySource {
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(bytes, "bytes");
        }

        @Override
        public long size() {
            return bytes.length;
        }

        @Override
        public InputStream openStream() {
            return new ByteArrayInputStream(bytes);
        }
    }

    private record DeclaredEntry(
            EntrySource source,
            ContainerPath path,
            byte[] name,
            long size,
            long localOffset
    ) {
        private DeclaredEntry(
                EntrySource source,
                ContainerPath path,
                byte[] name,
                long size
        ) {
            this(source, path, name, size, -1);
        }

        private DeclaredEntry withLocalOffset(long value) {
            return new DeclaredEntry(source, path, name, size, value);
        }
    }

    private record PreparedEntry(
            EntrySource source,
            byte[] name,
            long size,
            long crc,
            byte[] sha256,
            long localOffset
    ) {
    }

    private record PreparedArchive(
            List<PreparedEntry> entries,
            long centralOffset,
            long centralBytes,
            long totalBytes
    ) {
    }

    private record SourceDigest(long crc32, byte[] sha256) {
    }
}
