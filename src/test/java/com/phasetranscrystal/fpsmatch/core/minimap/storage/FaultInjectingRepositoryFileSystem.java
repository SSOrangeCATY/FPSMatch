package com.phasetranscrystal.fpsmatch.core.minimap.storage;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

final class FaultInjectingRepositoryFileSystem implements RepositoryFileSystem {
    enum Operation {
        CREATE_DIRECTORIES,
        READ,
        OPEN_READ_CHANNEL,
        WRITE,
        FSYNC_FILE,
        FSYNC_DIRECTORY,
        MOVE_ATOMICALLY,
        REPLACE_ATOMICALLY
    }

    record Call(Operation operation, Path path, Path target) {
        Call {
            Objects.requireNonNull(operation, "operation");
            path = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
            target = target == null ? null : target.toAbsolutePath().normalize();
        }
    }

    private final RepositoryFileSystem delegate;
    private final List<Call> calls = new ArrayList<>();
    private Predicate<Call> failure = ignored -> false;
    private boolean armed;

    FaultInjectingRepositoryFileSystem(RepositoryFileSystem delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    void failNext(Predicate<Call> predicate) {
        failure = Objects.requireNonNull(predicate, "predicate");
        armed = true;
    }

    List<Call> calls() {
        return List.copyOf(calls);
    }

    private void boundary(Operation operation, Path path, Path target) throws IOException {
        Call call = new Call(operation, path, target);
        calls.add(call);
        if (armed && failure.test(call)) {
            armed = false;
            throw new IOException("injected repository failure at " + operation + ": " + path);
        }
    }

    @Override
    public DirectorySyncSupport directorySyncSupport() {
        return delegate.directorySyncSupport();
    }

    @Override
    public void createDirectories(Path directory) throws IOException {
        boundary(Operation.CREATE_DIRECTORIES, directory, null);
        delegate.createDirectories(directory);
    }

    @Override
    public LockHandle acquireExclusiveLock(Path lockFile) throws IOException {
        return delegate.acquireExclusiveLock(lockFile);
    }

    @Override
    public byte[] readAllBytes(Path file) throws IOException {
        boundary(Operation.READ, file, null);
        return delegate.readAllBytes(file);
    }

    @Override
    public BasicFileAttributes readAttributesNoFollow(Path path) throws IOException {
        return delegate.readAttributesNoFollow(path);
    }

    @Override
    public BoundedReadChannel openBoundedReadChannel(Path file, long maximumBytes)
            throws IOException {
        boundary(Operation.OPEN_READ_CHANNEL, file, null);
        return delegate.openBoundedReadChannel(file, maximumBytes);
    }

    @Override
    public void write(Path file, byte[] bytes) throws IOException {
        boundary(Operation.WRITE, file, null);
        delegate.write(file, bytes);
    }

    @Override
    public void fsyncFile(Path file) throws IOException {
        boundary(Operation.FSYNC_FILE, file, null);
        delegate.fsyncFile(file);
    }

    @Override
    public void fsyncDirectory(Path directory) throws IOException {
        boundary(Operation.FSYNC_DIRECTORY, directory, null);
        delegate.fsyncDirectory(directory);
    }

    @Override
    public void moveAtomically(Path source, Path target) throws IOException {
        boundary(Operation.MOVE_ATOMICALLY, source, target);
        delegate.moveAtomically(source, target);
    }

    @Override
    public void replaceAtomically(Path source, Path target) throws IOException {
        boundary(Operation.REPLACE_ATOMICALLY, source, target);
        delegate.replaceAtomically(source, target);
    }
}
