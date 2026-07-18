package com.phasetranscrystal.fpsmatch.core.minimap.storage;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

public final class NioRepositoryFileSystem implements RepositoryFileSystem {
    private static final ConcurrentMap<Path, LockEntry> JVM_LOCKS =
            new ConcurrentHashMap<>();
    private static final boolean WINDOWS =
            System.getProperty("os.name", "").startsWith("Windows");
    private static final DirectorySyncSupport DIRECTORY_SYNC_SUPPORT =
            detectDirectorySyncSupport();

    @Override
    public DirectorySyncSupport directorySyncSupport() {
        return DIRECTORY_SYNC_SUPPORT;
    }

    @Override
    public void createDirectories(Path directory) throws IOException {
        Files.createDirectories(Objects.requireNonNull(directory, "directory"));
    }

    @Override
    public LockHandle acquireExclusiveLock(Path lockFile) throws IOException {
        Path normalized = Objects.requireNonNull(lockFile, "lockFile")
                .toAbsolutePath().normalize();
        LockEntry entry = retainJvmLock(normalized);
        ReentrantLock jvmLock = entry.lock;
        jvmLock.lock();
        FileChannel channel = null;
        FileLock fileLock = null;
        try {
            channel = FileChannel.open(
                    normalized,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    LinkOption.NOFOLLOW_LINKS
            );
            fileLock = channel.lock();
            FileChannel acquiredChannel = channel;
            FileLock acquiredFileLock = fileLock;
            AtomicBoolean closed = new AtomicBoolean();
            return () -> {
                if (closed.compareAndSet(false, true)) {
                    closeLock(
                            acquiredFileLock, acquiredChannel,
                            normalized, entry
                    );
                }
            };
        } catch (IOException | RuntimeException exception) {
            closeAfterFailedLock(fileLock, channel);
            try {
                jvmLock.unlock();
            } finally {
                releaseJvmLock(normalized, entry);
            }
            throw exception;
        }
    }

    @Override
    public byte[] readAllBytes(Path file) throws IOException {
        return Files.readAllBytes(Objects.requireNonNull(file, "file"));
    }

    @Override
    public BasicFileAttributes readAttributesNoFollow(Path path) throws IOException {
        return Files.readAttributes(
                Objects.requireNonNull(path, "path"),
                BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS
        );
    }

    @Override
    public BoundedReadChannel openBoundedReadChannel(
            Path file,
            long maximumBytes
    ) throws IOException {
        FileChannel channel = FileChannel.open(
                Objects.requireNonNull(file, "file"),
                StandardOpenOption.READ,
                LinkOption.NOFOLLOW_LINKS
        );
        return BoundedReadChannel.acquire(channel, maximumBytes);
    }

    @Override
    public void write(Path file, byte[] bytes) throws IOException {
        Files.write(
                Objects.requireNonNull(file, "file"),
                Objects.requireNonNull(bytes, "bytes"),
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE,
                LinkOption.NOFOLLOW_LINKS
        );
    }

    @Override
    public void fsyncFile(Path file) throws IOException {
        try (FileChannel channel = FileChannel.open(
                Objects.requireNonNull(file, "file"),
                StandardOpenOption.WRITE,
                LinkOption.NOFOLLOW_LINKS
        )) {
            channel.force(true);
        }
    }

    @Override
    public void fsyncDirectory(Path directory) throws IOException {
        Objects.requireNonNull(directory, "directory");
        BasicFileAttributes attributes = Files.readAttributes(
                directory, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS
        );
        if (!attributes.isDirectory()) {
            throw new NotDirectoryException(directory.toString());
        }
        if (DIRECTORY_SYNC_SUPPORT == DirectorySyncSupport.UNSUPPORTED) {
            throw new IOException("Directory synchronization is unavailable");
        }
        if (WINDOWS) {
            try {
                WindowsDirectorySynchronizer.sync(directory);
            } catch (RuntimeException | LinkageError nativeFailure) {
                throw new IOException(
                        "Windows directory synchronization failed", nativeFailure
                );
            }
            return;
        }
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            channel.force(true);
        }
    }

    @Override
    public void moveAtomically(Path source, Path target) throws IOException {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new FileAlreadyExistsException(target.toString());
        }
        Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
    }

    @Override
    public void replaceAtomically(Path source, Path target) throws IOException {
        Files.move(
                Objects.requireNonNull(source, "source"),
                Objects.requireNonNull(target, "target"),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
        );
    }

    private static void closeLock(
            FileLock fileLock,
            FileChannel channel,
            Path path,
            LockEntry entry
    ) throws IOException {
        IOException failure = null;
        try {
            fileLock.release();
        } catch (IOException exception) {
            failure = exception;
        }
        try {
            channel.close();
        } catch (IOException exception) {
            if (failure == null) {
                failure = exception;
            } else {
                failure.addSuppressed(exception);
            }
        } finally {
            try {
                entry.lock.unlock();
            } finally {
                releaseJvmLock(path, entry);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private static void closeAfterFailedLock(FileLock fileLock, FileChannel channel) {
        try {
            if (fileLock != null) {
                fileLock.release();
            }
        } catch (IOException ignored) {
        }
        try {
            if (channel != null) {
                channel.close();
            }
        } catch (IOException ignored) {
        }
    }

    private static DirectorySyncSupport detectDirectorySyncSupport() {
        if (!WINDOWS) {
            return DirectorySyncSupport.SUPPORTED;
        }
        try {
            WindowsDirectorySynchronizer.ensureAvailable();
            return DirectorySyncSupport.SUPPORTED;
        } catch (LinkageError | RuntimeException unavailable) {
            return DirectorySyncSupport.UNSUPPORTED;
        }
    }

    private static LockEntry retainJvmLock(Path path) {
        return JVM_LOCKS.compute(path, (ignored, existing) -> {
            LockEntry entry = existing == null ? new LockEntry() : existing;
            entry.references++;
            return entry;
        });
    }

    private static void releaseJvmLock(Path path, LockEntry expected) {
        JVM_LOCKS.compute(path, (ignored, existing) -> {
            if (existing != expected) {
                return existing;
            }
            existing.references--;
            return existing.references == 0 ? null : existing;
        });
    }

    private static final class LockEntry {
        private final ReentrantLock lock = new ReentrantLock();
        private int references;
    }
}
