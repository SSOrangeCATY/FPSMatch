package com.phasetranscrystal.fpsmatch.core.minimap.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepositoryFileSystemContractTest {
    @TempDir
    Path temporaryDirectory;

    private final RepositoryFileSystem fileSystem = new NioRepositoryFileSystem();

    @Test
    void createsNestedDirectoriesIdempotently() throws IOException {
        Path nested = temporaryDirectory.resolve("maps").resolve("transactions");

        fileSystem.createDirectories(nested);
        fileSystem.createDirectories(nested);

        assertTrue(Files.isDirectory(nested));
    }

    @Test
    void writesAndReadsExactBytesWithoutCreatingAnImplicitParent() throws IOException {
        Path missingParentFile = temporaryDirectory.resolve("missing").resolve("state.json");
        assertThrows(IOException.class,
                () -> fileSystem.write(missingParentFile, new byte[]{1}));

        Path file = temporaryDirectory.resolve("state.json");
        fileSystem.write(file, new byte[]{1, 2, 3});
        fileSystem.write(file, new byte[]{4});

        assertArrayEquals(new byte[]{4}, fileSystem.readAllBytes(file));
    }

    @Test
    void fsyncsACompletedFileWrite() throws IOException {
        Path file = temporaryDirectory.resolve("publish-state.json");
        fileSystem.write(file, new byte[]{7, 8, 9});

        fileSystem.fsyncFile(file);

        assertArrayEquals(new byte[]{7, 8, 9}, Files.readAllBytes(file));
    }

    @Test
    void finalLeafSymlinksAreRejectedByReadWriteSyncAndLockBoundaries()
            throws Exception {
        Path victim = temporaryDirectory.resolve("victim.bin");
        byte[] sentinel = new byte[]{9, 7};
        Files.write(victim, sentinel);
        Path link = temporaryDirectory.resolve("linked.bin");
        Path danglingTarget = temporaryDirectory.resolve("outside-lock");
        Path lockLink = temporaryDirectory.resolve("repository.lock");
        try {
            Files.createSymbolicLink(link, victim.toAbsolutePath());
            Files.createSymbolicLink(lockLink, danglingTarget.toAbsolutePath());
        } catch (UnsupportedOperationException | IOException | SecurityException unavailable) {
            Assumptions.assumeTrue(false, "Symbolic links are unavailable for this test");
        }

        assertThrows(IOException.class, () -> fileSystem.write(link, new byte[]{1}));
        assertThrows(IOException.class, () -> fileSystem.fsyncFile(link));
        assertThrows(IOException.class, () -> fileSystem.openBoundedReadChannel(link, 16));
        assertThrows(IOException.class, () -> fileSystem.acquireExclusiveLock(lockLink));
        assertArrayEquals(sentinel, Files.readAllBytes(victim));
        assertFalse(Files.exists(danglingTarget));
    }

    @Test
    void directoryFsyncSucceedsOnTheSupportedDefaultBackend() throws IOException {
        Path directory = temporaryDirectory.resolve("revisions");
        fileSystem.createDirectories(directory);

        assertEquals(
                RepositoryFileSystem.DirectorySyncSupport.SUPPORTED,
                fileSystem.directorySyncSupport()
        );
        fileSystem.fsyncDirectory(directory);
    }

    @Test
    void movesAtomicallyWithoutReplacingAnExistingTarget() throws IOException {
        Path source = temporaryDirectory.resolve("transaction");
        Path target = temporaryDirectory.resolve("revision");
        fileSystem.createDirectories(source);
        fileSystem.write(source.resolve("pair.bin"), new byte[]{1});

        fileSystem.moveAtomically(source, target);

        assertFalse(Files.exists(source));
        assertArrayEquals(new byte[]{1}, Files.readAllBytes(target.resolve("pair.bin")));

        Path secondSource = temporaryDirectory.resolve("second-transaction");
        fileSystem.createDirectories(secondSource);
        fileSystem.write(secondSource.resolve("pair.bin"), new byte[]{2});

        assertThrows(FileAlreadyExistsException.class,
                () -> fileSystem.moveAtomically(secondSource, target));
        assertArrayEquals(new byte[]{1}, Files.readAllBytes(target.resolve("pair.bin")));
        assertArrayEquals(new byte[]{2}, Files.readAllBytes(secondSource.resolve("pair.bin")));
    }

    @Test
    void replacesTheCurrentPointerAtomically() throws IOException {
        Path current = temporaryDirectory.resolve("CURRENT");
        Path temporary = temporaryDirectory.resolve("CURRENT.tmp");
        fileSystem.write(current, new byte[]{1});
        fileSystem.write(temporary, new byte[]{2});

        fileSystem.replaceAtomically(temporary, current);

        assertFalse(Files.exists(temporary));
        assertArrayEquals(new byte[]{2}, Files.readAllBytes(current));
    }

    @Test
    void holdsAnExclusiveRepositoryLockUntilItsHandleCloses() throws Exception {
        Path lockFile = temporaryDirectory.resolve("map").resolve("repository.lock");
        fileSystem.createDirectories(lockFile.getParent());
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<Boolean> second;
        try (RepositoryFileSystem.LockHandle ignored =
                     fileSystem.acquireExclusiveLock(lockFile)) {
            second = executor.submit(() -> {
                try (RepositoryFileSystem.LockHandle alsoIgnored =
                             fileSystem.acquireExclusiveLock(lockFile)) {
                    return true;
                }
            });
            assertThrows(TimeoutException.class,
                    () -> second.get(Duration.ofMillis(200).toMillis(), TimeUnit.MILLISECONDS));
        } finally {
            executor.shutdown();
        }
        assertTrue(second.get(5, TimeUnit.SECONDS));
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
    }

    @Test
    void closedLockHandlesDoNotRetainEveryVisitedPathForever() throws Exception {
        Field locksField = NioRepositoryFileSystem.class.getDeclaredField("JVM_LOCKS");
        locksField.setAccessible(true);
        Map<?, ?> locks = (Map<?, ?>) locksField.get(null);
        int before = locks.size();

        for (int index = 0; index < 257; index++) {
            Path lockFile = temporaryDirectory.resolve("lock-" + index);
            try (RepositoryFileSystem.LockHandle ignored =
                         fileSystem.acquireExclusiveLock(lockFile)) {
                assertTrue(Files.isRegularFile(lockFile));
            }
        }

        assertTrue(
                locks.size() <= before,
                "closed JVM lock handles retained " + (locks.size() - before) + " paths"
        );
    }

    @Test
    void aDecoratorCanInjectAnAtomicReplaceFailureWithoutTouchingEitherFile() throws IOException {
        Path current = temporaryDirectory.resolve("CURRENT");
        Path temporary = temporaryDirectory.resolve("CURRENT.tmp");
        fileSystem.write(current, new byte[]{1});
        fileSystem.write(temporary, new byte[]{2});
        RepositoryFileSystem faultInjecting = new ReplaceFailingFileSystem(fileSystem);

        assertThrows(IOException.class,
                () -> faultInjecting.replaceAtomically(temporary, current));

        assertArrayEquals(new byte[]{1}, Files.readAllBytes(current));
        assertArrayEquals(new byte[]{2}, Files.readAllBytes(temporary));
    }

    private record ReplaceFailingFileSystem(
            RepositoryFileSystem delegate
    ) implements RepositoryFileSystem {
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
            throw new IOException("injected atomic replace failure");
        }
    }
}
