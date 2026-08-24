package com.ptcrys.fpsmatch.core.minimap.storage;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.ptcrys.fpsmatch.core.minimap.codec.MinimapCodecs;
import com.ptcrys.fpsmatch.core.minimap.contract.MinimapHardLimits;
import com.ptcrys.fpsmatch.core.minimap.format.JcsCanonicalizer;
import com.ptcrys.fpsmatch.core.minimap.format.MapRebindService;
import com.ptcrys.fpsmatch.core.minimap.format.CommittedMapPairSnapshot;
import com.ptcrys.fpsmatch.core.minimap.format.ContainerLimits;
import com.ptcrys.fpsmatch.core.minimap.format.ContainerValidationException;
import com.ptcrys.fpsmatch.core.minimap.format.Sha256Digest;
import com.ptcrys.fpsmatch.core.minimap.format.StrictJsonParser;
import com.ptcrys.fpsmatch.core.minimap.model.MapKey;
import com.ptcrys.fpsmatch.core.minimap.model.NamespacedId;
import com.ptcrys.fpsmatch.core.minimap.model.Sha256;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public final class MinimapRepository {
    private static final String STATE_FILE = "publish-state.json";
    private static final String RECORD_FILE = "publish-record.json";
    private static final String SOURCE_FILE = "source.fpsmap";
    private static final String RUNTIME_FILE = "runtime.fpsmapc";
    private static final String DIRECTORY_SYNC_LOST_REASON = "directory-sync-lost";
    private static final int LOCK_STRIPE_COUNT = 64;
    private static final Pattern PIN_FILE_NAME = Pattern.compile(
            "[0-9a-f]{64}\\.json"
    );
    private static final Sha256 ZERO_HASH = Sha256.parse("0".repeat(64));
    private static final Duration DEFAULT_RESERVATION_TTL = Duration.ofMinutes(30);
    private static final int MAX_DIRECTORY_ENTRIES = 4096;
    private static final int MAX_CLEANUP_TREE_NODES = 16384;
    private static final int MAX_CLEANUP_TREE_DEPTH = 16;

    private final RepositoryFileSystem fileSystem;
    private final Clock clock;
    private final MinimapRepositoryPersistence persistence;
    private final MinimapRepositoryJournalAccess journalAccess;
    private final StrictRepositoryDispatch strictDispatch;
    private final Map<Integer, Object> locks = new ConcurrentHashMap<>();
    private final Map<String, CurrentPointer> authoritativePointers = new ConcurrentHashMap<>();
    private final Set<String> suspendedMaps = ConcurrentHashMap.newKeySet();

    public MinimapRepository(Path root) {
        this(root, new NioRepositoryFileSystem(), Clock.systemUTC());
    }

    public MinimapRepository(Path root, RepositoryFileSystem fileSystem) {
        this(root, fileSystem, Clock.systemUTC());
    }

    public MinimapRepository(Path root, RepositoryFileSystem fileSystem, Clock clock) {
        this(root, fileSystem, clock, MinimapAuthorityJournal.provider());
    }

    MinimapRepository(
            Path root,
            RepositoryFileSystem fileSystem,
            Clock clock,
            AuthorityJournalProvider authorityProvider
    ) {
        Path normalizedRoot = Objects.requireNonNull(root, "root")
                .toAbsolutePath().normalize();
        this.fileSystem = Objects.requireNonNull(fileSystem, "fileSystem");
        this.clock = Objects.requireNonNull(clock, "clock");
        Objects.requireNonNull(authorityProvider, "authorityProvider");
        try {
            fileSystem.createDirectories(normalizedRoot);
            this.persistence = new MinimapRepositoryPersistence(
                    normalizedRoot, normalizedRoot.toRealPath(), fileSystem
            );
            this.journalAccess = new MinimapRepositoryJournalAccess(
                    authorityProvider
            );
            this.strictDispatch = new StrictRepositoryDispatch(
                    fileSystem, authorityProvider, journalAccess
            );
        } catch (IOException exception) {
            throw storageFailure("Unable to create minimap repository root", exception);
        }
    }

    public PublishTransaction reserve(
            MapKey key,
            NamespacedId dimension,
            NamespacedId documentId,
            long baseRevision
    ) {
        return reserve(
                key, dimension, documentId, baseRevision, DEFAULT_RESERVATION_TTL
        );
    }

    public PublishTransaction reserve(
            MapKey key,
            NamespacedId dimension,
            NamespacedId documentId,
            long baseRevision,
            Duration ttl
    ) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(documentId, "documentId");
        Objects.requireNonNull(ttl, "ttl");
        if (baseRevision < 0 || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("Reservation base revision and TTL are invalid");
        }
        Path mapDirectory = mapDirectory(key);
        if (strictDispatch.mode(mapDirectory, key)
                == StrictRepositoryDispatch.Mode.STRICT) {
            AuthorityJournalProvider.StrictReservationResult strictReservation =
                    strictDispatch.reservePublish(
                            mapDirectory, "publish-" + UUID.randomUUID()
                    );
            if (strictReservation.status()
                    == AuthorityJournalProvider.MutationStatus.UNAVAILABLE) {
                throw new ContainerStorageException(
                        "Strict publication reservation is unavailable: "
                                + strictReservation.detail()
                );
            }
            throw new ContainerStorageException(
                    "Strict publication reservation is not available through the legacy transaction API"
            );
        }
        synchronized (lockFor(mapDirectory)) {
            requireDirectorySyncForPublish();
            try (RepositoryFileSystem.LockHandle ignored = acquireMapLock(mapDirectory)) {
            requireDirectorySyncForPublish();
            requireRecoveredForPublish(mapDirectory, key);
            Optional<CurrentPointer> current = readCurrent(mapDirectory);
            long currentRevision = current.map(CurrentPointer::revision).orElse(0L);
            if (baseRevision != currentRevision) {
                throw new ContainerStorageException(
                        "Publish base revision is stale: expected " + currentRevision
                );
            }
            if (currentRevision > 0) {
                RecoveryCandidate currentCandidate = findCandidate(
                        mapDirectory,
                        key,
                        currentRevision,
                        current.orElseThrow(),
                        false
                ).orElseThrow(() -> new ContainerStorageException(
                        "Publishing requires repository recovery"
                ));
                PublishTarget requestedTarget = new PublishTarget(
                        key, dimension, documentId
                );
                if (!currentCandidate.record().target().equals(requestedTarget)) {
                    throw new ContainerStorageException(
                            "Ordinary publish reservation cannot change the current target"
                    );
                }
            }
            long highWater = Math.max(readHighWaterMark(mapDirectory), currentRevision);
            if (highWater == Long.MAX_VALUE) {
                throw new ContainerStorageException("Publish revision space is exhausted");
            }
            long publishRevision = highWater + 1;
            String token = UUID.randomUUID().toString();
            Instant expiresAt;
            long expiresAtEpochMillis;
            try {
                expiresAt = clock.instant().plus(ttl);
                expiresAtEpochMillis = expiresAt.toEpochMilli();
            } catch (RuntimeException exception) {
                throw new IllegalArgumentException("Reservation TTL exceeds the clock range", exception);
            }
            PublishDescriptor descriptor = new PublishDescriptor(
                    token, baseRevision, publishRevision, expiresAtEpochMillis,
                    ZERO_HASH, ZERO_HASH, ZERO_HASH
            );
            Path transactions = mapDirectory.resolve("transactions");
            Path transactionDirectory = transactions.resolve(token);
            try {
                fileSystem.createDirectories(mapDirectory);
                verifySafeRepositoryPath(transactions);
                fileSystem.createDirectories(transactions);
                verifySafeRepositoryPath(transactions);
                requireDirectoryCapacity(transactions, "publish transactions");
                writeHighWaterMark(mapDirectory, publishRevision);
                verifySafeRepositoryPath(transactionDirectory);
                fileSystem.createDirectories(transactionDirectory);
                verifySafeRepositoryPath(transactionDirectory);
                writeRecordDurable(
                        transactionDirectory.resolve(RECORD_FILE),
                        PublishRecord.reserved(
                                new PublishTarget(key, dimension, documentId), descriptor
                        ).canonicalBytes()
                );
                syncDirectory(transactionDirectory);
                syncDirectory(transactions);
            } catch (IOException exception) {
                throw storageFailure("Unable to persist publish reservation", exception);
            }
            return new PublishTransaction(
                    new PublishTarget(key, dimension, documentId),
                    descriptor, transactionDirectory,
                    expiresAt
            );
            } catch (IOException exception) {
                throw storageFailure("Unable to lock publish reservation", exception);
            }
        }
    }

    public void abort(PublishTransaction transaction, String reason) {
        Objects.requireNonNull(transaction, "transaction");
        Objects.requireNonNull(reason, "reason");
        if (reason.getBytes(StandardCharsets.UTF_8).length
                > MinimapHardLimits.MAX_WIRE_STRING_UTF8_BYTES) {
            throw new IllegalArgumentException("Publish abort reason is too long");
        }
        Path mapDirectory = mapDirectory(transaction.mapKey());
        synchronized (lockFor(mapDirectory)) {
            try (RepositoryFileSystem.LockHandle ignored = acquireMapLock(mapDirectory)) {
            verifyTransactionDirectory(transaction, mapDirectory);
            Path recordPath = transaction.transactionDirectory().resolve(RECORD_FILE);
            try {
                PublishRecord record = readPublishRecord(recordPath);
                if (!record.descriptor().publishToken().equals(transaction.publishToken())
                        || !record.target().equals(transaction.target())) {
                    throw new ContainerStorageException("Publish token does not match its record");
                }
                if (record.state() == PublishState.ABORTED) {
                    return;
                }
                writeRecordDurable(
                        recordPath,
                        record.transition(PublishState.ABORTED, reason).canonicalBytes()
                );
                syncDirectory(transaction.transactionDirectory());
            } catch (IOException exception) {
                throw storageFailure("Unable to abort publish reservation", exception);
            }
            } catch (IOException exception) {
                throw storageFailure("Unable to lock publish abort", exception);
            }
        }
    }

    public PublishTransaction prepare(
            PublishTransaction transaction,
            byte[] sourceBytes,
            byte[] runtimeBytes
    ) {
        Objects.requireNonNull(transaction, "transaction");
        Objects.requireNonNull(sourceBytes, "sourceBytes");
        Objects.requireNonNull(runtimeBytes, "runtimeBytes");
        if (transaction.state() != PublishState.RESERVED) {
            throw new ContainerStorageException("Only RESERVED transactions can be prepared");
        }
        Path mapDirectory = mapDirectory(transaction.mapKey());
        synchronized (lockFor(mapDirectory)) {
            try (RepositoryFileSystem.LockHandle ignored = acquireMapLock(mapDirectory)) {
            verifyTransactionDirectory(transaction, mapDirectory);
            Path recordPath = transaction.transactionDirectory().resolve(RECORD_FILE);
            try {
                PublishRecord current = readPublishRecord(recordPath);
                if (current.state() != PublishState.RESERVED
                        || !current.descriptor().publishToken().equals(transaction.publishToken())
                        || !current.descriptorChecksum().equals(
                        transaction.descriptor().descriptorChecksum()
                ) || !current.target().equals(transaction.target())) {
                    throw new ContainerStorageException("Publish reservation is no longer available");
                }
                abortIfExpired(current, recordPath, transaction.transactionDirectory());
                PublishTransaction persistedReservation = new PublishTransaction(
                        current.target(),
                        current.descriptor(),
                        transaction.transactionDirectory(),
                        Instant.ofEpochMilli(current.descriptor().expiresAtEpochMillis()),
                        PublishState.RESERVED
                );
                MinimapPairValidator.Result validated = MinimapPairValidator.validate(
                        persistedReservation, sourceBytes, runtimeBytes
                );
                PublishDescriptor descriptor = new PublishDescriptor(
                        current.descriptor().publishToken(),
                        current.descriptor().baseRevision(),
                        current.descriptor().publishRevision(),
                        current.descriptor().expiresAtEpochMillis(),
                        validated.sourceHash(),
                        validated.runtimeHash(),
                        validated.runtimeContainerHash()
                );
                verifySafeRepositoryPath(
                        transaction.transactionDirectory().resolve(SOURCE_FILE)
                );
                verifySafeRepositoryPath(
                        transaction.transactionDirectory().resolve(RUNTIME_FILE)
                );
                fileSystem.write(
                        transaction.transactionDirectory().resolve(SOURCE_FILE), sourceBytes.clone()
                );
                fileSystem.fsyncFile(transaction.transactionDirectory().resolve(SOURCE_FILE));
                fileSystem.write(
                        transaction.transactionDirectory().resolve(RUNTIME_FILE), runtimeBytes.clone()
                );
                fileSystem.fsyncFile(transaction.transactionDirectory().resolve(RUNTIME_FILE));
                PublishRecord prepared = new PublishRecord(
                        current.target(), descriptor, PublishState.PREPARED, "prepared"
                );
                writeRecordDurable(recordPath, prepared.canonicalBytes());
                syncDirectory(transaction.transactionDirectory());
                return transaction.withDescriptorAndState(descriptor, PublishState.PREPARED);
            } catch (IOException exception) {
                throw storageFailure("Unable to prepare publish candidate", exception);
            }
            } catch (IOException exception) {
                throw storageFailure("Unable to lock publish candidate", exception);
            }
        }
    }

    public PublishOutcome commit(PublishTransaction transaction) {
        Objects.requireNonNull(transaction, "transaction");
        if (transaction.state() != PublishState.PREPARED) {
            throw new ContainerStorageException("Only PREPARED transactions can be committed");
        }
        Path mapDirectory = mapDirectory(transaction.mapKey());
        synchronized (lockFor(mapDirectory)) {
            try (RepositoryFileSystem.LockHandle ignored = acquireMapLock(mapDirectory)) {
            if (recoveryMarkerPresent(mapDirectory)) {
                throw new ContainerStorageException(
                        "Publish token was consumed by a commit attempt; recovery is required"
                );
            }
            verifyTransactionDirectory(transaction, mapDirectory);
            Path recordPath = transaction.transactionDirectory().resolve(RECORD_FILE);
            try {
                PublishRecord record = readPublishRecord(recordPath);
                if (record.state() != PublishState.PREPARED
                        || !record.descriptor().publishToken().equals(transaction.publishToken())
                        || !record.descriptorChecksum().equals(
                        transaction.descriptor().descriptorChecksum()
                ) || !record.target().equals(transaction.target())) {
                    throw new ContainerStorageException("Publish token is not PREPARED");
                }
                if (fileSystem.directorySyncSupport()
                        == RepositoryFileSystem.DirectorySyncSupport.UNSUPPORTED) {
                    return suspendForDirectorySyncLoss(
                            mapDirectory, recordPath, record
                    );
                }
                abortIfExpired(record, recordPath, transaction.transactionDirectory());
                verifyPersistedPreparedPair(transaction, record, recordPath);
                Optional<CurrentPointer> currentPointer = readCurrent(mapDirectory);
                long currentRevision = currentPointer.map(CurrentPointer::revision).orElse(0L);
                currentPointer.filter(pointer -> pointer.revision() == 0)
                        .ifPresent(CurrentPointer::requireResetTombstone);
                boolean currentContentValid = currentPointer.isEmpty()
                        || currentRevision == 0
                        || findCandidate(
                        mapDirectory,
                        transaction.mapKey(),
                        currentRevision,
                        currentPointer.orElseThrow(),
                        true
                ).isPresent();
                if (currentRevision != record.descriptor().baseRevision()
                        || !currentContentValid) {
                    try {
                        writeRecordDurable(
                                recordPath,
                                record.transition(
                                        PublishState.ABORTED,
                                        "REVISION_CONFLICT"
                                ).canonicalBytes()
                        );
                        syncDirectory(transaction.transactionDirectory());
                    } catch (IOException persistenceFailure) {
                        throw storageFailure(
                                "Revision conflict could not be persisted", persistenceFailure
                        );
                    }
                    throw new ContainerStorageException(
                            "Publish base revision changed before commit"
                    );
                }
                Path revisions = mapDirectory.resolve("revisions");
                fileSystem.createDirectories(revisions);
                requireDirectoryCapacity(revisions, "revisions");
                markRecoveryRequired(mapDirectory, record.descriptor());
                Path revisionDirectory = revisions.resolve(
                        Long.toString(record.descriptor().publishRevision())
                );
                fileSystem.moveAtomically(transaction.transactionDirectory(), revisionDirectory);
                syncDirectory(revisions);

                Path temporaryCurrent = mapDirectory.resolve(
                        "CURRENT." + record.descriptor().publishToken() + ".tmp"
                );
                CurrentPointer pointer = new CurrentPointer(
                        record.descriptor().baseRevision(),
                        record.descriptor().publishRevision(),
                        record.descriptor().descriptorChecksum()
                );
                writeDurable(temporaryCurrent, pointer.canonicalBytes());
                fileSystem.replaceAtomically(temporaryCurrent, mapDirectory.resolve("CURRENT"));
                try {
                    syncDirectory(mapDirectory);
                } catch (IOException | RuntimeException exception) {
                    suspendedMaps.add(mapDirectory.toString());
                    return new PublishOutcome(
                            PublishState.PREPARED,
                            PublishOutcome.Status.COMMIT_STATUS_UNKNOWN,
                            record.descriptor().publishRevision(),
                            "CURRENT replaced but parent directory sync failed"
                    );
                }
                authoritativePointers.put(mapDirectory.toString(), pointer);

                Path committedRecord = revisionDirectory.resolve(RECORD_FILE);
                PublishRecord committed = record.transition(PublishState.COMMITTED, "committed");
                try {
                    writeRecordDurable(committedRecord, committed.canonicalBytes());
                    syncDirectory(revisionDirectory);
                    clearRecoveryRequired(mapDirectory);
                } catch (IOException exception) {
                    suspendedMaps.add(mapDirectory.toString());
                    return new PublishOutcome(
                            PublishState.COMMITTED,
                            PublishOutcome.Status.COMMITTED,
                            record.descriptor().publishRevision(),
                            "committed; publish record requires recovery"
                    );
                }
                return new PublishOutcome(
                        PublishState.COMMITTED,
                        PublishOutcome.Status.COMMITTED,
                        record.descriptor().publishRevision(),
                        "committed"
                );
            } catch (IOException exception) {
                throw storageFailure("Unable to commit publish transaction", exception);
            }
            } catch (IOException exception) {
                throw storageFailure("Unable to lock publish transaction", exception);
            }
        }
    }

    public long highWaterMark(MapKey key) {
        Objects.requireNonNull(key, "key");
        Path mapDirectory = mapDirectory(key);
        if (strictDispatch.mode(mapDirectory, key)
                == StrictRepositoryDispatch.Mode.STRICT) {
            return strictDispatch.snapshot(mapDirectory)
                    .map(snapshot -> Math.max(
                            snapshot.highWater(), snapshot.headGeneration()
                    ))
                    .orElse(0L);
        }
        synchronized (lockFor(mapDirectory)) {
            return readHighWaterMark(mapDirectory);
        }
    }

    public Optional<CurrentPointer> current(MapKey key) {
        Objects.requireNonNull(key, "key");
        Path mapDirectory = mapDirectory(key);
        if (strictDispatch.mode(mapDirectory, key)
                == StrictRepositoryDispatch.Mode.STRICT) {
            return strictDispatch.snapshot(mapDirectory)
                    .flatMap(snapshot -> {
                        if (!snapshot.active() || snapshot.currentPointer().length == 0) {
                            return Optional.empty();
                        }
                        return Optional.of(CurrentPointer.read(snapshot.currentPointer()));
                    });
        }
        synchronized (lockFor(mapDirectory)) {
            if (recoveryMarkerPresent(mapDirectory)) {
                return Optional.ofNullable(
                        authoritativePointers.get(mapDirectory.toString())
                );
            }
            return readCurrent(mapDirectory);
        }
    }

    public void activateStrictJournal(MapKey key) {
        Objects.requireNonNull(key, "key");
        Path mapDirectory = mapDirectory(key);
        if (strictDispatch.hasStrictCapability()) {
            strictDispatch.activate(mapDirectory, key);
            return;
        }
        synchronized (lockFor(mapDirectory)) {
            try (RepositoryFileSystem.LockHandle ignored = acquireMapLock(mapDirectory)) {
                MinimapRepositoryJournalAccess.Detection detection =
                        journalAccess.detect(mapDirectory, key);
                if (detection == MinimapRepositoryJournalAccess.Detection.JOURNAL) {
                    return;
                }
                if (detection == MinimapRepositoryJournalAccess.Detection.UNAVAILABLE) {
                    throw new IOException("Strict authority journal is unavailable");
                }
                if (readCurrent(mapDirectory).isPresent()
                        || recoveryMarkerPresent(mapDirectory)) {
                    throw new ContainerStorageException(
                            "Strict journal activation requires empty legacy authority");
                }
                journalAccess.activate(mapDirectory, key);
            } catch (IOException exception) {
                throw storageFailure("Unable to activate strict authority journal", exception);
            }
        }
    }

    public boolean isStrictJournalActive(MapKey key) {
        Objects.requireNonNull(key, "key");
        Path mapDirectory = mapDirectory(key);
        if (strictDispatch.mode(mapDirectory, key)
                == StrictRepositoryDispatch.Mode.STRICT) {
            return strictDispatch.active(mapDirectory);
        }
        synchronized (lockFor(mapDirectory)) {
            try {
                MinimapRepositoryJournalAccess.Detection detection =
                        journalAccess.detect(mapDirectory, key);
                if (detection == MinimapRepositoryJournalAccess.Detection.UNAVAILABLE) {
                    throw new IOException("Strict authority journal is unavailable");
                }
                return detection == MinimapRepositoryJournalAccess.Detection.JOURNAL;
            } catch (IOException exception) {
                throw storageFailure("Unable to inspect strict authority journal", exception);
            }
        }
    }

    public Optional<CurrentPublication> currentPublication(MapKey key) {
        Objects.requireNonNull(key, "key");
        Path mapDirectory = mapDirectory(key);
        if (strictDispatch.mode(mapDirectory, key)
                == StrictRepositoryDispatch.Mode.STRICT) {
            return strictDispatch.withSession(mapDirectory, () -> {
                Optional<MinimapAuthorityJournal.Snapshot> snapshot =
                        journalAccess.snapshot(mapDirectory);
                if (snapshot.isEmpty() || !snapshot.orElseThrow().active()) {
                    return Optional.empty();
                }
                byte[] pointerBytes = snapshot.orElseThrow().currentPointer();
                if (pointerBytes.length == 0) {
                    return Optional.empty();
                }
                CurrentPointer pointer = CurrentPointer.read(pointerBytes);
                if (pointer.revision() == 0) {
                    CurrentPointer.requireResetTombstone(pointer);
                    return Optional.empty();
                }
                return Optional.of(requireCurrentPublication(mapDirectory, key, pointer));
            });
        }
        synchronized (lockFor(mapDirectory)) {
            try (RepositoryFileSystem.LockHandle ignored = acquireMapLock(mapDirectory)) {
                if (suspendedMaps.contains(mapDirectory.toString())
                        || recoveryMarkerPresent(mapDirectory)) {
                    throw new ContainerStorageException("Current publication requires recovery");
                }
                Optional<CurrentPointer> current = readCurrent(mapDirectory);
                if (current.isEmpty()) return Optional.empty();
                CurrentPointer pointer = current.orElseThrow();
                if (pointer.revision() == 0) {
                    CurrentPointer.requireResetTombstone(pointer);
                    return Optional.empty();
                }
                return Optional.of(requireCurrentPublication(mapDirectory, key, pointer));
            } catch (IOException exception) {
                throw storageFailure("Unable to lock current publication read", exception);
            }
        }
    }

    public CurrentResetResult compareAndResetCurrent(MapKey key, CurrentPointer expected) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(expected, "expected");
        Path mapDirectory = mapDirectory(key);
        synchronized (lockFor(mapDirectory)) {
            try (RepositoryFileSystem.LockHandle ignored = acquireMapLock(mapDirectory)) {
                if (suspendedMaps.contains(mapDirectory.toString())
                        || recoveryMarkerPresent(mapDirectory)) {
                    throw new ContainerStorageException("CURRENT reset requires recovery");
                }
                requireDirectorySyncForPublish();
                CurrentPointer current = readCurrent(mapDirectory).orElseThrow(
                        () -> new ContainerStorageException("CURRENT pointer is missing"));
                if (current.revision() == 0) {
                    CurrentPointer.requireResetTombstone(current);
                    return CurrentResetResult.ALREADY_RESET;
                }
                if (!current.equals(expected)) return CurrentResetResult.MISMATCH;
                requireCurrentPublication(mapDirectory, key, current);
                authoritativePointers.put(mapDirectory.toString(), current);
                try {
                    persistence.resetCurrentDurably(mapDirectory, current, CurrentPointer.RESET_TOMBSTONE);
                } catch (IOException exception) {
                    suspendedMaps.add(mapDirectory.toString());
                    throw storageFailure("CURRENT reset status is unknown", exception);
                } catch (RuntimeException exception) {
                    suspendedMaps.add(mapDirectory.toString());
                    throw exception;
                }
                authoritativePointers.put(mapDirectory.toString(), CurrentPointer.RESET_TOMBSTONE);
                suspendedMaps.remove(mapDirectory.toString());
                return CurrentResetResult.RESET;
            } catch (IOException exception) {
                throw storageFailure("Unable to lock CURRENT reset", exception);
            }
        }
    }

    public boolean directorySyncDegraded() {
        return persistence.directorySyncDegraded() || !suspendedMaps.isEmpty();
    }

    public boolean isDurabilityDegraded() {
        return persistence.directorySyncDegraded()
                || !suspendedMaps.isEmpty()
                || hasPersistedRecoveryMarker();
    }

    private boolean hasPersistedRecoveryMarker() {
        return persistence.hasPersistedRecoveryMarker();
    }

    private boolean recoveryMarkerPresent(Path mapDirectory) {
        return persistence.recoveryMarkerPresent(mapDirectory);
    }

    public void pinRevision(MapKey key, long revision, String pinId) {
        Objects.requireNonNull(key, "key");
        validatePinId(pinId);
        if (revision < 0) {
            throw new IllegalArgumentException("Pinned revision must be non-negative");
        }
        Path mapDirectory = mapDirectory(key);
        synchronized (lockFor(mapDirectory)) {
            try (RepositoryFileSystem.LockHandle ignored = acquireMapLock(mapDirectory)) {
                requirePinnableRevision(mapDirectory, revision);
                persistRevisionPin(mapDirectory, revision, pinId);
            } catch (IOException exception) {
                throw storageFailure("Unable to lock revision pin", exception);
            }
        }
    }

    public void pinCommittedRevision(
            MapKey key,
            long revision,
            Sha256 expectedSourceHash,
            String pinId
    ) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(expectedSourceHash, "expectedSourceHash");
        validatePinId(pinId);
        if (revision < 0) {
            throw new IllegalArgumentException("Pinned revision must be non-negative");
        }
        Path mapDirectory = mapDirectory(key);
        synchronized (lockFor(mapDirectory)) {
            try (RepositoryFileSystem.LockHandle ignored = acquireMapLock(mapDirectory)) {
                Path revisionDirectory = requirePinnableRevision(mapDirectory, revision);
                PublishRecord record = readRevisionRecordForPinning(revisionDirectory);
                if (!record.target().mapKey().equals(key)
                        || record.state() != PublishState.COMMITTED
                        || record.descriptor().publishRevision() != revision) {
                    throw new ContainerStorageException(
                            "Cannot pin a revision that is not committed"
                    );
                }
                if (!record.descriptor().sourceHash().equals(expectedSourceHash)) {
                    throw new ContainerStorageException(
                            "Pinned revision source hash does not match"
                    );
                }
                try (CommittedMapPairSnapshot snapshot = openCommittedSnapshot(
                        revisionDirectory, record.target(), record.descriptor()
                )) {
                    requireSnapshotTarget(snapshot, record.target());
                } catch (ContainerValidationException invalidPair) {
                    throw new ContainerStorageException(
                            "Cannot pin a damaged committed revision", invalidPair
                    );
                }
                persistRevisionPin(mapDirectory, revision, pinId);
            } catch (IOException exception) {
                throw storageFailure("Unable to lock committed revision pin", exception);
            }
        }
    }

    private PublishRecord readRevisionRecordForPinning(Path revisionDirectory) {
        try {
            return readPublishRecord(revisionDirectory.resolve(RECORD_FILE));
        } catch (NoSuchFileException missingRecord) {
            throw new ContainerStorageException(
                    "Cannot pin a revision without a publish record"
            );
        } catch (IOException readFailure) {
            throw storageFailure("Unable to read publish record for pinning", readFailure);
        }
    }

    private Path requirePinnableRevision(Path mapDirectory, long revision) {
        Path revisionDirectory = mapDirectory.resolve("revisions")
                .resolve(Long.toString(revision));
        verifySafeRepositoryPath(revisionDirectory);
        BasicFileAttributes revisionAttributes;
        try {
            revisionAttributes = fileSystem.readAttributesNoFollow(revisionDirectory);
        } catch (NoSuchFileException missingRevision) {
            throw new ContainerStorageException("Cannot pin a missing revision");
        } catch (IOException inspectionFailure) {
            throw storageFailure("Unable to inspect revision for pinning", inspectionFailure);
        }
        if (!revisionAttributes.isDirectory()
                || revisionAttributes.isSymbolicLink()
                || revisionAttributes.isOther()) {
            throw new ContainerStorageException("Cannot pin a non-directory revision");
        }
        return revisionDirectory;
    }

    private void persistRevisionPin(Path mapDirectory, long revision, String pinId) {
        Path pins = mapDirectory.resolve("pins");
        try {
            verifySafeRepositoryPath(pins);
            fileSystem.createDirectories(pins);
            verifySafeRepositoryPath(pins);
            Path pin = pinPath(pins, pinId);
            java.util.OptionalLong existingPin = readPinIfPresent(pin, pinId);
            if (existingPin.isPresent()) {
                long actual = existingPin.orElseThrow();
                if (actual == revision) {
                    return;
                }
                throw new ContainerStorageException(
                        "Pin ID is already bound to another revision"
                );
            }
            requireDirectoryCapacity(pins, "revision pins");
            JsonObject root = new JsonObject();
            root.addProperty("pinId", pinId);
            root.addProperty("revision", Long.toString(revision));
            Path temporary = pins.resolve(
                    pin.getFileName() + "." + UUID.randomUUID() + ".tmp"
            );
            try {
                writeDurable(temporary, JcsCanonicalizer.canonicalize(root));
                fileSystem.moveAtomically(temporary, pin);
                syncDirectory(pins);
                syncDirectory(mapDirectory);
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException exception) {
            throw storageFailure("Unable to persist revision pin", exception);
        }
    }

    public void unpinRevision(MapKey key, long revision, String pinId) {
        Objects.requireNonNull(key, "key");
        validatePinId(pinId);
        if (revision < 0) {
            throw new IllegalArgumentException("Pinned revision must be non-negative");
        }
        Path mapDirectory = mapDirectory(key);
        synchronized (lockFor(mapDirectory)) {
            try (RepositoryFileSystem.LockHandle ignored = acquireMapLock(mapDirectory)) {
            Path pins = mapDirectory.resolve("pins");
            verifySafeRepositoryPath(pins);
            Path pin = pinPath(pins, pinId);
            verifySafeRepositoryPath(pin);
            java.util.OptionalLong existingPin = readPinIfPresent(pin, pinId);
            if (existingPin.isEmpty()) {
                return;
            }
            long actual = existingPin.orElseThrow();
            if (actual != revision) {
                throw new ContainerStorageException("Pin revision does not match the request");
            }
            try {
                Files.deleteIfExists(pin);
                syncDirectory(pins);
            } catch (IOException exception) {
                throw storageFailure("Unable to remove revision pin", exception);
            }
            } catch (IOException exception) {
                throw storageFailure("Unable to lock revision unpin", exception);
            }
        }
    }

    public void collectGarbage(MapKey key) {
        Objects.requireNonNull(key, "key");
        Path mapDirectory = mapDirectory(key);
        synchronized (lockFor(mapDirectory)) {
            try (RepositoryFileSystem.LockHandle ignored = acquireMapLock(mapDirectory)) {
            if (suspendedMaps.contains(mapDirectory.toString())
                    || recoveryMarkerPresent(mapDirectory)) {
                throw new ContainerStorageException(
                        "Cannot collect revisions while publishing is suspended"
                );
            }
            Path revisions = mapDirectory.resolve("revisions");
            verifySafeRepositoryPath(revisions);
            try {
                java.util.List<Path> revisionEntries = listDirectoryIfPresent(revisions);
                if (revisionEntries.isEmpty()) {
                    return;
                }
                Optional<CurrentPointer> current = readCurrent(mapDirectory);
                if (current.isEmpty()) {
                    throw new ContainerStorageException(
                            "Garbage collection requires repository recovery"
                    );
                }
                Set<Long> keep = new java.util.HashSet<>(readPinnedRevisions(mapDirectory));
                long currentRevision = current.orElseThrow().revision();
                if (currentRevision == 0) {
                    CurrentPointer.requireResetTombstone(current.orElseThrow());
                } else {
                    keep.add(currentRevision);
                }
                java.util.List<Long> existing = new java.util.ArrayList<>();
                for (Path entry : revisionEntries) {
                    BasicFileAttributes attributes;
                    try {
                        attributes = fileSystem.readAttributesNoFollow(entry);
                    } catch (NoSuchFileException missingEntry) {
                        continue;
                    }
                    if (!attributes.isDirectory()
                            || attributes.isSymbolicLink()
                            || attributes.isOther()) {
                        throw new ContainerStorageException(
                                "Revision entry is not a directory"
                        );
                    }
                    long revision = revisionNumber(entry);
                    if (revision < 0) {
                        throw new ContainerStorageException(
                                "Revision directory name is not canonical"
                        );
                    }
                    existing.add(revision);
                }
                existing.sort(Long::compareTo);
                for (int index = existing.size() - 1; index >= 0; index--) {
                    long revision = existing.get(index);
                    if (revision < currentRevision) {
                        Optional<RecoveryCandidate> previous = findCandidate(
                                mapDirectory, key, revision, null, false
                        );
                        if (previous.isPresent()
                                && previous.orElseThrow().record().state()
                                == PublishState.COMMITTED) {
                            keep.add(revision);
                            break;
                        }
                    }
                }
                java.util.List<Path> deletionPaths = new java.util.ArrayList<>();
                for (long revision : existing) {
                    if (keep.contains(revision)) {
                        continue;
                    }
                    java.util.List<Path> revisionPaths = validateCleanupTree(
                            revisions.resolve(Long.toString(revision))
                    );
                    if (revisionPaths.size()
                            > MAX_CLEANUP_TREE_NODES - deletionPaths.size()) {
                        throw new IOException(
                                "Repository cleanup batch exceeds its node limit"
                        );
                    }
                    deletionPaths.addAll(revisionPaths);
                }
                for (Path path : deletionPaths.stream()
                        .sorted(java.util.Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
                syncDirectory(revisions);
            } catch (IOException exception) {
                throw storageFailure("Unable to collect minimap revisions", exception);
            }
            } catch (IOException exception) {
                throw storageFailure("Unable to lock minimap garbage collection", exception);
            }
        }
    }

    public PublishOutcome recover(MapKey key) {
        Objects.requireNonNull(key, "key");
        Path mapDirectory = mapDirectory(key);
        synchronized (lockFor(mapDirectory)) {
            try (RepositoryFileSystem.LockHandle ignored = acquireMapLock(mapDirectory)) {
            try {
                rebuildHighWaterMark(mapDirectory);
                cleanupCurrentTemporaries(mapDirectory);
                Optional<CurrentPointer> pointer = readCurrentSafely(mapDirectory);
                if (pointer.filter(value -> value.revision() == 0).isPresent()) {
                    CurrentPointer tombstone = pointer.orElseThrow();
                    CurrentPointer.requireResetTombstone(tombstone);
                    cleanupTransactions(mapDirectory);
                    cleanupOrphanRevisions(mapDirectory, Optional.empty());
                    // Clear the marker only after the tombstone's parent is durable.
                    syncDirectory(mapDirectory);
                    clearRecoveryRequired(mapDirectory);
                    authoritativePointers.put(mapDirectory.toString(), tombstone);
                    suspendedMaps.remove(mapDirectory.toString());
                    return new PublishOutcome(PublishState.ABORTED,
                            PublishOutcome.Status.ABORTED, 0, "current reset preserved");
                }
                Optional<RecoveryCandidate> pointedCandidate = pointer.flatMap(
                        value -> findCandidate(mapDirectory, key, value.revision(), value, true)
                );
                boolean damagedCurrent = pointer
                        .filter(value -> value.revision() > 0)
                        .isPresent() && pointedCandidate.isEmpty();
                Optional<RecoveryCandidate> candidate = pointedCandidate;
                if (candidate.isEmpty()) {
                    if (pointer.isPresent() && pointer.orElseThrow().revision() > 0) {
                        candidate = findHighestCandidateBelow(
                                mapDirectory, key, pointer.orElseThrow().revision()
                        );
                    } else {
                        candidate = findHighestCandidate(mapDirectory, key);
                    }
                }
                cleanupTransactions(mapDirectory);
                cleanupOrphanRevisions(mapDirectory, candidate);
                if (damagedCurrent && candidate.isPresent()) {
                    PublishOutcome rebound = rebindCommittedCandidate(
                            key,
                            mapDirectory,
                            pointer.orElseThrow(),
                            candidate.orElseThrow()
                    );
                    if (rebound.committed()) {
                        suspendedMaps.remove(mapDirectory.toString());
                    }
                    return rebound;
                }
                if (damagedCurrent) {
                    authoritativePointers.remove(mapDirectory.toString());
                    suspendedMaps.add(mapDirectory.toString());
                    return new PublishOutcome(
                            PublishState.ABORTED,
                            PublishOutcome.Status.UNAVAILABLE,
                            pointer.orElseThrow().revision(),
                            "current revision is damaged and no complete fallback exists"
                    );
                }
                if (candidate.isEmpty()) {
                    authoritativePointers.remove(mapDirectory.toString());
                    if (pointer.isEmpty()) {
                        clearInvalidCurrent(mapDirectory);
                    }
                    clearRecoveryRequired(mapDirectory);
                    suspendedMaps.remove(mapDirectory.toString());
                    return new PublishOutcome(
                            PublishState.ABORTED,
                            PublishOutcome.Status.ABORTED,
                            0,
                            "no complete committed revision"
                    );
                }
                RecoveryCandidate recovered = candidate.orElseThrow();
                CurrentPointer rebuilt = new CurrentPointer(
                        recovered.record().descriptor().baseRevision(),
                        recovered.record().descriptor().publishRevision(),
                        recovered.record().descriptorChecksum()
                );
                if (pointer.isEmpty() || !pointer.orElseThrow().equals(rebuilt)) {
                    Path temporary = mapDirectory.resolve("CURRENT.recovery.tmp");
                    writeDurable(temporary, rebuilt.canonicalBytes());
                    fileSystem.replaceAtomically(temporary, mapDirectory.resolve("CURRENT"));
                    syncDirectory(mapDirectory);
                }
                if (recovered.record().state() != PublishState.COMMITTED) {
                    writeRecordDurable(
                            recovered.directory().resolve(RECORD_FILE),
                            recovered.record().transition(PublishState.COMMITTED, "recovered").canonicalBytes()
                    );
                    syncDirectory(recovered.directory());
                }
                authoritativePointers.put(mapDirectory.toString(), rebuilt);
                clearRecoveryRequired(mapDirectory);
                suspendedMaps.remove(mapDirectory.toString());
                return new PublishOutcome(
                        PublishState.COMMITTED,
                        PublishOutcome.Status.COMMITTED,
                        rebuilt.revision(),
                        "recovered"
                );
            } catch (IOException exception) {
                throw storageFailure("Unable to recover minimap repository", exception);
            }
            } catch (IOException exception) {
                throw storageFailure("Unable to lock minimap recovery", exception);
            }
        }
    }

    private Optional<CurrentPointer> readCurrentSafely(Path mapDirectory) {
        try {
            return readCurrent(mapDirectory);
        } catch (ContainerStorageException invalidCurrent) {
            if (hasCause(invalidCurrent, IOException.class)) {
                throw invalidCurrent;
            }
            return Optional.empty();
        }
    }

    private void clearInvalidCurrent(Path mapDirectory) throws IOException {
        if (Files.deleteIfExists(mapDirectory.resolve("CURRENT"))) {
            syncDirectory(mapDirectory);
        }
    }

    private static boolean hasCause(Throwable failure, Class<? extends Throwable> type) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (type.isInstance(current)) {
                return true;
            }
        }
        return false;
    }

    private void requireRecoveredForPublish(Path mapDirectory, MapKey key) {
        if (suspendedMaps.contains(mapDirectory.toString())
                || recoveryMarkerPresent(mapDirectory)) {
            throw new ContainerStorageException(
                    "Publishing is suspended until repository recovery completes"
            );
        }
        requireNoFrozenPreparedTransaction(mapDirectory);
        Optional<CurrentPointer> pointer;
        try {
            pointer = readCurrent(mapDirectory);
        } catch (ContainerStorageException invalidCurrent) {
            throw new ContainerStorageException(
                    "Publishing requires repository recovery", invalidCurrent
            );
        }
        if (pointer.isEmpty()) {
            Path revisions = mapDirectory.resolve("revisions");
            verifySafeRepositoryPath(revisions);
            try {
                if (!listDirectoryIfPresent(revisions).isEmpty()) {
                    throw new ContainerStorageException(
                            "Publishing requires repository recovery"
                    );
                }
            } catch (IOException exception) {
                throw storageFailure(
                        "Unable to inspect revisions before publishing", exception
                );
            }
            return;
        }
        if (pointer.orElseThrow().revision() == 0) {
            CurrentPointer.requireResetTombstone(pointer.orElseThrow());
            return;
        }
        Optional<RecoveryCandidate> candidate = findCandidate(
                mapDirectory,
                key,
                pointer.orElseThrow().revision(),
                pointer.orElseThrow(),
                true
        );
        if (candidate.isEmpty()
                || candidate.orElseThrow().record().state() != PublishState.COMMITTED) {
            throw new ContainerStorageException(
                    "Publishing requires repository recovery"
            );
        }
    }

    private void requireNoFrozenPreparedTransaction(Path mapDirectory) {
        Path transactions = mapDirectory.resolve("transactions");
        verifySafeRepositoryPath(transactions);
        try {
            for (Path transactionDirectory : listDirectoryIfPresent(transactions)) {
                Path recordPath = transactionDirectory.resolve(RECORD_FILE);
                PublishRecord record;
                try {
                    record = readPublishRecord(recordPath);
                } catch (NoSuchFileException incompleteReservation) {
                    continue;
                } catch (RuntimeException invalidRecord) {
                    throw new ContainerStorageException(
                            "Publishing requires repository recovery",
                            invalidRecord
                    );
                }
                if (record.state() == PublishState.PREPARED
                        && DIRECTORY_SYNC_LOST_REASON.equals(record.reason())) {
                    throw new ContainerStorageException(
                            "Publishing is suspended until repository recovery completes"
                    );
                }
            }
        } catch (IOException exception) {
            throw storageFailure(
                    "Unable to inspect prepared publish transactions", exception
            );
        }
    }

    private Set<Long> readPinnedRevisions(Path mapDirectory) throws IOException {
        Path pins = mapDirectory.resolve("pins");
        verifySafeRepositoryPath(pins);
        Set<Long> revisions = new java.util.HashSet<>();
        for (Path path : listDirectoryIfPresent(pins)) {
            String fileName = path.getFileName().toString();
            if (fileName.endsWith(".tmp")) {
                discardPinTemporary(path);
                continue;
            }
            if (!PIN_FILE_NAME.matcher(fileName).matches()) {
                throw new ContainerStorageException(
                        "Revision pin filename is invalid"
                );
            }
            BasicFileAttributes attributes;
            try {
                attributes = fileSystem.readAttributesNoFollow(path);
            } catch (NoSuchFileException missingPin) {
                continue;
            }
            if (!attributes.isRegularFile()
                    || attributes.isSymbolicLink()
                    || attributes.isOther()) {
                throw new ContainerStorageException(
                        "Revision pin entry is not a regular file"
                );
            }
            JsonElement parsed = StrictJsonParser.parse(readMetadata(path));
            if (!parsed.isJsonObject()) {
                throw new ContainerStorageException("Revision pin is not a JSON object");
            }
            JsonObject root = parsed.getAsJsonObject();
            JsonElement pinIdValue = root.get("pinId");
            if (pinIdValue == null || !pinIdValue.isJsonPrimitive()
                    || !pinIdValue.getAsJsonPrimitive().isString()) {
                throw new ContainerStorageException("Revision pin ID is invalid");
            }
            String pinId = pinIdValue.getAsString();
            try {
                validatePinId(pinId);
            } catch (IllegalArgumentException invalidPinId) {
                throw new ContainerStorageException(
                        "Revision pin ID is invalid", invalidPinId
                );
            }
            if (!pinPath(pins, pinId).getFileName().equals(path.getFileName())) {
                throw new ContainerStorageException(
                        "Revision pin filename does not match its ID"
                );
            }
            revisions.add(readPin(path, pinId));
        }
        return Set.copyOf(revisions);
    }

    private static void discardPinTemporary(Path temporary) {
        try {
            Files.deleteIfExists(temporary);
        } catch (IOException | SecurityException ignored) {
            // A stale temporary is never authoritative and cannot pin a revision.
        }
    }

    private long readPin(Path path, String expectedPinId) {
        return readPinIfPresent(path, expectedPinId)
                .orElseThrow(() -> new ContainerStorageException("Revision pin is missing"));
    }

    private java.util.OptionalLong readPinIfPresent(Path path, String expectedPinId) {
        try {
            byte[] bytes = readMetadata(path);
            JsonElement parsed = StrictJsonParser.parse(bytes);
            if (!parsed.isJsonObject() || !Arrays.equals(bytes, JcsCanonicalizer.canonicalize(parsed))) {
                throw new ContainerStorageException("Revision pin is not canonical JSON");
            }
            JsonObject root = parsed.getAsJsonObject();
            if (!root.keySet().equals(Set.of("pinId", "revision"))
                    || !expectedPinId.equals(root.get("pinId").getAsString())) {
                throw new ContainerStorageException("Revision pin fields are invalid");
            }
            long revision = MinimapCodecs.NON_NEGATIVE_LONG
                    .parse(JsonOps.INSTANCE, root.get("revision"))
                    .result()
                    .orElseThrow(() -> new ContainerStorageException(
                            "Revision pin value is invalid"
                    ));
            return java.util.OptionalLong.of(revision);
        } catch (NoSuchFileException missing) {
            return java.util.OptionalLong.empty();
        } catch (IOException exception) {
            throw storageFailure("Unable to read revision pin", exception);
        } catch (RuntimeException exception) {
            if (exception instanceof ContainerStorageException storageException) {
                throw storageException;
            }
            throw new ContainerStorageException("Revision pin is invalid", exception);
        }
    }

    private static Path pinPath(Path pins, String pinId) {
        String name = Sha256Digest.of(pinId.getBytes(StandardCharsets.UTF_8)).value() + ".json";
        return pins.resolve(name);
    }

    private static void validatePinId(String pinId) {
        if (pinId == null || pinId.isEmpty()
                || pinId.getBytes(StandardCharsets.UTF_8).length > 256) {
            throw new IllegalArgumentException("Revision pin ID is invalid");
        }
        for (int index = 0; index < pinId.length(); index++) {
            if (Character.isISOControl(pinId.charAt(index))) {
                throw new IllegalArgumentException("Revision pin ID contains a control character");
            }
        }
    }

    private static void deleteRecursively(Path root) throws IOException {
        try {
            java.util.List<Path> paths = validateCleanupTree(root);
            for (Path path : paths.stream()
                    .sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (NoSuchFileException missing) {
            // The cleanup target may disappear concurrently.
        }
    }

    private Optional<RecoveryCandidate> findHighestCandidate(Path mapDirectory, MapKey key)
            throws IOException {
        return findHighestCandidateBelow(mapDirectory, key, Long.MAX_VALUE);
    }

    private Optional<RecoveryCandidate> findHighestCandidateBelow(
            Path mapDirectory,
            MapKey key,
            long exclusiveRevision
    ) throws IOException {
        Path revisions = mapDirectory.resolve("revisions");
        verifySafeRepositoryPath(revisions);
        return listDirectoryIfPresent(revisions).stream()
                .filter(directory -> revisionNumber(directory) >= 0
                        && revisionNumber(directory) < exclusiveRevision)
                .sorted((left, right) -> Long.compare(
                        revisionNumber(right), revisionNumber(left)
                ))
                .map(directory -> findCandidate(
                        mapDirectory, key, revisionNumber(directory), null, false
                ).orElse(null))
                .filter(Objects::nonNull)
                .findFirst();
    }

    private Optional<RecoveryCandidate> findCandidate(
            Path mapDirectory,
            MapKey key,
            long revision,
            CurrentPointer pointer,
            boolean allowPrepared
    ) {
        if (revision < 0) {
            return Optional.empty();
        }
        Path directory = mapDirectory.resolve("revisions").resolve(Long.toString(revision));
        Path recordPath = directory.resolve(RECORD_FILE);
        try {
            verifySafeRepositoryPath(directory);
            BasicFileAttributes attributes = fileSystem.readAttributesNoFollow(directory);
            if (!attributes.isDirectory()
                    || attributes.isSymbolicLink()
                    || attributes.isOther()) {
                return Optional.empty();
            }
            PublishRecord record = readPublishRecord(recordPath);
            if (!record.target().mapKey().equals(key)
                    || record.descriptor().publishRevision() != revision) {
                return Optional.empty();
            }
            if (pointer != null && (!record.descriptorChecksum().equals(pointer.descriptorChecksum())
                    || record.descriptor().publishRevision() != pointer.revision()
                    || record.descriptor().baseRevision() != pointer.expectedBaseRevision())) {
                return Optional.empty();
            }
            if (record.state() != PublishState.COMMITTED
                    && (!allowPrepared || record.state() != PublishState.PREPARED)) {
                return Optional.empty();
            }
            try (CommittedMapPairSnapshot snapshot = openCommittedSnapshot(
                    directory, record.target(), record.descriptor()
            )) {
                requireSnapshotTarget(snapshot, record.target());
                return Optional.of(new RecoveryCandidate(directory, record));
            }
        } catch (NoSuchFileException missingCandidateContent) {
            return Optional.empty();
        } catch (IOException readFailure) {
            throw storageFailure("Unable to read minimap revision candidate", readFailure);
        } catch (RuntimeException invalidCandidate) {
            if (hasCause(invalidCandidate, IOException.class)) {
                throw invalidCandidate;
            }
            return Optional.empty();
        }
    }

    private CommittedMapPairSnapshot openCommittedSnapshot(
            Path directory,
            PublishTarget target,
            PublishDescriptor descriptor
    ) throws IOException {
        RepositoryFileSystem.BoundedReadChannel sourceInput = null;
        RepositoryFileSystem.BoundedReadChannel runtimeInput = null;
        try {
            Path source = directory.resolve(SOURCE_FILE);
            Path runtime = directory.resolve(RUNTIME_FILE);
            verifySafeRepositoryPath(source);
            verifySafeRepositoryPath(runtime);
            sourceInput = fileSystem.openBoundedReadChannel(
                    source,
                    ContainerLimits.sourceHardLimits().maxCanonicalContainerBytes()
            );
            runtimeInput = fileSystem.openBoundedReadChannel(
                    runtime,
                    ContainerLimits.runtimeHardLimits().maxCanonicalContainerBytes()
            );
            return CommittedMapPairSnapshot.open(
                    sourceInput.channel(), sourceInput.size(),
                    runtimeInput.channel(), runtimeInput.size(),
                    target.mapKey(),
                    descriptor.publishRevision(),
                    descriptor.sourceHash(),
                    descriptor.runtimeHash(),
                    descriptor.runtimeContainerHash()
            );
        } catch (IOException failure) {
            closeReadInputsAfterFailure(sourceInput, runtimeInput, failure);
            throw failure;
        } catch (RuntimeException | Error failure) {
            closeReadInputsAfterFailure(sourceInput, runtimeInput, failure);
            throw failure;
        }
    }

    private static void closeReadInputsAfterFailure(
            RepositoryFileSystem.BoundedReadChannel sourceInput,
            RepositoryFileSystem.BoundedReadChannel runtimeInput,
            Throwable failure
    ) {
        for (RepositoryFileSystem.BoundedReadChannel input
                : new RepositoryFileSystem.BoundedReadChannel[]{runtimeInput, sourceInput}) {
            if (input == null) {
                continue;
            }
            try {
                input.close();
            } catch (IOException closeFailure) {
                failure.addSuppressed(closeFailure);
            }
        }
    }

    private static void requireSnapshotTarget(
            CommittedMapPairSnapshot snapshot,
            PublishTarget target
    ) {
        if (!snapshot.sourceManifest().binding().equals(target.mapKey())
                || !snapshot.sourceManifest().dimension().equals(target.dimension())
                || !snapshot.sourceManifest().documentId().equals(target.documentId())) {
            throw new ContainerStorageException(
                    "Committed map pair does not match its persisted publish target"
            );
        }
    }

    private void cleanupTransactions(Path mapDirectory) throws IOException {
        Path transactions = mapDirectory.resolve("transactions");
        verifySafeRepositoryPath(transactions);
        if (listDirectoryIfPresent(transactions).isEmpty()) {
            return;
        }
        {
            java.util.List<Path> paths = validateCleanupTree(transactions);
            paths.stream().sorted(java.util.Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException exception) {
                            throw new ContainerStorageException(
                                    "Unable to clean orphan publish transaction", exception
                            );
                        }
                    });
        }
    }

    private static java.util.List<Path> validateCleanupTree(Path root)
            throws IOException {
        try (java.util.stream.Stream<Path> stream = Files.walk(
                root, MAX_CLEANUP_TREE_DEPTH + 1
        )) {
            java.util.List<Path> paths = stream
                    .limit(MAX_CLEANUP_TREE_NODES + 1L)
                    .toList();
            if (paths.size() > MAX_CLEANUP_TREE_NODES) {
                throw new IOException("Repository cleanup tree exceeds its node limit");
            }
            if (paths.stream().anyMatch(path -> root.relativize(path).getNameCount()
                    > MAX_CLEANUP_TREE_DEPTH)) {
                throw new IOException("Repository cleanup tree exceeds its depth limit");
            }
            return paths;
        }
    }

    private PublishOutcome rebindCommittedCandidate(
            MapKey key,
            Path mapDirectory,
            CurrentPointer damagedCurrent,
            RecoveryCandidate fallback
    ) throws IOException {
        long highWater = readHighWaterMark(mapDirectory);
        if (highWater == Long.MAX_VALUE) {
            throw new ContainerStorageException("Publish revision space is exhausted");
        }
        long publishRevision = highWater + 1;
        String token = UUID.randomUUID().toString();
        PublishDescriptor reservedDescriptor = new PublishDescriptor(
                token,
                damagedCurrent.revision(),
                publishRevision,
                ZERO_HASH,
                ZERO_HASH,
                ZERO_HASH
        );
        Path transactions = mapDirectory.resolve("transactions");
        Path transactionDirectory = transactions.resolve(token);
        verifySafeRepositoryPath(transactions);
        fileSystem.createDirectories(transactions);
        verifySafeRepositoryPath(transactions);
        writeHighWaterMark(mapDirectory, publishRevision);
        verifySafeRepositoryPath(transactionDirectory);
        fileSystem.createDirectories(transactionDirectory);
        verifySafeRepositoryPath(transactionDirectory);
        writeRecordDurable(
                transactionDirectory.resolve(RECORD_FILE),
                PublishRecord.reserved(
                        fallback.record().target(), reservedDescriptor
                ).canonicalBytes()
        );
        syncDirectory(transactionDirectory);
        syncDirectory(transactions);

        PublishDescriptor fallbackDescriptor = fallback.record().descriptor();
        MapRebindService.ReboundMapPair rebound;
        try (CommittedMapPairSnapshot snapshot = openCommittedSnapshot(
                fallback.directory(), fallback.record().target(), fallbackDescriptor
        )) {
            requireSnapshotTarget(snapshot, fallback.record().target());
            rebound = MapRebindService.rebindCommitted(snapshot, publishRevision);
        }
        PublishDescriptor preparedDescriptor = new PublishDescriptor(
                token,
                damagedCurrent.revision(),
                publishRevision,
                rebound.sourceHash(),
                rebound.runtimeHash(),
                rebound.runtimeContainerHash()
        );
        Path sourcePath = transactionDirectory.resolve(SOURCE_FILE);
        Path runtimePath = transactionDirectory.resolve(RUNTIME_FILE);
        verifySafeRepositoryPath(sourcePath);
        verifySafeRepositoryPath(runtimePath);
        fileSystem.write(sourcePath, rebound.sourceBytes());
        fileSystem.fsyncFile(sourcePath);
        fileSystem.write(runtimePath, rebound.runtimeBytes());
        fileSystem.fsyncFile(runtimePath);
        PublishRecord prepared = PublishRecord.trustedPrepared(
                fallback.record().target(), preparedDescriptor,
                "recovery rebind prepared"
        );
        writeRecordDurable(
                transactionDirectory.resolve(RECORD_FILE), prepared.canonicalBytes()
        );
        syncDirectory(transactionDirectory);

        CurrentPointer currentBeforeCommit = readCurrent(mapDirectory)
                .orElseThrow(() -> new ContainerStorageException(
                        "CURRENT disappeared during recovery rebind"
                ));
        if (!currentBeforeCommit.equals(damagedCurrent)) {
            writeRecordDurable(
                    transactionDirectory.resolve(RECORD_FILE),
                    prepared.transition(PublishState.ABORTED, "REVISION_CONFLICT")
                            .canonicalBytes()
            );
            throw new ContainerStorageException(
                    "CURRENT changed during recovery rebind"
            );
        }

        markRecoveryRequired(mapDirectory, preparedDescriptor);
        Path revisions = mapDirectory.resolve("revisions");
        fileSystem.createDirectories(revisions);
        Path revisionDirectory = revisions.resolve(Long.toString(publishRevision));
        fileSystem.moveAtomically(transactionDirectory, revisionDirectory);
        syncDirectory(revisions);
        CurrentPointer reboundPointer = new CurrentPointer(
                damagedCurrent.revision(),
                publishRevision,
                preparedDescriptor.descriptorChecksum()
        );
        Path temporaryCurrent = mapDirectory.resolve("CURRENT." + token + ".tmp");
        writeDurable(temporaryCurrent, reboundPointer.canonicalBytes());
        fileSystem.replaceAtomically(temporaryCurrent, mapDirectory.resolve("CURRENT"));
        try {
            syncDirectory(mapDirectory);
        } catch (IOException exception) {
            suspendedMaps.add(mapDirectory.toString());
            return new PublishOutcome(
                    PublishState.PREPARED,
                    PublishOutcome.Status.COMMIT_STATUS_UNKNOWN,
                    publishRevision,
                    "rebound CURRENT replaced but parent directory sync failed"
            );
        }
        authoritativePointers.put(mapDirectory.toString(), reboundPointer);

        try {
            writeRecordDurable(
                    revisionDirectory.resolve(RECORD_FILE),
                    prepared.transition(PublishState.COMMITTED, "recovery rebound")
                            .canonicalBytes()
            );
            syncDirectory(revisionDirectory);
            clearRecoveryRequired(mapDirectory);
        } catch (IOException exception) {
            suspendedMaps.add(mapDirectory.toString());
            return new PublishOutcome(
                    PublishState.COMMITTED,
                    PublishOutcome.Status.COMMITTED,
                    publishRevision,
                    "rebound committed; publish record requires recovery"
            );
        }
        return new PublishOutcome(
                PublishState.COMMITTED,
                PublishOutcome.Status.COMMITTED,
                publishRevision,
                "rebound damaged current content"
        );
    }

    private void rebuildHighWaterMark(Path mapDirectory) throws IOException {
        java.util.List<Path> durableTemporaries = listHighWaterTemporaries(mapDirectory);
        boolean stateValid;
        long persisted;
        try {
            java.util.OptionalLong persistedState = readHighWaterMarkIfPresent(mapDirectory);
            stateValid = persistedState.isPresent();
            persisted = persistedState.orElse(0L);
        } catch (ContainerStorageException invalidState) {
            if (hasCause(invalidState, IOException.class)) {
                throw invalidState;
            }
            stateValid = false;
            persisted = 0;
        }
        long observed = persisted;
        observed = Math.max(observed, scanHighWaterTemporaries(durableTemporaries));
        observed = Math.max(observed, readCurrentSafely(mapDirectory)
                .map(CurrentPointer::revision).orElse(0L));
        observed = Math.max(observed, scanRevisionContainer(
                mapDirectory.resolve("revisions"), true
        ));
        observed = Math.max(observed, scanRevisionContainer(
                mapDirectory.resolve("transactions"), false
        ));
        if (!stateValid || observed != persisted) {
            writeHighWaterMark(mapDirectory, observed);
        }
        deleteHighWaterTemporaries(mapDirectory, durableTemporaries);
    }

    private java.util.List<Path> listHighWaterTemporaries(Path mapDirectory)
            throws IOException {
        return listDirectoryIfPresent(mapDirectory).stream()
                .filter(MinimapRepository::isHighWaterTemporary)
                .toList();
    }

    private long scanHighWaterTemporaries(java.util.List<Path> temporaries)
            throws IOException {
        long maximum = 0;
        for (Path temporary : temporaries) {
            verifySafeRepositoryPath(temporary);
            try {
                maximum = Math.max(maximum, readHighWaterStateFile(temporary));
            } catch (NoSuchFileException disappeared) {
                // A concurrently removed stale temporary contributes no revision.
            } catch (ContainerStorageException invalidTemporary) {
                if (hasCause(invalidTemporary, IOException.class)) {
                    throw invalidTemporary;
                }
                // A partial pre-fsync write is not durable revision evidence.
            }
        }
        return maximum;
    }

    private void deleteHighWaterTemporaries(
            Path mapDirectory,
            java.util.List<Path> temporaries
    ) throws IOException {
        boolean deleted = false;
        for (Path temporary : temporaries) {
            verifySafeRepositoryPath(temporary);
            deleted |= Files.deleteIfExists(temporary);
        }
        if (deleted) {
            syncDirectory(mapDirectory);
        }
    }

    private void cleanupCurrentTemporaries(Path mapDirectory) throws IOException {
        boolean deleted = false;
        for (Path entry : listDirectoryIfPresent(mapDirectory)) {
            if (!isCurrentTemporary(entry)) {
                continue;
            }
            verifySafeRepositoryPath(entry);
            deleted |= Files.deleteIfExists(entry);
        }
        if (deleted) {
            syncDirectory(mapDirectory);
        }
    }

    private static boolean isCurrentTemporary(Path path) {
        String name = path.getFileName().toString();
        String prefix = "CURRENT.";
        String suffix = ".tmp";
        if (!name.startsWith(prefix) || !name.endsWith(suffix)) {
            return false;
        }
        String token = name.substring(prefix.length(), name.length() - suffix.length());
        try {
            return UUID.fromString(token).toString().equals(token);
        } catch (IllegalArgumentException invalidToken) {
            return false;
        }
    }

    private static boolean isHighWaterTemporary(Path path) {
        String fileName = path.getFileName().toString();
        String prefix = STATE_FILE + ".";
        String suffix = ".tmp";
        if (!fileName.startsWith(prefix) || !fileName.endsWith(suffix)) {
            return false;
        }
        String token = fileName.substring(prefix.length(), fileName.length() - suffix.length());
        if (token.length() != 36) {
            return false;
        }
        try {
            return UUID.fromString(token).toString().equals(token);
        } catch (IllegalArgumentException invalidUuid) {
            return false;
        }
    }

    private long scanRevisionContainer(Path container, boolean includeDirectoryNames)
            throws IOException {
        verifySafeRepositoryPath(container);
        long maximum = 0;
        for (Path directory : listDirectoryIfPresent(container)) {
            if (includeDirectoryNames) {
                long revision = revisionNumber(directory);
                if (revision < 0) {
                    throw new ContainerStorageException(
                            "Revision directory name is not canonical"
                    );
                }
                maximum = Math.max(maximum, revision);
            }
            Path recordPath = directory.resolve(RECORD_FILE);
            try {
                PublishRecord record = readPublishRecord(recordPath);
                maximum = Math.max(maximum, record.descriptor().publishRevision());
            } catch (NoSuchFileException missingRecord) {
                // A missing record does not make the numeric revision reusable.
            } catch (RuntimeException invalidRecord) {
                if (hasCause(invalidRecord, IOException.class)) {
                    throw invalidRecord;
                }
                // A malformed candidate is not promotable; its numeric directory
                // (when present) still remains consumed for high-water purposes.
            }
        }
        return Math.max(0, maximum);
    }

    private void cleanupOrphanRevisions(
            Path mapDirectory,
            Optional<RecoveryCandidate> selectedCandidate
    ) throws IOException {
        Path revisions = mapDirectory.resolve("revisions");
        verifySafeRepositoryPath(revisions);
        Path selectedDirectory = selectedCandidate
                .map(RecoveryCandidate::directory)
                .orElse(null);
        java.util.List<Path> deletionRoots = new java.util.ArrayList<>();
        for (Path directory : listDirectoryIfPresent(revisions)) {
            if (directory.equals(selectedDirectory)) {
                continue;
            }
            Path recordPath = directory.resolve(RECORD_FILE);
            PublishRecord record;
            try {
                record = readPublishRecord(recordPath);
            } catch (NoSuchFileException missingRecord) {
                continue;
            } catch (RuntimeException invalidRecord) {
                continue;
            }
            if (record.state() == PublishState.RESERVED
                    || record.state() == PublishState.PREPARED) {
                deletionRoots.add(directory);
            }
        }
        java.util.List<Path> deletionPaths = validateCleanupBatch(deletionRoots);
        for (Path path : deletionPaths.stream()
                .sorted(java.util.Comparator.reverseOrder()).toList()) {
            Files.deleteIfExists(path);
        }
        if (!deletionPaths.isEmpty()) {
            syncDirectory(revisions);
        }
    }

    private static java.util.List<Path> validateCleanupBatch(
            java.util.List<Path> roots
    ) throws IOException {
        java.util.List<Path> paths = new java.util.ArrayList<>();
        for (Path root : roots) {
            java.util.List<Path> tree = validateCleanupTree(root);
            if (tree.size() > MAX_CLEANUP_TREE_NODES - paths.size()) {
                throw new IOException(
                        "Repository cleanup batch exceeds its node limit"
                );
            }
            paths.addAll(tree);
        }
        return paths;
    }

    private static java.util.List<Path> listDirectoryIfPresent(Path directory)
            throws IOException {
        try (java.util.stream.Stream<Path> stream = Files.list(directory)) {
            java.util.List<Path> entries = stream
                    .limit(MAX_DIRECTORY_ENTRIES + 1L)
                    .toList();
            if (entries.size() > MAX_DIRECTORY_ENTRIES) {
                throw new IOException("Repository directory exceeds its entry limit");
            }
            return entries;
        } catch (NoSuchFileException missingDirectory) {
            return java.util.List.of();
        }
    }

    private static void requireDirectoryCapacity(Path directory, String label)
            throws IOException {
        if (listDirectoryIfPresent(directory).size() >= MAX_DIRECTORY_ENTRIES) {
            throw new IOException("Repository " + label + " directory is full");
        }
    }

    private static long revisionNumber(Path directory) {
        String name = directory.getFileName().toString();
        if (!name.matches("0|[1-9][0-9]{0,18}")) {
            return -1;
        }
        try {
            return Long.parseLong(name);
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    private record RecoveryCandidate(Path directory, PublishRecord record) {
    }

    private void verifyPersistedPreparedPair(
            PublishTransaction caller,
            PublishRecord record,
            Path recordPath
    ) throws IOException {
        try {
            PublishTransaction persisted = new PublishTransaction(
                    record.target(),
                    record.descriptor(),
                    caller.transactionDirectory(),
                    Instant.ofEpochMilli(record.descriptor().expiresAtEpochMillis()),
                    PublishState.PREPARED
            );
            MinimapPairValidator.Result validated = MinimapPairValidator.validate(
                    persisted,
                    caller.transactionDirectory().resolve(SOURCE_FILE),
                    caller.transactionDirectory().resolve(RUNTIME_FILE),
                    fileSystem,
                    this::verifySafeRepositoryPath
            );
            if (!validated.sourceHash().equals(record.descriptor().sourceHash())
                    || !validated.runtimeHash().equals(record.descriptor().runtimeHash())
                    || !validated.runtimeContainerHash().equals(
                    record.descriptor().runtimeContainerHash()
            )) {
                throw new ContainerStorageException(
                        "Persisted publish pair does not match its descriptor"
                );
            }
        } catch (IOException | RuntimeException invalidPair) {
            try {
                writeRecordDurable(
                        recordPath,
                        record.transition(PublishState.ABORTED, "PAIR_INVALID").canonicalBytes()
                );
                syncDirectory(caller.transactionDirectory());
            } catch (IOException abortFailure) {
                invalidPair.addSuppressed(abortFailure);
                throw abortFailure;
            }
            if (invalidPair instanceof IOException ioException) {
                throw ioException;
            }
            if (invalidPair instanceof ContainerStorageException storageException) {
                throw storageException;
            }
            throw new ContainerStorageException("Persisted publish pair is invalid", invalidPair);
        }
    }

    public Path mapDirectory(MapKey key) {
        return persistence.mapDirectory(key);
    }

    private Object lockFor(Path mapDirectory) {
        int stripe = mapDirectory.hashCode() & (LOCK_STRIPE_COUNT - 1);
        return locks.computeIfAbsent(stripe, ignored -> new Object());
    }

    private RepositoryFileSystem.LockHandle acquireMapLock(Path mapDirectory)
            throws IOException {
        return persistence.acquireMapLock(mapDirectory);
    }

    private CurrentPublication requireCurrentPublication(Path mapDirectory, MapKey key, CurrentPointer pointer) {
        RecoveryCandidate candidate = findCandidate(
                mapDirectory, key, pointer.revision(), pointer, false)
                .orElseThrow(() -> new ContainerStorageException(
                        "Current publication metadata is invalid"));
        return new CurrentPublication(pointer, candidate.record());
    }

    private void verifySafeRepositoryPath(Path candidate) {
        persistence.verifySafeRepositoryPath(candidate);
    }

    private void verifyTransactionDirectory(
            PublishTransaction transaction,
            Path mapDirectory
    ) {
        Path transactions = mapDirectory.resolve("transactions").toAbsolutePath().normalize();
        Path candidate = transaction.transactionDirectory().toAbsolutePath().normalize();
        if (!candidate.startsWith(transactions)
                || !candidate.getParent().equals(transactions)
                || !candidate.getFileName().toString().equals(transaction.publishToken())) {
            throw new ContainerStorageException("Publish transaction path is outside its map scope");
        }
        verifySafeRepositoryPath(transactions);
        verifySafeRepositoryPath(candidate);
    }

    private Optional<CurrentPointer> readCurrent(Path mapDirectory) {
        return persistence.readCurrent(mapDirectory);
    }

    private long readHighWaterMark(Path mapDirectory) {
        return persistence.readHighWaterMark(mapDirectory);
    }

    private java.util.OptionalLong readHighWaterMarkIfPresent(Path mapDirectory) {
        return persistence.readHighWaterMarkIfPresent(mapDirectory);
    }

    private long readHighWaterStateFile(Path state) throws IOException {
        return persistence.readHighWaterStateFile(state);
    }

    private PublishRecord readPublishRecord(Path path) throws IOException {
        return persistence.readPublishRecord(path);
    }

    private byte[] readMetadata(Path path) throws IOException {
        return persistence.readMetadata(path);
    }

    private void writeHighWaterMark(Path mapDirectory, long highWaterMark) throws IOException {
        persistence.writeHighWaterMark(mapDirectory, highWaterMark);
    }

    private void writeDurable(Path file, byte[] bytes) throws IOException {
        persistence.writeDurable(file, bytes);
    }

    private void writeRecordDurable(Path record, byte[] bytes) throws IOException {
        persistence.writeRecordDurable(record, bytes);
    }

    private void markRecoveryRequired(
            Path mapDirectory,
            PublishDescriptor descriptor
    ) throws IOException {
        persistence.markRecoveryRequired(mapDirectory, descriptor);
    }

    private void clearRecoveryRequired(Path mapDirectory) throws IOException {
        persistence.clearRecoveryRequired(mapDirectory);
    }

    private void abortIfExpired(
            PublishRecord record,
            Path recordPath,
            Path transactionDirectory
    ) throws IOException {
        if (clock.millis() < record.descriptor().expiresAtEpochMillis()) {
            return;
        }
        writeRecordDurable(
                recordPath,
                record.transition(PublishState.ABORTED, "TOKEN_EXPIRED").canonicalBytes()
        );
        syncDirectory(transactionDirectory);
        throw new ContainerStorageException("Publish token has expired");
    }

    private void syncDirectory(Path directory) throws IOException {
        persistence.syncDirectory(directory);
    }

    private void requireDirectorySyncForPublish() {
        persistence.requireDirectorySyncForPublish();
    }

    private PublishOutcome suspendForDirectorySyncLoss(
            Path mapDirectory,
            Path recordPath,
            PublishRecord record
    ) {
        persistence.markDirectorySyncDegraded();
        suspendedMaps.add(mapDirectory.toString());
        PublishRecord frozen = new PublishRecord(
                record.target(),
                record.descriptor(),
                PublishState.PREPARED,
                DIRECTORY_SYNC_LOST_REASON,
                record.pairValidation()
        );
        try {
            fileSystem.write(recordPath, frozen.canonicalBytes());
            fileSystem.fsyncFile(recordPath);
            markRecoveryRequired(mapDirectory, frozen.descriptor());
        } catch (MinimapRepositoryPersistence.DirectorySyncUnavailableException expected) {
            if (!persistence.recoveryMarkerExists(mapDirectory)) {
                throw storageFailure(
                        "Unable to freeze publish after directory sync loss", expected
                );
            }
        } catch (IOException persistenceFailure) {
            throw storageFailure(
                    "Unable to freeze publish after directory sync loss",
                    persistenceFailure
            );
        }
        return new PublishOutcome(
                PublishState.PREPARED,
                PublishOutcome.Status.COMMIT_STATUS_UNKNOWN,
                frozen.descriptor().publishRevision(),
                "Directory synchronization became unavailable before commit"
        );
    }

    private static ContainerStorageException storageFailure(String message, IOException exception) {
        return new ContainerStorageException(message, exception);
    }
}
