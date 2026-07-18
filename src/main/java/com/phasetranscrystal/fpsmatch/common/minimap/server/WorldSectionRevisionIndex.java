package com.phasetranscrystal.fpsmatch.common.minimap.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.phasetranscrystal.fpsmatch.core.minimap.format.JcsCanonicalizer;
import com.phasetranscrystal.fpsmatch.core.minimap.format.StrictJsonParser;
import com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public final class WorldSectionRevisionIndex {
    private static final long RESERVATION_SEGMENT_SIZE = 4096;
    private static final long MAX_SNAPSHOT_BYTES = 64L * 1024 * 1024;
    private static final long MAX_HIGH_WATER_BYTES = 1024L * 1024;
    private static final long MAX_PERSISTED_DIMENSIONS = 1_024;
    private static final long MAX_PERSISTED_SECTIONS = 262_144;
    private static final int MAX_HIGH_WATER_TEMPORARIES = 128;
    private static final int DIMENSION_LOCK_STRIPES = 64;
    private static final String STATE_FILE = "section-revision-state.json";
    private static final String SNAPSHOT_FILE = "section-revision-snapshot.json";
    private static final String OWNER_LOCK_FILE = "section-revision-owner.lock";
    private static final Set<String> SNAPSHOT_FIELDS = Set.of(
            "actualEpochs", "cleanShutdown", "dimensionCount", "dirty",
            "dirtyCount", "sectionCount", "sections"
    );
    private static final Set<String> SECTION_FIELDS = Set.of(
            "dimension", "revision", "sectionX", "sectionY", "sectionZ"
    );
    private static final Comparator<WorldSectionKey> SECTION_ORDER = Comparator
            .comparing((WorldSectionKey section) -> section.dimension().toString())
            .thenComparingInt(WorldSectionKey::sectionX)
            .thenComparingInt(WorldSectionKey::sectionY)
            .thenComparingInt(WorldSectionKey::sectionZ);
    private static final ConcurrentMap<Path, PersistenceLockEntry> JVM_STATE_LOCKS =
            new ConcurrentHashMap<>();

    private final Map<NamespacedId, Long> dimensionEpochs;
    private final Map<WorldSectionKey, Long> sectionRevisions;
    private final Map<WorldSectionKey, Long> dirtySections = new ConcurrentHashMap<>();
    private final Object[] locks = createDimensionLocks();
    private final Map<NamespacedId, Long> reservedHighWater = new ConcurrentHashMap<>();
    private final AtomicInteger dimensionCount = new AtomicInteger();
    private final AtomicInteger sectionCount = new AtomicInteger();
    private final AtomicInteger dirtySectionCount = new AtomicInteger();
    private final ReentrantReadWriteLock snapshotLock = new ReentrantReadWriteLock();
    private final Path stateDirectory;
    private final SnapshotFileAccess snapshotFileAccess;
    private final WorldSectionRevisionLimits limits;
    private final OwnerLease ownerLease;
    private final boolean snapshotOwner;
    private boolean closed;

    public WorldSectionRevisionIndex() {
        this(Map.of(), Map.of());
    }

    public WorldSectionRevisionIndex(Path stateDirectory) {
        this(stateDirectory, Set.of());
    }

    public WorldSectionRevisionIndex(
            Path stateDirectory,
            Set<WorldSectionKey> publishedCoverage
    ) {
        this(stateDirectory, publishedCoverage, NioSnapshotFileAccess.INSTANCE);
    }

    WorldSectionRevisionIndex(
            Path stateDirectory,
            Set<WorldSectionKey> publishedCoverage,
            SnapshotFileAccess snapshotFileAccess
    ) {
        this(
                stateDirectory,
                publishedCoverage,
                snapshotFileAccess,
                WorldSectionRevisionLimits.hardDefaults()
        );
    }

    WorldSectionRevisionIndex(
            Path stateDirectory,
            Set<WorldSectionKey> publishedCoverage,
            SnapshotFileAccess snapshotFileAccess,
            WorldSectionRevisionLimits limits
    ) {
        this.stateDirectory = Objects.requireNonNull(stateDirectory, "stateDirectory")
                .toAbsolutePath().normalize();
        this.snapshotFileAccess = Objects.requireNonNull(
                snapshotFileAccess, "snapshotFileAccess"
        );
        this.limits = Objects.requireNonNull(limits, "limits");
        this.snapshotOwner = true;
        Objects.requireNonNull(publishedCoverage, "publishedCoverage");
        if (publishedCoverage.size() > this.limits.maximumSections()) {
            throw new IllegalArgumentException(
                    "Published coverage exceeds its hard limit"
            );
        }
        Set<NamespacedId> coverageDimensions = new HashSet<>();
        for (WorldSectionKey section : publishedCoverage) {
            Objects.requireNonNull(section, "publishedCoverage contains null");
            if (coverageDimensions.add(section.dimension())
                    && coverageDimensions.size() > this.limits.maximumDimensions()) {
                throw new IllegalArgumentException(
                        "Published coverage dimension quota is exhausted"
                );
            }
        }
        Set<WorldSectionKey> coverage = Set.copyOf(publishedCoverage);
        OwnerLease acquiredOwnerLease = null;
        try {
            Files.createDirectories(this.stateDirectory);
            acquiredOwnerLease = acquireOwnerLease(this.stateDirectory);
            RestoredState restored = withPersistenceLock(() -> {
                HighWaterRecovery highWaterRecovery = readHighWaterRecovery();
                Map<NamespacedId, Long> recoveredHighWater = highWaterRecovery.state();
                Map<NamespacedId, Long> highWater = new HashMap<>(recoveredHighWater);
                SnapshotRead snapshotRead = readSnapshot();
                PersistedSnapshot snapshot = snapshotRead.snapshot();
                Map<NamespacedId, Long> epochs = new HashMap<>(snapshot.actualEpochs());
                recoveredHighWater.forEach((dimension, revision) ->
                        epochs.merge(dimension, revision, Math::max));
                Map<WorldSectionKey, Long> sections = new HashMap<>(snapshot.sections());
                Map<WorldSectionKey, Long> dirty = new HashMap<>(snapshot.dirty());
                if (snapshotRead.present() && !snapshot.cleanShutdown()) {
                    Map<NamespacedId, Long> recoveryRevisions = new HashMap<>();
                    for (WorldSectionKey section : coverage) {
                        recoveryRevisions.computeIfAbsent(section.dimension(), dimension -> {
                            long durableHighWater = highWater.getOrDefault(dimension, 0L);
                            if (durableHighWater > 0) {
                                return durableHighWater;
                            }
                            highWater.put(dimension, RESERVATION_SEGMENT_SIZE);
                            return 1L;
                        });
                    }
                    for (WorldSectionKey section : coverage) {
                        long recoveryRevision = recoveryRevisions.get(section.dimension());
                        sections.put(section, recoveryRevision);
                        dirty.put(section, recoveryRevision);
                        epochs.merge(section.dimension(), recoveryRevision, Math::max);
                    }
                }
                PersistedSnapshot running = new PersistedSnapshot(
                        false,
                        Map.copyOf(epochs),
                        Map.copyOf(sections),
                        Map.copyOf(dirty)
                );
                byte[] runningBytes = encodeSnapshot(running);
                if (highWaterRecovery.needsPersistence()
                        || !highWater.equals(recoveredHighWater)) {
                    persistHighWaterState(highWater);
                }
                writeAtomic(stateDirectory.resolve(SNAPSHOT_FILE), runningBytes);
                return new RestoredState(running, Map.copyOf(highWater));
            });
            this.dimensionEpochs = new ConcurrentHashMap<>(
                    restored.snapshot().actualEpochs()
            );
            this.sectionRevisions = new ConcurrentHashMap<>(
                    restored.snapshot().sections()
            );
            this.dirtySections.putAll(restored.snapshot().dirty());
            this.reservedHighWater.putAll(restored.highWater());
            this.dimensionCount.set(this.dimensionEpochs.size());
            this.sectionCount.set(this.sectionRevisions.size());
            this.dirtySectionCount.set(this.dirtySections.size());
            this.ownerLease = acquiredOwnerLease;
        } catch (IOException exception) {
            closeAfterConstructionFailure(acquiredOwnerLease, exception);
            throw persistenceFailure("Unable to open section revision state", exception);
        } catch (RuntimeException | Error failure) {
            closeAfterConstructionFailure(acquiredOwnerLease, failure);
            throw failure;
        }
    }

    public WorldSectionRevisionIndex(
            Map<NamespacedId, Long> dimensionEpochs,
            Map<WorldSectionKey, Long> sectionRevisions
    ) {
        this.stateDirectory = null;
        this.snapshotFileAccess = null;
        this.limits = WorldSectionRevisionLimits.hardDefaults();
        this.ownerLease = null;
        this.snapshotOwner = false;
        this.dimensionEpochs = new ConcurrentHashMap<>(
                Objects.requireNonNull(dimensionEpochs, "dimensionEpochs")
        );
        this.sectionRevisions = new ConcurrentHashMap<>(
                Objects.requireNonNull(sectionRevisions, "sectionRevisions")
        );
        this.dimensionEpochs.forEach((dimension, revision) -> {
            Objects.requireNonNull(dimension, "dimension");
            requireNonNegative(revision, "world mutation epoch");
        });
        this.sectionRevisions.forEach((section, revision) -> {
            Objects.requireNonNull(section, "section");
            requireNonNegative(revision, "section revision");
        });
        this.dimensionCount.set(this.dimensionEpochs.size());
        this.sectionCount.set(this.sectionRevisions.size());
    }

    static WorldSectionRevisionIndex allocatorOnly(Path stateDirectory) {
        return new WorldSectionRevisionIndex(stateDirectory, true);
    }

    private WorldSectionRevisionIndex(
            Path stateDirectory,
            boolean allocatorOnly
    ) {
        if (!allocatorOnly) {
            throw new IllegalArgumentException("Allocator-only mode is required");
        }
        this.stateDirectory = Objects.requireNonNull(stateDirectory, "stateDirectory")
                .toAbsolutePath().normalize();
        this.snapshotFileAccess = null;
        this.limits = WorldSectionRevisionLimits.hardDefaults();
        this.ownerLease = null;
        this.snapshotOwner = false;
        try {
            Files.createDirectories(this.stateDirectory);
            Map<NamespacedId, Long> highWater = withPersistenceLock(
                    this::recoverHighWaterState
            );
            this.dimensionEpochs = new ConcurrentHashMap<>(highWater);
            this.sectionRevisions = new ConcurrentHashMap<>();
            this.reservedHighWater.putAll(highWater);
            this.dimensionCount.set(this.dimensionEpochs.size());
        } catch (IOException exception) {
            throw persistenceFailure(
                    "Unable to open section revision allocator", exception
            );
        }
    }

    public long markMutated(WorldSectionKey section) {
        Objects.requireNonNull(section, "section");
        snapshotLock.readLock().lock();
        try {
            requireOpen();
            synchronized (lockFor(section.dimension())) {
                long current = dimensionEpochs.getOrDefault(section.dimension(), 0L);
                if (current == Long.MAX_VALUE) {
                    throw new IllegalStateException("World mutation epoch is exhausted");
                }
                boolean dimensionSlot = false;
                boolean sectionSlot = false;
                boolean dirtySlot = false;
                boolean completed = false;
                try {
                    if (!dimensionEpochs.containsKey(section.dimension())) {
                        acquireSlot(
                                dimensionCount,
                                limits.maximumDimensions(),
                                "World section dimension quota is exhausted"
                        );
                        dimensionSlot = true;
                    }
                    if (!sectionRevisions.containsKey(section)) {
                        acquireSlot(
                                sectionCount,
                                limits.maximumSections(),
                                "World section quota is exhausted"
                        );
                        sectionSlot = true;
                    }
                    if (!dirtySections.containsKey(section)) {
                        acquireSlot(
                                dirtySectionCount,
                                limits.maximumDirtySections(),
                                "World section dirty quota is exhausted"
                        );
                        dirtySlot = true;
                    }
                    if (stateDirectory != null
                            && current >= reservedHighWater.getOrDefault(
                                    section.dimension(), 0L
                            )) {
                        current = reserveNextSegment(section.dimension(), current);
                    }
                    long next = current + 1;
                    dimensionEpochs.put(section.dimension(), next);
                    sectionRevisions.put(section, next);
                    dirtySections.put(section, next);
                    completed = true;
                    return next;
                } finally {
                    if (!completed) {
                        if (dirtySlot && !dirtySections.containsKey(section)) {
                            dirtySectionCount.decrementAndGet();
                        }
                        if (sectionSlot && !sectionRevisions.containsKey(section)) {
                            sectionCount.decrementAndGet();
                        }
                        if (dimensionSlot
                                && !dimensionEpochs.containsKey(section.dimension())) {
                            dimensionCount.decrementAndGet();
                        }
                    }
                }
            }
        } finally {
            snapshotLock.readLock().unlock();
        }
    }

    public long worldMutationEpoch(NamespacedId dimension) {
        return dimensionEpochs.getOrDefault(
                Objects.requireNonNull(dimension, "dimension"), 0L
        );
    }

    public long sectionRevision(WorldSectionKey section) {
        return sectionRevisions.getOrDefault(
                Objects.requireNonNull(section, "section"), 0L
        );
    }

    public SectionSnapshotStamp beginSnapshot(
            long snapshotId,
            WorldSectionKey section
    ) {
        return new SectionSnapshotStamp(
                snapshotId, section, sectionRevision(section), false
        );
    }

    public SectionSnapshotStamp finishSnapshot(SectionSnapshotStamp stamp) {
        Objects.requireNonNull(stamp, "stamp");
        return stamp.withStale(
                stamp.sectionRevision() != sectionRevision(stamp.section())
        );
    }

    public Map<WorldSectionKey, Long> dirtySections() {
        snapshotLock.readLock().lock();
        try {
            return Map.copyOf(dirtySections);
        } finally {
            snapshotLock.readLock().unlock();
        }
    }

    public boolean clearDirtyIfUnchanged(
            WorldSectionKey section,
            long bakedRevision
    ) {
        if (bakedRevision < 0) {
            throw new IllegalArgumentException("Baked revision must be non-negative");
        }
        snapshotLock.readLock().lock();
        try {
            requireOpen();
            Objects.requireNonNull(section, "section");
            synchronized (lockFor(section.dimension())) {
                boolean removed = dirtySections.remove(section, bakedRevision);
                if (removed) {
                    dirtySectionCount.decrementAndGet();
                }
                return removed;
            }
        } finally {
            snapshotLock.readLock().unlock();
        }
    }

    public void saveState() {
        persistCurrentSnapshot(false, "Unable to save section revision state");
    }

    public void closeCleanly() {
        snapshotLock.writeLock().lock();
        try {
            if (closed) {
                return;
            }
            persistCurrentSnapshot(
                    true, "Unable to close section revision state cleanly"
            );
            try {
                ownerLease.close();
            } catch (IOException exception) {
                throw persistenceFailure(
                        "Unable to close section revision state cleanly", exception
                );
            }
            closed = true;
        } finally {
            snapshotLock.writeLock().unlock();
        }
    }

    void abandonWithoutCleanForTest() {
        snapshotLock.writeLock().lock();
        try {
            if (closed) {
                return;
            }
            if (!snapshotOwner) {
                throw new IllegalStateException(
                        "Only a snapshot owner can be abandoned"
                );
            }
            try {
                ownerLease.close();
            } catch (IOException exception) {
                throw persistenceFailure(
                        "Unable to abandon section revision state", exception
                );
            }
            closed = true;
        } finally {
            snapshotLock.writeLock().unlock();
        }
    }

    private void persistCurrentSnapshot(boolean cleanShutdown, String failureMessage) {
        snapshotLock.writeLock().lock();
        try {
            requireOpen();
            if (stateDirectory == null) {
                throw new IllegalStateException(
                        "In-memory section revision state cannot be saved"
                );
            }
            try {
                withPersistenceLock(() -> {
                    persistSnapshot(new PersistedSnapshot(
                            cleanShutdown,
                            Map.copyOf(dimensionEpochs),
                            Map.copyOf(sectionRevisions),
                            Map.copyOf(dirtySections)
                    ));
                    return null;
                });
            } catch (IOException exception) {
                throw persistenceFailure(failureMessage, exception);
            }
        } finally {
            snapshotLock.writeLock().unlock();
        }
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("Section revision state is closed");
        }
    }

    private Object lockFor(NamespacedId dimension) {
        int hash = dimension.hashCode();
        return locks[(hash ^ (hash >>> 16)) & (DIMENSION_LOCK_STRIPES - 1)];
    }

    private static Object[] createDimensionLocks() {
        Object[] locks = new Object[DIMENSION_LOCK_STRIPES];
        java.util.Arrays.setAll(locks, ignored -> new Object());
        return locks;
    }

    private static void acquireSlot(
            AtomicInteger count,
            int maximum,
            String failureMessage
    ) {
        while (true) {
            int current = count.get();
            if (current >= maximum) {
                throw new IllegalStateException(failureMessage);
            }
            if (count.compareAndSet(current, current + 1)) {
                return;
            }
        }
    }

    private long reserveNextSegment(NamespacedId dimension, long current) {
        try {
            return withPersistenceLock(() -> {
                Map<NamespacedId, Long> persisted = recoverHighWaterState();
                long segmentStart = Math.max(
                        current, persisted.getOrDefault(dimension, 0L)
                );
                if (segmentStart > Long.MAX_VALUE - RESERVATION_SEGMENT_SIZE) {
                    throw new IllegalStateException("World mutation epoch is exhausted");
                }
                long nextHighWater = segmentStart + RESERVATION_SEGMENT_SIZE;
                Map<NamespacedId, Long> nextState =
                        new ConcurrentHashMap<>(persisted);
                nextState.put(dimension, nextHighWater);
                persistHighWaterState(nextState);
                reservedHighWater.put(dimension, nextHighWater);
                return segmentStart;
            });
        } catch (IOException exception) {
            throw persistenceFailure("Unable to reserve section revision segment", exception);
        }
    }

    private <T> T withPersistenceLock(IoSupplier<T> action) throws IOException {
        Path lockFile = stateDirectory.resolve("section-revision-state.lock");
        PersistenceLockEntry entry = retainPersistenceLock(lockFile);
        try {
            entry.lock.lock();
            try {
                try (FileChannel channel = FileChannel.open(
                        lockFile,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE
                ); FileLock ignored = channel.lock()) {
                    return action.get();
                }
            } finally {
                entry.lock.unlock();
            }
        } finally {
            releasePersistenceLock(lockFile, entry);
        }
    }

    static PersistenceLockHandle holdPersistenceLockForTest(Path stateDirectory) {
        Path lockFile = Objects.requireNonNull(stateDirectory, "stateDirectory")
                .toAbsolutePath().normalize()
                .resolve("section-revision-state.lock");
        PersistenceLockEntry entry = retainPersistenceLock(lockFile);
        boolean locked = false;
        try {
            entry.lock.lock();
            locked = true;
            return new PersistenceLockHandle(lockFile, entry);
        } finally {
            if (!locked) {
                releasePersistenceLock(lockFile, entry);
            }
        }
    }

    private static PersistenceLockEntry retainPersistenceLock(Path lockFile) {
        return JVM_STATE_LOCKS.compute(lockFile, (ignored, existing) -> {
            PersistenceLockEntry entry = existing == null
                    ? new PersistenceLockEntry()
                    : existing;
            entry.references++;
            return entry;
        });
    }

    private static void releasePersistenceLock(
            Path lockFile,
            PersistenceLockEntry retained
    ) {
        JVM_STATE_LOCKS.compute(lockFile, (ignored, existing) -> {
            if (existing != retained || existing.references <= 0) {
                throw new IllegalStateException(
                        "Section revision persistence lock reference is invalid"
                );
            }
            existing.references--;
            return existing.references == 0 ? null : existing;
        });
    }

    private static OwnerLease acquireOwnerLease(Path stateDirectory)
            throws IOException {
        FileChannel channel = FileChannel.open(
                stateDirectory.resolve(OWNER_LOCK_FILE),
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE
        );
        try {
            FileLock lock;
            try {
                lock = channel.tryLock();
            } catch (OverlappingFileLockException alreadyOpen) {
                throw new IOException(
                        "Section revision state is already open", alreadyOpen
                );
            }
            if (lock == null) {
                throw new IOException("Section revision state is already open");
            }
            return new OwnerLease(channel, lock);
        } catch (IOException | RuntimeException | Error failure) {
            try {
                channel.close();
            } catch (IOException closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw failure;
        }
    }

    private static void closeAfterConstructionFailure(
            OwnerLease ownerLease,
            Throwable failure
    ) {
        if (ownerLease == null) {
            return;
        }
        try {
            ownerLease.close();
        } catch (IOException closeFailure) {
            failure.addSuppressed(closeFailure);
        }
    }

    private Map<NamespacedId, Long> recoverHighWaterState() throws IOException {
        HighWaterRecovery recovery = readHighWaterRecovery();
        if (recovery.needsPersistence()) {
            persistHighWaterState(recovery.state());
        }
        return recovery.state();
    }

    private HighWaterRecovery readHighWaterRecovery() throws IOException {
        Map<NamespacedId, Long> restored = new ConcurrentHashMap<>(
                readHighWaterState(stateDirectory.resolve(STATE_FILE), true)
        );
        boolean recoveredTemporary = false;
        try (var paths = Files.list(stateDirectory)) {
            var iterator = paths.filter(this::isHighWaterTemporary).iterator();
            int temporaryCount = 0;
            while (iterator.hasNext()) {
                if (++temporaryCount > MAX_HIGH_WATER_TEMPORARIES) {
                    throw new IOException(
                            "Section revision state temporary file count exceeds its hard limit"
                    );
                }
                Path path = iterator.next();
                try {
                    Map<NamespacedId, Long> candidate =
                            readHighWaterState(path, false);
                    for (Map.Entry<NamespacedId, Long> entry : candidate.entrySet()) {
                        long before = restored.getOrDefault(entry.getKey(), 0L);
                        if (entry.getValue() > before) {
                            if (before == 0L
                                    && !restored.containsKey(entry.getKey())
                                    && restored.size() >= limits.maximumDimensions()) {
                                throw new IOException(
                                        "Section revision state dimension quota is exhausted"
                                );
                            }
                            restored.put(entry.getKey(), entry.getValue());
                            recoveredTemporary = true;
                        }
                    }
                } catch (IOException incompleteTemporary) {
                    // A crash before the temporary file fsync may leave an invalid candidate.
                    if (!isIgnorableHighWaterTemporary(incompleteTemporary)) {
                        throw incompleteTemporary;
                    }
                }
            }
        }
        return new HighWaterRecovery(Map.copyOf(restored), recoveredTemporary);
    }

    private static boolean isIgnorableHighWaterTemporary(IOException failure) {
        return "Section revision state format is invalid".equals(failure.getMessage())
                || "Section revision state is not canonical JSON".equals(
                failure.getMessage()
        )
                || "Section revision state fields are invalid".equals(
                failure.getMessage()
        )
                || "Section revision high-water value is invalid".equals(
                failure.getMessage()
        )
                || "Section revision state is invalid".equals(failure.getMessage())
                || failure instanceof NoSuchFileException;
    }

    private Map<NamespacedId, Long> readHighWaterState(
            Path stateFile,
            boolean missingIsEmpty
    ) throws IOException {
        byte[] bytes = readHighWaterBytes(stateFile, missingIsEmpty);
        if (bytes == null) {
            return Map.of();
        }
        try {
        JsonElement parsed = StrictJsonParser.parse(bytes);
        if (!parsed.isJsonObject()
                || !java.util.Arrays.equals(bytes, JcsCanonicalizer.canonicalize(parsed))) {
            throw new IOException("Section revision state is not canonical JSON");
        }
        JsonObject root = parsed.getAsJsonObject();
        if (!root.keySet().equals(Set.of("dimensions"))
                || !root.get("dimensions").isJsonObject()) {
            throw new IOException("Section revision state fields are invalid");
        }
        JsonObject dimensions = root.getAsJsonObject("dimensions");
        if (dimensions.size() > limits.maximumDimensions()) {
            throw new IOException(
                    "Section revision state dimension quota is exhausted"
            );
        }
        Map<NamespacedId, Long> restored = new ConcurrentHashMap<>();
        for (Map.Entry<String, JsonElement> entry
                : dimensions.entrySet()) {
            JsonElement value = entry.getValue();
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                throw new IOException("Section revision high-water value is invalid");
            }
            String encoded = value.getAsString();
            if (!encoded.matches("0|[1-9][0-9]{0,18}")) {
                throw new IOException("Section revision high-water value is invalid");
            }
            try {
                restored.put(NamespacedId.parse(entry.getKey()), Long.parseLong(encoded));
            } catch (IllegalArgumentException invalid) {
                throw new IOException("Section revision state is invalid", invalid);
            }
        }
        return Map.copyOf(restored);
        } catch (IOException formatFailure) {
            throw formatFailure;
        } catch (RuntimeException formatFailure) {
            throw new IOException(
                    "Section revision state format is invalid",
                    formatFailure
            );
        }
    }

    private byte[] readHighWaterBytes(Path stateFile, boolean missingIsEmpty)
            throws IOException {
        try (FileChannel channel = FileChannel.open(
                stateFile,
                StandardOpenOption.READ,
                LinkOption.NOFOLLOW_LINKS
        )) {
            BasicFileAttributes attributes = Files.readAttributes(
                    stateFile,
                    BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS
            );
            if (!attributes.isRegularFile()) {
                throw new IOException("Section revision state must be a regular file");
            }
            long size = channel.size();
            if (size < 0) {
                throw highWaterChangedDuringRead();
            }
            if (size > MAX_HIGH_WATER_BYTES) {
                throw new IOException(
                        "Section revision state exceeds its byte hard limit"
                );
            }
            byte[] bytes = new byte[Math.toIntExact(size)];
            ByteBuffer destination = ByteBuffer.wrap(bytes);
            while (destination.hasRemaining()) {
                int count = channel.read(destination);
                if (count <= 0) {
                    throw highWaterChangedDuringRead();
                }
            }
            ByteBuffer trailing = ByteBuffer.allocate(1);
            if (channel.read(trailing) != -1) {
                throw highWaterChangedDuringRead();
            }
            return bytes;
        } catch (NoSuchFileException missing) {
            if (missingIsEmpty) {
                return null;
            }
            throw missing;
        }
    }

    private static IOException highWaterChangedDuringRead() {
        return new IOException("Section revision state changed while being read");
    }

    private SnapshotRead readSnapshot() throws IOException {
        Path snapshotFile = stateDirectory.resolve(SNAPSHOT_FILE);
        byte[] bytes;
        try (SnapshotReadHandle handle = snapshotFileAccess.openNoFollow(snapshotFile)) {
            long size = handle.size();
            if (size < 0) {
                throw snapshotChangedDuringRead();
            }
            if (size > MAX_SNAPSHOT_BYTES) {
                throw new IOException(
                        "Section revision snapshot exceeds its byte hard limit"
                );
            }
            bytes = new byte[Math.toIntExact(size)];
            ByteBuffer destination = ByteBuffer.wrap(bytes);
            while (destination.hasRemaining()) {
                int count = handle.read(destination);
                if (count <= 0) {
                    throw snapshotChangedDuringRead();
                }
            }
            ByteBuffer trailing = ByteBuffer.allocate(1);
            if (handle.read(trailing) != -1) {
                throw snapshotChangedDuringRead();
            }
        } catch (NoSuchFileException missing) {
            return new SnapshotRead(false, PersistedSnapshot.empty());
        }
        try {
        JsonElement parsed = StrictJsonParser.parse(bytes);
        if (!parsed.isJsonObject()
                || !java.util.Arrays.equals(bytes, JcsCanonicalizer.canonicalize(parsed))) {
            throw new IOException("Section revision snapshot is not canonical JSON");
        }
        JsonObject root = parsed.getAsJsonObject();
        if (!root.keySet().equals(SNAPSHOT_FIELDS)
                || !root.get("actualEpochs").isJsonObject()
                || !root.get("sections").isJsonArray()
                || !root.get("dirty").isJsonArray()) {
            throw new IOException("Section revision snapshot fields are invalid");
        }
        boolean cleanShutdown = booleanValue(root, "cleanShutdown");
        long dimensionCount = count(root, "dimensionCount");
        if (dimensionCount > MAX_PERSISTED_DIMENSIONS) {
            throw new IOException(
                    "Section revision snapshot dimension count exceeds its hard limit"
            );
        }
        Map<NamespacedId, Long> epochs = readEpochs(
                root.getAsJsonObject("actualEpochs")
        );
        if (dimensionCount != epochs.size()) {
            throw new IOException("Section revision snapshot dimension count is invalid");
        }
        long sectionCount = count(root, "sectionCount");
        if (sectionCount > MAX_PERSISTED_SECTIONS) {
            throw new IOException(
                    "Section revision snapshot section count exceeds its hard limit"
            );
        }
        Map<WorldSectionKey, Long> sections = readSections(
                root.getAsJsonArray("sections")
        );
        if (sectionCount != sections.size()) {
            throw new IOException("Section revision snapshot section count is invalid");
        }
        for (Map.Entry<WorldSectionKey, Long> entry : sections.entrySet()) {
            long actualEpoch = epochs.getOrDefault(entry.getKey().dimension(), 0L);
            if (entry.getValue() > actualEpoch) {
                throw new IOException(
                        "Section revision snapshot epoch precedes a section revision"
                );
            }
        }
        long dirtyCount = count(root, "dirtyCount");
        if (dirtyCount > MAX_PERSISTED_SECTIONS) {
            throw new IOException(
                    "Section revision snapshot dirty count exceeds its hard limit"
            );
        }
        Map<WorldSectionKey, Long> dirty = readSections(root.getAsJsonArray("dirty"));
        if (dirtyCount != dirty.size()) {
            throw new IOException("Section revision snapshot dirty count is invalid");
        }
        for (Map.Entry<WorldSectionKey, Long> entry : dirty.entrySet()) {
            Long sectionRevision = sections.get(entry.getKey());
            if (sectionRevision == null) {
                throw new IOException(
                        "Section revision snapshot dirty entry is missing from sections"
                );
            }
            if (!sectionRevision.equals(entry.getValue())) {
                throw new IOException(
                        "Section revision snapshot dirty revision does not match its section revision"
                );
            }
        }
        return new SnapshotRead(
                true,
                new PersistedSnapshot(cleanShutdown, epochs, sections, dirty)
        );
        } catch (IOException formatFailure) {
            throw formatFailure;
        } catch (RuntimeException formatFailure) {
            throw new IOException(
                    "Section revision snapshot format is invalid",
                    formatFailure
            );
        }
    }

    private static IOException snapshotChangedDuringRead() {
        return new IOException("Section revision snapshot changed while being read");
    }

    private Map<NamespacedId, Long> readEpochs(JsonObject encoded) throws IOException {
        Map<NamespacedId, Long> epochs = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : encoded.entrySet()) {
            try {
                epochs.put(NamespacedId.parse(entry.getKey()), count(entry.getValue()));
            } catch (IllegalArgumentException invalid) {
                throw new IOException("Section revision snapshot epoch is invalid", invalid);
            }
        }
        return Map.copyOf(epochs);
    }

    private Map<WorldSectionKey, Long> readSections(JsonArray encoded) throws IOException {
        Map<WorldSectionKey, Long> sections = new HashMap<>();
        for (JsonElement element : encoded) {
            if (!element.isJsonObject()) {
                throw new IOException("Section revision snapshot entry is invalid");
            }
            JsonObject entry = element.getAsJsonObject();
            if (!entry.keySet().equals(SECTION_FIELDS)) {
                throw new IOException("Section revision snapshot entry fields are invalid");
            }
            try {
                WorldSectionKey section = new WorldSectionKey(
                        NamespacedId.parse(string(entry, "dimension")),
                        coordinate(entry, "sectionX"),
                        coordinate(entry, "sectionY"),
                        coordinate(entry, "sectionZ")
                );
                if (sections.put(section, count(entry, "revision")) != null) {
                    throw new IOException("Section revision snapshot entry is duplicated");
                }
            } catch (IllegalArgumentException invalid) {
                throw new IOException("Section revision snapshot entry is invalid", invalid);
            }
        }
        return Map.copyOf(sections);
    }

    private static int coordinate(JsonObject root, String key) throws IOException {
        JsonElement value = root.get(key);
        if (value == null || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isNumber()) {
            throw new IOException("Section revision snapshot coordinate is invalid");
        }
        try {
            return new BigDecimal(value.getAsString()).intValueExact();
        } catch (NumberFormatException | ArithmeticException invalid) {
            throw new IOException(
                    "Section revision snapshot coordinate is invalid", invalid
            );
        }
    }

    private void persistSnapshot(PersistedSnapshot snapshot) throws IOException {
        writeAtomic(stateDirectory.resolve(SNAPSHOT_FILE), encodeSnapshot(snapshot));
    }

    private byte[] encodeSnapshot(PersistedSnapshot snapshot) throws IOException {
        validateSnapshotLimits(snapshot);
        JsonObject epochs = new JsonObject();
        snapshot.actualEpochs().entrySet().stream()
                .sorted(Map.Entry.comparingByKey(
                        Comparator.comparing(NamespacedId::toString)
                ))
                .forEach(entry -> epochs.addProperty(
                        entry.getKey().toString(), Long.toString(entry.getValue())
                ));
        JsonObject root = new JsonObject();
        root.add("actualEpochs", epochs);
        root.addProperty("cleanShutdown", snapshot.cleanShutdown());
        root.addProperty("dimensionCount", Long.toString(snapshot.actualEpochs().size()));
        root.add("dirty", encodeSections(snapshot.dirty()));
        root.addProperty("dirtyCount", Long.toString(snapshot.dirty().size()));
        root.addProperty("sectionCount", Long.toString(snapshot.sections().size()));
        root.add("sections", encodeSections(snapshot.sections()));
        byte[] bytes = JcsCanonicalizer.canonicalize(root);
        if (bytes.length > limits.maximumSnapshotBytes()) {
            throw new IOException(
                    "Section revision snapshot exceeds its byte hard limit"
            );
        }
        return bytes;
    }

    private void validateSnapshotLimits(PersistedSnapshot snapshot)
            throws IOException {
        if (snapshot.actualEpochs().size() > limits.maximumDimensions()) {
            throw new IOException(
                    "Section revision snapshot dimension quota is exhausted"
            );
        }
        if (snapshot.sections().size() > limits.maximumSections()) {
            throw new IOException("Section revision snapshot section quota is exhausted");
        }
        if (snapshot.dirty().size() > limits.maximumDirtySections()) {
            throw new IOException(
                    "Section revision snapshot dirty quota is exhausted"
            );
        }
    }

    private JsonArray encodeSections(Map<WorldSectionKey, Long> sections) {
        JsonArray encoded = new JsonArray();
        sections.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(SECTION_ORDER))
                .forEach(entry -> {
                    JsonObject value = new JsonObject();
                    value.addProperty("dimension", entry.getKey().dimension().toString());
                    value.addProperty("revision", Long.toString(entry.getValue()));
                    value.addProperty("sectionX", entry.getKey().sectionX());
                    value.addProperty("sectionY", entry.getKey().sectionY());
                    value.addProperty("sectionZ", entry.getKey().sectionZ());
                    encoded.add(value);
                });
        return encoded;
    }

    private static boolean booleanValue(JsonObject root, String key) throws IOException {
        JsonElement value = root.get(key);
        if (value == null || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isBoolean()) {
            throw new IOException("Section revision snapshot boolean is invalid: " + key);
        }
        return value.getAsBoolean();
    }

    private static String string(JsonObject root, String key) throws IOException {
        JsonElement value = root.get(key);
        if (value == null || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isString()) {
            throw new IOException("Section revision snapshot string is invalid: " + key);
        }
        return value.getAsString();
    }

    private static long count(JsonObject root, String key) throws IOException {
        return count(root.get(key));
    }

    private static long count(JsonElement value) throws IOException {
        if (value == null || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isString()) {
            throw new IOException("Section revision snapshot count is invalid");
        }
        String encoded = value.getAsString();
        if (!encoded.matches("0|[1-9][0-9]{0,18}")) {
            throw new IOException("Section revision snapshot count is invalid");
        }
        try {
            return Long.parseLong(encoded);
        } catch (NumberFormatException invalid) {
            throw new IOException("Section revision snapshot count is invalid", invalid);
        }
    }

    private boolean isHighWaterTemporary(Path path) {
        String name = path.getFileName().toString();
        return name.startsWith(STATE_FILE + ".") && name.endsWith(".tmp");
    }

    private void persistHighWaterState(Map<NamespacedId, Long> state) throws IOException {
        JsonObject dimensions = new JsonObject();
        state.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(
                        java.util.Comparator.comparing(NamespacedId::toString)
                ))
                .forEach(entry -> dimensions.addProperty(
                        entry.getKey().toString(), Long.toString(entry.getValue())
                ));
        JsonObject root = new JsonObject();
        root.add("dimensions", dimensions);
        writeAtomic(stateDirectory.resolve(STATE_FILE), JcsCanonicalizer.canonicalize(root));
    }

    private static void writeAtomic(Path target, byte[] bytes) throws IOException {
        Path temporary = target.resolveSibling(
                target.getFileName() + "." + UUID.randomUUID() + ".tmp"
        );
        try {
            Files.write(
                    temporary, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE
            );
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            try {
                Files.move(
                        temporary, target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                );
            } catch (AtomicMoveNotSupportedException unsupported) {
                throw new IOException(
                        "Section revision state requires atomic replacement", unsupported
                );
            }
            syncDirectory(target.getParent());
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void syncDirectory(Path directory) throws IOException {
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            channel.force(true);
        } catch (AccessDeniedException unsupportedOnWindows) {
            if (!System.getProperty("os.name", "").startsWith("Windows")) {
                throw unsupportedOnWindows;
            }
        }
    }

    private static IllegalStateException persistenceFailure(
            String message,
            IOException cause
    ) {
        return new IllegalStateException(message, cause);
    }

    private static void requireNonNegative(Long value, String label) {
        if (value == null || value < 0) {
            throw new IllegalArgumentException(label + " must be non-negative");
        }
    }

    private record PersistedSnapshot(
            boolean cleanShutdown,
            Map<NamespacedId, Long> actualEpochs,
            Map<WorldSectionKey, Long> sections,
            Map<WorldSectionKey, Long> dirty
    ) {
        private PersistedSnapshot {
            actualEpochs = Map.copyOf(actualEpochs);
            sections = Map.copyOf(sections);
            dirty = Map.copyOf(dirty);
        }

        private static PersistedSnapshot empty() {
            return new PersistedSnapshot(false, Map.of(), Map.of(), Map.of());
        }
    }

    private record RestoredState(
            PersistedSnapshot snapshot,
            Map<NamespacedId, Long> highWater
    ) {
    }

    private record HighWaterRecovery(
            Map<NamespacedId, Long> state,
            boolean needsPersistence
    ) {
        private HighWaterRecovery {
            state = Map.copyOf(state);
        }
    }

    private record SnapshotRead(boolean present, PersistedSnapshot snapshot) {
    }

    private static final class OwnerLease implements AutoCloseable {
        private final FileChannel channel;
        private final FileLock lock;
        private boolean closed;

        private OwnerLease(FileChannel channel, FileLock lock) {
            this.channel = Objects.requireNonNull(channel, "channel");
            this.lock = Objects.requireNonNull(lock, "lock");
        }

        @Override
        public void close() throws IOException {
            if (closed) {
                return;
            }
            IOException failure = null;
            try {
                lock.release();
            } catch (IOException releaseFailure) {
                failure = releaseFailure;
            }
            try {
                channel.close();
            } catch (IOException closeFailure) {
                if (failure == null) {
                    failure = closeFailure;
                } else {
                    failure.addSuppressed(closeFailure);
                }
            }
            if (failure != null) {
                throw failure;
            }
            closed = true;
        }
    }

    private static final class PersistenceLockEntry {
        private final ReentrantLock lock = new ReentrantLock();
        private int references;
    }

    static final class PersistenceLockHandle implements AutoCloseable {
        private final Path lockFile;
        private final PersistenceLockEntry entry;
        private boolean closed;

        private PersistenceLockHandle(
                Path lockFile,
                PersistenceLockEntry entry
        ) {
            this.lockFile = Objects.requireNonNull(lockFile, "lockFile");
            this.entry = Objects.requireNonNull(entry, "entry");
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            entry.lock.unlock();
            closed = true;
            releasePersistenceLock(lockFile, entry);
        }
    }

    @FunctionalInterface
    private interface IoSupplier<T> {
        T get() throws IOException;
    }
}

interface SnapshotFileAccess {
    SnapshotReadHandle openNoFollow(Path snapshotFile) throws IOException;
}

interface SnapshotReadHandle extends AutoCloseable {
    long size() throws IOException;

    int read(ByteBuffer destination) throws IOException;

    @Override
    void close() throws IOException;
}

enum NioSnapshotFileAccess implements SnapshotFileAccess {
    INSTANCE;

    @Override
    public SnapshotReadHandle openNoFollow(Path snapshotFile) throws IOException {
        Objects.requireNonNull(snapshotFile, "snapshotFile");
        FileChannel channel = null;
        try {
            channel = FileChannel.open(
                    snapshotFile,
                    StandardOpenOption.READ,
                    LinkOption.NOFOLLOW_LINKS
            );
            BasicFileAttributes attributes = Files.readAttributes(
                    snapshotFile,
                    BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS
            );
            if (!attributes.isRegularFile()) {
                throw new IOException("Section revision snapshot must be a regular file");
            }
            return new NioSnapshotReadHandle(channel);
        } catch (IOException | RuntimeException failure) {
            if (channel != null) {
                try {
                    channel.close();
                } catch (IOException closeFailure) {
                    failure.addSuppressed(closeFailure);
                }
            }
            throw failure;
        }
    }
}

record NioSnapshotReadHandle(FileChannel channel) implements SnapshotReadHandle {
    NioSnapshotReadHandle {
        Objects.requireNonNull(channel, "channel");
    }

    @Override
    public long size() throws IOException {
        return channel.size();
    }

    @Override
    public int read(ByteBuffer destination) throws IOException {
        return channel.read(Objects.requireNonNull(destination, "destination"));
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }
}
