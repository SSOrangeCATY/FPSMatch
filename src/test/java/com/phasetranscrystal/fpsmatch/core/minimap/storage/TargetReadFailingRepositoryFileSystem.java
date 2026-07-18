package com.phasetranscrystal.fpsmatch.core.minimap.storage;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Path;
import java.util.Objects;

final class TargetReadFailingRepositoryFileSystem implements RepositoryFileSystem {
    private final RepositoryFileSystem delegate;
    private Path failingPath;

    TargetReadFailingRepositoryFileSystem(RepositoryFileSystem delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    void failNextRead(Path path) {
        failingPath = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
    }

    @Override
    public void createDirectories(Path directory) throws IOException {
        delegate.createDirectories(directory);
    }

    @Override
    public DirectorySyncSupport directorySyncSupport() {
        return delegate.directorySyncSupport();
    }

    @Override
    public LockHandle acquireExclusiveLock(Path lockFile) throws IOException {
        return delegate.acquireExclusiveLock(lockFile);
    }

    @Override
    public byte[] readAllBytes(Path file) throws IOException {
        failIfTargeted(file);
        return delegate.readAllBytes(file);
    }

    @Override
    public BoundedReadChannel openBoundedReadChannel(
            Path file,
            long maximumBytes
    ) throws IOException {
        failIfTargeted(file);
        return delegate.openBoundedReadChannel(file, maximumBytes);
    }

    @Override
    public void write(Path file, byte[] bytes) throws IOException {
        delegate.write(file, bytes);
    }

    @Override
    public void fsyncFile(Path file) throws IOException {
        delegate.fsyncFile(file);
    }

    @Override
    public void fsyncDirectory(Path directory) throws IOException {
        delegate.fsyncDirectory(directory);
    }

    @Override
    public void moveAtomically(Path source, Path target) throws IOException {
        delegate.moveAtomically(source, target);
    }

    @Override
    public void replaceAtomically(Path source, Path target) throws IOException {
        delegate.replaceAtomically(source, target);
    }

    private void failIfTargeted(Path file) throws AccessDeniedException {
        Path normalized = file.toAbsolutePath().normalize();
        if (normalized.equals(failingPath)) {
            failingPath = null;
            throw new AccessDeniedException(file.toString());
        }
    }
}
