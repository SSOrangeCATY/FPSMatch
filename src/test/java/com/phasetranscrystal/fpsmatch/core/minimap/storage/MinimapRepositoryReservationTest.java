package com.phasetranscrystal.fpsmatch.core.minimap.storage;

import com.phasetranscrystal.fpsmatch.core.minimap.model.MapKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Assumptions;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinimapRepositoryReservationTest {
    private static PublishTransaction reserve(
            MinimapRepository repository, MapKey key, long baseRevision
    ) {
        return PublishTargetFixture.reserve(repository, key, baseRevision);
    }

    @TempDir
    Path temporaryDirectory;

    @Test
    void reservesMonotonicRevisionsWithoutReusingConcurrentOrAbortedReservations() {
        MinimapRepository repository = new MinimapRepository(temporaryDirectory);
        MapKey key = new MapKey("fpsmatch:test", "Test Map");

        PublishTransaction first = reserve(repository, key, 0);
        assertEquals(1, first.descriptor().publishRevision());
        assertEquals(1, repository.highWaterMark(key));
        assertFalse(repository.current(key).isPresent());

        PublishTransaction concurrent = reserve(repository, key, 0);
        assertEquals(2, concurrent.descriptor().publishRevision());
        assertEquals(2, repository.highWaterMark(key));

        repository.abort(first, "client disconnected");
        repository.abort(concurrent, "client disconnected");
        PublishTransaction second = reserve(repository, key, 0);
        assertEquals(3, second.descriptor().publishRevision());
        assertEquals(3, repository.highWaterMark(key));
    }

    @Test
    void repositoryExposesOnlyFullyBoundPublishReservationApis() {
        for (Method method : MinimapRepository.class.getMethods()) {
            if (!method.getName().equals("reserve")) {
                continue;
            }
            assertTrue(
                    Arrays.asList(method.getParameterTypes())
                            .contains(com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId.class),
                    method.toString()
            );
        }
    }

    @Test
    void aPersistedHighWaterMarkIsNotReusedWhenReservedRecordWritingFails() {
        FailFirstRecordWriteFileSystem fileSystem = new FailFirstRecordWriteFileSystem(
                new DirectoryFsyncTolerantFileSystem(new NioRepositoryFileSystem())
        );
        MinimapRepository repository = new MinimapRepository(temporaryDirectory, fileSystem);
        MapKey key = new MapKey("fpsmatch:test", "Test Map");

        assertThrows(ContainerStorageException.class, () -> reserve(repository, key, 0));
        assertEquals(1, repository.highWaterMark(key));

        PublishTransaction next = reserve(repository, key, 0);
        assertEquals(2, next.publishRevision());
        assertEquals(2, repository.highWaterMark(key));
    }

    @Test
    void firstReservationPersistsMapDirectoryParentsBeforeWritingHighWater() {
        FaultInjectingRepositoryFileSystem fileSystem =
                new FaultInjectingRepositoryFileSystem(
                        new DirectoryFsyncTolerantFileSystem(
                                new NioRepositoryFileSystem()
                        )
                );
        MinimapRepository repository = new MinimapRepository(
                temporaryDirectory, fileSystem
        );
        MapKey key = new MapKey("fpsmatch:test", "First Durable Map");
        Path mapDirectory = repository.mapDirectory(key);

        reserve(repository, key, 0);

        int highWaterWrite = firstCallIndex(fileSystem.calls(), call ->
                call.operation() == FaultInjectingRepositoryFileSystem.Operation.WRITE
                        && call.path().getFileName().toString()
                        .startsWith("publish-state.json."));
        int mapsSync = firstCallIndex(fileSystem.calls(), call ->
                call.operation()
                        == FaultInjectingRepositoryFileSystem.Operation.FSYNC_DIRECTORY
                        && call.path().equals(mapDirectory.getParent()));
        int mapSync = firstCallIndex(fileSystem.calls(), call ->
                call.operation()
                        == FaultInjectingRepositoryFileSystem.Operation.FSYNC_DIRECTORY
                        && call.path().equals(mapDirectory));

        assertTrue(mapsSync >= 0 && mapsSync < highWaterWrite);
        assertTrue(mapSync >= 0 && mapSync < highWaterWrite);
    }

    @Test
    void repositoryInstancesCannotReserveTheSameRevision() throws Exception {
        MapKey key = new MapKey("fpsmatch:test", "Shared Map");
        MinimapRepository layout = new MinimapRepository(temporaryDirectory);
        Path mapDirectory = layout.mapDirectory(key);
        Files.createDirectories(mapDirectory);
        Files.writeString(
                mapDirectory.resolve("publish-state.json"),
                "{\"highWaterMark\":\"0\"}"
        );
        RepositoryFileSystem fileSystem = new BarrierStateReadFileSystem(
                new DirectoryFsyncTolerantFileSystem(new NioRepositoryFileSystem())
        );
        MinimapRepository firstRepository = new MinimapRepository(
                temporaryDirectory, fileSystem
        );
        MinimapRepository secondRepository = new MinimapRepository(
                temporaryDirectory, fileSystem
        );
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Long> first = executor.submit(
                    () -> reserve(firstRepository, key, 0).publishRevision()
            );
            Future<Long> second = executor.submit(
                    () -> reserve(secondRepository, key, 0).publishRevision()
            );

            List<Long> revisions = new ArrayList<>(List.of(
                    first.get(5, TimeUnit.SECONDS),
                    second.get(5, TimeUnit.SECONDS)
            ));
            revisions.sort(Long::compareTo);
            assertEquals(List.of(1L, 2L), revisions);
            assertEquals(2, firstRepository.highWaterMark(key));
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void aFailedHighWaterWriteDoesNotDestroyThePreviousCanonicalState() throws Exception {
        PartialStateWriteFileSystem fileSystem = new PartialStateWriteFileSystem(
                new DirectoryFsyncTolerantFileSystem(new NioRepositoryFileSystem())
        );
        MinimapRepository repository = new MinimapRepository(temporaryDirectory, fileSystem);
        MapKey key = new MapKey("fpsmatch:test", "Durable State");
        reserve(repository, key, 0);
        fileSystem.failNextStateWrite = true;

        assertThrows(ContainerStorageException.class, () -> reserve(repository, key, 0));
        assertEquals(
                "{\"highWaterMark\":\"1\"}",
                Files.readString(repository.mapDirectory(key).resolve("publish-state.json"))
        );
        assertEquals(1, repository.highWaterMark(key));
    }

    @Test
    void recoveryRebuildsHighWaterFromPersistedReservationsBeforeCleanup() throws Exception {
        MinimapRepository repository = new MinimapRepository(temporaryDirectory);
        MapKey key = new MapKey("fpsmatch:test", "Rebuild State");
        reserve(repository, key, 0);
        reserve(repository, key, 0);
        Path state = repository.mapDirectory(key).resolve("publish-state.json");
        Files.delete(state);

        MinimapRepository restarted = new MinimapRepository(temporaryDirectory);
        new RecoveryService(restarted).recover(key);

        assertEquals(2, restarted.highWaterMark(key));
        assertEquals(3, reserve(restarted, key, 0).publishRevision());
    }

    @Test
    void missingCurrentWithCommittedRevisionRequiresRecoveryBeforeReserve()
            throws Exception {
        MinimapRepository repository = new MinimapRepository(temporaryDirectory);
        MapKey key = new MapKey("fpsmatch:test", "Test Map");
        MinimapStorageFixtures.Pair pair = MinimapStorageFixtures.validPair(1);
        PublishTransaction prepared = repository.prepare(
                reserve(repository, key, 0), pair.source(), pair.runtime()
        );
        assertTrue(repository.commit(prepared).committed());
        Path mapDirectory = repository.mapDirectory(key);
        Files.delete(mapDirectory.resolve("CURRENT"));

        assertThrows(ContainerStorageException.class, () -> reserve(repository, key, 0));

        assertEquals(1, repository.highWaterMark(key));
        assertTrue(Files.isDirectory(mapDirectory.resolve("revisions").resolve("1")));
        try (var transactions = Files.list(mapDirectory.resolve("transactions"))) {
            assertTrue(transactions.findAny().isEmpty());
        }
    }

    @Test
    void fullTransactionDirectoryRejectsReserveBeforeConsumingARevision()
            throws Exception {
        MinimapRepository repository = new MinimapRepository(temporaryDirectory);
        MapKey key = new MapKey("fpsmatch:test", "Full Transactions");
        Path mapDirectory = repository.mapDirectory(key);
        Path transactions = mapDirectory.resolve("transactions");
        Files.createDirectories(transactions);
        for (int index = 0; index < 4_096; index++) {
            Files.createDirectory(transactions.resolve("entry-" + index));
        }

        assertThrows(ContainerStorageException.class, () -> reserve(repository, key, 0));

        assertFalse(Files.exists(mapDirectory.resolve("publish-state.json")));
        try (var entries = Files.list(transactions)) {
            assertEquals(4_096, entries.count());
        }
    }

    @Test
    void recoveryConsumesDurableHighWaterTemporaryStateLeftBeforeAtomicReplace()
            throws Exception {
        MinimapRepository layout = new MinimapRepository(temporaryDirectory);
        MapKey key = new MapKey("fpsmatch:test", "Interrupted High Water");
        Path mapDirectory = layout.mapDirectory(key);
        Files.createDirectories(mapDirectory);
        Files.writeString(
                mapDirectory.resolve("publish-state.json"),
                "{\"highWaterMark\":\"0\"}"
        );
        Path durableTemporary = mapDirectory.resolve(
                "publish-state.json.00000000-0000-0000-0000-000000000001.tmp"
        );
        Files.writeString(durableTemporary, "{\"highWaterMark\":\"1\"}");

        MinimapRepository restarted = new MinimapRepository(temporaryDirectory);
        new RecoveryService(restarted).recover(key);

        assertEquals(1, restarted.highWaterMark(key));
        assertFalse(Files.exists(durableTemporary));
        assertEquals(2, reserve(restarted, key, 0).publishRevision());
    }

    @Test
    void recoveryPropagatesPublishStateReadFailureWithoutRewritingOrCleaning()
            throws Exception {
        RepositoryFileSystem durableFileSystem = new DirectoryFsyncTolerantFileSystem(
                new NioRepositoryFileSystem()
        );
        MinimapRepository repository = new MinimapRepository(
                temporaryDirectory, durableFileSystem
        );
        MapKey key = new MapKey("fpsmatch:test", "Unreadable State");
        PublishTransaction reserved = reserve(repository, key, 0);
        Path state = repository.mapDirectory(key).resolve("publish-state.json");
        byte[] stateBeforeRecovery = Files.readAllBytes(state);
        MinimapRepository restarted = new MinimapRepository(
                temporaryDirectory,
                new PublishStateReadFailingFileSystem(durableFileSystem)
        );

        assertThrows(
                ContainerStorageException.class,
                () -> new RecoveryService(restarted).recover(key)
        );
        assertArrayEquals(stateBeforeRecovery, Files.readAllBytes(state));
        assertTrue(Files.isDirectory(reserved.transactionDirectory()));
    }

    @Test
    void recoveryPropagatesTransactionRecordAttributeFailureWithoutReusingRevision()
            throws Exception {
        MinimapRepository repository = new MinimapRepository(temporaryDirectory);
        MapKey key = new MapKey("fpsmatch:test", "Unreadable Reservation Record");
        PublishTransaction reserved = reserve(repository, key, 0);
        Path state = repository.mapDirectory(key).resolve("publish-state.json");
        Files.delete(state);
        TargetAttributeReadFailingRepositoryFileSystem failingFileSystem =
                new TargetAttributeReadFailingRepositoryFileSystem(
                        new NioRepositoryFileSystem()
                );
        failingFileSystem.failNextAttributeRead(
                reserved.transactionDirectory().resolve("publish-record.json")
        );
        MinimapRepository restarted = new MinimapRepository(
                temporaryDirectory, failingFileSystem
        );

        assertThrows(
                ContainerStorageException.class,
                () -> new RecoveryService(restarted).recover(key)
        );

        assertFalse(Files.exists(state));
        assertTrue(Files.isDirectory(reserved.transactionDirectory()));
    }

    @Test
    void expiredReservedTokenCannotPrepareAndIsPersistedAsAborted() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-12T00:00:00Z"));
        RepositoryFileSystem fileSystem = new DirectoryFsyncTolerantFileSystem(
                new NioRepositoryFileSystem()
        );
        MinimapRepository repository = new MinimapRepository(
                temporaryDirectory, fileSystem, clock
        );
        MapKey key = new MapKey("fpsmatch:test", "Test Map");
        PublishTransaction reserved = PublishTargetFixture.reserve(
                repository, key, 0, Duration.ofMinutes(1)
        );
        clock.advance(Duration.ofMinutes(2));
        MinimapStorageFixtures.Pair pair = MinimapStorageFixtures.validPair(1);

        assertThrows(ContainerStorageException.class, () -> repository.prepare(
                reserved, pair.source(), pair.runtime()
        ));
        assertEquals(
                PublishState.ABORTED,
                PublishRecord.read(Files.readAllBytes(
                        reserved.transactionDirectory().resolve("publish-record.json")
                )).state()
        );
    }

    @Test
    void oversizedAbortReasonIsRejectedWithoutChangingTheRecord()
            throws Exception {
        MinimapRepository repository = new MinimapRepository(temporaryDirectory);
        MapKey key = new MapKey("fpsmatch:test", "Abort Reason Limit");
        PublishTransaction reserved = reserve(repository, key, 0);
        Path record = reserved.transactionDirectory().resolve("publish-record.json");
        byte[] before = Files.readAllBytes(record);

        assertThrows(
                IllegalArgumentException.class,
                () -> repository.abort(reserved, "x".repeat(1024 * 1024))
        );

        assertArrayEquals(before, Files.readAllBytes(record));
        assertEquals(PublishState.RESERVED, PublishRecord.read(before).state());
    }

    @Test
    void expiredPreparedTokenCannotCommitAfterRepositoryRestart() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-12T00:00:00Z"));
        RepositoryFileSystem fileSystem = new DirectoryFsyncTolerantFileSystem(
                new NioRepositoryFileSystem()
        );
        MinimapRepository repository = new MinimapRepository(
                temporaryDirectory, fileSystem, clock
        );
        MapKey key = new MapKey("fpsmatch:test", "Test Map");
        PublishTransaction reserved = PublishTargetFixture.reserve(
                repository, key, 0, Duration.ofMinutes(1)
        );
        MinimapStorageFixtures.Pair pair = MinimapStorageFixtures.validPair(1);
        PublishTransaction prepared = repository.prepare(
                reserved, pair.source(), pair.runtime()
        );
        clock.advance(Duration.ofMinutes(2));

        MinimapRepository restarted = new MinimapRepository(
                temporaryDirectory, fileSystem, clock
        );
        assertThrows(ContainerStorageException.class, () -> restarted.commit(prepared));
        assertEquals(
                PublishState.ABORTED,
                PublishRecord.read(Files.readAllBytes(
                        prepared.transactionDirectory().resolve("publish-record.json")
                )).state()
        );
        assertTrue(restarted.current(key).isEmpty());
    }

    @Test
    void accessDeniedDuringRequiredDirectorySyncIsNotTreatedAsUnsupported() {
        MinimapRepository repository = new MinimapRepository(
                temporaryDirectory,
                new AccessDeniedDirectorySyncFileSystem(new NioRepositoryFileSystem())
        );
        MapKey key = new MapKey("fpsmatch:test", "Test Map");

        assertThrows(ContainerStorageException.class, () -> reserve(repository, key, 0));
    }

    @Test
    void markerReadFailureFailsClosedWhenPrecheckCannotObserveIt() {
        TargetReadFailingRepositoryFileSystem fileSystem =
                new TargetReadFailingRepositoryFileSystem(new NioRepositoryFileSystem());
        MinimapRepository repository = new MinimapRepository(temporaryDirectory, fileSystem);
        MapKey key = new MapKey("fpsmatch:test", "Unreadable Recovery Marker");
        Path mapDirectory = repository.mapDirectory(key);
        fileSystem.failNextRead(mapDirectory.resolve("RECOVERY_REQUIRED"));

        assertThrows(ContainerStorageException.class, () -> reserve(repository, key, 0));
        assertFalse(Files.exists(mapDirectory.resolve("publish-state.json")));
    }

    @Test
    void currentReadFailureFailsClosedWhenPrecheckCannotObserveIt() {
        TargetReadFailingRepositoryFileSystem fileSystem =
                new TargetReadFailingRepositoryFileSystem(new NioRepositoryFileSystem());
        MinimapRepository repository = new MinimapRepository(temporaryDirectory, fileSystem);
        MapKey key = new MapKey("fpsmatch:test", "Unreadable Current");
        fileSystem.failNextRead(repository.mapDirectory(key).resolve("CURRENT"));

        assertThrows(ContainerStorageException.class, () -> repository.current(key));
    }

    @Test
    void highWaterReadFailureFailsClosedWhenPrecheckCannotObserveIt() {
        TargetReadFailingRepositoryFileSystem fileSystem =
                new TargetReadFailingRepositoryFileSystem(new NioRepositoryFileSystem());
        MinimapRepository repository = new MinimapRepository(temporaryDirectory, fileSystem);
        MapKey key = new MapKey("fpsmatch:test", "Unreadable High Water");
        fileSystem.failNextRead(
                repository.mapDirectory(key).resolve("publish-state.json")
        );

        assertThrows(ContainerStorageException.class, () -> repository.highWaterMark(key));
    }

    @Test
    void metadataReadsUseBoundedChannelsInsteadOfReadAllBytes() {
        MetadataReadGuardFileSystem fileSystem = new MetadataReadGuardFileSystem(
                new NioRepositoryFileSystem()
        );
        MinimapRepository repository = new MinimapRepository(temporaryDirectory, fileSystem);
        MapKey key = new MapKey("fpsmatch:test", "Test Map");
        MinimapStorageFixtures.Pair pair = MinimapStorageFixtures.validPair(1);

        PublishTransaction prepared = repository.prepare(
                reserve(repository, key, 0), pair.source(), pair.runtime()
        );
        assertTrue(repository.commit(prepared).committed());
        repository.pinRevision(key, 1, "bounded-pin");
        assertEquals(1, repository.current(key).orElseThrow().revision());
        assertEquals(1, repository.highWaterMark(key));
        repository.collectGarbage(key);

        assertTrue(fileSystem.metadataBoundedReads > 0);
    }

    @Test
    void repositoryPathAttributeReadFailureFailsClosedWhenPrecheckCannotObserveIt() {
        TargetAttributeReadFailingRepositoryFileSystem fileSystem =
                new TargetAttributeReadFailingRepositoryFileSystem(
                        new NioRepositoryFileSystem()
                );
        MinimapRepository repository = new MinimapRepository(temporaryDirectory, fileSystem);
        MapKey key = new MapKey("fpsmatch:test", "Unreadable Repository Path");
        Path mapDirectory = repository.mapDirectory(key);
        fileSystem.failNextAttributeRead(mapDirectory);

        assertThrows(ContainerStorageException.class, () -> reserve(repository, key, 0));
        assertFalse(Files.exists(mapDirectory.resolve("publish-state.json")));
    }

    @Test
    void transactionScopeSymlinkCannotEscapeTheRepositoryRoot() throws Exception {
        MapKey key = new MapKey("fpsmatch:test", "Symlink Map");
        MinimapRepository layout = new MinimapRepository(temporaryDirectory);
        Path mapDirectory = layout.mapDirectory(key);
        Path outside = temporaryDirectory.resolve("outside-transactions");
        Files.createDirectories(mapDirectory);
        Files.createDirectories(outside);
        Path transactions = mapDirectory.resolve("transactions");
        try {
            Files.createSymbolicLink(transactions, outside);
        } catch (UnsupportedOperationException | IOException | SecurityException unavailable) {
            Assumptions.assumeTrue(false, "Symbolic links are unavailable for this test");
        }

        MinimapRepository repository = new MinimapRepository(temporaryDirectory);
        assertThrows(ContainerStorageException.class, () -> reserve(repository, key, 0));
        try (java.util.stream.Stream<Path> entries = Files.list(outside)) {
            assertTrue(entries.findAny().isEmpty());
        }
    }

    @Test
    void transactionTokenSymlinkCannotRedirectPrepareWrites() throws Exception {
        MapKey key = new MapKey("fpsmatch:test", "Test Map");
        MinimapRepository repository = new MinimapRepository(temporaryDirectory);
        PublishTransaction reserved = reserve(repository, key, 0);
        Path record = reserved.transactionDirectory().resolve("publish-record.json");
        byte[] recordBytes = Files.readAllBytes(record);
        Files.delete(record);
        Files.delete(reserved.transactionDirectory());
        Path outside = temporaryDirectory.resolve("outside-token");
        Files.createDirectories(outside);
        Files.write(outside.resolve("publish-record.json"), recordBytes);
        try {
            Files.createSymbolicLink(reserved.transactionDirectory(), outside);
        } catch (UnsupportedOperationException | IOException | SecurityException unavailable) {
            Assumptions.assumeTrue(false, "Symbolic links are unavailable for this test");
        }
        MinimapStorageFixtures.Pair pair = MinimapStorageFixtures.validPair(1);

        assertThrows(ContainerStorageException.class, () -> repository.prepare(
                reserved, pair.source(), pair.runtime()
        ));
        assertFalse(Files.exists(outside.resolve("source.fpsmap")));
        assertFalse(Files.exists(outside.resolve("runtime.fpsmapc")));
    }

    @Test
    void sourceLeafSymlinkCannotRedirectPrepareWrites() throws Exception {
        MapKey key = new MapKey("fpsmatch:test", "Test Map");
        MinimapRepository repository = new MinimapRepository(temporaryDirectory);
        PublishTransaction reserved = reserve(repository, key, 0);
        Path outside = temporaryDirectory.resolve("outside-source.fpsmap");
        byte[] sentinel = new byte[]{4, 2};
        Files.write(outside, sentinel);
        try {
            Files.createSymbolicLink(
                    reserved.transactionDirectory().resolve("source.fpsmap"),
                    outside.toAbsolutePath()
            );
        } catch (UnsupportedOperationException | IOException | SecurityException unavailable) {
            Assumptions.assumeTrue(false, "Symbolic links are unavailable for this test");
        }
        assertTrue(Files.isSymbolicLink(
                reserved.transactionDirectory().resolve("source.fpsmap")
        ));
        MinimapStorageFixtures.Pair pair = MinimapStorageFixtures.validPair(1);

        ContainerStorageException failure = assertThrows(
                ContainerStorageException.class,
                () -> repository.prepare(reserved, pair.source(), pair.runtime())
        );
        assertTrue(failure.getMessage().contains("link"), failure.getMessage());
        assertArrayEquals(sentinel, Files.readAllBytes(outside));
        assertEquals(
                PublishState.RESERVED,
                PublishRecord.read(Files.readAllBytes(
                        reserved.transactionDirectory().resolve("publish-record.json")
                )).state()
        );
    }

    @Test
    void runtimeLeafSymlinkCannotRedirectPrepareWrites() throws Exception {
        MapKey key = new MapKey("fpsmatch:test", "Test Map");
        MinimapRepository repository = new MinimapRepository(temporaryDirectory);
        PublishTransaction reserved = reserve(repository, key, 0);
        Path outside = temporaryDirectory.resolve("outside-runtime.fpsmapc");
        byte[] sentinel = new byte[]{7, 3};
        Files.write(outside, sentinel);
        try {
            Files.createSymbolicLink(
                    reserved.transactionDirectory().resolve("runtime.fpsmapc"),
                    outside.toAbsolutePath()
            );
        } catch (UnsupportedOperationException | IOException | SecurityException unavailable) {
            Assumptions.assumeTrue(false, "Symbolic links are unavailable for this test");
        }
        MinimapStorageFixtures.Pair pair = MinimapStorageFixtures.validPair(1);

        assertThrows(
                ContainerStorageException.class,
                () -> repository.prepare(reserved, pair.source(), pair.runtime())
        );
        assertArrayEquals(sentinel, Files.readAllBytes(outside));
        assertEquals(
                PublishState.RESERVED,
                PublishRecord.read(Files.readAllBytes(
                        reserved.transactionDirectory().resolve("publish-record.json")
                )).state()
        );
    }

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
        public BoundedReadChannel openBoundedReadChannel(Path file, long maximumBytes)
                throws IOException {
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
            try {
                delegate.fsyncDirectory(directory);
            } catch (AccessDeniedException ignoredOnWindows) {
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

    private static int firstCallIndex(
            List<FaultInjectingRepositoryFileSystem.Call> calls,
            java.util.function.Predicate<FaultInjectingRepositoryFileSystem.Call> predicate
    ) {
        for (int index = 0; index < calls.size(); index++) {
            if (predicate.test(calls.get(index))) {
                return index;
            }
        }
        return -1;
    }

    private static final class MetadataReadGuardFileSystem
            extends DirectoryFsyncTolerantFileSystem {
        private int metadataBoundedReads;

        private MetadataReadGuardFileSystem(RepositoryFileSystem delegate) {
            super(delegate);
        }

        @Override
        public byte[] readAllBytes(Path file) throws IOException {
            if (isMetadata(file)) {
                throw new AssertionError("metadata must use a bounded read channel: " + file);
            }
            return super.readAllBytes(file);
        }

        @Override
        public BoundedReadChannel openBoundedReadChannel(Path file, long maximumBytes)
                throws IOException {
            if (isMetadata(file)) {
                metadataBoundedReads++;
            }
            return super.openBoundedReadChannel(file, maximumBytes);
        }

        private static boolean isMetadata(Path path) {
            String name = path.getFileName().toString();
            return name.equals("CURRENT")
                    || name.equals("publish-state.json")
                    || name.startsWith("publish-state.json.")
                    || name.equals("publish-record.json")
                    || name.equals("RECOVERY_REQUIRED")
                    || name.endsWith(".json") && path.getParent() != null
                    && path.getParent().getFileName().toString().equals("pins");
        }
    }

    private static final class FailFirstRecordWriteFileSystem
            extends DirectoryFsyncTolerantFileSystem {
        private boolean failed;

        private FailFirstRecordWriteFileSystem(RepositoryFileSystem delegate) {
            super(delegate);
        }

        @Override
        public void write(Path file, byte[] bytes) throws IOException {
            if (!failed && file.getFileName().toString().startsWith("publish-record.json")) {
                failed = true;
                throw new IOException("injected reserved-record write failure");
            }
            super.write(file, bytes);
        }
    }

    private static final class BarrierStateReadFileSystem
            extends DirectoryFsyncTolerantFileSystem {
        private final CyclicBarrier barrier = new CyclicBarrier(2);
        private final AtomicInteger stateReads = new AtomicInteger();

        private BarrierStateReadFileSystem(RepositoryFileSystem delegate) {
            super(delegate);
        }

        @Override
        public byte[] readAllBytes(Path file) throws IOException {
            byte[] snapshot = super.readAllBytes(file);
            if (file.getFileName().toString().equals("publish-state.json")
                    && stateReads.getAndIncrement() < 2) {
                try {
                    barrier.await(250, TimeUnit.MILLISECONDS);
                } catch (TimeoutException | BrokenBarrierException ignored) {
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted at reservation barrier", exception);
                }
            }
            return snapshot;
        }
    }

    private static final class PartialStateWriteFileSystem
            extends DirectoryFsyncTolerantFileSystem {
        private boolean failNextStateWrite;

        private PartialStateWriteFileSystem(RepositoryFileSystem delegate) {
            super(delegate);
        }

        @Override
        public void write(Path file, byte[] bytes) throws IOException {
            if (failNextStateWrite
                    && file.getFileName().toString().startsWith("publish-state")) {
                failNextStateWrite = false;
                super.write(file, Arrays.copyOf(bytes, Math.max(1, bytes.length / 2)));
                throw new IOException("injected partial high-water write");
            }
            super.write(file, bytes);
        }
    }

    private static final class AccessDeniedDirectorySyncFileSystem
            extends DirectoryFsyncTolerantFileSystem {
        private AccessDeniedDirectorySyncFileSystem(RepositoryFileSystem delegate) {
            super(delegate);
        }

        @Override
        public void fsyncDirectory(Path directory) throws IOException {
            throw new AccessDeniedException(directory.toString());
        }
    }

    private static final class PublishStateReadFailingFileSystem
            extends DirectoryFsyncTolerantFileSystem {
        private PublishStateReadFailingFileSystem(RepositoryFileSystem delegate) {
            super(delegate);
        }

        @Override
        public BoundedReadChannel openBoundedReadChannel(Path file, long maximumBytes)
                throws IOException {
            if (file.getFileName().toString().equals("publish-state.json")) {
                throw new AccessDeniedException(file.toString());
            }
            return super.openBoundedReadChannel(file, maximumBytes);
        }
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            if (!getZone().equals(zone)) {
                throw new UnsupportedOperationException("test clock has a fixed UTC zone");
            }
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
