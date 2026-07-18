package com.phasetranscrystal.fpsmatch.core.minimap.storage;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;

final class TargetAttributeReadFailingRepositoryFileSystem implements RepositoryFileSystem {
    private final RepositoryFileSystem delegate;
    private Path failingPath;

    TargetAttributeReadFailingRepositoryFileSystem(RepositoryFileSystem delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    void failNextAttributeRead(Path path) {
        failingPath = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
    }

    @Override
    public BasicFileAttributes readAttributesNoFollow(Path path) throws IOException {
        Path normalized = path.toAbsolutePath().normalize();
        if (normalized.equals(failingPath)) {
            failingPath = null;
            throw new AccessDeniedException(path.toString());
        }
        return Files.readAttributes(
                path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS
        );
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
        return delegate.readAllBytes(file);
    }

    @Override
    public BoundedReadChannel openBoundedReadChannel(
            Path file,
            long maximumBytes
    ) throws IOException {
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
}
