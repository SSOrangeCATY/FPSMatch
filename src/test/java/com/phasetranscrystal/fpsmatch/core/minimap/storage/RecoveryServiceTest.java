package com.phasetranscrystal.fpsmatch.core.minimap.storage;

import com.phasetranscrystal.fpsmatch.core.minimap.model.MapKey;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecoveryServiceTest {
    private static PublishTransaction reserve(
            MinimapRepository repository, MapKey key, long baseRevision
    ) {
        return PublishTargetFixture.reserve(repository, key, baseRevision);
    }

    @TempDir
    Path temporaryDirectory;

    @Test
    void rebuildsACorruptCurrentPointerFromTheHighestCompleteCommittedRevision() throws Exception {
        MinimapRepository repository = new MinimapRepository(temporaryDirectory);
        MapKey key = new MapKey("fpsmatch:test", "Test Map");
        MinimapStorageFixtures.Pair first = MinimapStorageFixtures.validPair(1);
        PublishTransaction transaction = repository.prepare(
                reserve(repository, key, 0), first.source(), first.runtime()
        );
        assertTrue(repository.commit(transaction).committed());
        Files.write(repository.mapDirectory(key).resolve("CURRENT"), new byte[]{1, 2, 3});

        PublishOutcome outcome = new RecoveryService(repository).recover(key);

        assertTrue(outcome.committed());
        assertEquals(1, repository.current(key).orElseThrow().revision());
    }

    @Test
    void rebuildsAMissingCurrentPointerFromTheHighestCompleteCommittedRevision()
            throws Exception {
        MinimapRepository repository = new MinimapRepository(temporaryDirectory);
        MapKey key = new MapKey("fpsmatch:test", "Test Map");
        publish(repository, key, 1);
        publish(repository, key, 2);
        Path current = repository.mapDirectory(key).resolve("CURRENT");
        Files.delete(current);

        PublishOutcome outcome = new RecoveryService(repository).recover(key);

        assertTrue(outcome.committed());
        assertEquals(2, repository.current(key).orElseThrow().revision());
        assertTrue(Files.isRegularFile(current));
    }

    @Test
    void cleansAnOrphanReservationWithoutPromotingIt() {
        MinimapRepository repository = new MinimapRepository(temporaryDirectory);
        MapKey key = new MapKey("fpsmatch:test", "Test Map");
        reserve(repository, key, 0);

        PublishOutcome outcome = new RecoveryService(repository).recover(key);

        assertEquals(PublishOutcome.Status.ABORTED, outcome.status());
        assertTrue(repository.current(key).isEmpty());
        assertEquals(2, reserve(repository, key, 0).publishRevision());
    }

    @Test
    void damagedOnlyCommittedRevisionBecomesUnavailableWithoutRewritingCurrent()
            throws Exception {
        MinimapRepository repository = new MinimapRepository(temporaryDirectory);
        MapKey key = new MapKey("fpsmatch:test", "Test Map");
        MinimapStorageFixtures.Pair pair = MinimapStorageFixtures.validPair(1);
        PublishTransaction prepared = repository.prepare(
                reserve(repository, key, 0), pair.source(), pair.runtime()
        );
        assertTrue(repository.commit(prepared).committed());
        Files.delete(repository.mapDirectory(key).resolve("revisions")
                .resolve("1").resolve("runtime.fpsmapc"));

        PublishOutcome outcome = new RecoveryService(repository).recover(key);

        assertEquals(PublishOutcome.Status.UNAVAILABLE, outcome.status());
        assertEquals(1, repository.current(key).orElseThrow().revision());
    }

    @Test
    void committedSourceLeafSymlinkIsNotARecoveryCandidate() throws Exception {
        MinimapRepository repository = new MinimapRepository(temporaryDirectory);
        MapKey key = new MapKey("fpsmatch:test", "Test Map");
        MinimapStorageFixtures.Pair pair = MinimapStorageFixtures.validPair(1);
        PublishTransaction prepared = repository.prepare(
                reserve(repository, key, 0), pair.source(), pair.runtime()
        );
        assertTrue(repository.commit(prepared).committed());
        Path source = repository.mapDirectory(key).resolve("revisions")
                .resolve("1").resolve("source.fpsmap");
        Path outside = temporaryDirectory.resolve("committed-source.fpsmap");
        Files.write(outside, pair.source());
        Files.delete(source);
        try {
            Files.createSymbolicLink(source, outside.toAbsolutePath());
        } catch (UnsupportedOperationException | IOException | SecurityException unavailable) {
            Assumptions.assumeTrue(false, "Symbolic links are unavailable for this test");
        }

        PublishOutcome outcome = new RecoveryService(repository).recover(key);

        assertEquals(PublishOutcome.Status.UNAVAILABLE, outcome.status());
        assertEquals(1, repository.current(key).orElseThrow().revision());
    }

    @Test
    void committedRuntimeLeafSymlinkIsNotARecoveryCandidate() throws Exception {
        MinimapRepository repository = new MinimapRepository(temporaryDirectory);
        MapKey key = new MapKey("fpsmatch:test", "Test Map");
        MinimapStorageFixtures.Pair pair = MinimapStorageFixtures.validPair(1);
        PublishTransaction prepared = repository.prepare(
                reserve(repository, key, 0), pair.source(), pair.runtime()
        );
        assertTrue(repository.commit(prepared).committed());
        Path runtime = repository.mapDirectory(key).resolve("revisions")
                .resolve("1").resolve("runtime.fpsmapc");
        Path outside = temporaryDirectory.resolve("committed-runtime.fpsmapc");
        Files.write(outside, pair.runtime());
        Files.delete(runtime);
        try {
            Files.createSymbolicLink(runtime, outside.toAbsolutePath());
        } catch (UnsupportedOperationException | IOException | SecurityException unavailable) {
            Assumptions.assumeTrue(false, "Symbolic links are unavailable for this test");
        }

        PublishOutcome outcome = new RecoveryService(repository).recover(key);

        assertEquals(PublishOutcome.Status.UNAVAILABLE, outcome.status());
        assertEquals(1, repository.current(key).orElseThrow().revision());
    }

    @Test
    void recoveryRejectsRevisionDirectoryWhoseNameDiffersFromItsDescriptor() throws Exception {
        MinimapRepository repository = new MinimapRepository(temporaryDirectory);
        MapKey key = new MapKey("fpsmatch:test", "Test Map");
        MinimapStorageFixtures.Pair pair = MinimapStorageFixtures.validPair(1);
        PublishTransaction prepared = repository.prepare(
                reserve(repository, key, 0), pair.source(), pair.runtime()
        );
        assertTrue(repository.commit(prepared).committed());
        Path revisions = repository.mapDirectory(key).resolve("revisions");
        Files.move(revisions.resolve("1"), revisions.resolve("99"));
        Files.write(repository.mapDirectory(key).resolve("CURRENT"), new byte[]{1, 2, 3});

        PublishOutcome outcome = new RecoveryService(repository).recover(key);

        assertEquals(PublishOutcome.Status.ABORTED, outcome.status());
        assertTrue(repository.current(key).isEmpty());
    }

    @Test
    void recoveryRejectsNonCanonicalRevisionNamesBeforeWritingOrDeleting()
            throws Exception {
        MinimapRepository repository = new MinimapRepository(temporaryDirectory);
        MapKey key = new MapKey("fpsmatch:test", "Test Map");
        publish(repository, key, 1);
        Path mapDirectory = repository.mapDirectory(key);
        Path revisions = mapDirectory.resolve("revisions");
        Path alias = Files.createDirectory(revisions.resolve("01"));
        byte[] current = Files.readAllBytes(mapDirectory.resolve("CURRENT"));
        byte[] state = Files.readAllBytes(mapDirectory.resolve("publish-state.json"));

        assertThrows(
                ContainerStorageException.class,
                () -> new RecoveryService(repository).recover(key)
        );

        assertTrue(Files.isDirectory(alias));
        assertArrayEquals(current, Files.readAllBytes(mapDirectory.resolve("CURRENT")));
        assertArrayEquals(state, Files.readAllBytes(
                mapDirectory.resolve("publish-state.json")
        ));
    }

    @Test
    void currentCandidateReadIoFailureDoesNotTriggerRecoveryRebind() throws Exception {
        TargetReadFailingRepositoryFileSystem fileSystem =
                new TargetReadFailingRepositoryFileSystem(new NioRepositoryFileSystem());
        MinimapRepository repository = new MinimapRepository(temporaryDirectory, fileSystem);
        MapKey key = new MapKey("fpsmatch:test", "Test Map");
        publish(repository, key, 1);
        publish(repository, key, 2);
        Path mapDirectory = repository.mapDirectory(key);
        Path current = mapDirectory.resolve("CURRENT");
        byte[] currentBeforeRecovery = Files.readAllBytes(current);
        fileSystem.failNextRead(
                mapDirectory.resolve("revisions").resolve("2").resolve("source.fpsmap")
        );

        assertThrows(
                ContainerStorageException.class,
                () -> new RecoveryService(repository).recover(key)
        );
        assertArrayEquals(currentBeforeRecovery, Files.readAllBytes(current));
        assertEquals(2, repository.highWaterMark(key));
        assertFalse(Files.exists(mapDirectory.resolve("revisions").resolve("3")));
    }

    @Test
    void candidateRecordReadFailureIsNotMaskedAsMissing() throws Exception {
        TargetReadFailingRepositoryFileSystem fileSystem =
                new TargetReadFailingRepositoryFileSystem(new NioRepositoryFileSystem());
        MinimapRepository repository = new MinimapRepository(temporaryDirectory, fileSystem);
        MapKey key = new MapKey("fpsmatch:test", "Test Map");
        publish(repository, key, 1);
        Path mapDirectory = repository.mapDirectory(key);
        Path current = mapDirectory.resolve("CURRENT");
        Path state = mapDirectory.resolve("publish-state.json");
        Path record = mapDirectory.resolve("revisions").resolve("1")
                .resolve("publish-record.json");
        byte[] currentBeforeRecovery = Files.readAllBytes(current);
        byte[] stateBeforeRecovery = Files.readAllBytes(state);
        Files.delete(record);
        fileSystem.failNextRead(record);

        assertThrows(
                ContainerStorageException.class,
                () -> new RecoveryService(repository).recover(key)
        );
        assertArrayEquals(currentBeforeRecovery, Files.readAllBytes(current));
        assertArrayEquals(stateBeforeRecovery, Files.readAllBytes(state));
        assertFalse(Files.exists(mapDirectory.resolve("revisions").resolve("2")));
    }

    @Test
    void nonDirectoryRevisionContainerIsNotTreatedAsMissing() throws Exception {
        MinimapRepository repository = new MinimapRepository(temporaryDirectory);
        MapKey key = new MapKey("fpsmatch:test", "Test Map");
        Path mapDirectory = repository.mapDirectory(key);
        Files.createDirectories(mapDirectory);
        Path revisions = mapDirectory.resolve("revisions");
        Files.write(revisions, new byte[]{1});

        assertThrows(
                ContainerStorageException.class,
                () -> new RecoveryService(repository).recover(key)
        );
        assertTrue(Files.isRegularFile(revisions));
    }

    @Test
    void nonDirectoryTransactionsContainerIsNotTreatedAsMissingDuringCleanup()
            throws Exception {
        MapKey key = new MapKey("fpsmatch:test", "Test Map");
        MinimapRepository layout = new MinimapRepository(temporaryDirectory);
        Path mapDirectory = layout.mapDirectory(key);
        Path transactions = mapDirectory.resolve("transactions");
        Path current = mapDirectory.resolve("CURRENT");
        Files.createDirectories(transactions);
        Files.writeString(
                mapDirectory.resolve("publish-state.json"),
                "{\"highWaterMark\":\"0\"}"
        );
        RepositoryFileSystem fileSystem = new ReplaceTransactionsOnSecondCurrentReadFileSystem(
                new NioRepositoryFileSystem(), current, transactions
        );
        MinimapRepository repository = new MinimapRepository(
                temporaryDirectory, fileSystem
        );

        assertThrows(
                ContainerStorageException.class,
                () -> new RecoveryService(repository).recover(key)
        );
        assertTrue(Files.isRegularFile(transactions));
    }

    @Test
    void overdeepTransactionTreeFailsBeforeDeletingAnyEntry() throws Exception {
        MinimapRepository repository = new MinimapRepository(temporaryDirectory);
        MapKey key = new MapKey("fpsmatch:test", "Test Map");
        Path mapDirectory = repository.mapDirectory(key);
        Path transactions = mapDirectory.resolve("transactions");
        Path sentinel = transactions.resolve("zz-sentinel.bin");
        Files.createDirectories(transactions);
        Files.write(sentinel, new byte[]{7});
        Path deep = transactions.resolve("aa-deep");
        for (int depth = 0; depth < 18; depth++) {
            deep = deep.resolve("level-" + depth);
        }
        Files.createDirectories(deep);
        Files.write(deep.resolve("payload.bin"), new byte[]{1});
        Files.writeString(
                mapDirectory.resolve("publish-state.json"),
                "{\"highWaterMark\":\"0\"}"
        );

        assertThrows(
                ContainerStorageException.class,
                () -> new RecoveryService(repository).recover(key)
        );
        assertTrue(Files.isRegularFile(sentinel));
        assertTrue(Files.isRegularFile(deep.resolve("payload.bin")));
    }

    @Test
    void recoveryPrevalidatesEveryOrphanRevisionBeforeDeletingAnyRevision()
            throws Exception {
        MinimapRepository repository = new MinimapRepository(temporaryDirectory);
        MapKey key = new MapKey("fpsmatch:test", "Test Map");
        MinimapStorageFixtures.Pair firstPair = MinimapStorageFixtures.validPair(1);
        PublishTransaction first = repository.prepare(
                reserve(repository, key, 0), firstPair.source(), firstPair.runtime()
        );
        MinimapStorageFixtures.Pair secondPair = MinimapStorageFixtures.validPair(2);
        PublishTransaction second = repository.prepare(
                reserve(repository, key, 0), secondPair.source(), secondPair.runtime()
        );
        Path revisions = repository.mapDirectory(key).resolve("revisions");
        Files.createDirectories(revisions);
        Files.move(first.transactionDirectory(), revisions.resolve("1"));
        Files.move(second.transactionDirectory(), revisions.resolve("2"));
        Path deep = revisions.resolve("2");
        for (int depth = 0; depth < 17; depth++) {
            deep = deep.resolve("level-" + depth);
        }
        Files.createDirectories(deep);
        Files.write(deep.resolve("sentinel.bin"), new byte[]{1});

        assertThrows(
                ContainerStorageException.class,
                () -> new RecoveryService(repository).recover(key)
        );

        assertTrue(Files.isDirectory(revisions.resolve("1")));
        assertTrue(Files.isDirectory(revisions.resolve("2")));
        assertTrue(Files.isRegularFile(deep.resolve("sentinel.bin")));
    }

    @Test
    void persistedRecoveryMarkersRemainDegradedUntilEveryMarkerIsCleared()
            throws Exception {
        MinimapRepository layout = new MinimapRepository(temporaryDirectory);
        MapKey first = new MapKey("fpsmatch:first", "First Map");
        MapKey second = new MapKey("fpsmatch:second", "Second Map");
        Path firstDirectory = layout.mapDirectory(first);
        Path secondDirectory = layout.mapDirectory(second);
        Files.createDirectories(firstDirectory);
        Files.createDirectories(secondDirectory);
        Files.write(firstDirectory.resolve("RECOVERY_REQUIRED"), new byte[]{1});
        Files.write(secondDirectory.resolve("RECOVERY_REQUIRED"), new byte[]{2});
        MinimapRepository restarted = new MinimapRepository(temporaryDirectory);

        assertTrue(restarted.isDurabilityDegraded());
        Files.delete(firstDirectory.resolve("RECOVERY_REQUIRED"));
        assertTrue(restarted.isDurabilityDegraded());
        Files.delete(secondDirectory.resolve("RECOVERY_REQUIRED"));
        assertFalse(restarted.isDurabilityDegraded());
    }

    private static void publish(MinimapRepository repository, MapKey key, long revision) {
        MinimapStorageFixtures.Pair pair = MinimapStorageFixtures.validPair(revision);
        PublishTransaction prepared = repository.prepare(
                reserve(repository, key, revision - 1), pair.source(), pair.runtime()
        );
        if (!repository.commit(prepared).committed()) {
            throw new AssertionError("Fixture publish did not commit");
        }
    }

    private static final class ReplaceTransactionsOnSecondCurrentReadFileSystem
            implements RepositoryFileSystem {
        private final RepositoryFileSystem delegate;
        private final Path current;
        private final Path transactions;
        private int currentReads;

        private ReplaceTransactionsOnSecondCurrentReadFileSystem(
                RepositoryFileSystem delegate,
                Path current,
                Path transactions
        ) {
            this.delegate = delegate;
            this.current = current.toAbsolutePath().normalize();
            this.transactions = transactions;
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
        public BoundedReadChannel openBoundedReadChannel(Path file, long maximumBytes)
                throws IOException {
            if (file.toAbsolutePath().normalize().equals(current)
                    && ++currentReads == 2) {
                Files.delete(transactions);
                Files.write(transactions, new byte[]{1});
            }
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
