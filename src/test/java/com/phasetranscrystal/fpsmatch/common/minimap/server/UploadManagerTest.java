package com.phasetranscrystal.fpsmatch.common.minimap.server;

import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapErrorCode;
import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapHardLimits;
import com.phasetranscrystal.fpsmatch.core.minimap.format.Sha256Digest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.function.Executable;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UploadManagerTest {
    private static final int FRAGMENT_BYTES = 256 * 1024;

    @TempDir
    Path temporaryDirectory;

    @Test
    void fragmentsMayArriveOutOfOrderAndIdenticalDuplicatesAreIdempotent()
            throws Exception {
        byte[] first = new byte[FRAGMENT_BYTES];
        Arrays.fill(first, (byte) 7);
        byte[] second = new byte[]{8, 9, 10, 11, 12};
        byte[] complete = new byte[first.length + second.length];
        System.arraycopy(first, 0, complete, 0, first.length);
        System.arraycopy(second, 0, complete, first.length, second.length);
        UploadManager uploads = manager(Clock.systemUTC(), complete.length);
        UploadOwnerScope owner = ownerScope();
        UploadReservation reservation = uploads.begin(
                owner, complete.length, Sha256Digest.of(complete)
        );

        assertFalse(uploads.accept(
                owner, reservation.uploadId(), 1, second
        ).complete());
        assertFalse(uploads.accept(
                owner, reservation.uploadId(), 1, second
        ).complete());
        UploadProgress progress = uploads.accept(
                owner, reservation.uploadId(), 0, first
        );

        assertTrue(progress.complete());
        try (CompletedUpload completed = uploads.finish(owner, reservation.uploadId())) {
            assertArrayEquals(complete, readAll(completed));
        }
        assertUploadNotFound(() -> uploads.finish(owner, reservation.uploadId()));
    }

    @Test
    void conflictingDuplicateLengthHashAndQuotaViolationsFailClosed() {
        UploadManager uploads = manager(Clock.systemUTC(), 16);
        UploadOwnerScope owner = ownerScope();
        byte[] expected = new byte[]{1, 2};
        UploadReservation conflict = uploads.begin(
                owner, expected.length, Sha256Digest.of(expected)
        );
        uploads.accept(owner, conflict.uploadId(), 0, expected);
        UploadException duplicate = assertThrows(
                UploadException.class,
                () -> uploads.accept(owner, conflict.uploadId(), 0, new byte[]{2, 1})
        );
        assertEquals(MinimapErrorCode.FRAGMENT_CONFLICT, duplicate.errorCode());

        UploadReservation badHash = uploads.begin(
                owner, expected.length, Sha256Digest.of(expected)
        );
        uploads.accept(owner, badHash.uploadId(), 0, new byte[]{3, 4});
        UploadException hash = assertThrows(
                UploadException.class,
                () -> uploads.finish(owner, badHash.uploadId())
        );
        assertEquals(MinimapErrorCode.HASH_MISMATCH, hash.errorCode());

        UploadException quota = assertThrows(
                UploadException.class,
                () -> uploads.begin(owner, 17, Sha256Digest.of(new byte[17]))
        );
        assertEquals(MinimapErrorCode.QUOTA_EXCEEDED, quota.errorCode());
        assertThrows(IllegalArgumentException.class, () -> uploads.begin(
                owner, 0, Sha256Digest.of(new byte[0])
        ));
        assertThrows(IllegalArgumentException.class, () -> uploads.accept(
                owner, UUID.randomUUID(), 0,
                new byte[MinimapHardLimits.MAX_WIRE_BODY_BYTES + 1]
        ));
    }

    @Test
    void incompleteUploadsExpireWithoutBecomingReadable() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-12T00:00:00Z"));
        UploadManager uploads = manager(clock, 1024);
        UploadOwnerScope owner = ownerScope();
        byte[] expected = new byte[]{1, 2};
        UploadReservation reservation = uploads.begin(
                owner, expected.length, Sha256Digest.of(expected)
        );
        clock.advance(Duration.ofMinutes(30));

        assertEquals(1, uploads.removeExpired());
        UploadException expired = assertThrows(
                UploadException.class,
                () -> uploads.accept(owner, reservation.uploadId(), 0, expected)
        );
        assertEquals(MinimapErrorCode.SESSION_NOT_FOUND, expired.errorCode());
    }

    @Test
    void uploadRequiringMoreThan4096CanonicalFragmentsIsRejectedBeforeReservation() {
        long oversized = 4096L * FRAGMENT_BYTES + 1;
        UploadManager uploads = manager(Clock.systemUTC(), oversized);
        UploadOwnerScope owner = ownerScope();

        UploadException failure = assertThrows(
                UploadException.class,
                () -> uploads.begin(
                        owner,
                        oversized,
                        Sha256Digest.of(new byte[0])
                )
        );

        assertEquals(MinimapErrorCode.QUOTA_EXCEEDED, failure.errorCode());
    }

    @Test
    void publicApiDoesNotExposeAConfigurableFragmentLimitConstructor() {
        assertThrows(NoSuchMethodException.class, () -> UploadManager.class.getConstructor(
                long.class,
                int.class,
                Duration.class,
                Clock.class
        ));
        assertTrue(Arrays.stream(UploadManager.class.getConstructors()).anyMatch(
                constructor -> Arrays.equals(constructor.getParameterTypes(), new Class<?>[]{
                Path.class,
                UploadLimits.class,
                Duration.class,
                Clock.class
                })
        ));
    }

    @Test
    void publicApiDoesNotExposeOwnerlessUploadOperations() {
        assertThrows(NoSuchMethodException.class, () -> UploadManager.class.getMethod(
                "begin",
                long.class,
                int.class,
                com.phasetranscrystal.fpsmatch.core.minimap.model.Sha256.class
        ));
        assertThrows(NoSuchMethodException.class, () -> UploadManager.class.getMethod(
                "accept",
                UUID.class,
                int.class,
                byte[].class
        ));
        assertThrows(NoSuchMethodException.class, () -> UploadManager.class.getMethod(
                "finish",
                UUID.class
        ));
    }

    @Test
    void declarationsReserveGlobalAndOwnerBudgetsUntilAbort() {
        UploadLimits limits = new UploadLimits(8, 2, 1, 6, 4);
        UploadManager uploads = new UploadManager(
                temporaryDirectory,
                limits,
                Duration.ofMinutes(30),
                Clock.systemUTC()
        );
        UploadOwnerScope firstOwner = new UploadOwnerScope(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 3
        );
        UploadOwnerScope secondOwner = new UploadOwnerScope(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 3
        );
        UploadOwnerScope thirdOwner = new UploadOwnerScope(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 3
        );
        var hash = Sha256Digest.of(new byte[0]);

        UploadReservation first = uploads.begin(firstOwner, 4, hash);

        assertEquals(MinimapErrorCode.QUOTA_EXCEEDED, assertThrows(
                UploadException.class,
                () -> uploads.begin(firstOwner, 1, hash)
        ).errorCode());
        assertEquals(MinimapErrorCode.QUOTA_EXCEEDED, assertThrows(
                UploadException.class,
                () -> uploads.begin(secondOwner, 3, hash)
        ).errorCode());
        uploads.begin(secondOwner, 2, hash);
        assertEquals(MinimapErrorCode.QUOTA_EXCEEDED, assertThrows(
                UploadException.class,
                () -> uploads.begin(thirdOwner, 1, hash)
        ).errorCode());

        assertTrue(uploads.abort(firstOwner, first.uploadId()));
        uploads.begin(thirdOwner, 4, hash);
    }

    @Test
    void concurrentBeginsAtomicallyReserveTheGlobalUploadBudget() throws Exception {
        UploadManager uploads = new UploadManager(
                temporaryDirectory,
                new UploadLimits(8, 1, 1, 8, 8),
                Duration.ofMinutes(30),
                Clock.systemUTC()
        );
        UploadOwnerScope firstOwner = new UploadOwnerScope(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 3
        );
        UploadOwnerScope secondOwner = new UploadOwnerScope(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 3
        );
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(
                    () -> attemptBegin(uploads, firstOwner, ready, start)
            );
            Future<Boolean> second = executor.submit(
                    () -> attemptBegin(uploads, secondOwner, ready, start)
            );
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            int successes = (first.get(5, TimeUnit.SECONDS) ? 1 : 0)
                    + (second.get(5, TimeUnit.SECONDS) ? 1 : 0);

            assertEquals(1, successes);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void slowPayloadPreallocationDoesNotBlockUnrelatedAbortBudgetRelease()
            throws Exception {
        CountDownLatch secondAllocationEntered = new CountDownLatch(1);
        CountDownLatch allowSecondAllocation = new CountDownLatch(1);
        AtomicInteger allocationCount = new AtomicInteger();
        UploadManager uploads = new UploadManager(
                temporaryDirectory,
                new UploadLimits(1, 2, 1, 2, 1),
                Duration.ofMinutes(30),
                Clock.systemUTC(),
                (payloadFile, totalLength) -> {
                    if (allocationCount.incrementAndGet() == 2) {
                        secondAllocationEntered.countDown();
                        try {
                            if (!allowSecondAllocation.await(10, TimeUnit.SECONDS)) {
                                throw new IOException("Timed out waiting to resume allocation");
                            }
                        } catch (InterruptedException exception) {
                            Thread.currentThread().interrupt();
                            throw new IOException("Payload allocation was interrupted", exception);
                        }
                    }
                    Files.write(
                            payloadFile,
                            new byte[Math.toIntExact(totalLength)],
                            java.nio.file.StandardOpenOption.CREATE_NEW,
                            java.nio.file.StandardOpenOption.WRITE
                    );
                }
        );
        UploadOwnerScope firstOwner = ownerScope();
        UploadOwnerScope secondOwner = ownerScope();
        UploadOwnerScope thirdOwner = ownerScope();
        var hash = Sha256Digest.of(new byte[]{1});
        UploadReservation first = uploads.begin(firstOwner, 1, hash);
        CountDownLatch abortFinished = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<UploadReservation> second = executor.submit(
                () -> uploads.begin(secondOwner, 1, hash)
        );
        Future<Boolean> aborted = null;
        UploadReservation secondReservation = null;
        UploadReservation thirdReservation = null;
        try {
            assertTrue(secondAllocationEntered.await(5, TimeUnit.SECONDS));
            Future<UploadException> pendingBudget = executor.submit(() -> assertThrows(
                    UploadException.class,
                    () -> uploads.begin(thirdOwner, 1, hash)
            ));
            assertEquals(
                    MinimapErrorCode.QUOTA_EXCEEDED,
                    pendingBudget.get(1, TimeUnit.SECONDS).errorCode()
            );
            aborted = executor.submit(() -> {
                try {
                    return uploads.abort(firstOwner, first.uploadId());
                } finally {
                    abortFinished.countDown();
                }
            });

            assertTrue(
                    abortFinished.await(5, TimeUnit.SECONDS),
                    "Unrelated abort waited for slow payload preallocation"
            );
            assertTrue(aborted.get(1, TimeUnit.SECONDS));
            thirdReservation = uploads.begin(thirdOwner, 1, hash);
            allowSecondAllocation.countDown();
            secondReservation = second.get(5, TimeUnit.SECONDS);
            assertTrue(uploads.abort(thirdOwner, thirdReservation.uploadId()));
            thirdReservation = null;
            assertTrue(uploads.abort(secondOwner, secondReservation.uploadId()));
            secondReservation = null;
        } finally {
            allowSecondAllocation.countDown();
            try {
                if (secondReservation == null && !second.isCancelled()) {
                    try {
                        secondReservation = second.get(5, TimeUnit.SECONDS);
                    } catch (Exception ignored) {
                        second.cancel(true);
                    }
                }
                abortQuietly(uploads, firstOwner, first);
                abortQuietly(uploads, thirdOwner, thirdReservation);
                abortQuietly(uploads, secondOwner, secondReservation);
            } finally {
                if (aborted != null) {
                    aborted.cancel(true);
                }
                second.cancel(true);
                executor.shutdownNow();
                try {
                    executor.awaitTermination(5, TimeUnit.SECONDS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    @Test
    void uploadTtlStartsAfterPayloadPreallocationCompletes() throws Exception {
        Instant initial = Instant.parse("2026-07-12T00:00:00Z");
        MutableClock clock = new MutableClock(initial);
        Duration idleTtl = Duration.ofMinutes(30);
        UploadManager uploads = new UploadManager(
                temporaryDirectory,
                new UploadLimits(1, 1, 1, 1, 1),
                idleTtl,
                clock,
                (payloadFile, totalLength) -> {
                    clock.advance(idleTtl.plusMinutes(1));
                    Files.write(
                            payloadFile,
                            new byte[Math.toIntExact(totalLength)],
                            java.nio.file.StandardOpenOption.CREATE_NEW,
                            java.nio.file.StandardOpenOption.WRITE
                    );
                }
        );
        UploadOwnerScope owner = ownerScope();

        UploadReservation reservation = uploads.begin(
                owner, 1, Sha256Digest.of(new byte[]{1})
        );

        assertEquals(clock.instant().plus(idleTtl), reservation.expiresAt());
        assertTrue(uploads.abort(owner, reservation.uploadId()));
    }

    @Test
    void uuidDirectoryCollisionDoesNotDeleteExistingContentOrReserveTwice()
            throws Exception {
        UUID occupiedUploadId = UUID.randomUUID();
        UUID acceptedUploadId = UUID.randomUUID();
        AtomicInteger idCalls = new AtomicInteger();
        UploadManager uploads = new UploadManager(
                temporaryDirectory,
                new UploadLimits(1, 1, 1, 1, 1),
                Duration.ofMinutes(30),
                Clock.systemUTC(),
                (payloadFile, totalLength) -> Files.write(
                        payloadFile,
                        new byte[Math.toIntExact(totalLength)],
                        java.nio.file.StandardOpenOption.CREATE_NEW,
                        java.nio.file.StandardOpenOption.WRITE
                ),
                () -> switch (idCalls.getAndIncrement()) {
                    case 0 -> occupiedUploadId;
                    case 1 -> acceptedUploadId;
                    default -> UUID.randomUUID();
                }
        );
        Path occupiedDirectory = Files.createDirectory(
                temporaryDirectory.resolve(occupiedUploadId.toString())
        );
        Path sentinel = occupiedDirectory.resolve("sentinel.bin");
        Files.write(sentinel, new byte[]{7});
        UploadOwnerScope owner = ownerScope();
        UploadOwnerScope otherOwner = ownerScope();
        var hash = Sha256Digest.of(new byte[]{1});

        UploadReservation reservation = uploads.begin(owner, 1, hash);

        assertEquals(acceptedUploadId, reservation.uploadId());
        assertArrayEquals(new byte[]{7}, Files.readAllBytes(sentinel));
        assertEquals(2, idCalls.get());
        assertEquals(MinimapErrorCode.QUOTA_EXCEEDED, assertThrows(
                UploadException.class,
                () -> uploads.begin(otherOwner, 1, hash)
        ).errorCode());
        assertEquals(2, idCalls.get());
        assertTrue(uploads.abort(owner, reservation.uploadId()));
        UploadReservation replacement = uploads.begin(otherOwner, 1, hash);
        assertTrue(uploads.abort(otherOwner, replacement.uploadId()));
    }

    @Test
    void checkedAndUncheckedAllocationFailuresReleaseThePendingBudget()
            throws Exception {
        AtomicInteger allocationAttempts = new AtomicInteger();
        UploadManager uploads = new UploadManager(
                temporaryDirectory,
                new UploadLimits(1, 1, 1, 1, 1),
                Duration.ofMinutes(30),
                Clock.systemUTC(),
                (payloadFile, totalLength) -> {
                    switch (allocationAttempts.incrementAndGet()) {
                        case 1 -> throw new IOException("checked allocation failure");
                        case 2 -> throw new IllegalStateException(
                                "unchecked allocation failure"
                        );
                        default -> Files.write(
                                payloadFile,
                                new byte[Math.toIntExact(totalLength)],
                                java.nio.file.StandardOpenOption.CREATE_NEW,
                                java.nio.file.StandardOpenOption.WRITE
                        );
                    }
                }
        );
        var hash = Sha256Digest.of(new byte[]{1});

        UploadException checkedFailure = assertThrows(
                UploadException.class,
                () -> uploads.begin(ownerScope(), 1, hash)
        );
        assertEquals(MinimapErrorCode.PUBLISH_IO_FAILED, checkedFailure.errorCode());
        IllegalStateException uncheckedFailure = assertThrows(
                IllegalStateException.class,
                () -> uploads.begin(ownerScope(), 1, hash)
        );
        assertEquals("unchecked allocation failure", uncheckedFailure.getMessage());
        UploadOwnerScope successfulOwner = ownerScope();
        UploadReservation successful = uploads.begin(successfulOwner, 1, hash);
        assertEquals(3, allocationAttempts.get());
        assertTrue(uploads.abort(successfulOwner, successful.uploadId()));
    }

    @Test
    void allocatorFileCollisionFailsInsteadOfRetryingTheUploadId()
            throws Exception {
        UUID failedUploadId = UUID.randomUUID();
        UUID successfulUploadId = UUID.randomUUID();
        AtomicInteger idCalls = new AtomicInteger();
        AtomicInteger allocationAttempts = new AtomicInteger();
        UploadManager uploads = new UploadManager(
                temporaryDirectory,
                new UploadLimits(1, 1, 1, 1, 1),
                Duration.ofMinutes(30),
                Clock.systemUTC(),
                (payloadFile, totalLength) -> {
                    if (allocationAttempts.incrementAndGet() == 1) {
                        throw new FileAlreadyExistsException(payloadFile.toString());
                    }
                    Files.write(
                            payloadFile,
                            new byte[Math.toIntExact(totalLength)],
                            java.nio.file.StandardOpenOption.CREATE_NEW,
                            java.nio.file.StandardOpenOption.WRITE
                    );
                },
                () -> switch (idCalls.getAndIncrement()) {
                    case 0 -> failedUploadId;
                    case 1 -> successfulUploadId;
                    default -> UUID.randomUUID();
                }
        );
        var hash = Sha256Digest.of(new byte[]{1});

        UploadException allocationFailure = assertThrows(
                UploadException.class,
                () -> uploads.begin(ownerScope(), 1, hash)
        );
        assertEquals(MinimapErrorCode.PUBLISH_IO_FAILED, allocationFailure.errorCode());
        assertEquals(1, idCalls.get());
        UploadOwnerScope successfulOwner = ownerScope();
        UploadReservation successful = uploads.begin(successfulOwner, 1, hash);
        assertEquals(successfulUploadId, successful.uploadId());
        assertEquals(2, idCalls.get());
        assertTrue(uploads.abort(successfulOwner, successful.uploadId()));
    }

    @Test
    void cleanupFailureDoesNotReplaceUncheckedAllocationFailureAndBudgetIsReusable()
            throws Exception {
        UUID failedUploadId = UUID.randomUUID();
        UUID successfulUploadId = UUID.randomUUID();
        AtomicInteger idCalls = new AtomicInteger();
        AtomicInteger allocationAttempts = new AtomicInteger();
        IllegalStateException expectedFailure = new IllegalStateException(
                "unchecked allocation failure"
        );
        UploadManager uploads = new UploadManager(
                temporaryDirectory,
                new UploadLimits(1, 1, 1, 1, 1),
                Duration.ofMinutes(30),
                Clock.systemUTC(),
                (payloadFile, totalLength) -> {
                    if (allocationAttempts.incrementAndGet() == 1) {
                        Files.createDirectory(payloadFile.getParent().resolve("blocker"));
                        throw expectedFailure;
                    }
                    Files.write(
                            payloadFile,
                            new byte[Math.toIntExact(totalLength)],
                            java.nio.file.StandardOpenOption.CREATE_NEW,
                            java.nio.file.StandardOpenOption.WRITE
                    );
                },
                () -> switch (idCalls.getAndIncrement()) {
                    case 0 -> failedUploadId;
                    case 1 -> successfulUploadId;
                    default -> UUID.randomUUID();
                }
        );
        Path failedDirectory = temporaryDirectory.resolve(failedUploadId.toString());
        var hash = Sha256Digest.of(new byte[]{1});
        IllegalStateException actualFailure;
        try {
            actualFailure = assertThrows(
                    IllegalStateException.class,
                    () -> uploads.begin(ownerScope(), 1, hash)
            );
        } finally {
            Files.deleteIfExists(failedDirectory.resolve("blocker"));
            Files.deleteIfExists(failedDirectory.resolve("payload.bin"));
            Files.deleteIfExists(failedDirectory);
        }

        assertSame(expectedFailure, actualFailure);
        assertTrue(Arrays.stream(actualFailure.getSuppressed()).anyMatch(
                DirectoryNotEmptyException.class::isInstance
        ));
        UploadOwnerScope successfulOwner = ownerScope();
        UploadReservation successful = uploads.begin(successfulOwner, 1, hash);
        assertEquals(successfulUploadId, successful.uploadId());
        assertTrue(uploads.abort(successfulOwner, successful.uploadId()));
    }

    @Test
    void everyUploadOperationRequiresTheExactOwnerScope() {
        UploadManager uploads = new UploadManager(
                temporaryDirectory,
                new UploadLimits(8, 2, 2, 16, 8),
                Duration.ofMinutes(30),
                Clock.systemUTC()
        );
        UploadOwnerScope owner = new UploadOwnerScope(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 3
        );
        List<UploadOwnerScope> wrongScopes = List.of(
                new UploadOwnerScope(
                        UUID.randomUUID(), owner.sessionId(), owner.draftId(), owner.baseRevision()
                ),
                new UploadOwnerScope(
                        owner.actorId(), UUID.randomUUID(), owner.draftId(), owner.baseRevision()
                ),
                new UploadOwnerScope(
                        owner.actorId(), owner.sessionId(), UUID.randomUUID(), owner.baseRevision()
                ),
                new UploadOwnerScope(
                        owner.actorId(), owner.sessionId(), owner.draftId(), owner.baseRevision() + 1
                )
        );
        byte[] payload = new byte[]{1, 2};
        UploadReservation reservation = uploads.begin(
                owner, payload.length, Sha256Digest.of(payload)
        );

        assertTrue(uploads.accept(
                owner, reservation.uploadId(), 0, payload
        ).complete());

        for (UploadOwnerScope wrongScope : wrongScopes) {
            assertUploadNotFound(() -> uploads.accept(
                    wrongScope, reservation.uploadId(), 0, payload
            ));
            assertUploadNotFound(() -> uploads.finish(
                    wrongScope, reservation.uploadId()
            ));
            assertUploadNotFound(() -> uploads.abort(
                    wrongScope, reservation.uploadId()
            ));
        }
        assertTrue(uploads.abort(owner, reservation.uploadId()));
    }

    @Test
    void typedFragmentsRequireCanonicalNonFinalAndFinalLengths() {
        long totalLength = FRAGMENT_BYTES + 5L;
        UploadManager uploads = new UploadManager(
                temporaryDirectory,
                new UploadLimits(totalLength, 1, 1, totalLength, totalLength),
                Duration.ofMinutes(30),
                Clock.systemUTC()
        );
        UploadOwnerScope owner = new UploadOwnerScope(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 3
        );
        byte[] complete = new byte[Math.toIntExact(totalLength)];
        byte[] first = new byte[FRAGMENT_BYTES];
        byte[] last = new byte[5];
        var expectedHash = Sha256Digest.of(complete);

        UploadReservation shortNonFinal = uploads.begin(owner, totalLength, expectedHash);
        assertThrows(IllegalArgumentException.class, () -> uploads.accept(
                owner,
                shortNonFinal.uploadId(),
                0,
                new byte[FRAGMENT_BYTES - 1]
        ));
        assertFalse(uploads.accept(
                owner, shortNonFinal.uploadId(), 0, first
        ).complete());
        assertTrue(uploads.abort(owner, shortNonFinal.uploadId()));

        UploadReservation longFinal = uploads.begin(owner, totalLength, expectedHash);
        assertThrows(IllegalArgumentException.class, () -> uploads.accept(
                owner, longFinal.uploadId(), 1, new byte[6]
        ));
        assertFalse(uploads.accept(
                owner, longFinal.uploadId(), 1, last
        ).complete());
        assertTrue(uploads.abort(owner, longFinal.uploadId()));

        UploadReservation shortFinal = uploads.begin(owner, totalLength, expectedHash);
        assertThrows(IllegalArgumentException.class, () -> uploads.accept(
                owner, shortFinal.uploadId(), 1, new byte[4]
        ));
        assertFalse(uploads.accept(
                owner, shortFinal.uploadId(), 1, last
        ).complete());
        assertTrue(uploads.abort(owner, shortFinal.uploadId()));

        UploadReservation outOfOrder = uploads.begin(owner, totalLength, expectedHash);
        assertFalse(uploads.accept(
                owner, outOfOrder.uploadId(), 1, last
        ).complete());
        assertTrue(uploads.accept(
                owner, outOfOrder.uploadId(), 0, first
        ).complete());
        assertTrue(uploads.abort(owner, outOfOrder.uploadId()));
    }

    @Test
    void typedHashMismatchClosesUploadDeletesPayloadAndReleasesBudget() {
        UploadManager uploads = new UploadManager(
                temporaryDirectory,
                new UploadLimits(1, 1, 1, 1, 1),
                Duration.ofMinutes(30),
                Clock.systemUTC()
        );
        UploadOwnerScope owner = new UploadOwnerScope(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 3
        );
        UploadOwnerScope replacementOwner = new UploadOwnerScope(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 3
        );
        byte[] declaredPayload = new byte[]{1};
        byte[] receivedPayload = new byte[]{2};
        UploadReservation reservation = uploads.begin(
                owner, receivedPayload.length, Sha256Digest.of(declaredPayload)
        );
        Path uploadDirectory = temporaryDirectory.resolve(reservation.uploadId().toString());
        assertTrue(uploads.accept(
                owner, reservation.uploadId(), 0, receivedPayload
        ).complete());

        UploadException mismatch = assertThrows(
                UploadException.class,
                () -> uploads.finish(owner, reservation.uploadId())
        );

        assertEquals(MinimapErrorCode.HASH_MISMATCH, mismatch.errorCode());
        assertFalse(Files.exists(uploadDirectory, LinkOption.NOFOLLOW_LINKS));
        assertUploadNotFound(() -> uploads.finish(owner, reservation.uploadId()));
        UploadReservation replacement = uploads.begin(
                replacementOwner, 1, Sha256Digest.of(new byte[]{3})
        );
        assertTrue(uploads.abort(replacementOwner, replacement.uploadId()));
    }

    @Test
    void typedFragmentIoFailureClosesUploadAndReleasesBudget() throws Exception {
        UploadManager uploads = new UploadManager(
                temporaryDirectory,
                new UploadLimits(1, 1, 1, 1, 1),
                Duration.ofMinutes(30),
                Clock.systemUTC()
        );
        UploadOwnerScope owner = new UploadOwnerScope(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 3
        );
        UploadOwnerScope replacementOwner = new UploadOwnerScope(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 3
        );
        byte[] payload = new byte[]{1};
        UploadReservation reservation = uploads.begin(
                owner, payload.length, Sha256Digest.of(payload)
        );
        Path uploadDirectory = temporaryDirectory.resolve(reservation.uploadId().toString());
        Files.delete(uploadDirectory.resolve("payload.bin"));

        UploadException ioFailure = assertThrows(
                UploadException.class,
                () -> uploads.accept(owner, reservation.uploadId(), 0, payload)
        );

        assertEquals(MinimapErrorCode.PUBLISH_IO_FAILED, ioFailure.errorCode());
        assertFalse(Files.exists(uploadDirectory, LinkOption.NOFOLLOW_LINKS));
        assertUploadNotFound(() -> uploads.accept(
                owner, reservation.uploadId(), 0, payload
        ));
        UploadReservation replacement = uploads.begin(
                replacementOwner, 1, Sha256Digest.of(new byte[]{2})
        );
        assertTrue(uploads.abort(replacementOwner, replacement.uploadId()));
    }

    @Test
    void typedFinishIoFailureClosesUploadAndReleasesBudget() throws Exception {
        UploadManager uploads = new UploadManager(
                temporaryDirectory,
                new UploadLimits(1, 1, 1, 1, 1),
                Duration.ofMinutes(30),
                Clock.systemUTC()
        );
        UploadOwnerScope owner = new UploadOwnerScope(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 3
        );
        UploadOwnerScope replacementOwner = new UploadOwnerScope(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 3
        );
        byte[] payload = new byte[]{1};
        UploadReservation reservation = uploads.begin(
                owner, payload.length, Sha256Digest.of(payload)
        );
        Path uploadDirectory = temporaryDirectory.resolve(reservation.uploadId().toString());
        assertTrue(uploads.accept(
                owner, reservation.uploadId(), 0, payload
        ).complete());
        Files.delete(uploadDirectory.resolve("payload.bin"));

        UploadException ioFailure = assertThrows(
                UploadException.class,
                () -> uploads.finish(owner, reservation.uploadId())
        );

        assertEquals(MinimapErrorCode.PUBLISH_IO_FAILED, ioFailure.errorCode());
        assertFalse(Files.exists(uploadDirectory, LinkOption.NOFOLLOW_LINKS));
        assertUploadNotFound(() -> uploads.finish(owner, reservation.uploadId()));
        UploadReservation replacement = uploads.begin(
                replacementOwner, 1, Sha256Digest.of(new byte[]{2})
        );
        assertTrue(uploads.abort(replacementOwner, replacement.uploadId()));
    }

    @Test
    void typedFinishLengthMismatchClosesUploadAndReleasesBudget() throws Exception {
        UploadManager uploads = new UploadManager(
                temporaryDirectory,
                new UploadLimits(1, 1, 1, 1, 1),
                Duration.ofMinutes(30),
                Clock.systemUTC()
        );
        UploadOwnerScope owner = new UploadOwnerScope(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 3
        );
        UploadOwnerScope replacementOwner = new UploadOwnerScope(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 3
        );
        byte[] payload = new byte[]{1};
        UploadReservation reservation = uploads.begin(
                owner, payload.length, Sha256Digest.of(payload)
        );
        Path uploadDirectory = temporaryDirectory.resolve(reservation.uploadId().toString());
        assertTrue(uploads.accept(
                owner, reservation.uploadId(), 0, payload
        ).complete());
        Files.write(uploadDirectory.resolve("payload.bin"), new byte[0]);

        UploadException mismatch = assertThrows(
                UploadException.class,
                () -> uploads.finish(owner, reservation.uploadId())
        );

        assertEquals(MinimapErrorCode.VALIDATION_FAILED, mismatch.errorCode());
        assertFalse(Files.exists(uploadDirectory, LinkOption.NOFOLLOW_LINKS));
        assertUploadNotFound(() -> uploads.finish(owner, reservation.uploadId()));
        UploadReservation replacement = uploads.begin(
                replacementOwner, 1, Sha256Digest.of(new byte[]{2})
        );
        assertTrue(uploads.abort(replacementOwner, replacement.uploadId()));
    }

    @Test
    void rejectedOwnerReservationsDoNotRetainZeroBudgetEntries() throws Exception {
        UploadManager uploads = new UploadManager(
                temporaryDirectory,
                new UploadLimits(2, 1, 1, 1, 2),
                Duration.ofMinutes(30),
                Clock.systemUTC()
        );
        UUID sessionId = UUID.randomUUID();
        UUID draftId = UUID.randomUUID();
        var hash = Sha256Digest.of(new byte[0]);

        for (int index = 0; index < 257; index++) {
            UploadOwnerScope owner = new UploadOwnerScope(
                    UUID.randomUUID(), sessionId, draftId, 3
            );
            UploadException rejected = assertThrows(
                    UploadException.class,
                    () -> uploads.begin(owner, 2, hash)
            );
            assertEquals(MinimapErrorCode.QUOTA_EXCEEDED, rejected.errorCode());
        }

        var ownerBudgetsField = UploadManager.class.getDeclaredField("ownerBudgets");
        ownerBudgetsField.setAccessible(true);
        Map<?, ?> ownerBudgets = (Map<?, ?>) ownerBudgetsField.get(uploads);
        assertTrue(ownerBudgets.isEmpty());
    }

    @Test
    void typedUploadUsesDiskAndRetainsItsBudgetUntilTheHandleCloses() throws Exception {
        long totalLength = FRAGMENT_BYTES + 5L;
        UploadManager uploads = new UploadManager(
                temporaryDirectory,
                new UploadLimits(totalLength, 1, 1, totalLength, totalLength),
                Duration.ofMinutes(30),
                Clock.systemUTC()
        );
        UploadOwnerScope owner = new UploadOwnerScope(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 3
        );
        UploadOwnerScope secondOwner = new UploadOwnerScope(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 3
        );
        byte[] first = new byte[FRAGMENT_BYTES];
        Arrays.fill(first, (byte) 7);
        byte[] last = new byte[]{1, 2, 3, 4, 5};
        byte[] complete = new byte[Math.toIntExact(totalLength)];
        System.arraycopy(first, 0, complete, 0, first.length);
        System.arraycopy(last, 0, complete, first.length, last.length);
        UploadReservation reservation = uploads.begin(
                owner, totalLength, Sha256Digest.of(complete)
        );
        Path uploadDirectory = temporaryDirectory.resolve(reservation.uploadId().toString());
        Path payloadFile = uploadDirectory.resolve("payload.bin");

        assertTrue(Files.isRegularFile(payloadFile, LinkOption.NOFOLLOW_LINKS));
        assertEquals(totalLength, Files.size(payloadFile));
        assertFalse(uploads.accept(
                owner, reservation.uploadId(), 1, last
        ).complete());
        assertTrue(uploads.accept(
                owner, reservation.uploadId(), 0, first
        ).complete());

        try (CompletedUpload completed = uploads.finish(owner, reservation.uploadId())) {
            ByteBuffer read = ByteBuffer.allocate(complete.length);
            while (read.hasRemaining()) {
                int count = completed.read(read);
                assertTrue(count > 0);
            }
            assertEquals(-1, completed.read(ByteBuffer.allocate(1)));
            assertArrayEquals(complete, read.array());
            UploadException quota = assertThrows(
                    UploadException.class,
                    () -> uploads.begin(secondOwner, 1, Sha256Digest.of(new byte[]{9}))
            );
            assertEquals(MinimapErrorCode.QUOTA_EXCEEDED, quota.errorCode());
        }

        assertFalse(Files.exists(uploadDirectory, LinkOption.NOFOLLOW_LINKS));
        UploadReservation reused = uploads.begin(
                secondOwner, 1, Sha256Digest.of(new byte[]{9})
        );
        assertTrue(uploads.abort(secondOwner, reused.uploadId()));
    }

    @Test
    void claimedCloseFailureStillReleasesBudgetAndLeavesStaleFilesForStartup()
            throws Exception {
        UploadManager uploads = new UploadManager(
                temporaryDirectory,
                new UploadLimits(1, 1, 1, 1, 1),
                Duration.ofMinutes(30),
                Clock.systemUTC()
        );
        UploadOwnerScope owner = new UploadOwnerScope(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 3
        );
        UploadOwnerScope replacementOwner = new UploadOwnerScope(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 3
        );
        byte[] payload = new byte[]{1};
        UploadReservation reservation = uploads.begin(
                owner, payload.length, Sha256Digest.of(payload)
        );
        assertTrue(uploads.accept(
                owner, reservation.uploadId(), 0, payload
        ).complete());
        Path uploadDirectory = temporaryDirectory.resolve(reservation.uploadId().toString());
        Path blocker = uploadDirectory.resolve("blocker.bin");
        Files.write(blocker, new byte[]{2});
        CompletedUpload completed = uploads.finish(owner, reservation.uploadId());

        try {
            assertThrows(IOException.class, completed::close);

            assertUploadNotFound(() -> uploads.finish(owner, reservation.uploadId()));
            UploadReservation replacement = uploads.begin(
                    replacementOwner, 1, Sha256Digest.of(new byte[]{3})
            );
            assertTrue(uploads.abort(replacementOwner, replacement.uploadId()));
            assertTrue(Files.isRegularFile(blocker, LinkOption.NOFOLLOW_LINKS));
            assertTrue(Files.isDirectory(uploadDirectory, LinkOption.NOFOLLOW_LINKS));
        } finally {
            Files.deleteIfExists(blocker);
            Files.deleteIfExists(uploadDirectory.resolve("payload.bin"));
            Files.deleteIfExists(uploadDirectory);
        }
    }

    @Test
    void abortCleanupFailureStillRemovesStateAndReleasesBudget()
            throws Exception {
        UploadManager uploads = new UploadManager(
                temporaryDirectory,
                new UploadLimits(1, 1, 1, 1, 1),
                Duration.ofMinutes(30),
                Clock.systemUTC()
        );
        UploadOwnerScope owner = new UploadOwnerScope(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 3
        );
        UploadOwnerScope replacementOwner = new UploadOwnerScope(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 3
        );
        UploadReservation reservation = uploads.begin(
                owner, 1, Sha256Digest.of(new byte[]{1})
        );
        Path uploadDirectory = temporaryDirectory.resolve(
                reservation.uploadId().toString()
        );
        Path blocker = Files.createDirectory(uploadDirectory.resolve("blocker"));
        try {
            UploadException cleanupFailure = assertThrows(
                    UploadException.class,
                    () -> uploads.abort(owner, reservation.uploadId())
            );

            assertEquals(
                    MinimapErrorCode.PUBLISH_IO_FAILED,
                    cleanupFailure.errorCode()
            );
            assertFalse(uploads.abort(owner, reservation.uploadId()));
            UploadReservation replacement = uploads.begin(
                    replacementOwner, 1, Sha256Digest.of(new byte[]{2})
            );
            assertTrue(uploads.abort(replacementOwner, replacement.uploadId()));
        } finally {
            Files.deleteIfExists(blocker);
            Files.deleteIfExists(uploadDirectory.resolve("payload.bin"));
            Files.deleteIfExists(uploadDirectory);
        }
    }

    @Test
    void receivingExpiryCleanupFailureStillRemovesStateAndReleasesBudget()
            throws Exception {
        MutableClock clock = new MutableClock(
                Instant.parse("2026-07-12T00:00:00Z")
        );
        UploadManager uploads = new UploadManager(
                temporaryDirectory,
                new UploadLimits(1, 1, 1, 1, 1),
                Duration.ofMinutes(30),
                clock
        );
        UploadOwnerScope owner = new UploadOwnerScope(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 3
        );
        UploadOwnerScope replacementOwner = new UploadOwnerScope(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 3
        );
        UploadReservation reservation = uploads.begin(
                owner, 1, Sha256Digest.of(new byte[]{1})
        );
        Path uploadDirectory = temporaryDirectory.resolve(
                reservation.uploadId().toString()
        );
        Path blocker = Files.createDirectory(uploadDirectory.resolve("blocker"));
        clock.advance(Duration.ofMinutes(30));
        try {
            UploadException cleanupFailure = assertThrows(
                    UploadException.class, uploads::removeExpired
            );

            assertEquals(
                    MinimapErrorCode.PUBLISH_IO_FAILED,
                    cleanupFailure.errorCode()
            );
            assertEquals(0, uploads.removeExpired());
            assertFalse(uploads.abort(owner, reservation.uploadId()));
            UploadReservation replacement = uploads.begin(
                    replacementOwner, 1, Sha256Digest.of(new byte[]{2})
            );
            assertTrue(uploads.abort(replacementOwner, replacement.uploadId()));
        } finally {
            Files.deleteIfExists(blocker);
            Files.deleteIfExists(uploadDirectory.resolve("payload.bin"));
            Files.deleteIfExists(uploadDirectory);
        }
    }

    @Test
    void typedExpiryCleansReceivingAndClaimedResourcesAndReleasesBudgets()
            throws Exception {
        MutableClock receivingClock = new MutableClock(
                Instant.parse("2026-07-12T00:00:00Z")
        );
        Path receivingRoot = temporaryDirectory.resolve("receiving");
        UploadManager receiving = new UploadManager(
                receivingRoot,
                new UploadLimits(1, 1, 1, 1, 1),
                Duration.ofMinutes(30),
                receivingClock
        );
        UploadOwnerScope receivingOwner = new UploadOwnerScope(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 3
        );
        UploadOwnerScope receivingReplacement = new UploadOwnerScope(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 3
        );
        UploadReservation receivingReservation = receiving.begin(
                receivingOwner, 1, Sha256Digest.of(new byte[]{1})
        );
        Path receivingDirectory = receivingRoot.resolve(
                receivingReservation.uploadId().toString()
        );

        receivingClock.advance(Duration.ofMinutes(30));

        assertEquals(1, receiving.removeExpired());
        assertFalse(Files.exists(receivingDirectory, LinkOption.NOFOLLOW_LINKS));
        UploadReservation receivingReused = receiving.begin(
                receivingReplacement, 1, Sha256Digest.of(new byte[]{2})
        );
        assertTrue(receiving.abort(receivingReplacement, receivingReused.uploadId()));

        MutableClock claimedClock = new MutableClock(
                Instant.parse("2026-07-12T00:00:00Z")
        );
        Path claimedRoot = temporaryDirectory.resolve("claimed");
        UploadManager claimed = new UploadManager(
                claimedRoot,
                new UploadLimits(1, 1, 1, 1, 1),
                Duration.ofMinutes(30),
                claimedClock
        );
        UploadOwnerScope claimedOwner = new UploadOwnerScope(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 3
        );
        UploadOwnerScope claimedReplacement = new UploadOwnerScope(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 3
        );
        byte[] claimedPayload = new byte[]{3};
        UploadReservation claimedReservation = claimed.begin(
                claimedOwner, 1, Sha256Digest.of(claimedPayload)
        );
        claimed.accept(claimedOwner, claimedReservation.uploadId(), 0, claimedPayload);
        Path claimedDirectory = claimedRoot.resolve(claimedReservation.uploadId().toString());
        CompletedUpload handle = claimed.finish(
                claimedOwner, claimedReservation.uploadId()
        );
        try {
            claimedClock.advance(Duration.ofMinutes(30));

            assertEquals(1, claimed.removeExpired());
            assertFalse(handle.isOpen());
            assertFalse(Files.exists(claimedDirectory, LinkOption.NOFOLLOW_LINKS));
            UploadReservation claimedReused = claimed.begin(
                    claimedReplacement, 1, Sha256Digest.of(new byte[]{4})
            );
            assertTrue(claimed.abort(claimedReplacement, claimedReused.uploadId()));
        } finally {
            handle.close();
        }
    }

    @Test
    void claimedExpiryDoesNotDeadlockWithConcurrentHandleClose() throws Exception {
        MutableClock clock = new MutableClock(
                Instant.parse("2026-07-12T00:00:00Z")
        );
        UploadManager uploads = new UploadManager(
                temporaryDirectory,
                new UploadLimits(1, 1, 1, 1, 1),
                Duration.ofMinutes(30),
                clock
        );
        UploadOwnerScope owner = ownerScope();
        byte[] payload = new byte[]{1};
        UploadReservation reservation = uploads.begin(
                owner, payload.length, Sha256Digest.of(payload)
        );
        assertTrue(uploads.accept(
                owner, reservation.uploadId(), 0, payload
        ).complete());
        CompletedUpload completed = uploads.finish(owner, reservation.uploadId());
        clock.advance(Duration.ofMinutes(30));

        CountDownLatch closeOwnsHandle = new CountDownLatch(1);
        CountDownLatch allowClose = new CountDownLatch(1);
        AtomicReference<Throwable> closeFailure = new AtomicReference<>();
        AtomicReference<Throwable> expiryFailure = new AtomicReference<>();
        AtomicInteger removed = new AtomicInteger(-1);
        Thread closeThread = daemonThread("upload-handle-close", () -> {
            synchronized (completed) {
                closeOwnsHandle.countDown();
                try {
                    if (!allowClose.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Expiry did not attempt handle close");
                    }
                    completed.close();
                } catch (Throwable failure) {
                    closeFailure.set(failure);
                }
            }
        });
        Thread expiryThread = daemonThread("upload-expiry", () -> {
            try {
                removed.set(uploads.removeExpired());
            } catch (Throwable failure) {
                expiryFailure.set(failure);
            }
        });

        try {
            closeThread.start();
            assertTrue(closeOwnsHandle.await(5, TimeUnit.SECONDS));
            expiryThread.start();
            assertTrue(awaitBlockedInCompletedUploadClose(expiryThread));
        } finally {
            allowClose.countDown();
        }
        closeThread.join(TimeUnit.SECONDS.toMillis(2));
        expiryThread.join(TimeUnit.SECONDS.toMillis(2));

        assertFalse(closeThread.isAlive(), "Handle close deadlocked on upload state");
        assertFalse(expiryThread.isAlive(), "Expiry deadlocked on completed handle");
        assertNull(closeFailure.get());
        assertNull(expiryFailure.get());
        assertEquals(1, removed.get());
        assertFalse(completed.isOpen());
    }

    @Test
    void typedOperationOnExpiredUploadCleansResourcesAndReleasesBudget() {
        MutableClock clock = new MutableClock(
                Instant.parse("2026-07-12T00:00:00Z")
        );
        UploadManager uploads = new UploadManager(
                temporaryDirectory,
                new UploadLimits(1, 1, 1, 1, 1),
                Duration.ofMinutes(30),
                clock
        );
        UploadOwnerScope owner = new UploadOwnerScope(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 3
        );
        UploadOwnerScope replacementOwner = new UploadOwnerScope(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 3
        );
        byte[] payload = new byte[]{1};
        UploadReservation reservation = uploads.begin(
                owner, payload.length, Sha256Digest.of(payload)
        );
        Path uploadDirectory = temporaryDirectory.resolve(reservation.uploadId().toString());
        clock.advance(Duration.ofMinutes(30));

        assertUploadNotFound(() -> uploads.accept(
                owner, reservation.uploadId(), 0, payload
        ));

        assertFalse(Files.exists(uploadDirectory, LinkOption.NOFOLLOW_LINKS));
        assertUploadNotFound(() -> uploads.accept(
                owner, reservation.uploadId(), 0, payload
        ));
        UploadReservation replacement = uploads.begin(
                replacementOwner, 1, Sha256Digest.of(new byte[]{2})
        );
        assertTrue(uploads.abort(replacementOwner, replacement.uploadId()));
    }

    @Test
    void identicalDuplicateAcceptRenewalWinsRemoveExpiredRace() throws Exception {
        Instant initial = Instant.parse("2026-07-12T00:00:00Z");
        AcceptExpiryRaceClock clock = new AcceptExpiryRaceClock(initial);
        UploadManager uploads = new UploadManager(
                temporaryDirectory,
                new UploadLimits(1, 1, 1, 1, 1),
                Duration.ofMinutes(30),
                clock
        );
        UploadOwnerScope owner = new UploadOwnerScope(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 3
        );
        byte[] payload = new byte[]{1};
        UploadReservation reservation = uploads.begin(
                owner, payload.length, Sha256Digest.of(payload)
        );
        assertTrue(uploads.accept(
                owner, reservation.uploadId(), 0, payload
        ).complete());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            clock.arm();
            Future<UploadProgress> duplicate = executor.submit(() -> {
                clock.markAcceptThread();
                return uploads.accept(owner, reservation.uploadId(), 0, payload);
            });
            assertTrue(clock.awaitAcceptHoldingState());
            Future<Integer> expired = executor.submit(() -> {
                clock.markExpiryThread();
                return uploads.removeExpired();
            });
            assertTrue(clock.awaitExpiryCaptured());

            assertTrue(duplicate.get(5, TimeUnit.SECONDS).complete());
            assertEquals(0, expired.get(5, TimeUnit.SECONDS));
            assertTrue(uploads.abort(owner, reservation.uploadId()));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void startupRemovesOnlyCanonicalUuidUploadDirectories() throws Exception {
        Path staleUpload = temporaryDirectory.resolve(UUID.randomUUID().toString());
        Files.createDirectories(staleUpload.resolve("nested"));
        Files.write(staleUpload.resolve("payload.bin"), new byte[]{1});
        Files.write(staleUpload.resolve("nested").resolve("fragment.bin"), new byte[]{2});

        Path unrelatedDirectory = temporaryDirectory.resolve("keep-directory");
        Files.createDirectories(unrelatedDirectory);
        Files.write(unrelatedDirectory.resolve("keep.bin"), new byte[]{3});
        Path unrelatedFile = temporaryDirectory.resolve("keep-file.bin");
        Files.write(unrelatedFile, new byte[]{4});
        Path uuidNamedFile = temporaryDirectory.resolve(UUID.randomUUID().toString());
        Files.write(uuidNamedFile, new byte[]{5});
        Path nonCanonicalUuidDirectory = temporaryDirectory.resolve(
                UUID.randomUUID().toString().toUpperCase(java.util.Locale.ROOT)
        );
        Files.createDirectory(nonCanonicalUuidDirectory);

        new UploadManager(
                temporaryDirectory,
                new UploadLimits(1, 1, 1, 1, 1),
                Duration.ofMinutes(30),
                Clock.systemUTC()
        );

        assertFalse(Files.exists(staleUpload, LinkOption.NOFOLLOW_LINKS));
        assertTrue(Files.isRegularFile(
                unrelatedDirectory.resolve("keep.bin"), LinkOption.NOFOLLOW_LINKS
        ));
        assertTrue(Files.isRegularFile(unrelatedFile, LinkOption.NOFOLLOW_LINKS));
        assertTrue(Files.isRegularFile(uuidNamedFile, LinkOption.NOFOLLOW_LINKS));
        assertTrue(Files.isDirectory(
                nonCanonicalUuidDirectory, LinkOption.NOFOLLOW_LINKS
        ));
    }

    private static void assertUploadNotFound(Executable operation) {
        UploadException failure = assertThrows(UploadException.class, operation);
        assertEquals(MinimapErrorCode.SESSION_NOT_FOUND, failure.errorCode());
    }

    private static void abortQuietly(
            UploadManager uploads,
            UploadOwnerScope owner,
            UploadReservation reservation
    ) {
        if (reservation == null) {
            return;
        }
        try {
            uploads.abort(owner, reservation.uploadId());
        } catch (RuntimeException ignored) {
        }
    }

    private static boolean attemptBegin(
            UploadManager uploads,
            UploadOwnerScope ownerScope,
            CountDownLatch ready,
            CountDownLatch start
    ) throws Exception {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent upload start was not released");
        }
        try {
            uploads.begin(ownerScope, 4, Sha256Digest.of(new byte[0]));
            return true;
        } catch (UploadException rejected) {
            assertEquals(MinimapErrorCode.QUOTA_EXCEEDED, rejected.errorCode());
            return false;
        }
    }

    private UploadManager manager(Clock clock, long maxUploadBytes) {
        return new UploadManager(
                temporaryDirectory,
                new UploadLimits(
                        maxUploadBytes,
                        Integer.MAX_VALUE,
                        Integer.MAX_VALUE,
                        Long.MAX_VALUE,
                        Long.MAX_VALUE
                ),
                Duration.ofMinutes(30),
                clock
        );
    }

    private static UploadOwnerScope ownerScope() {
        return new UploadOwnerScope(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 3
        );
    }

    private static byte[] readAll(CompletedUpload completed) throws IOException {
        ByteBuffer destination = ByteBuffer.allocate(Math.toIntExact(completed.length()));
        while (destination.hasRemaining()) {
            assertTrue(completed.read(destination) > 0);
        }
        assertEquals(-1, completed.read(ByteBuffer.allocate(1)));
        return destination.array();
    }

    private static Thread daemonThread(String name, Runnable operation) {
        Thread thread = new Thread(operation, name);
        thread.setDaemon(true);
        return thread;
    }

    private static boolean awaitBlockedInCompletedUploadClose(Thread thread)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            boolean blockedInClose = thread.getState() == Thread.State.BLOCKED
                    && Arrays.stream(thread.getStackTrace()).anyMatch(
                    frame -> frame.getClassName().equals(CompletedUpload.class.getName())
                            && frame.getMethodName().equals("close")
            );
            if (blockedInClose) {
                return true;
            }
            Thread.sleep(10);
        }
        return false;
    }

    private static final class AcceptExpiryRaceClock extends Clock {
        private final Instant initial;
        private final CountDownLatch acceptHoldingState = new CountDownLatch(1);
        private final CountDownLatch expiryCaptured = new CountDownLatch(1);
        private volatile Thread acceptThread;
        private volatile Thread expiryThread;
        private volatile boolean armed;
        private boolean firstAcceptRead = true;

        private AcceptExpiryRaceClock(Instant initial) {
            this.initial = initial;
        }

        private void arm() {
            armed = true;
        }

        private void markAcceptThread() {
            acceptThread = Thread.currentThread();
        }

        private void markExpiryThread() {
            expiryThread = Thread.currentThread();
        }

        private boolean awaitAcceptHoldingState() throws InterruptedException {
            return acceptHoldingState.await(5, TimeUnit.SECONDS);
        }

        private boolean awaitExpiryCaptured() throws InterruptedException {
            return expiryCaptured.await(5, TimeUnit.SECONDS);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Instant instant() {
            if (!armed) {
                return initial;
            }
            Thread current = Thread.currentThread();
            if (current == acceptThread) {
                if (firstAcceptRead) {
                    firstAcceptRead = false;
                    acceptHoldingState.countDown();
                    awaitExpiryCapture();
                }
                return initial.plus(Duration.ofMinutes(29));
            }
            if (current == expiryThread) {
                expiryCaptured.countDown();
                return initial.plus(Duration.ofMinutes(30));
            }
            return initial;
        }

        private void awaitExpiryCapture() {
            try {
                if (!expiryCaptured.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Expiry sweep did not capture its time");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Accept race was interrupted", exception);
            }
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
            throw new UnsupportedOperationException();
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
