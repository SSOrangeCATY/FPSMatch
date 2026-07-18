package com.phasetranscrystal.fpsmatch.core.minimap.storage;

import com.phasetranscrystal.fpsmatch.core.minimap.model.MapKey;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinimapRepositoryDirectoryDurabilityTest {
    private static final int WINDOWS_DRIVE_FIXED = 3;

    @TempDir
    Path temporaryDirectory;

    @Test
    void unsupportedDirectorySyncRejectsReservationBeforeReturningATokenOrAdvancingHighWater() {
        MutableDirectorySyncSupportFileSystem fileSystem =
                new MutableDirectorySyncSupportFileSystem(
                        new NioRepositoryFileSystem(),
                        RepositoryFileSystem.DirectorySyncSupport.UNSUPPORTED
                );
        MinimapRepository repository = new MinimapRepository(
                temporaryDirectory, fileSystem
        );
        MapKey key = new MapKey("fpsmatch:test", "Unsupported Durability");

        assertThrows(
                ContainerStorageException.class,
                () -> PublishTargetFixture.reserve(repository, key, 0)
        );

        assertEquals(0, repository.highWaterMark(key));
        assertFalse(Files.exists(
                repository.mapDirectory(key).resolve("publish-state.json")
        ));
        assertTrue(repository.current(key).isEmpty());
    }

    @Test
    void losingDirectorySyncSupportBeforeCommitReturnsUnknownAndFreezesTheToken()
            throws IOException {
        MutableDirectorySyncSupportFileSystem fileSystem =
                new MutableDirectorySyncSupportFileSystem(
                        new NioRepositoryFileSystem(),
                        RepositoryFileSystem.DirectorySyncSupport.SUPPORTED
                );
        MinimapRepository repository = new MinimapRepository(
                temporaryDirectory, fileSystem
        );
        MapKey key = new MapKey("fpsmatch:test", "Test Map");
        MinimapStorageFixtures.Pair pair = MinimapStorageFixtures.validPair(1);
        PublishTransaction prepared = repository.prepare(
                PublishTargetFixture.reserve(repository, key, 0),
                pair.source(),
                pair.runtime()
        );

        fileSystem.setDirectorySyncSupport(
                RepositoryFileSystem.DirectorySyncSupport.UNSUPPORTED
        );
        PublishOutcome outcome = repository.commit(prepared);

        assertFalse(outcome.committed());
        assertTrue(outcome.unknown());
        assertEquals(PublishState.PREPARED, outcome.state());
        assertTrue(repository.current(key).isEmpty());
        assertTrue(repository.isDurabilityDegraded());
        assertTrue(Files.exists(
                repository.mapDirectory(key).resolve("RECOVERY_REQUIRED")
        ));
        assertThrows(ContainerStorageException.class, () -> repository.commit(prepared));
        assertThrows(
                ContainerStorageException.class,
                () -> PublishTargetFixture.reserve(repository, key, 0)
        );

        Files.delete(repository.mapDirectory(key).resolve("RECOVERY_REQUIRED"));
        MinimapRepository restarted = new MinimapRepository(temporaryDirectory);
        assertThrows(
                ContainerStorageException.class,
                () -> PublishTargetFixture.reserve(restarted, key, 0)
        );
        PublishOutcome recovered = new RecoveryService(restarted).recover(key);
        assertEquals(PublishOutcome.Status.ABORTED, recovered.status());
        assertEquals(
                2,
                PublishTargetFixture.reserve(restarted, key, 0).publishRevision()
        );
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void defaultBackendPublishesDurablyOnLocalFixedNtfs() throws IOException {
        assumeLocalFixedNtfs(temporaryDirectory);
        MinimapRepository repository = new MinimapRepository(temporaryDirectory);
        MapKey key = new MapKey("fpsmatch:test", "Test Map");
        MinimapStorageFixtures.Pair pair = MinimapStorageFixtures.validPair(1);

        PublishTransaction prepared = repository.prepare(
                PublishTargetFixture.reserve(repository, key, 0),
                pair.source(),
                pair.runtime()
        );
        PublishOutcome outcome = repository.commit(prepared);

        assertTrue(outcome.committed());
        assertEquals(1, repository.current(key).orElseThrow().revision());
        assertFalse(repository.isDurabilityDegraded());

        MinimapRepository restarted = new MinimapRepository(temporaryDirectory);
        assertEquals(1, restarted.current(key).orElseThrow().revision());
        assertFalse(restarted.isDurabilityDegraded());
    }

    private static void assumeLocalFixedNtfs(Path path) throws IOException {
        FileStore store = Files.getFileStore(path);
        Assumptions.assumeTrue(
                "NTFS".equalsIgnoreCase(store.type()),
                "Windows durability integration requires NTFS"
        );
        Assumptions.assumeFalse(
                store.isReadOnly(),
                "Windows durability integration requires a writable volume"
        );
        Path volumeRoot = path.toRealPath().getRoot();
        Assumptions.assumeTrue(
                volumeRoot != null,
                "Windows durability integration requires a volume root"
        );
        try {
            Class<?> kernel32Type = Class.forName(
                    "com.sun.jna.platform.win32.Kernel32"
            );
            Field instanceField = kernel32Type.getField("INSTANCE");
            Method getDriveType = kernel32Type.getMethod("GetDriveType", String.class);
            int driveType = (Integer) getDriveType.invoke(
                    instanceField.get(null), volumeRoot.toString()
            );
            Assumptions.assumeTrue(
                    driveType == WINDOWS_DRIVE_FIXED,
                    "Windows durability integration requires a local fixed volume"
            );
        } catch (ReflectiveOperationException | LinkageError probeUnavailable) {
            throw new AssertionError(
                    "Windows fixed-volume probe must be on the test runtime classpath",
                    probeUnavailable
            );
        }
    }

    private static final class MutableDirectorySyncSupportFileSystem
            implements RepositoryFileSystem {
        private final RepositoryFileSystem delegate;
        private volatile DirectorySyncSupport directorySyncSupport;

        private MutableDirectorySyncSupportFileSystem(
                RepositoryFileSystem delegate,
                DirectorySyncSupport directorySyncSupport
        ) {
            this.delegate = delegate;
            this.directorySyncSupport = directorySyncSupport;
        }

        private void setDirectorySyncSupport(DirectorySyncSupport directorySyncSupport) {
            this.directorySyncSupport = directorySyncSupport;
        }

        @Override
        public DirectorySyncSupport directorySyncSupport() {
            return directorySyncSupport;
        }

        @Override
        public void createDirectories(Path directory) throws IOException {
            delegate.createDirectories(directory);
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
        public BasicFileAttributes readAttributesNoFollow(Path path) throws IOException {
            return delegate.readAttributesNoFollow(path);
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
}
