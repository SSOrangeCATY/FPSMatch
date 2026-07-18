package com.phasetranscrystal.fpsmatch.core.minimap.storage;

import java.io.IOException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;

/**
 * Durable filesystem operations used by minimap repository transactions.
 * Each method is a separate boundary so tests can inject failures precisely.
 */
public interface RepositoryFileSystem {
    enum DirectorySyncSupport {
        SUPPORTED,
        UNSUPPORTED
    }

    @FunctionalInterface
    interface LockHandle extends AutoCloseable {
        @Override
        void close() throws IOException;
    }

    record BoundedReadChannel(SeekableByteChannel channel, long size)
            implements AutoCloseable {
        public BoundedReadChannel {
            Objects.requireNonNull(channel, "channel");
            if (size < 0) {
                throw new IllegalArgumentException("Read channel size must be non-negative");
            }
        }

        static BoundedReadChannel acquire(
                SeekableByteChannel channel,
                long maximumBytes
        ) throws IOException {
            Objects.requireNonNull(channel, "channel");
            if (maximumBytes < 0) {
                IllegalArgumentException failure = new IllegalArgumentException(
                        "Read channel limit must be non-negative"
                );
                closeAfterFailure(channel, failure);
                throw failure;
            }
            try {
                long size = channel.size();
                if (size > maximumBytes) {
                    throw new IOException("File exceeds its read channel byte limit");
                }
                return new BoundedReadChannel(channel, size);
            } catch (IOException | RuntimeException failure) {
                closeAfterFailure(channel, failure);
                throw failure;
            }
        }

        private static void closeAfterFailure(
                SeekableByteChannel channel,
                Throwable failure
        ) {
            try {
                channel.close();
            } catch (IOException closeFailure) {
                failure.addSuppressed(closeFailure);
            }
        }

        @Override
        public void close() throws IOException {
            channel.close();
        }
    }

    void createDirectories(Path directory) throws IOException;

    default DirectorySyncSupport directorySyncSupport() {
        return DirectorySyncSupport.SUPPORTED;
    }

    LockHandle acquireExclusiveLock(Path lockFile) throws IOException;

    byte[] readAllBytes(Path file) throws IOException;

    default BasicFileAttributes readAttributesNoFollow(Path path) throws IOException {
        return Files.readAttributes(
                Objects.requireNonNull(path, "path"),
                BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS
        );
    }

    default BoundedReadChannel openBoundedReadChannel(
            Path file,
            long maximumBytes
    ) throws IOException {
        SeekableByteChannel channel = Files.newByteChannel(
                Objects.requireNonNull(file, "file"),
                java.util.Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)
        );
        return BoundedReadChannel.acquire(channel, maximumBytes);
    }

    void write(Path file, byte[] bytes) throws IOException;

    void fsyncFile(Path file) throws IOException;

    void fsyncDirectory(Path directory) throws IOException;

    void moveAtomically(Path source, Path target) throws IOException;

    void replaceAtomically(Path source, Path target) throws IOException;
}
