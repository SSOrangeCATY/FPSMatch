package com.ptcrys.fpsmatch.core.minimap.storage;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Small NIO adapter for the shared session domain. Windows uses the same
 * exclusive FileChannel lock so NIO and the authority provider expose one
 * provider identity instead of building independent lock graphs.
 */
final class NioNativeSessionFacade implements NativeSessionFacade {
    private final AtomicLong nextId = new AtomicLong();
    private final ConcurrentMap<Long, Entry> entries = new ConcurrentHashMap<>();

    @Override
    public Handle openMapDirectory(Path mapDirectory) throws IOException {
        Path path = normalize(mapDirectory);
        BasicFileAttributes attributes = Files.readAttributes(
                path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS
        );
        if (!attributes.isDirectory()) {
            throw new IOException("Repository map path is not a directory: " + path);
        }
        return register(new Handle(nextId.incrementAndGet(), path), null);
    }

    @Override
    public Handle openOrCreateLock(Path lockFile) throws IOException {
        Path path = normalize(lockFile);
        FileChannel channel = FileChannel.open(
                path,
                StandardOpenOption.CREATE,
                StandardOpenOption.READ,
                StandardOpenOption.WRITE,
                LinkOption.NOFOLLOW_LINKS
        );
        return register(
                new Handle(nextId.incrementAndGet(), path),
                new Entry(channel)
        );
    }

    @Override
    public Handle openExistingLock(Path lockFile) throws IOException {
        Path path = normalize(lockFile);
        FileChannel channel = FileChannel.open(
                path,
                StandardOpenOption.READ,
                StandardOpenOption.WRITE,
                LinkOption.NOFOLLOW_LINKS
        );
        return register(
                new Handle(nextId.incrementAndGet(), path),
                new Entry(channel)
        );
    }

    @Override
    public void lock(Handle lockHandle) throws IOException {
        entry(lockHandle).fileLock = entry(lockHandle).channel.lock();
    }

    @Override
    public ObjectState inspect(Handle handle) throws IOException {
        Entry entry = entry(handle);
        Path path = handle.path();
        BasicFileAttributes attributes = Files.readAttributes(
                path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS
        );
        byte[] identity = String.valueOf(attributes.fileKey())
                .getBytes(StandardCharsets.UTF_8);
        return new ObjectState(
                identity.length == 0 ? path.toString().getBytes(StandardCharsets.UTF_8) : identity,
                !attributes.isSymbolicLink(),
                !attributes.isSymbolicLink(),
                1,
                attributes.size()
        );
    }

    @Override
    public void force(Handle handle) throws IOException {
        entry(handle).channel.force(true);
    }

    @Override
    public void flushParent(Handle parentHandle) throws IOException {
        new NioRepositoryFileSystem().fsyncDirectory(parentHandle.path());
    }

    @Override
    public void unlock(Handle lockHandle) throws IOException {
        Entry entry = entry(lockHandle);
        FileLock lock = entry.fileLock;
        if (lock != null && lock.isValid()) {
            lock.release();
        }
        entry.fileLock = null;
    }

    @Override
    public void closeLockHandle(Handle lockHandle) throws IOException {
        close(lockHandle);
    }

    @Override
    public void closeProofHandle(Handle proofHandle) throws IOException {
        close(proofHandle);
    }

    @Override
    public void closeParentHandle(Handle parentHandle) throws IOException {
        close(parentHandle);
    }

    private Handle register(Handle handle, Entry entry) {
        if (entry != null) {
            entries.put(handle.nativeId(), entry);
        }
        return handle;
    }

    private Entry entry(Handle handle) throws IOException {
        Objects.requireNonNull(handle, "handle");
        Entry entry = entries.get(handle.nativeId());
        if (entry == null) {
            throw new IOException("Native session handle is closed: " + handle.path());
        }
        return entry;
    }

    private void close(Handle handle) throws IOException {
        Objects.requireNonNull(handle, "handle");
        Entry entry = entries.remove(handle.nativeId());
        if (entry == null) {
            return;
        }
        IOException failure = null;
        try {
            if (entry.fileLock != null && entry.fileLock.isValid()) {
                entry.fileLock.release();
            }
        } catch (IOException exception) {
            failure = exception;
        }
        try {
            entry.channel.close();
        } catch (IOException exception) {
            if (failure == null) {
                failure = exception;
            } else {
                failure.addSuppressed(exception);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private static Path normalize(Path path) {
        return Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
    }

    private static final class Entry {
        private final FileChannel channel;
        private volatile FileLock fileLock;

        private Entry(FileChannel channel) {
            this.channel = channel;
        }
    }
}
