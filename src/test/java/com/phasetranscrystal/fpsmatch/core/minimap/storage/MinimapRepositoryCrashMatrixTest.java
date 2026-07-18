package com.phasetranscrystal.fpsmatch.core.minimap.storage;

import com.phasetranscrystal.fpsmatch.core.minimap.model.MapKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MinimapRepositoryCrashMatrixTest {
    private static PublishTransaction reserve(
            MinimapRepository repository, MapKey key, long baseRevision
    ) {
        return PublishTargetFixture.reserve(repository, key, baseRevision);
    }

    @TempDir
    Path temporaryDirectory;

    @Test
    void prepareFailureMatrixNeverCreatesAPreparedRecordAndKeepsRevisionConsumed()
            throws Exception {
        for (PrepareBoundary boundary : PrepareBoundary.values()) {
            Path root = temporaryDirectory.resolve(boundary.name().toLowerCase());
            FaultInjectingRepositoryFileSystem fileSystem = new FaultInjectingRepositoryFileSystem(
                    new DirectoryFsyncTolerantFileSystem(new NioRepositoryFileSystem())
            );
            MinimapRepository repository = new MinimapRepository(root, fileSystem);
            MapKey key = new MapKey("fpsmatch:test", "Test Map");
            PublishTransaction reserved = reserve(repository, key, 0);
            MinimapStorageFixtures.Pair pair = MinimapStorageFixtures.validPair(1);
            fileSystem.failNext(boundary::matches);

            assertThrows(ContainerStorageException.class, () -> repository.prepare(
                    reserved, pair.source(), pair.runtime()
            ), boundary.name());

            Path record = reserved.transactionDirectory().resolve("publish-record.json");
            assertEquals(PublishState.RESERVED,
                    PublishRecord.read(Files.readAllBytes(record)).state(), boundary.name());
            assertFalse(repository.current(key).isPresent(), boundary.name());
            new RecoveryService(repository).recover(key);
            assertEquals(2, reserve(repository, key, 0).publishRevision(), boundary.name());
        }
    }

    @Test
    void currentTemporaryFailureMatrixLeavesNoCurrentAndRecoveryAbortsCandidate()
            throws Exception {
        for (CurrentBoundary boundary : CurrentBoundary.values()) {
            Path root = temporaryDirectory.resolve(boundary.name().toLowerCase());
            FaultInjectingRepositoryFileSystem fileSystem = new FaultInjectingRepositoryFileSystem(
                    new DirectoryFsyncTolerantFileSystem(new NioRepositoryFileSystem())
            );
            MinimapRepository repository = new MinimapRepository(root, fileSystem);
            MapKey key = new MapKey("fpsmatch:test", "Test Map");
            MinimapStorageFixtures.Pair pair = MinimapStorageFixtures.validPair(1);
            PublishTransaction prepared = repository.prepare(
                    reserve(repository, key, 0), pair.source(), pair.runtime()
            );
            fileSystem.failNext(boundary::matches);

            assertThrows(ContainerStorageException.class,
                    () -> repository.commit(prepared), boundary.name());

            assertFalse(repository.current(key).isPresent(), boundary.name());
            assertEquals(
                    PublishOutcome.Status.ABORTED,
                    new RecoveryService(repository).recover(key).status(),
                    boundary.name()
            );
            try (var entries = Files.list(repository.mapDirectory(key))) {
                assertFalse(entries.anyMatch(path -> {
                    String name = path.getFileName().toString();
                    return name.startsWith("CURRENT.") && name.endsWith(".tmp");
                }), boundary.name());
            }
            assertEquals(2, reserve(repository, key, 0).publishRevision(), boundary.name());
        }
    }

    @Test
    void moveFailureConsumesPreparedTokenUntilRecovery() {
        FaultInjectingRepositoryFileSystem fileSystem =
                new FaultInjectingRepositoryFileSystem(
                        new DirectoryFsyncTolerantFileSystem(
                                new NioRepositoryFileSystem()
                        )
                );
        MinimapRepository repository = new MinimapRepository(
                temporaryDirectory, fileSystem
        );
        MapKey key = new MapKey("fpsmatch:test", "Test Map");
        MinimapStorageFixtures.Pair pair = MinimapStorageFixtures.validPair(1);
        PublishTransaction prepared = repository.prepare(
                reserve(repository, key, 0), pair.source(), pair.runtime()
        );
        fileSystem.failNext(call -> call.operation()
                == FaultInjectingRepositoryFileSystem.Operation.MOVE_ATOMICALLY
                && call.target() != null
                && call.target().getParent().getFileName().toString().equals("revisions"));

        assertThrows(ContainerStorageException.class, () -> repository.commit(prepared));
        assertThrows(ContainerStorageException.class, () -> repository.commit(prepared));

        MinimapRepository restarted = new MinimapRepository(
                temporaryDirectory, fileSystem
        );
        assertThrows(ContainerStorageException.class, () -> restarted.commit(prepared));
        assertEquals(
                PublishOutcome.Status.ABORTED,
                new RecoveryService(restarted).recover(key).status()
        );
        assertFalse(restarted.current(key).isPresent());
    }

    @Test
    void fullRevisionDirectoryRejectsCommitBeforeConsumingTheToken()
            throws Exception {
        MinimapRepository repository = new MinimapRepository(temporaryDirectory);
        MapKey key = new MapKey("fpsmatch:test", "Test Map");
        MinimapStorageFixtures.Pair pair = MinimapStorageFixtures.validPair(1);
        PublishTransaction prepared = repository.prepare(
                reserve(repository, key, 0), pair.source(), pair.runtime()
        );
        Path mapDirectory = repository.mapDirectory(key);
        Path revisions = mapDirectory.resolve("revisions");
        Files.createDirectories(revisions);
        for (int index = 0; index < 4_096; index++) {
            Files.createDirectory(revisions.resolve("occupied-" + index));
        }

        assertThrows(ContainerStorageException.class, () -> repository.commit(prepared));

        assertFalse(Files.exists(mapDirectory.resolve("RECOVERY_REQUIRED")));
        assertEquals(PublishState.PREPARED, PublishRecord.read(Files.readAllBytes(
                prepared.transactionDirectory().resolve("publish-record.json")
        )).state());
        try (var entries = Files.list(revisions)) {
            assertEquals(4_096, entries.count());
        }
    }

    @Test
    void committedRecordFsyncFailureStaysCommittedAndRecovers() {
        FaultInjectingRepositoryFileSystem fileSystem = new FaultInjectingRepositoryFileSystem(
                new DirectoryFsyncTolerantFileSystem(new NioRepositoryFileSystem())
        );
        MinimapRepository repository = new MinimapRepository(temporaryDirectory, fileSystem);
        MapKey key = new MapKey("fpsmatch:test", "Test Map");
        MinimapStorageFixtures.Pair pair = MinimapStorageFixtures.validPair(1);
        PublishTransaction prepared = repository.prepare(
                reserve(repository, key, 0), pair.source(), pair.runtime()
        );
        fileSystem.failNext(call -> call.operation()
                == FaultInjectingRepositoryFileSystem.Operation.FSYNC_FILE
                && call.path().getFileName().toString().startsWith("publish-record.json")
                && call.path().getParent().getParent().getFileName().toString().equals("revisions"));

        PublishOutcome outcome = repository.commit(prepared);

        assertEquals(PublishOutcome.Status.COMMITTED, outcome.status());
        assertEquals(1, repository.current(key).orElseThrow().revision());
        assertEquals(PublishOutcome.Status.COMMITTED,
                new RecoveryService(repository).recover(key).status());
    }

    private enum PrepareBoundary {
        SOURCE_WRITE,
        SOURCE_FSYNC,
        RUNTIME_WRITE,
        RUNTIME_FSYNC,
        PREPARED_RECORD_FSYNC;

        private boolean matches(FaultInjectingRepositoryFileSystem.Call call) {
            String name = call.path().getFileName().toString();
            return switch (this) {
                case SOURCE_WRITE -> call.operation()
                        == FaultInjectingRepositoryFileSystem.Operation.WRITE
                        && name.equals("source.fpsmap");
                case SOURCE_FSYNC -> call.operation()
                        == FaultInjectingRepositoryFileSystem.Operation.FSYNC_FILE
                        && name.equals("source.fpsmap");
                case RUNTIME_WRITE -> call.operation()
                        == FaultInjectingRepositoryFileSystem.Operation.WRITE
                        && name.equals("runtime.fpsmapc");
                case RUNTIME_FSYNC -> call.operation()
                        == FaultInjectingRepositoryFileSystem.Operation.FSYNC_FILE
                        && name.equals("runtime.fpsmapc");
                case PREPARED_RECORD_FSYNC -> call.operation()
                        == FaultInjectingRepositoryFileSystem.Operation.FSYNC_FILE
                        && name.startsWith("publish-record.json");
            };
        }
    }

    private enum CurrentBoundary {
        TEMP_WRITE,
        TEMP_FSYNC;

        private boolean matches(FaultInjectingRepositoryFileSystem.Call call) {
            String name = call.path().getFileName().toString();
            return name.startsWith("CURRENT.") && name.endsWith(".tmp")
                    && call.operation() == switch (this) {
                case TEMP_WRITE -> FaultInjectingRepositoryFileSystem.Operation.WRITE;
                case TEMP_FSYNC -> FaultInjectingRepositoryFileSystem.Operation.FSYNC_FILE;
            };
        }
    }

    private static final class DirectoryFsyncTolerantFileSystem
            implements RepositoryFileSystem {
        private final RepositoryFileSystem delegate;

        private DirectoryFsyncTolerantFileSystem(RepositoryFileSystem delegate) {
            this.delegate = delegate;
        }

        @Override
        public DirectorySyncSupport directorySyncSupport() {
            return delegate.directorySyncSupport();
        }

        @Override
        public void createDirectories(Path directory) throws java.io.IOException {
            delegate.createDirectories(directory);
        }

        @Override
        public LockHandle acquireExclusiveLock(Path lockFile) throws java.io.IOException {
            return delegate.acquireExclusiveLock(lockFile);
        }

        @Override
        public byte[] readAllBytes(Path file) throws java.io.IOException {
            return delegate.readAllBytes(file);
        }

        @Override
        public java.nio.file.attribute.BasicFileAttributes readAttributesNoFollow(Path path)
                throws java.io.IOException {
            return delegate.readAttributesNoFollow(path);
        }

        @Override
        public BoundedReadChannel openBoundedReadChannel(Path file, long maximumBytes)
                throws java.io.IOException {
            return delegate.openBoundedReadChannel(file, maximumBytes);
        }

        @Override
        public void write(Path file, byte[] bytes) throws java.io.IOException {
            delegate.write(file, bytes);
        }

        @Override
        public void fsyncFile(Path file) throws java.io.IOException {
            delegate.fsyncFile(file);
        }

        @Override
        public void fsyncDirectory(Path directory) throws java.io.IOException {
            try {
                delegate.fsyncDirectory(directory);
            } catch (java.nio.file.AccessDeniedException ignoredOnWindows) {
            }
        }

        @Override
        public void moveAtomically(Path source, Path target) throws java.io.IOException {
            delegate.moveAtomically(source, target);
        }

        @Override
        public void replaceAtomically(Path source, Path target) throws java.io.IOException {
            delegate.replaceAtomically(source, target);
        }
    }
}
