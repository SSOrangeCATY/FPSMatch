package com.ptcrys.fpsmatch.common.minimap.server;

import com.ptcrys.fpsmatch.core.minimap.contract.MinimapErrorCode;
import com.ptcrys.fpsmatch.core.minimap.format.Sha256Digest;
import com.ptcrys.fpsmatch.core.minimap.model.ContainerPath;
import com.ptcrys.fpsmatch.core.minimap.model.Sha256;
import com.ptcrys.fpsmatch.core.minimap.wire.WireEditor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

public final class UploadManager {
    private static final int FRAGMENT_BYTES = 256 * 1024;
    private static final int MAX_FRAGMENT_COUNT = 4096;

    private final long maximumUploadBytes;
    private final Duration idleTtl;
    private final Clock clock;
    private final Path root;
    private final UploadLimits limits;
    private final PayloadAllocator payloadAllocator;
    private final UploadIdSource uploadIdSource;
    private final Map<UUID, UploadState> uploads = new ConcurrentHashMap<>();
    private final Object budgetLock = new Object();
    private final Map<UUID, OwnerBudget> ownerBudgets = new HashMap<>();
    private int reservedUploads;
    private long reservedBytes;

    public UploadManager(
            Path root,
            UploadLimits limits,
            Duration idleTtl,
            Clock clock
    ) {
        this(
                root,
                limits,
                idleTtl,
                clock,
                UploadManager::allocatePayload,
                UUID::randomUUID
        );
    }

    UploadManager(
            Path root,
            UploadLimits limits,
            Duration idleTtl,
            Clock clock,
            PayloadAllocator payloadAllocator
    ) {
        this(root, limits, idleTtl, clock, payloadAllocator, UUID::randomUUID);
    }

    UploadManager(
            Path root,
            UploadLimits limits,
            Duration idleTtl,
            Clock clock,
            PayloadAllocator payloadAllocator,
            UploadIdSource uploadIdSource
    ) {
        this.root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
        this.limits = Objects.requireNonNull(limits, "limits");
        this.maximumUploadBytes = limits.maximumUploadBytes();
        this.idleTtl = Objects.requireNonNull(idleTtl, "idleTtl");
        if (idleTtl.isZero() || idleTtl.isNegative()) {
            throw new IllegalArgumentException("Upload TTL must be positive");
        }
        this.clock = Objects.requireNonNull(clock, "clock");
        this.payloadAllocator = Objects.requireNonNull(payloadAllocator, "payloadAllocator");
        this.uploadIdSource = Objects.requireNonNull(uploadIdSource, "uploadIdSource");
        try {
            Files.createDirectories(this.root);
            removeStaleUploadDirectories();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to create upload root", exception);
        }
    }

    public UploadReservation begin(
            UploadOwnerScope ownerScope,
            long totalLength,
            Sha256 expectedHash
    ) {
        Objects.requireNonNull(ownerScope, "ownerScope");
        if (totalLength <= 0) {
            throw new IllegalArgumentException("Upload dimensions are invalid");
        }
        long fragmentCount = (totalLength - 1L) / FRAGMENT_BYTES + 1L;
        if (fragmentCount > MAX_FRAGMENT_COUNT) {
            throw failure(MinimapErrorCode.QUOTA_EXCEEDED, "Upload has too many fragments");
        }
        return beginInternal(
                ownerScope, totalLength, Math.toIntExact(fragmentCount), expectedHash, null
        );
    }

    public UploadReservation begin(
            UploadOwnerScope ownerScope,
            WireEditor.UploadBegin metadata
    ) {
        Objects.requireNonNull(ownerScope, "ownerScope");
        Objects.requireNonNull(metadata, "metadata");
        return beginInternal(
                ownerScope,
                metadata.totalLength(),
                metadata.fragmentCount(),
                metadata.expectedHash(),
                metadata
        );
    }

    private UploadReservation beginInternal(
            UploadOwnerScope ownerScope,
            long totalLength,
            int fragmentCount,
            Sha256 expectedHash,
            WireEditor.UploadBegin metadata
    ) {
        Objects.requireNonNull(expectedHash, "expectedHash");
        if (totalLength <= 0 || fragmentCount <= 0 || fragmentCount > totalLength) {
            throw new IllegalArgumentException("Upload dimensions are invalid");
        }
        if (totalLength > maximumUploadBytes) {
            throw failure(MinimapErrorCode.QUOTA_EXCEEDED, "Upload exceeds its byte quota");
        }
        if (fragmentCount > MAX_FRAGMENT_COUNT) {
            throw failure(MinimapErrorCode.QUOTA_EXCEEDED, "Upload has too many fragments");
        }
        long requiredFragments = (totalLength - 1L) / FRAGMENT_BYTES + 1L;
        if (fragmentCount != requiredFragments) {
            throw new IllegalArgumentException("Upload fragment count is not canonical");
        }
        BudgetReservation budgetReservation = reserveBudget(ownerScope, totalLength);
        UploadState candidate = null;
        try {
            while (true) {
                UUID uploadId = Objects.requireNonNull(
                        uploadIdSource.next(), "uploadIdSource returned null"
                );
                try {
                    candidate = createTypedState(
                            uploadId,
                            totalLength,
                            fragmentCount,
                            expectedHash,
                            ownerScope,
                            budgetReservation,
                            metadata
                    );
                } catch (UploadDirectoryCollisionException collision) {
                    continue;
                } catch (IOException exception) {
                    throw storageFailure("Unable to create upload payload", exception);
                }
                UploadReservation reservation = candidate.reservation;
                if (uploads.putIfAbsent(reservation.uploadId(), candidate) == null) {
                    return reservation;
                }
                try {
                    deleteTypedFiles(candidate);
                } catch (IOException exception) {
                    throw storageFailure("Unable to remove colliding upload payload", exception);
                }
                candidate = null;
            }
        } catch (RuntimeException | Error failure) {
            rollbackUnpublished(candidate, budgetReservation, failure);
            throw failure;
        }
    }

    public boolean abort(UploadOwnerScope ownerScope, UUID uploadId) {
        Objects.requireNonNull(ownerScope, "ownerScope");
        Objects.requireNonNull(uploadId, "uploadId");
        UploadState state = uploads.get(uploadId);
        if (state == null) {
            return false;
        }
        DetachedUpload detached;
        synchronized (state) {
            if (!ownerScope.equals(state.ownerScope)) {
                throw failure(MinimapErrorCode.SESSION_NOT_FOUND, "Upload was not found");
            }
            if (uploads.get(uploadId) != state
                    || (state.phase != UploadPhase.RECEIVING
                    && state.phase != UploadPhase.COMPLETED)) {
                return false;
            }
            detached = Objects.requireNonNull(detachState(state), "Upload state disappeared during abort");
        }
        UploadCleanupFailures.rethrow(closeDetached(detached));
        return true;
    }

    public UploadProgress accept(
            UploadOwnerScope ownerScope,
            UUID uploadId,
            int fragmentIndex,
            byte[] bytes
    ) {
        return acceptInternal(
                Objects.requireNonNull(ownerScope, "ownerScope"),
                uploadId,
                fragmentIndex,
                bytes
        );
    }

    private UploadProgress acceptInternal(
            UploadOwnerScope ownerScope,
            UUID uploadId,
            int fragmentIndex,
            byte[] bytes
    ) {
        Objects.requireNonNull(uploadId, "uploadId");
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length == 0 || bytes.length > FRAGMENT_BYTES) {
            throw new IllegalArgumentException("Upload fragment length is invalid");
        }
        UploadState state = requirePresent(uploadId);
        synchronized (state) {
            requireActive(ownerScope, uploadId, state);
            if (fragmentIndex < 0 || fragmentIndex >= state.fragmentCount()) {
                throw new IllegalArgumentException("Upload fragment index is invalid");
            }
            return acceptTyped(state, fragmentIndex, bytes);
        }
    }

    public CompletedUpload finish(UploadOwnerScope ownerScope, UUID uploadId) {
        Objects.requireNonNull(ownerScope, "ownerScope");
        UploadState state = requirePresent(Objects.requireNonNull(uploadId, "uploadId"));
        synchronized (state) {
            requireActive(ownerScope, uploadId, state);
            if (state.receivedFragments != state.fragmentCount()
                    || state.receivedBytes != state.reservation.totalLength()) {
                throw failure(MinimapErrorCode.VALIDATION_FAILED, "Upload is incomplete");
            }
            try {
                if (Files.size(state.payloadFile) != state.reservation.totalLength()) {
                    throw closeTypedWithFailure(
                            state,
                            failure(
                                    MinimapErrorCode.VALIDATION_FAILED,
                                    "Upload length does not match its declaration"
                            )
                    );
                }
                Sha256 actualHash;
                try (InputStream input = Files.newInputStream(state.payloadFile)) {
                    actualHash = Sha256Digest.of(input);
                }
                if (!actualHash.equals(state.reservation.expectedHash())) {
                    throw closeTypedWithFailure(
                            state,
                            failure(MinimapErrorCode.HASH_MISMATCH, "Upload hash does not match")
                    );
                }
                FileChannel channel = FileChannel.open(
                        state.payloadFile, StandardOpenOption.READ
                );
                state.phase = UploadPhase.COMPLETED;
                state.renew(clock.instant().plus(idleTtl));
                CompletedUpload completed = new CompletedUpload(
                        uploadId,
                        ownerScope,
                        state.reservation.totalLength(),
                        state.reservation.expectedHash(),
                        channel,
                        state.metadata == null,
                        () -> closeClaim(state)
                );
                state.completedUpload = completed;
                return completed;
            } catch (IOException exception) {
                throw closeTypedWithFailure(
                        state,
                        storageFailure("Unable to finish upload payload", exception)
                );
            }
        }
    }

    public CompletedUpload claimCompleted(
            UUID uploadId,
            UploadOwnerScope ownerScope,
            WireEditor.UploadPurpose purpose,
            Optional<ContainerPath> path,
            Sha256 expectedHash
    ) {
        Objects.requireNonNull(uploadId, "uploadId");
        Objects.requireNonNull(ownerScope, "ownerScope");
        Objects.requireNonNull(purpose, "purpose");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(expectedHash, "expectedHash");

        UploadState state = requirePresent(uploadId);
        CompletedUpload completed;
        UploadException rejection = null;
        synchronized (state) {
            if (uploads.get(uploadId) != state || !ownerScope.equals(state.ownerScope)) {
                throw failure(MinimapErrorCode.SESSION_NOT_FOUND, "Upload was not found");
            }
            if (state.phase != UploadPhase.COMPLETED) {
                throw failure(MinimapErrorCode.SESSION_NOT_FOUND, "Upload was not found");
            }
            completed = Objects.requireNonNull(
                    state.completedUpload, "Completed upload handle is missing"
            );
            if (!clock.instant().isBefore(state.expiresAt)) {
                rejection = failure(MinimapErrorCode.SESSION_NOT_FOUND, "Upload has expired");
            } else if (state.metadata == null
                    || state.metadata.purpose() != purpose
                    || !state.metadata.path().equals(path)) {
                rejection = failure(
                        MinimapErrorCode.VALIDATION_FAILED,
                        "Upload metadata does not match its reservation"
                );
            } else if (!state.metadata.expectedHash().equals(expectedHash)
                    || !state.reservation.expectedHash().equals(expectedHash)) {
                rejection = failure(
                        MinimapErrorCode.HASH_MISMATCH,
                        "Upload hash does not match its reservation"
                );
            }
            // Reserve the claim before I/O; handle close always happens after releasing state.
            state.phase = UploadPhase.CLAIMING;
        }

        if (rejection != null) {
            throw closeCompletedWithFailure(completed, rejection);
        }

        try {
            if (!completed.hasExpectedLength()) {
                throw closeCompletedWithFailure(
                        completed,
                        failure(
                                MinimapErrorCode.VALIDATION_FAILED,
                                "Upload length does not match its declaration"
                        )
                );
            }
            if (!completed.hasExpectedHash()) {
                throw closeCompletedWithFailure(
                        completed,
                        failure(MinimapErrorCode.HASH_MISMATCH, "Upload hash does not match")
                );
            }
            completed.activate();
        } catch (IOException exception) {
            synchronized (state) {
                rejection = uploads.get(uploadId) != state || state.phase == UploadPhase.CLOSED
                        ? failure(MinimapErrorCode.SESSION_NOT_FOUND, "Upload was not found")
                        : storageFailure("Unable to verify completed upload payload", exception);
            }
            throw closeCompletedWithFailure(completed, rejection);
        }

        synchronized (state) {
            if (uploads.get(uploadId) != state
                    || state.phase != UploadPhase.CLAIMING
                    || !clock.instant().isBefore(state.expiresAt)) {
                rejection = failure(MinimapErrorCode.SESSION_NOT_FOUND, "Upload was not found");
            } else {
                state.phase = UploadPhase.CLAIMED;
                state.renew(clock.instant().plus(idleTtl));
                return completed;
            }
        }
        throw closeCompletedWithFailure(completed, rejection);
    }

    public int removeExpired() {
        Instant now = clock.instant();
        int removed = 0;
        for (Map.Entry<UUID, UploadState> entry : uploads.entrySet()) {
            UploadState state = entry.getValue();
            CompletedUpload claimed = null;
            synchronized (state) {
                if (uploads.get(entry.getKey()) != state || now.isBefore(state.expiresAt)) {
                    continue;
                }
                if (state.phase == UploadPhase.COMPLETED
                        || state.phase == UploadPhase.CLAIMING
                        || state.phase == UploadPhase.CLAIMED) {
                    claimed = state.completedUpload;
                } else {
                    IOException cleanupFailure = null;
                    try {
                        deleteTypedFiles(state);
                    } catch (IOException exception) {
                        cleanupFailure = exception;
                    }
                    synchronized (budgetLock) {
                        if (uploads.remove(entry.getKey(), state)) {
                            releaseBudget(state);
                            state.phase = UploadPhase.CLOSED;
                            removed++;
                        }
                    }
                    if (cleanupFailure != null) {
                        throw storageFailure(
                                "Unable to expire upload payload", cleanupFailure
                        );
                    }
                }
            }
            if (claimed != null) {
                try {
                    claimed.close();
                } catch (IOException exception) {
                    throw storageFailure("Unable to expire claimed upload", exception);
                }
                if (uploads.get(entry.getKey()) != state) {
                    removed++;
                }
            }
        }
        return removed;
    }

    public void closeScope(UploadOwnerScope ownerScope) {
        Objects.requireNonNull(ownerScope, "ownerScope");
        cleanupStates(state -> ownerScope.equals(state.ownerScope));
    }

    public boolean tracks(UploadOwnerScope ownerScope, UUID uploadId) {
        Objects.requireNonNull(ownerScope, "ownerScope");
        UploadState state = uploads.get(Objects.requireNonNull(uploadId, "uploadId"));
        if (state == null) return false;
        synchronized (state) {
            return uploads.get(uploadId) == state && ownerScope.equals(state.ownerScope)
                    && state.phase != UploadPhase.CLOSED;
        }
    }

    public void closeAll() {
        cleanupStates(state -> true);
    }

    private void cleanupStates(Predicate<UploadState> selected) {
        Throwable failure = null;
        for (UploadState state : uploads.values().stream().toList()) {
            if (selected.test(state)) {
                failure = UploadCleanupFailures.merge(failure, closeState(state));
            }
        }
        UploadCleanupFailures.rethrow(failure);
    }

    private UploadState requirePresent(UUID uploadId) {
        UploadState state = uploads.get(uploadId);
        if (state == null) {
            throw failure(MinimapErrorCode.SESSION_NOT_FOUND, "Upload was not found");
        }
        return state;
    }

    private void requireActive(
            UploadOwnerScope ownerScope,
            UUID uploadId,
            UploadState state
    ) {
        if (uploads.get(uploadId) != state || !Objects.equals(ownerScope, state.ownerScope)) {
            throw failure(MinimapErrorCode.SESSION_NOT_FOUND, "Upload was not found");
        }
        if (state.phase != UploadPhase.RECEIVING) {
            throw failure(MinimapErrorCode.SESSION_NOT_FOUND, "Upload was not found");
        }
        if (!clock.instant().isBefore(state.expiresAt)) {
            UploadException expired = failure(
                    MinimapErrorCode.SESSION_NOT_FOUND, "Upload has expired"
            );
            throw closeTypedWithFailure(state, expired);
        }
    }

    private static UploadException failure(MinimapErrorCode code, String message) {
        return new UploadException(code, message);
    }

    private static UploadException storageFailure(String message, IOException cause) {
        return new UploadException(MinimapErrorCode.PUBLISH_IO_FAILED, message, cause);
    }

    private UploadReservation reservation(
            UUID uploadId,
            long totalLength,
            int fragmentCount,
            Sha256 expectedHash
    ) {
        return new UploadReservation(
                uploadId,
                totalLength,
                fragmentCount,
                expectedHash,
                clock.instant().plus(idleTtl)
        );
    }

    private UploadState createTypedState(
            UUID uploadId,
            long totalLength,
            int fragmentCount,
            Sha256 expectedHash,
            UploadOwnerScope ownerScope,
            BudgetReservation budgetReservation,
            WireEditor.UploadBegin metadata
    ) throws IOException {
        Path uploadDirectory = root.resolve(uploadId.toString());
        Path payloadFile = uploadDirectory.resolve("payload.bin");
        try {
            Files.createDirectory(uploadDirectory);
        } catch (FileAlreadyExistsException collision) {
            throw new UploadDirectoryCollisionException(collision);
        }
        try {
            payloadAllocator.allocate(payloadFile, totalLength);
            UploadReservation reservation = reservation(
                    uploadId, totalLength, fragmentCount, expectedHash
            );
            UploadState state = new UploadState(
                    reservation,
                    ownerScope,
                    budgetReservation,
                    metadata,
                    uploadDirectory,
                    payloadFile
            );
            return state;
        } catch (IOException | RuntimeException | Error failure) {
            cleanupFailedAllocation(payloadFile, uploadDirectory, failure);
            throw failure;
        }
    }

    private static void cleanupFailedAllocation(
            Path payloadFile,
            Path uploadDirectory,
            Throwable failure
    ) {
        deleteAfterFailure(payloadFile, failure);
        deleteAfterFailure(uploadDirectory, failure);
    }

    private static void deleteAfterFailure(Path path, Throwable failure) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException | RuntimeException | Error cleanupFailure) {
            if (cleanupFailure != failure) {
                failure.addSuppressed(cleanupFailure);
            }
        }
    }

    private static void allocatePayload(Path payloadFile, long totalLength)
            throws IOException {
        try (FileChannel channel = FileChannel.open(
                payloadFile,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE
        )) {
            channel.position(totalLength - 1L);
            ByteBuffer marker = ByteBuffer.wrap(new byte[]{0});
            while (marker.hasRemaining()) {
                if (channel.write(marker) <= 0) {
                    throw new IOException("Upload payload allocation made no progress");
                }
            }
        }
    }

    private void removeStaleUploadDirectories() throws IOException {
        try (var entries = Files.list(root)) {
            for (Path entry : entries.toList()) {
                String name = entry.getFileName().toString();
                UUID uploadId;
                try {
                    uploadId = UUID.fromString(name);
                } catch (IllegalArgumentException notAnUploadId) {
                    continue;
                }
                if (!uploadId.toString().equals(name)) {
                    continue;
                }
                BasicFileAttributes attributes = Files.readAttributes(
                        entry,
                        BasicFileAttributes.class,
                        java.nio.file.LinkOption.NOFOLLOW_LINKS
                );
                if (attributes.isDirectory()) {
                    deleteTreeNoFollow(entry);
                }
            }
        }
    }

    private static void deleteTreeNoFollow(Path directory) throws IOException {
        Files.walkFileTree(directory, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(
                    Path file,
                    BasicFileAttributes attributes
            ) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(
                    Path visitedDirectory,
                    IOException failure
            ) throws IOException {
                if (failure != null) {
                    throw failure;
                }
                Files.delete(visitedDirectory);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private UploadProgress acceptTyped(
            UploadState state,
            int fragmentIndex,
            byte[] bytes
    ) {
        if (bytes.length != expectedTypedFragmentLength(state, fragmentIndex)) {
            throw new IllegalArgumentException("Upload fragment length is not canonical");
        }
        long offset = (long) fragmentIndex * FRAGMENT_BYTES;
        try {
            if (state.received.get(fragmentIndex)) {
                if (!Arrays.equals(readFragment(state.payloadFile, offset, bytes.length), bytes)) {
                    throw failure(
                            MinimapErrorCode.FRAGMENT_CONFLICT,
                            "Upload fragment conflicts with its prior value"
                    );
                }
                state.renew(clock.instant().plus(idleTtl));
                return state.progress();
            }
            writeFragment(state.payloadFile, offset, bytes);
        } catch (IOException exception) {
            throw closeTypedWithFailure(
                    state,
                    storageFailure("Unable to access upload fragment", exception)
            );
        }
        state.received.set(fragmentIndex);
        state.receivedFragments++;
        state.receivedBytes += bytes.length;
        state.renew(clock.instant().plus(idleTtl));
        return state.progress();
    }

    private static int expectedTypedFragmentLength(UploadState state, int fragmentIndex) {
        if (fragmentIndex < state.fragmentCount() - 1) {
            return FRAGMENT_BYTES;
        }
        long precedingBytes = (long) (state.fragmentCount() - 1) * FRAGMENT_BYTES;
        return Math.toIntExact(state.reservation.totalLength() - precedingBytes);
    }

    private static byte[] readFragment(Path payloadFile, long offset, int length)
            throws IOException {
        byte[] result = new byte[length];
        ByteBuffer destination = ByteBuffer.wrap(result);
        try (FileChannel channel = FileChannel.open(payloadFile, StandardOpenOption.READ)) {
            long position = offset;
            while (destination.hasRemaining()) {
                int count = channel.read(destination, position);
                if (count <= 0) {
                    throw new IOException("Upload fragment read made no progress");
                }
                position += count;
            }
        }
        return result;
    }

    private static void writeFragment(Path payloadFile, long offset, byte[] bytes)
            throws IOException {
        ByteBuffer source = ByteBuffer.wrap(bytes);
        try (FileChannel channel = FileChannel.open(payloadFile, StandardOpenOption.WRITE)) {
            long position = offset;
            while (source.hasRemaining()) {
                int count = channel.write(source, position);
                if (count <= 0) {
                    throw new IOException("Upload fragment write made no progress");
                }
                position += count;
            }
        }
    }

    private void closeClaim(UploadState state) throws IOException {
        synchronized (state) {
            if (state.phase == UploadPhase.CLOSED) {
                return;
            }
            if (uploads.get(state.reservation.uploadId()) != state) {
                state.phase = UploadPhase.CLOSED;
                return;
            }
            IOException cleanupFailure = null;
            try {
                deleteTypedFiles(state);
            } catch (IOException exception) {
                cleanupFailure = exception;
            }
            synchronized (budgetLock) {
                if (uploads.remove(state.reservation.uploadId(), state)) {
                    releaseBudget(state);
                }
                state.phase = UploadPhase.CLOSED;
            }
            if (cleanupFailure != null) {
                throw cleanupFailure;
            }
        }
    }

    private Throwable closeState(UploadState state) { return closeDetached(detachState(state)); }

    private DetachedUpload detachState(UploadState state) {
        Throwable failure = null;
        synchronized (state) {
            UUID uploadId = state.reservation.uploadId();
            if (uploads.get(uploadId) != state) {
                return null;
            }
            synchronized (budgetLock) {
                if (!uploads.remove(uploadId, state)) {
                    return null;
                }
                state.phase = UploadPhase.CLOSED;
                try {
                    releaseBudget(state);
                } catch (RuntimeException | Error budgetFailure) {
                    failure = budgetFailure;
                }
            }
            return new DetachedUpload(state, state.completedUpload, failure);
        }
    }

    private Throwable closeDetached(DetachedUpload detached) {
        if (detached == null) {
            return null;
        }
        Throwable failure = detached.failure();
        if (detached.completed() != null) {
            try {
                detached.completed().close();
            } catch (IOException | RuntimeException | Error closeFailure) {
                failure = UploadCleanupFailures.merge(failure, closeFailure);
            }
        }
        try {
            deleteTypedFiles(detached.state());
        } catch (IOException | RuntimeException | Error deleteFailure) {
            failure = UploadCleanupFailures.merge(failure, deleteFailure);
        }
        return failure;
    }

    private UploadException closeTypedWithFailure(
            UploadState state,
            UploadException failure
    ) {
        state.phase = UploadPhase.CLOSED;
        IOException cleanupFailure = null;
        try {
            deleteTypedFiles(state);
        } catch (IOException exception) {
            cleanupFailure = exception;
        }
        synchronized (budgetLock) {
            if (uploads.remove(state.reservation.uploadId(), state)) {
                releaseBudget(state);
            }
        }
        if (cleanupFailure != null) {
            failure.addSuppressed(cleanupFailure);
        }
        return failure;
    }

    private static UploadException closeCompletedWithFailure(
            CompletedUpload completed,
            UploadException failure
    ) {
        try {
            completed.close();
        } catch (Throwable cleanupFailure) {
            if (cleanupFailure != failure) {
                failure.addSuppressed(cleanupFailure);
            }
        }
        return failure;
    }

    private static void deleteTypedFiles(UploadState state) throws IOException {
        Files.deleteIfExists(state.payloadFile);
        Files.deleteIfExists(state.uploadDirectory);
    }

    private void requireBudget(OwnerBudget ownerBudget, long totalLength) {
        if (reservedUploads >= limits.maximumGlobalInFlightUploads()
                || ownerBudget.reservedUploads >= limits.maximumOwnerInFlightUploads()
                || totalLength > limits.maximumGlobalDeclaredBytes() - reservedBytes
                || totalLength > limits.maximumOwnerDeclaredBytes() - ownerBudget.reservedBytes) {
            throw failure(MinimapErrorCode.QUOTA_EXCEEDED, "Upload budget is exhausted");
        }
    }

    private BudgetReservation reserveBudget(
            UploadOwnerScope ownerScope,
            long totalLength
    ) {
        synchronized (budgetLock) {
            UUID ownerId = ownerScope.actorId();
            OwnerBudget ownerBudget = ownerBudgets.get(ownerId);
            if (ownerBudget == null) {
                ownerBudget = new OwnerBudget();
            }
            requireBudget(ownerBudget, totalLength);
            ownerBudgets.putIfAbsent(ownerId, ownerBudget);
            reservedUploads++;
            reservedBytes += totalLength;
            ownerBudget.reservedUploads++;
            ownerBudget.reservedBytes += totalLength;
            return new BudgetReservation(ownerId, totalLength);
        }
    }

    private void rollbackUnpublished(
            UploadState candidate,
            BudgetReservation budgetReservation,
            Throwable failure
    ) {
        try {
            if (candidate != null) {
                deleteTypedFiles(candidate);
            }
        } catch (IOException | RuntimeException cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        } finally {
            try {
                synchronized (budgetLock) {
                    releaseBudget(budgetReservation);
                }
            } catch (RuntimeException budgetFailure) {
                failure.addSuppressed(budgetFailure);
            }
        }
    }

    private void releaseBudget(UploadState state) {
        releaseBudget(state.budgetReservation);
    }

    private void releaseBudget(BudgetReservation budgetReservation) {
        if (!budgetReservation.reserved) {
            return;
        }
        OwnerBudget ownerBudget = ownerBudgets.get(budgetReservation.ownerId);
        if (ownerBudget == null) {
            throw new IllegalStateException("Upload owner budget is missing");
        }
        reservedUploads--;
        reservedBytes -= budgetReservation.totalLength;
        ownerBudget.reservedUploads--;
        ownerBudget.reservedBytes -= budgetReservation.totalLength;
        budgetReservation.reserved = false;
        if (ownerBudget.reservedUploads == 0 && ownerBudget.reservedBytes == 0) {
            ownerBudgets.remove(budgetReservation.ownerId);
        }
    }

    private static final class OwnerBudget {
        private int reservedUploads;
        private long reservedBytes;
    }

    private static final class BudgetReservation {
        private final UUID ownerId;
        private final long totalLength;
        private boolean reserved = true;

        private BudgetReservation(UUID ownerId, long totalLength) {
            this.ownerId = ownerId;
            this.totalLength = totalLength;
        }
    }

    private static final class UploadDirectoryCollisionException extends IOException {
        private UploadDirectoryCollisionException(FileAlreadyExistsException cause) {
            super(cause);
        }
    }

    @FunctionalInterface
    interface PayloadAllocator {
        void allocate(Path payloadFile, long totalLength) throws IOException;
    }

    @FunctionalInterface
    interface UploadIdSource {
        UUID next();
    }

    private record DetachedUpload(UploadState state, CompletedUpload completed,
                                  Throwable failure) { }

    private enum UploadPhase {
        RECEIVING,
        COMPLETED,
        CLAIMING,
        CLAIMED,
        CLOSED
    }

    private static final class UploadState {
        private final UploadReservation reservation;
        private final UploadOwnerScope ownerScope;
        private final WireEditor.UploadBegin metadata;
        private final BitSet received;
        private final Path uploadDirectory;
        private final Path payloadFile;
        private final BudgetReservation budgetReservation;
        private Instant expiresAt;
        private int receivedFragments;
        private long receivedBytes;
        private UploadPhase phase = UploadPhase.RECEIVING;
        private CompletedUpload completedUpload;

        private UploadState(
                UploadReservation reservation,
                UploadOwnerScope ownerScope,
                BudgetReservation budgetReservation,
                WireEditor.UploadBegin metadata,
                Path uploadDirectory,
                Path payloadFile
        ) {
            this.reservation = reservation;
            this.ownerScope = Objects.requireNonNull(ownerScope, "ownerScope");
            this.metadata = metadata;
            this.received = new BitSet(reservation.fragmentCount());
            this.uploadDirectory = uploadDirectory;
            this.payloadFile = payloadFile;
            this.budgetReservation = budgetReservation;
            this.expiresAt = reservation.expiresAt();
        }

        private int fragmentCount() {
            return reservation.fragmentCount();
        }

        private void renew(Instant nextExpiry) {
            expiresAt = nextExpiry;
        }

        private UploadProgress progress() {
            return new UploadProgress(
                    reservation.uploadId(),
                    receivedFragments,
                    fragmentCount(),
                    receivedBytes,
                    reservation.totalLength(),
                    receivedFragments == fragmentCount()
            );
        }
    }
}
