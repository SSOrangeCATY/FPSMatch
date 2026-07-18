package com.phasetranscrystal.fpsmatch.core.minimap.storage;

import com.phasetranscrystal.fpsmatch.core.minimap.model.MapKey;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinimapRepositoryPublishTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void preparesAndCommitsAReservedPairWithAnAtomicCurrentPointer() {
        MinimapRepository repository = new MinimapRepository(
                temporaryDirectory, new DirectoryFsyncTolerantFileSystem(new NioRepositoryFileSystem())
        );
        MapKey key = new MapKey("fpsmatch:test", "Test Map");
        MinimapStorageFixtures.Pair pair = MinimapStorageFixtures.validPair(1);

        PublishTransaction reserved = reserve(repository, key, 0);
        PublishTransaction prepared = repository.prepare(
                reserved, pair.source(), pair.runtime()
        );

        assertEquals(PublishState.PREPARED, prepared.state());
        PublishOutcome outcome = repository.commit(prepared);

        assertTrue(outcome.committed());
        assertEquals(PublishState.COMMITTED, outcome.state());
        assertEquals(1, repository.current(key).orElseThrow().revision());
        assertEquals(1, repository.highWaterMark(key));
        assertThrows(ContainerStorageException.class, () -> repository.commit(prepared));
    }

    @Test
    void aPreparedTransactionCannotCommitAgainstAChangedCurrentRevision() {
        MinimapRepository repository = new MinimapRepository(
                temporaryDirectory, new DirectoryFsyncTolerantFileSystem(new NioRepositoryFileSystem())
        );
        MapKey key = new MapKey("fpsmatch:test", "Test Map");
        MinimapStorageFixtures.Pair pair = MinimapStorageFixtures.validPair(1);

        PublishTransaction first = repository.prepare(
                reserve(repository, key, 0), pair.source(), pair.runtime()
        );
        PublishOutcome committed = repository.commit(first);
        assertTrue(committed.committed());

        PublishTransaction second = repository.prepare(
                reserve(repository, key, 1),
                MinimapStorageFixtures.validPair(2).source(),
                MinimapStorageFixtures.validPair(2).runtime()
        );
        assertEquals(2, second.descriptor().publishRevision());
        assertNotNull(repository.current(key).orElse(null));
        assertFalse(repository.isDurabilityDegraded());
    }

    @Test
    void currentReplaceFollowedByParentFsyncFailureReturnsUnknownAndSuspendsPublishing() {
        FailAfterReplaceFileSystem fileSystem = new FailAfterReplaceFileSystem(
                new DirectoryFsyncTolerantFileSystem(new NioRepositoryFileSystem())
        );
        MinimapRepository repository = new MinimapRepository(temporaryDirectory, fileSystem);
        MapKey key = new MapKey("fpsmatch:test", "Test Map");
        MinimapStorageFixtures.Pair pair = MinimapStorageFixtures.validPair(1);
        PublishTransaction prepared = repository.prepare(
                reserve(repository, key, 0), pair.source(), pair.runtime()
        );

        PublishOutcome outcome = repository.commit(prepared);

        assertTrue(outcome.unknown());
        assertEquals(PublishState.PREPARED, outcome.state());
        assertTrue(repository.current(key).isEmpty());
        assertTrue(repository.isDurabilityDegraded());
        assertThrows(ContainerStorageException.class, () -> repository.commit(prepared));
        assertThrows(ContainerStorageException.class, () -> reserve(repository, key, 1));
    }

    @Test
    void currentReplaceFollowedByUncheckedParentFsyncFailureReturnsUnknown() {
        FailAfterReplaceFileSystem fileSystem = new FailAfterReplaceFileSystem(
                new DirectoryFsyncTolerantFileSystem(new NioRepositoryFileSystem()),
                true
        );
        MinimapRepository repository = new MinimapRepository(temporaryDirectory, fileSystem);
        MapKey key = new MapKey("fpsmatch:test", "Test Map");
        MinimapStorageFixtures.Pair pair = MinimapStorageFixtures.validPair(1);
        PublishTransaction prepared = repository.prepare(
                reserve(repository, key, 0), pair.source(), pair.runtime()
        );

        PublishOutcome outcome = repository.commit(prepared);

        assertTrue(outcome.unknown());
        assertEquals(PublishState.PREPARED, outcome.state());
        assertTrue(repository.current(key).isEmpty());
        assertTrue(repository.isDurabilityDegraded());
        assertThrows(ContainerStorageException.class, () -> repository.commit(prepared));
    }

    @Test
    void recoveryCommitsAPreparedRevisionReferencedByCurrentAndResumesPublishing() {
        FailAfterReplaceFileSystem fileSystem = new FailAfterReplaceFileSystem(
                new DirectoryFsyncTolerantFileSystem(new NioRepositoryFileSystem())
        );
        MinimapRepository repository = new MinimapRepository(temporaryDirectory, fileSystem);
        MapKey key = new MapKey("fpsmatch:test", "Test Map");
        MinimapStorageFixtures.Pair pair = MinimapStorageFixtures.validPair(1);
        PublishTransaction prepared = repository.prepare(
                reserve(repository, key, 0), pair.source(), pair.runtime()
        );
        assertTrue(repository.commit(prepared).unknown());

        PublishOutcome recovered = new RecoveryService(repository).recover(key);

        assertTrue(recovered.committed());
        assertEquals(1, repository.current(key).orElseThrow().revision());
        assertFalse(repository.isDurabilityDegraded());
        assertEquals(2, reserve(repository, key, 1).publishRevision());
    }

    @Test
    void unknownCommitSuspendsPublishingAcrossRepositoryRestartUntilRecovery() {
        FailAfterReplaceFileSystem fileSystem = new FailAfterReplaceFileSystem(
                new DirectoryFsyncTolerantFileSystem(new NioRepositoryFileSystem())
        );
        MinimapRepository repository = new MinimapRepository(temporaryDirectory, fileSystem);
        MapKey key = new MapKey("fpsmatch:test", "Test Map");
        MinimapStorageFixtures.Pair pair = MinimapStorageFixtures.validPair(1);
        PublishTransaction prepared = repository.prepare(
                reserve(repository, key, 0), pair.source(), pair.runtime()
        );
        assertTrue(repository.commit(prepared).unknown());

        MinimapRepository restarted = new MinimapRepository(
                temporaryDirectory,
                new DirectoryFsyncTolerantFileSystem(new NioRepositoryFileSystem())
        );
        assertTrue(restarted.isDurabilityDegraded());
        assertTrue(restarted.current(key).isEmpty());
        assertThrows(ContainerStorageException.class, () -> reserve(restarted, key, 1));

        assertTrue(new RecoveryService(restarted).recover(key).committed());
        assertFalse(restarted.isDurabilityDegraded());
        assertEquals(2, reserve(restarted, key, 1).publishRevision());
    }

    @Test
    void damagedCurrentPairIsReboundToAHigherRevisionInsteadOfRollingBack() throws Exception {
        MinimapRepository repository = new MinimapRepository(
                temporaryDirectory,
                new DirectoryFsyncTolerantFileSystem(new NioRepositoryFileSystem())
        );
        MapKey key = new MapKey("fpsmatch:test", "Test Map");
        MinimapStorageFixtures.Pair firstPair = MinimapStorageFixtures.validPair(1);
        PublishTransaction first = repository.prepare(
                reserve(repository, key, 0), firstPair.source(), firstPair.runtime()
        );
        assertTrue(repository.commit(first).committed());
        MinimapStorageFixtures.Pair secondPair = MinimapStorageFixtures.validPair(2);
        PublishTransaction second = repository.prepare(
                reserve(repository, key, 1), secondPair.source(), secondPair.runtime()
        );
        assertTrue(repository.commit(second).committed());

        Path damagedRuntime = repository.mapDirectory(key)
                .resolve("revisions").resolve("2").resolve("runtime.fpsmapc");
        Files.delete(damagedRuntime);

        PublishOutcome recovered = new RecoveryService(repository).recover(key);

        assertTrue(recovered.committed());
        CurrentPointer reboundPointer = repository.current(key).orElseThrow();
        assertEquals(2, reboundPointer.expectedBaseRevision());
        assertEquals(3, reboundPointer.revision());
        assertEquals(3, repository.highWaterMark(key));
        Path reboundDirectory = repository.mapDirectory(key).resolve("revisions").resolve("3");
        try (com.phasetranscrystal.fpsmatch.core.minimap.format.SourceMap originalSource =
                     com.phasetranscrystal.fpsmatch.core.minimap.format.SourceMapReader.read(
                             firstPair.source()
                     );
             com.phasetranscrystal.fpsmatch.core.minimap.format.RuntimeMap originalRuntime =
                     com.phasetranscrystal.fpsmatch.core.minimap.format.RuntimeMapReader.read(
                             firstPair.runtime()
                     );
             com.phasetranscrystal.fpsmatch.core.minimap.format.SourceMap source =
                     com.phasetranscrystal.fpsmatch.core.minimap.format.SourceMapReader.read(
                             Files.readAllBytes(reboundDirectory.resolve("source.fpsmap"))
                     );
             com.phasetranscrystal.fpsmatch.core.minimap.format.RuntimeMap runtime =
                     com.phasetranscrystal.fpsmatch.core.minimap.format.RuntimeMapReader.read(
                             Files.readAllBytes(reboundDirectory.resolve("runtime.fpsmapc"))
                     )) {
            assertEquals(3, source.manifest().revision());
            assertEquals(3, runtime.manifest().publishRevision());
            assertEquals(source.sourceHash(), runtime.manifest().sourceHash());
            assertEquals(1, source.manifest().provenance().orElseThrow().originRevision());
            assertEquals(
                    originalSource.sourceHash(),
                    source.manifest().provenance().orElseThrow().originSourceHash()
            );
            for (com.phasetranscrystal.fpsmatch.core.minimap.model.ContainerPath path
                    : originalSource.paths()) {
                if (!path.equals(com.phasetranscrystal.fpsmatch.core.minimap.format
                        .MinimapContainerLayout.SOURCE_MANIFEST)) {
                    assertArrayEquals(originalSource.entryBytes(path), source.entryBytes(path));
                }
            }
            for (com.phasetranscrystal.fpsmatch.core.minimap.model.ContainerPath path
                    : originalRuntime.paths()) {
                if (!path.equals(com.phasetranscrystal.fpsmatch.core.minimap.format
                        .MinimapContainerLayout.RUNTIME_MANIFEST)) {
                    assertArrayEquals(originalRuntime.entryBytes(path), runtime.entryBytes(path));
                }
            }
            PublishRecord record = PublishRecord.read(
                    Files.readAllBytes(reboundDirectory.resolve("publish-record.json"))
            );
            assertEquals(PairValidation.METADATA_TRUSTED, record.pairValidation());
            assertEquals(source.sourceHash(), record.descriptor().sourceHash());
            assertEquals(runtime.runtimeHash(), record.descriptor().runtimeHash());
            assertEquals(
                    runtime.runtimeContainerHash(),
                    record.descriptor().runtimeContainerHash()
            );
        }
    }

    @Test
    void damagedCurrentRebindIgnoresCommittedDirectoriesAboveCurrent() throws Exception {
        MinimapRepository repository = new MinimapRepository(
                temporaryDirectory,
                new DirectoryFsyncTolerantFileSystem(new NioRepositoryFileSystem())
        );
        MapKey key = new MapKey("fpsmatch:test", "Test Map");
        for (long revision = 1; revision <= 2; revision++) {
            MinimapStorageFixtures.Pair pair = MinimapStorageFixtures.validPair(revision);
            PublishTransaction prepared = repository.prepare(
                    reserve(repository, key, revision - 1), pair.source(), pair.runtime()
            );
            assertTrue(repository.commit(prepared).committed());
        }
        byte[] currentTwo = repository.current(key).orElseThrow().canonicalBytes();
        MinimapStorageFixtures.Pair thirdPair = MinimapStorageFixtures.validPair(3);
        PublishTransaction third = repository.prepare(
                reserve(repository, key, 2), thirdPair.source(), thirdPair.runtime()
        );
        assertTrue(repository.commit(third).committed());
        Path mapDirectory = repository.mapDirectory(key);
        Files.write(mapDirectory.resolve("CURRENT"), currentTwo);
        Files.delete(mapDirectory.resolve("revisions").resolve("2")
                .resolve("runtime.fpsmapc"));

        PublishOutcome recovered = new RecoveryService(repository).recover(key);

        assertTrue(recovered.committed());
        assertEquals(4, repository.current(key).orElseThrow().revision());
        try (com.phasetranscrystal.fpsmatch.core.minimap.format.SourceMap source =
                     com.phasetranscrystal.fpsmatch.core.minimap.format.SourceMapReader.read(
                             Files.readAllBytes(mapDirectory.resolve("revisions").resolve("4")
                                     .resolve("source.fpsmap"))
                     )) {
            assertEquals(1, source.manifest().provenance().orElseThrow().originRevision());
        }
    }

    @Test
    void recoveryAndGcNeverReadWholeRevisionContainers() throws Exception {
        RepositoryFileSystem durableFileSystem = new DirectoryFsyncTolerantFileSystem(
                new NioRepositoryFileSystem()
        );
        MinimapRepository repository = new MinimapRepository(
                temporaryDirectory, durableFileSystem
        );
        MapKey key = new MapKey("fpsmatch:test", "Test Map");
        for (long revision = 1; revision <= 2; revision++) {
            MinimapStorageFixtures.Pair pair = MinimapStorageFixtures.validPair(revision);
            PublishTransaction prepared = repository.prepare(
                    reserve(repository, key, revision - 1), pair.source(), pair.runtime()
            );
            assertTrue(repository.commit(prepared).committed());
        }
        Path revisions = repository.mapDirectory(key).resolve("revisions");
        Files.delete(revisions.resolve("2").resolve("runtime.fpsmapc"));
        MinimapRepository restarted = new MinimapRepository(
                temporaryDirectory,
                new WholeContainerReadRejectingFileSystem(durableFileSystem)
        );

        assertTrue(new RecoveryService(restarted).recover(key).committed());
        assertEquals(3, restarted.current(key).orElseThrow().revision());
        restarted.collectGarbage(key);
        assertTrue(Files.isDirectory(revisions.resolve("1")));
        assertTrue(Files.isDirectory(revisions.resolve("3")));
    }

    @Test
    void concurrentPreparedTokensUseCasAndCannotMoveCurrentBackward() throws Exception {
        MinimapRepository repository = new MinimapRepository(
                temporaryDirectory,
                new DirectoryFsyncTolerantFileSystem(new NioRepositoryFileSystem())
        );
        MapKey key = new MapKey("fpsmatch:test", "Test Map");
        PublishTransaction firstReservation = reserve(repository, key, 0);
        PublishTransaction secondReservation = reserve(repository, key, 0);
        MinimapStorageFixtures.Pair firstPair = MinimapStorageFixtures.validPair(1);
        MinimapStorageFixtures.Pair secondPair = MinimapStorageFixtures.validPair(2);
        PublishTransaction first = repository.prepare(
                firstReservation, firstPair.source(), firstPair.runtime()
        );
        PublishTransaction second = repository.prepare(
                secondReservation, secondPair.source(), secondPair.runtime()
        );

        assertTrue(repository.commit(second).committed());
        assertEquals(2, repository.current(key).orElseThrow().revision());
        assertThrows(ContainerStorageException.class, () -> repository.commit(first));
        assertEquals(PublishState.ABORTED,
                PublishRecord.read(java.nio.file.Files.readAllBytes(
                        first.transactionDirectory().resolve("publish-record.json")
                )).state());
        assertEquals(2, repository.current(key).orElseThrow().revision());
        assertEquals(2, repository.highWaterMark(key));
    }

    @Test
    void concurrentRepositoriesAllowOnlyOnePreparedTokenToWinCas() throws Exception {
        BarrierCurrentReadFileSystem fileSystem = new BarrierCurrentReadFileSystem(
                new DirectoryFsyncTolerantFileSystem(new NioRepositoryFileSystem())
        );
        MinimapRepository firstRepository = new MinimapRepository(temporaryDirectory, fileSystem);
        MinimapRepository secondRepository = new MinimapRepository(temporaryDirectory, fileSystem);
        MapKey key = new MapKey("fpsmatch:test", "Test Map");
        Files.createDirectories(firstRepository.mapDirectory(key));
        Files.write(
                firstRepository.mapDirectory(key).resolve("CURRENT"),
                new CurrentPointer(0, 0, com.phasetranscrystal.fpsmatch.core.minimap.model.Sha256
                        .parse("0".repeat(64))).canonicalBytes()
        );
        PublishTransaction first = firstRepository.prepare(
                reserve(firstRepository, key, 0),
                MinimapStorageFixtures.validPair(1).source(),
                MinimapStorageFixtures.validPair(1).runtime()
        );
        PublishTransaction second = secondRepository.prepare(
                reserve(secondRepository, key, 0),
                MinimapStorageFixtures.validPair(2).source(),
                MinimapStorageFixtures.validPair(2).runtime()
        );
        fileSystem.enableCurrentBarrier();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Future<PublishOutcome>> futures = new ArrayList<>();
        try {
            futures.add(executor.submit(() -> firstRepository.commit(first)));
            futures.add(executor.submit(() -> secondRepository.commit(second)));
            int committed = 0;
            for (Future<PublishOutcome> future : futures) {
                try {
                    if (future.get(5, TimeUnit.SECONDS).committed()) {
                        committed++;
                    }
                } catch (ExecutionException expectedConflict) {
                    // The losing token is expected to be persisted as ABORTED.
                }
            }
            assertEquals(1, committed);
            assertTrue(List.of(1L, 2L).contains(
                    firstRepository.current(key).orElseThrow().revision()
            ));
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void malformedCandidatePairDoesNotAdvanceAReservedTransaction() {
        MinimapRepository repository = new MinimapRepository(
                temporaryDirectory,
                new DirectoryFsyncTolerantFileSystem(new NioRepositoryFileSystem())
        );
        MapKey key = new MapKey("fpsmatch:test", "Test Map");
        PublishTransaction reserved = reserve(repository, key, 0);

        assertThrows(ContainerStorageException.class,
                () -> repository.prepare(reserved, new byte[]{1}, new byte[]{2}));
        assertFalse(repository.current(key).isPresent());

        MinimapStorageFixtures.Pair pair = MinimapStorageFixtures.validPair(1);
        PublishTransaction prepared = repository.prepare(
                reserved, pair.source(), pair.runtime()
        );
        assertEquals(PublishState.PREPARED, prepared.state());
    }

    @Test
    void prepareRequiresTheAuthoritativeDocumentAndDimensionBinding() {
        MinimapRepository repository = new MinimapRepository(temporaryDirectory);
        MapKey key = new MapKey("fpsmatch:test", "Test Map");
        MinimapStorageFixtures.Pair pair = MinimapStorageFixtures.validPair(1);

        PublishTransaction wrongDocument = repository.reserve(
                key,
                com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId.parse(
                        "minecraft:overworld"
                ),
                com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId.parse(
                        "fpsmatch:wrong-document"
                ),
                0
        );
        assertThrows(ContainerStorageException.class, () -> repository.prepare(
                wrongDocument, pair.source(), pair.runtime()
        ));

        MinimapRepository anotherRepository = new MinimapRepository(
                temporaryDirectory.resolve("dimension")
        );
        PublishTransaction wrongDimension = anotherRepository.reserve(
                key,
                com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId.parse(
                        "minecraft:the_nether"
                ),
                com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId.parse(
                        "fpsmatch:test-map"
                ),
                0
        );
        assertThrows(ContainerStorageException.class, () -> anotherRepository.prepare(
                wrongDimension, pair.source(), pair.runtime()
        ));
    }

    @Test
    void prepareCannotReplaceThePersistedReservationTarget() throws Exception {
        MinimapRepository repository = new MinimapRepository(temporaryDirectory);
        MapKey key = new MapKey("fpsmatch:test", "Test Map");
        PublishTransaction reserved = repository.reserve(
                key,
                com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId.parse(
                        "minecraft:the_nether"
                ),
                com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId.parse(
                        "fpsmatch:reserved-document"
                ),
                0
        );
        PublishTransaction forged = new PublishTransaction(
                new PublishTarget(
                        key,
                        com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId.parse(
                                "minecraft:overworld"
                        ),
                        com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId.parse(
                                "fpsmatch:test-map"
                        )
                ),
                reserved.descriptor(),
                reserved.transactionDirectory(),
                reserved.expiresAt()
        );
        MinimapStorageFixtures.Pair pair = MinimapStorageFixtures.validPair(1);

        assertThrows(ContainerStorageException.class, () -> repository.prepare(
                forged, pair.source(), pair.runtime()
        ));
        assertEquals(
                PublishState.RESERVED,
                PublishRecord.read(Files.readAllBytes(
                        reserved.transactionDirectory().resolve("publish-record.json")
                )).state()
        );
    }

    @Test
    void commitCannotReplaceThePersistedPreparedTarget() {
        MinimapRepository repository = new MinimapRepository(temporaryDirectory);
        MapKey key = new MapKey("fpsmatch:test", "Test Map");
        MinimapStorageFixtures.Pair pair = MinimapStorageFixtures.validPair(1);
        PublishTransaction prepared = repository.prepare(
                reserve(repository, key, 0), pair.source(), pair.runtime()
        );
        PublishTransaction forged = new PublishTransaction(
                new PublishTarget(
                        key,
                        com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId.parse(
                                "minecraft:the_nether"
                        ),
                        com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId.parse(
                                "fpsmatch:forged-document"
                        )
                ),
                prepared.descriptor(),
                prepared.transactionDirectory(),
                prepared.expiresAt(),
                PublishState.PREPARED
        );

        assertThrows(ContainerStorageException.class, () -> repository.commit(forged));
        assertTrue(repository.current(key).isEmpty());
    }

    @Test
    void ordinaryReservationCannotChangeTheCurrentPublishedTarget() {
        MinimapRepository repository = new MinimapRepository(temporaryDirectory);
        MapKey key = new MapKey("fpsmatch:test", "Test Map");
        MinimapStorageFixtures.Pair pair = MinimapStorageFixtures.validPair(1);
        PublishTransaction first = repository.prepare(
                reserve(repository, key, 0), pair.source(), pair.runtime()
        );
        assertTrue(repository.commit(first).committed());

        assertThrows(
                ContainerStorageException.class,
                () -> repository.reserve(
                        key,
                        com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId.parse(
                                "minecraft:the_nether"
                        ),
                        com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId.parse(
                                "fpsmatch:other-document"
                        ),
                        1
                )
        );

        assertEquals(1, repository.highWaterMark(key));
    }

    @Test
    void commitRejectsAPersistedPairChangedAfterPrepare() throws Exception {
        MinimapRepository repository = new MinimapRepository(
                temporaryDirectory,
                new DirectoryFsyncTolerantFileSystem(new NioRepositoryFileSystem())
        );
        MapKey key = new MapKey("fpsmatch:test", "Test Map");
        MinimapStorageFixtures.Pair pair = MinimapStorageFixtures.validPair(1);
        PublishTransaction prepared = repository.prepare(
                reserve(repository, key, 0), pair.source(), pair.runtime()
        );
        Files.write(
                prepared.transactionDirectory().resolve("source.fpsmap"),
                new byte[]{1, 2, 3}
        );

        assertThrows(ContainerStorageException.class, () -> repository.commit(prepared));
        assertTrue(repository.current(key).isEmpty());
        assertEquals(
                PublishState.ABORTED,
                PublishRecord.read(Files.readAllBytes(
                        prepared.transactionDirectory().resolve("publish-record.json")
                )).state()
        );
    }

    @Test
    void commitRejectsAPreparedSourceLeafReplacedByASymlink() throws Exception {
        MinimapRepository repository = new MinimapRepository(temporaryDirectory);
        MapKey key = new MapKey("fpsmatch:test", "Test Map");
        MinimapStorageFixtures.Pair pair = MinimapStorageFixtures.validPair(1);
        PublishTransaction prepared = repository.prepare(
                reserve(repository, key, 0), pair.source(), pair.runtime()
        );
        Path source = prepared.transactionDirectory().resolve("source.fpsmap");
        Path outside = temporaryDirectory.resolve("prepared-source.fpsmap");
        Files.write(outside, pair.source());
        Files.delete(source);
        try {
            Files.createSymbolicLink(source, outside.toAbsolutePath());
        } catch (UnsupportedOperationException | IOException | SecurityException unavailable) {
            Assumptions.assumeTrue(false, "Symbolic links are unavailable for this test");
        }

        assertThrows(ContainerStorageException.class, () -> repository.commit(prepared));
        assertTrue(repository.current(key).isEmpty());
        assertArrayEquals(pair.source(), Files.readAllBytes(outside));
        assertEquals(
                PublishState.ABORTED,
                PublishRecord.read(Files.readAllBytes(
                        prepared.transactionDirectory().resolve("publish-record.json")
                )).state()
        );
    }

    @Test
    void commitValidatesPreparedContainersWithoutWholeFileReads() {
        RepositoryFileSystem durableFileSystem = new DirectoryFsyncTolerantFileSystem(
                new NioRepositoryFileSystem()
        );
        MinimapRepository repository = new MinimapRepository(
                temporaryDirectory, durableFileSystem
        );
        MapKey key = new MapKey("fpsmatch:test", "Test Map");
        MinimapStorageFixtures.Pair pair = MinimapStorageFixtures.validPair(1);
        PublishTransaction prepared = repository.prepare(
                reserve(repository, key, 0), pair.source(), pair.runtime()
        );
        MinimapRepository restarted = new MinimapRepository(
                temporaryDirectory,
                new WholeContainerReadRejectingFileSystem(durableFileSystem)
        );

        assertTrue(restarted.commit(prepared).committed());
    }

    @Test
    void prepareUsesThePersistedReservationDescriptorAsAuthority() throws Exception {
        MinimapRepository repository = new MinimapRepository(
                temporaryDirectory,
                new DirectoryFsyncTolerantFileSystem(new NioRepositoryFileSystem())
        );
        MapKey key = new MapKey("fpsmatch:test", "Test Map");
        PublishTransaction reserved = reserve(repository, key, 0);
        com.phasetranscrystal.fpsmatch.core.minimap.model.Sha256 zero =
                com.phasetranscrystal.fpsmatch.core.minimap.model.Sha256.parse("0".repeat(64));
        PublishTransaction forged = new PublishTransaction(
                reserved.target(),
                new PublishDescriptor(
                        reserved.publishToken(), 0, 2, zero, zero, zero
                ),
                reserved.transactionDirectory(),
                reserved.expiresAt()
        );
        MinimapStorageFixtures.Pair revisionTwo = MinimapStorageFixtures.validPair(2);

        assertThrows(ContainerStorageException.class, () -> repository.prepare(
                forged, revisionTwo.source(), revisionTwo.runtime()
        ));
        assertEquals(
                PublishState.RESERVED,
                PublishRecord.read(Files.readAllBytes(
                        reserved.transactionDirectory().resolve("publish-record.json")
                )).state()
        );
    }

    @Test
    void commitRejectsCurrentPointerWithAConflictingDescriptorChecksum() throws Exception {
        MinimapRepository repository = new MinimapRepository(
                temporaryDirectory,
                new DirectoryFsyncTolerantFileSystem(new NioRepositoryFileSystem())
        );
        MapKey key = new MapKey("fpsmatch:test", "Test Map");
        MinimapStorageFixtures.Pair firstPair = MinimapStorageFixtures.validPair(1);
        PublishTransaction first = repository.prepare(
                reserve(repository, key, 0), firstPair.source(), firstPair.runtime()
        );
        assertTrue(repository.commit(first).committed());
        PublishTransaction second = reserve(repository, key, 1);
        MinimapStorageFixtures.Pair secondPair = MinimapStorageFixtures.validPair(2);
        PublishTransaction prepared = repository.prepare(
                second, secondPair.source(), secondPair.runtime()
        );
        Path current = repository.mapDirectory(key).resolve("CURRENT");
        CurrentPointer pointer = CurrentPointer.read(Files.readAllBytes(current));
        Files.write(
                current,
                new CurrentPointer(
                        pointer.expectedBaseRevision(),
                        pointer.revision(),
                        com.phasetranscrystal.fpsmatch.core.minimap.model.Sha256
                                .parse("f".repeat(64))
                ).canonicalBytes()
        );

        assertThrows(ContainerStorageException.class, () -> repository.commit(prepared));
        assertEquals(
                PublishState.ABORTED,
                PublishRecord.read(Files.readAllBytes(
                        prepared.transactionDirectory().resolve("publish-record.json")
                )).state()
        );
    }

    @Test
    void currentCandidateReadIoFailureDoesNotAbortPreparedCommitAsConflict()
            throws Exception {
        TargetReadFailingRepositoryFileSystem fileSystem =
                new TargetReadFailingRepositoryFileSystem(new NioRepositoryFileSystem());
        MinimapRepository repository = new MinimapRepository(temporaryDirectory, fileSystem);
        MapKey key = new MapKey("fpsmatch:test", "Test Map");
        MinimapStorageFixtures.Pair firstPair = MinimapStorageFixtures.validPair(1);
        PublishTransaction first = repository.prepare(
                reserve(repository, key, 0), firstPair.source(), firstPair.runtime()
        );
        assertTrue(repository.commit(first).committed());
        MinimapStorageFixtures.Pair secondPair = MinimapStorageFixtures.validPair(2);
        PublishTransaction second = repository.prepare(
                reserve(repository, key, 1), secondPair.source(), secondPair.runtime()
        );
        fileSystem.failNextRead(
                repository.mapDirectory(key).resolve("revisions").resolve("1")
                        .resolve("source.fpsmap")
        );

        assertThrows(ContainerStorageException.class, () -> repository.commit(second));
        assertEquals(
                PublishState.PREPARED,
                PublishRecord.read(Files.readAllBytes(
                        second.transactionDirectory().resolve("publish-record.json")
                )).state()
        );
        assertEquals(1, repository.current(key).orElseThrow().revision());
    }

    @Test
    void committedRecordFailureAfterTheCommitPointRemainsCommittedAndRecovers() {
        FailCommittedRecordFileSystem fileSystem = new FailCommittedRecordFileSystem(
                new DirectoryFsyncTolerantFileSystem(new NioRepositoryFileSystem())
        );
        MinimapRepository repository = new MinimapRepository(temporaryDirectory, fileSystem);
        MapKey key = new MapKey("fpsmatch:test", "Test Map");
        MinimapStorageFixtures.Pair pair = MinimapStorageFixtures.validPair(1);
        PublishTransaction prepared = repository.prepare(
                reserve(repository, key, 0), pair.source(), pair.runtime()
        );

        PublishOutcome outcome = repository.commit(prepared);

        assertTrue(outcome.committed());
        assertEquals(1, repository.current(key).orElseThrow().revision());
        assertTrue(repository.isDurabilityDegraded());
        assertTrue(new RecoveryService(repository).recover(key).committed());
        assertFalse(repository.isDurabilityDegraded());
    }

    @Test
    void partialCommittedRecordOverwriteStillRecoversCurrentAsCommitted() {
        PartialCommittedRecordFileSystem fileSystem = new PartialCommittedRecordFileSystem(
                new DirectoryFsyncTolerantFileSystem(new NioRepositoryFileSystem())
        );
        MinimapRepository repository = new MinimapRepository(temporaryDirectory, fileSystem);
        MapKey key = new MapKey("fpsmatch:test", "Test Map");
        MinimapStorageFixtures.Pair pair = MinimapStorageFixtures.validPair(1);
        PublishTransaction prepared = repository.prepare(
                reserve(repository, key, 0), pair.source(), pair.runtime()
        );

        PublishOutcome outcome = repository.commit(prepared);

        assertTrue(outcome.committed());
        assertTrue(new RecoveryService(repository).recover(key).committed());
        assertEquals(1, repository.current(key).orElseThrow().revision());
    }

    @Test
    void failureBeforeCurrentReplacementLeavesNoPromotablePreparedRevision() {
        MinimapRepository repository = new MinimapRepository(
                temporaryDirectory,
                new FailCurrentReplaceFileSystem(
                        new DirectoryFsyncTolerantFileSystem(new NioRepositoryFileSystem())
                )
        );
        MapKey key = new MapKey("fpsmatch:test", "Test Map");
        MinimapStorageFixtures.Pair pair = MinimapStorageFixtures.validPair(1);
        PublishTransaction prepared = repository.prepare(
                reserve(repository, key, 0), pair.source(), pair.runtime()
        );

        assertThrows(ContainerStorageException.class, () -> repository.commit(prepared));
        PublishOutcome recovered = new RecoveryService(repository).recover(key);
        assertEquals(PublishOutcome.Status.ABORTED, recovered.status());
        assertFalse(Files.exists(repository.mapDirectory(key).resolve("revisions").resolve("1")));
    }

    @Test
    void preCurrentFailureSuspendsPublishingAcrossRestartUntilRecovery() {
        MinimapRepository repository = new MinimapRepository(
                temporaryDirectory,
                new FailCurrentReplaceFileSystem(
                        new DirectoryFsyncTolerantFileSystem(new NioRepositoryFileSystem())
                )
        );
        MapKey key = new MapKey("fpsmatch:test", "Test Map");
        MinimapStorageFixtures.Pair pair = MinimapStorageFixtures.validPair(1);
        PublishTransaction prepared = repository.prepare(
                reserve(repository, key, 0), pair.source(), pair.runtime()
        );

        assertThrows(ContainerStorageException.class, () -> repository.commit(prepared));
        MinimapRepository restarted = new MinimapRepository(temporaryDirectory);
        assertThrows(ContainerStorageException.class, () -> reserve(restarted, key, 0));

        assertEquals(PublishOutcome.Status.ABORTED,
                new RecoveryService(restarted).recover(key).status());
        assertEquals(2, reserve(restarted, key, 0).publishRevision());
    }

    /** Directory fsync is unsupported by the Windows JDK provider. */
    private static class DirectoryFsyncTolerantFileSystem implements RepositoryFileSystem {
        private final RepositoryFileSystem delegate;

        private DirectoryFsyncTolerantFileSystem(RepositoryFileSystem delegate) {
            this.delegate = delegate;
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
        public void write(Path file, byte[] bytes) throws IOException {
            delegate.write(file, bytes);
        }

        @Override
        public void fsyncFile(Path file) throws IOException {
            delegate.fsyncFile(file);
        }

        @Override
        public void fsyncDirectory(Path directory) throws IOException {
            try {
                delegate.fsyncDirectory(directory);
            } catch (AccessDeniedException exception) {
                // This test adapter models a filesystem with durable directory updates.
            }
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

    private static final class FailAfterReplaceFileSystem implements RepositoryFileSystem {
        private final RepositoryFileSystem delegate;
        private final boolean uncheckedFailure;
        private boolean replaced;

        private FailAfterReplaceFileSystem(RepositoryFileSystem delegate) {
            this(delegate, false);
        }

        private FailAfterReplaceFileSystem(
                RepositoryFileSystem delegate,
                boolean uncheckedFailure
        ) {
            this.delegate = delegate;
            this.uncheckedFailure = uncheckedFailure;
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
        public void write(Path file, byte[] bytes) throws IOException {
            delegate.write(file, bytes);
        }

        @Override
        public void fsyncFile(Path file) throws IOException {
            delegate.fsyncFile(file);
        }

        @Override
        public void fsyncDirectory(Path directory) throws IOException {
            if (replaced) {
                replaced = false;
                if (uncheckedFailure) {
                    throw new IllegalStateException("injected unchecked parent fsync failure");
                }
                throw new IOException("injected parent fsync failure");
            }
            delegate.fsyncDirectory(directory);
        }

        @Override
        public void moveAtomically(Path source, Path target) throws IOException {
            delegate.moveAtomically(source, target);
        }

        @Override
        public void replaceAtomically(Path source, Path target) throws IOException {
            delegate.replaceAtomically(source, target);
            if (target.getFileName().toString().equals("CURRENT")) {
                replaced = true;
            }
        }
    }

    private static final class WholeContainerReadRejectingFileSystem
            extends DirectoryFsyncTolerantFileSystem {
        private WholeContainerReadRejectingFileSystem(RepositoryFileSystem delegate) {
            super(delegate);
        }

        @Override
        public byte[] readAllBytes(Path file) throws IOException {
            String name = file.getFileName().toString();
            if (name.equals("source.fpsmap") || name.equals("runtime.fpsmapc")) {
                throw new AssertionError("Repository attempted a whole-container read: " + file);
            }
            return super.readAllBytes(file);
        }
    }

    private static PublishTransaction reserve(
            MinimapRepository repository,
            MapKey key,
            long baseRevision
    ) {
        return PublishTargetFixture.reserve(repository, key, baseRevision);
    }

    private static final class FailCommittedRecordFileSystem implements RepositoryFileSystem {
        private final RepositoryFileSystem delegate;
        private boolean failed;

        private FailCommittedRecordFileSystem(RepositoryFileSystem delegate) {
            this.delegate = delegate;
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
        public void write(Path file, byte[] bytes) throws IOException {
            String text = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
            if (!failed && file.getFileName().toString().startsWith("publish-record.json")
                    && text.contains("\"state\":\"COMMITTED\"")) {
                failed = true;
                throw new IOException("injected committed-record write failure");
            }
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

    private static final class PartialCommittedRecordFileSystem
            extends DirectoryFsyncTolerantFileSystem {
        private boolean failed;

        private PartialCommittedRecordFileSystem(RepositoryFileSystem delegate) {
            super(delegate);
        }

        @Override
        public void write(Path file, byte[] bytes) throws IOException {
            String text = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
            if (!failed && file.getFileName().toString().startsWith("publish-record.json")
                    && text.contains("\"state\":\"COMMITTED\"")) {
                failed = true;
                super.write(file, java.util.Arrays.copyOf(bytes, Math.max(1, bytes.length / 2)));
                throw new IOException("injected partial committed-record write");
            }
            super.write(file, bytes);
        }
    }

    private static final class FailCurrentReplaceFileSystem implements RepositoryFileSystem {
        private final RepositoryFileSystem delegate;

        private FailCurrentReplaceFileSystem(RepositoryFileSystem delegate) {
            this.delegate = delegate;
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
            if (target.getFileName().toString().equals("CURRENT")) {
                throw new IOException("injected CURRENT replace failure");
            }
            delegate.replaceAtomically(source, target);
        }
    }

    private static final class BarrierCurrentReadFileSystem
            extends DirectoryFsyncTolerantFileSystem {
        private final CyclicBarrier barrier = new CyclicBarrier(2);
        private final AtomicInteger currentReads = new AtomicInteger();
        private final AtomicBoolean barrierEnabled = new AtomicBoolean();

        private BarrierCurrentReadFileSystem(RepositoryFileSystem delegate) {
            super(delegate);
        }

        private void enableCurrentBarrier() {
            barrierEnabled.set(true);
        }

        @Override
        public byte[] readAllBytes(Path file) throws IOException {
            byte[] snapshot = super.readAllBytes(file);
            if (barrierEnabled.get()
                    && file.getFileName().toString().equals("CURRENT")
                    && currentReads.getAndIncrement() < 2) {
                try {
                    barrier.await(250, TimeUnit.MILLISECONDS);
                } catch (TimeoutException | BrokenBarrierException ignored) {
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted at CURRENT barrier", exception);
                }
            }
            return snapshot;
        }
    }
}
