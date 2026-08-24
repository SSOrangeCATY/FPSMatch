package com.ptcrys.fpsmatch.core.minimap.storage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Bounded, canonical model for the strict minimap authority journal. Filesystem
 * identity and create-if-absent linearization stay behind {@link AuthorityJournalProvider}.
 */
public final class MinimapAuthorityJournal {
    public static final int SLOT_COUNT = 128;
    public static final int CHECKPOINT_INTERVAL = 32;
    public static final int RECOVERY_WINDOW = 16;
    public static final int RETAINED_CHECKPOINTS = 2;
    public static final int MAX_OPERATION_ENTRIES = 3;
    public static final int MAX_CHECKPOINT_ENTRIES = 1;
    public static final int MAX_ENTRY_BYTES = 16 * 1024;
    public static final int MAX_IDENTITY_BYTES = 8 * 1024;
    public static final int MAX_POINTER_BYTES = 2 * 1024;
    public static final String ZERO_DIGEST = "0".repeat(64);

    private static final int MAX_STAGING_OBJECTS_PER_SLOT = 4;

    private final Config config;

    public MinimapAuthorityJournal(Config config) {
        this.config = Objects.requireNonNull(config, "config");
        config.validate();
    }

    public static MinimapAuthorityJournal defaults() {
        return new MinimapAuthorityJournal(Config.defaults());
    }

    public static AuthorityJournalProvider provider() {
        return WindowsAuthorityJournalProvider.create();
    }

    public static AuthorityJournalProvider provider(
            AuthorityJournalProvider.FaultInjector faults
    ) {
        return WindowsAuthorityJournalProvider.create(faults);
    }

    public Config config() {
        return config;
    }

    public enum Operation {
        ACTIVATION,
        PUBLISH,
        REBIND,
        RESET,
        CHECKPOINT,
        OWNER_LEDGER
    }

    public enum Phase {
        ACTIVATION_COMPLETE,
        INTENT,
        ADVANCED,
        COMPLETE,
        CHECKPOINT_COMPLETE
    }

    public enum CurrentKind {
        NONE,
        PUBLICATION,
        TOMBSTONE
    }

    public enum PreflightDecision {
        RESERVE_OPERATION,
        CHECKPOINT_REQUIRED
    }

    public record Config(
            int slotCount,
            int checkpointInterval,
            int recoveryWindow,
            int retainedCheckpoints,
            int maxOperationEntries,
            int maxCheckpointEntries
    ) {
        public static Config defaults() {
            return new Config(
                    SLOT_COUNT,
                    CHECKPOINT_INTERVAL,
                    RECOVERY_WINDOW,
                    RETAINED_CHECKPOINTS,
                    MAX_OPERATION_ENTRIES,
                    MAX_CHECKPOINT_ENTRIES
            );
        }

        public void validate() {
            if (slotCount <= 0 || checkpointInterval <= 0 || recoveryWindow <= 0
                    || retainedCheckpoints <= 0 || maxOperationEntries <= 0
                    || maxCheckpointEntries <= 0) {
                throw new IllegalArgumentException("Journal capacity values must be positive");
            }
            if (slotCount > 4096 || checkpointInterval > 1024
                    || recoveryWindow > 1024 || retainedCheckpoints > 16
                    || maxOperationEntries > 16 || maxCheckpointEntries > 16) {
                throw new IllegalArgumentException("Journal capacity values exceed parser bounds");
            }
            if (checkpointInterval < maxOperationEntries) {
                throw new IllegalArgumentException("Checkpoint interval cannot fit one operation");
            }
            long required;
            try {
                required = Math.addExact(
                        Math.addExact(
                                Math.multiplyExact(
                                        (long) retainedCheckpoints,
                                        Math.addExact(
                                                (long) checkpointInterval,
                                                (long) maxCheckpointEntries
                                        )
                                ),
                                recoveryWindow
                        ),
                        Math.addExact(
                                (long) maxOperationEntries,
                                (long) maxCheckpointEntries
                        )
                );
            } catch (ArithmeticException overflow) {
                throw new IllegalArgumentException("Journal capacity calculation overflowed", overflow);
            }
            if (slotCount < required) {
                throw new IllegalArgumentException(
                        "Journal slot count " + slotCount
                                + " is below worst-case capacity " + required
                );
            }
        }

        public int reservationWidth() {
            return Math.addExact(maxOperationEntries, maxCheckpointEntries);
        }
    }

    public record Hashes(
            String descriptorChecksum,
            String sourceHash,
            String runtimeHash,
            String runtimeContainerHash
    ) {
        public Hashes {
            descriptorChecksum = requireDigest(descriptorChecksum, "descriptorChecksum");
            sourceHash = requireDigest(sourceHash, "sourceHash");
            runtimeHash = requireDigest(runtimeHash, "runtimeHash");
            runtimeContainerHash = requireDigest(runtimeContainerHash, "runtimeContainerHash");
        }

        public static Hashes none() {
            return new Hashes(ZERO_DIGEST, ZERO_DIGEST, ZERO_DIGEST, ZERO_DIGEST);
        }

        public boolean isEmpty() {
            return descriptorChecksum.equals(ZERO_DIGEST)
                    && sourceHash.equals(ZERO_DIGEST)
                    && runtimeHash.equals(ZERO_DIGEST)
                    && runtimeContainerHash.equals(ZERO_DIGEST);
        }

        public boolean isComplete() {
            return !descriptorChecksum.equals(ZERO_DIGEST)
                    && !sourceHash.equals(ZERO_DIGEST)
                    && !runtimeHash.equals(ZERO_DIGEST)
                    && !runtimeContainerHash.equals(ZERO_DIGEST);
        }
    }

    public record Entry(
            String journalInstance,
            long generation,
            String previousDigest,
            Operation operation,
            Phase phase,
            String operationId,
            String receiptDigest,
            byte[] attemptIdentity,
            byte[] priorPointer,
            byte[] candidatePointer,
            CurrentKind projectedKind,
            Hashes hashes,
            long highWater,
            long previousCheckpointGeneration,
            String previousCheckpointDigest,
            String entryDigest
    ) {
        public Entry {
            journalInstance = requireToken(journalInstance, "journalInstance");
            if (generation <= 0 || highWater < 0 || previousCheckpointGeneration < 0) {
                throw new IllegalArgumentException("Journal entry numeric field is invalid");
            }
            previousDigest = requireDigest(previousDigest, "previousDigest");
            Objects.requireNonNull(operation, "operation");
            Objects.requireNonNull(phase, "phase");
            operationId = requireToken(operationId, "operationId");
            receiptDigest = requireDigest(receiptDigest, "receiptDigest");
            attemptIdentity = boundedCopy(
                    attemptIdentity, MAX_IDENTITY_BYTES, "attemptIdentity"
            );
            priorPointer = boundedCopy(priorPointer, MAX_POINTER_BYTES, "priorPointer");
            candidatePointer = boundedCopy(
                    candidatePointer, MAX_POINTER_BYTES, "candidatePointer"
            );
            Objects.requireNonNull(projectedKind, "projectedKind");
            Objects.requireNonNull(hashes, "hashes");
            previousCheckpointDigest = requireDigest(
                    previousCheckpointDigest, "previousCheckpointDigest"
            );
            entryDigest = requireDigest(entryDigest, "entryDigest");
            validateShape(
                    generation, operation, phase, candidatePointer,
                    projectedKind, hashes,
                    previousCheckpointGeneration, previousCheckpointDigest
            );
        }

        @Override
        public byte[] attemptIdentity() {
            return attemptIdentity.clone();
        }

        @Override
        public byte[] priorPointer() {
            return priorPointer.clone();
        }

        @Override
        public byte[] candidatePointer() {
            return candidatePointer.clone();
        }
    }

    public record PendingOperation(
            Operation operation,
            String operationId,
            String receiptDigest,
            byte[] attemptIdentity,
            byte[] priorPointer,
            byte[] candidatePointer,
            CurrentKind projectedKind,
            Hashes hashes,
            long highWater,
            Phase phase,
            int consumedEntries
    ) {
        public PendingOperation {
            Objects.requireNonNull(operation, "operation");
            operationId = requireToken(operationId, "operationId");
            receiptDigest = requireDigest(receiptDigest, "receiptDigest");
            attemptIdentity = boundedCopy(
                    attemptIdentity, MAX_IDENTITY_BYTES, "attemptIdentity"
            );
            priorPointer = boundedCopy(priorPointer, MAX_POINTER_BYTES, "priorPointer");
            candidatePointer = boundedCopy(
                    candidatePointer, MAX_POINTER_BYTES, "candidatePointer"
            );
            Objects.requireNonNull(projectedKind, "projectedKind");
            Objects.requireNonNull(hashes, "hashes");
            if (highWater < 0) {
                throw new IllegalArgumentException("Pending operation high-water is negative");
            }
            Objects.requireNonNull(phase, "phase");
            if (consumedEntries <= 0 || consumedEntries > MAX_OPERATION_ENTRIES) {
                throw new IllegalArgumentException("Pending operation entry count is invalid");
            }
        }

        @Override
        public byte[] attemptIdentity() {
            return attemptIdentity.clone();
        }

        @Override
        public byte[] priorPointer() {
            return priorPointer.clone();
        }

        @Override
        public byte[] candidatePointer() {
            return candidatePointer.clone();
        }

        private PendingOperation advance(Phase nextPhase, int nextConsumedEntries) {
            return new PendingOperation(
                    operation, operationId, receiptDigest, attemptIdentity,
                    priorPointer, candidatePointer, projectedKind, hashes,
                    highWater, nextPhase, nextConsumedEntries
            );
        }

        private boolean matches(Entry entry) {
            return operation == entry.operation()
                    && operationId.equals(entry.operationId())
                    && receiptDigest.equals(entry.receiptDigest())
                    && Arrays.equals(attemptIdentity, entry.attemptIdentity())
                    && Arrays.equals(priorPointer, entry.priorPointer())
                    && Arrays.equals(candidatePointer, entry.candidatePointer())
                    && projectedKind == entry.projectedKind()
                    && hashes.equals(entry.hashes())
                    && highWater == entry.highWater();
        }
    }

    public record Snapshot(
            String journalInstance,
            Optional<Entry> head,
            Optional<Entry> checkpoint,
            int sinceCheckpoint,
            Optional<PendingOperation> pending,
            CurrentKind currentKind,
            byte[] currentPointer,
            Hashes currentHashes,
            long highWater,
            List<Entry> validatedChain,
            int scannedSlots
    ) {
        public Snapshot {
            journalInstance = journalInstance == null ? "" : journalInstance;
            head = Objects.requireNonNull(head, "head");
            checkpoint = Objects.requireNonNull(checkpoint, "checkpoint");
            pending = Objects.requireNonNull(pending, "pending");
            Objects.requireNonNull(currentKind, "currentKind");
            currentPointer = boundedCopy(currentPointer, MAX_POINTER_BYTES, "currentPointer");
            Objects.requireNonNull(currentHashes, "currentHashes");
            validatedChain = List.copyOf(Objects.requireNonNull(validatedChain, "validatedChain"));
            if (sinceCheckpoint < 0 || highWater < 0 || scannedSlots < 0) {
                throw new IllegalArgumentException("Journal snapshot numeric field is invalid");
            }
            if (head.isEmpty()) {
                if (!journalInstance.isEmpty() || checkpoint.isPresent() || pending.isPresent()
                        || currentKind != CurrentKind.NONE || currentPointer.length != 0
                        || !currentHashes.isEmpty() || highWater != 0
                        || !validatedChain.isEmpty()) {
                    throw new IllegalArgumentException("Empty journal snapshot carries state");
                }
            } else {
                requireToken(journalInstance, "journalInstance");
            }
        }

        public static Snapshot empty() {
            return new Snapshot(
                    "", Optional.empty(), Optional.empty(), 0, Optional.empty(),
                    CurrentKind.NONE, new byte[0], Hashes.none(), 0, List.of(), 0
            );
        }

        public long headGeneration() {
            return head.map(Entry::generation).orElse(0L);
        }

        public String headDigest() {
            return head.map(Entry::entryDigest).orElse(ZERO_DIGEST);
        }

        public boolean active() {
            return head.isPresent();
        }

        @Override
        public byte[] currentPointer() {
            return currentPointer.clone();
        }
    }

    public record ReservationPlan(
            PreflightDecision decision,
            String journalInstance,
            String operationId,
            long expectedHeadGeneration,
            byte[] expectedHeadDigest,
            List<AuthorityJournalProvider.CapacityTarget> targets,
            byte[] canonicalReceipt
    ) {
        public ReservationPlan {
            Objects.requireNonNull(decision, "decision");
            journalInstance = requireToken(journalInstance, "journalInstance");
            operationId = requireToken(operationId, "operationId");
            if (expectedHeadGeneration <= 0) {
                throw new IllegalArgumentException("Reservation requires an active head");
            }
            expectedHeadDigest = boundedCopy(expectedHeadDigest, 32, "expectedHeadDigest");
            if (expectedHeadDigest.length != 32) {
                throw new IllegalArgumentException("Expected head digest must be 32 bytes");
            }
            targets = List.copyOf(Objects.requireNonNull(targets, "targets"));
            canonicalReceipt = boundedCopy(
                    canonicalReceipt, MAX_ENTRY_BYTES, "canonicalReceipt"
            );
        }

        public AuthorityJournalProvider.CapacityRequest providerRequest() {
            return new AuthorityJournalProvider.CapacityRequest(
                    journalInstance,
                    operationId,
                    expectedHeadGeneration,
                    expectedHeadDigest,
                    targets,
                    canonicalReceipt
            );
        }

        @Override
        public byte[] expectedHeadDigest() {
            return expectedHeadDigest.clone();
        }

        @Override
        public byte[] canonicalReceipt() {
            return canonicalReceipt.clone();
        }
    }

    public Snapshot load(AuthorityJournalProvider.JournalHandle handle) throws IOException {
        return parse(Objects.requireNonNull(handle, "handle").inspect());
    }

    public Snapshot activate(
            AuthorityJournalProvider.JournalHandle handle,
            String journalInstance,
            String operationId,
            byte[] activationIdentity
    ) throws IOException {
        Snapshot existing = load(handle);
        if (existing.active()) {
            if (!existing.journalInstance().equals(journalInstance)) {
                throw unavailable("Journal instance does not match the active journal");
            }
            return existing;
        }
        Entry activation = activation(journalInstance, operationId, activationIdentity);
        byte[] entryBytes = encode(activation);
        byte[] receipt = AuthorityJournalCodec.encodeActivationReceipt(activation);
        AuthorityJournalProvider.MutationResult result = handle.activate(
                new AuthorityJournalProvider.ActivationRequest(
                        journalInstance, entryBytes, receipt
                )
        );
        if (result.status() == AuthorityJournalProvider.MutationStatus.UNAVAILABLE) {
            throw unavailable("Journal activation was unavailable: " + result.detail());
        }
        Snapshot activated = load(handle);
        if (!activated.active() || !activated.journalInstance().equals(journalInstance)) {
            throw unavailable("Journal activation did not produce the exact visible journal");
        }
        return activated;
    }

    public Entry activation(
            String journalInstance,
            String operationId,
            byte[] activationIdentity
    ) {
        byte[] identity = boundedCopy(
                activationIdentity, MAX_IDENTITY_BYTES, "activationIdentity"
        );
        return materialize(
                journalInstance, 1, ZERO_DIGEST, Operation.ACTIVATION,
                Phase.ACTIVATION_COMPLETE, operationId,
                AuthorityJournalCodec.digestHex(identity), identity,
                new byte[0], new byte[0], CurrentKind.NONE, Hashes.none(), 0,
                0, ZERO_DIGEST
        );
    }

    public Entry next(
            Snapshot snapshot,
            Operation operation,
            Phase phase,
            String operationId,
            byte[] attemptIdentity,
            byte[] priorPointer,
            byte[] candidatePointer,
            Hashes hashes,
            long highWater
    ) {
        Objects.requireNonNull(snapshot, "snapshot");
        if (!snapshot.active()) {
            throw new IllegalStateException("Journal is not active");
        }
        if (operation == Operation.ACTIVATION || operation == Operation.CHECKPOINT) {
            throw new IllegalArgumentException("Use the dedicated activation/checkpoint factory");
        }
        CurrentKind kind = switch (operation) {
            case PUBLISH, REBIND -> CurrentKind.PUBLICATION;
            case RESET -> CurrentKind.TOMBSTONE;
            case OWNER_LEDGER -> snapshot.currentKind();
            default -> throw new IllegalArgumentException("Unsupported journal operation");
        };
        byte[] identity = boundedCopy(
                attemptIdentity, MAX_IDENTITY_BYTES, "attemptIdentity"
        );
        return materialize(
                snapshot.journalInstance(), snapshot.headGeneration() + 1,
                snapshot.headDigest(), operation, phase, operationId,
                AuthorityJournalCodec.digestHex(identity), identity,
                priorPointer, candidatePointer,
                kind, hashes, highWater,
                snapshot.checkpoint().map(Entry::generation).orElse(0L),
                snapshot.checkpoint().map(Entry::entryDigest).orElse(ZERO_DIGEST)
        );
    }

    public Entry checkpoint(
            Snapshot snapshot,
            String operationId,
            byte[] checkpointIdentity
    ) {
        Objects.requireNonNull(snapshot, "snapshot");
        if (!snapshot.active() || snapshot.pending().isPresent()) {
            throw new IllegalStateException("Checkpoint requires a resolved active journal");
        }
        byte[] identity = boundedCopy(
                checkpointIdentity, MAX_IDENTITY_BYTES, "checkpointIdentity"
        );
        return materialize(
                snapshot.journalInstance(), snapshot.headGeneration() + 1,
                snapshot.headDigest(), Operation.CHECKPOINT, Phase.CHECKPOINT_COMPLETE,
                operationId, AuthorityJournalCodec.digestHex(identity), identity,
                snapshot.currentPointer(), snapshot.currentPointer(),
                snapshot.currentKind(), snapshot.currentHashes(), snapshot.highWater(),
                snapshot.checkpoint().map(Entry::generation).orElse(0L),
                snapshot.checkpoint().map(Entry::entryDigest).orElse(ZERO_DIGEST)
        );
    }

    public Snapshot apply(Snapshot snapshot, Entry entry) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(entry, "entry");
        if (!Arrays.equals(encode(entry), encode(decode(encode(entry))))) {
            throw unavailable("Journal entry failed canonical round-trip");
        }
        if (!snapshot.active()) {
            if (entry.generation() != 1 || entry.operation() != Operation.ACTIVATION
                    || entry.phase() != Phase.ACTIVATION_COMPLETE
                    || !entry.previousDigest().equals(ZERO_DIGEST)) {
                throw unavailable("Journal does not begin with canonical activation");
            }
            return new Snapshot(
                    entry.journalInstance(), Optional.of(entry), Optional.empty(), 0,
                    Optional.empty(), CurrentKind.NONE, new byte[0], Hashes.none(),
                    0, List.of(entry), 1
            );
        }
        requireNext(snapshot, entry);
        Optional<PendingOperation> pending = snapshot.pending();
        CurrentKind currentKind = snapshot.currentKind();
        byte[] currentPointer = snapshot.currentPointer();
        Hashes currentHashes = snapshot.currentHashes();
        Optional<Entry> checkpoint = snapshot.checkpoint();
        int sinceCheckpoint = snapshot.sinceCheckpoint() + 1;

        switch (entry.phase()) {
            case INTENT -> {
                if (pending.isPresent() || entry.operation() == Operation.CHECKPOINT
                        || entry.operation() == Operation.ACTIVATION) {
                    throw unavailable("A second or invalid journal operation started");
                }
                pending = Optional.of(new PendingOperation(
                        entry.operation(), entry.operationId(), entry.receiptDigest(),
                        entry.attemptIdentity(), entry.priorPointer(),
                        entry.candidatePointer(), entry.projectedKind(), entry.hashes(),
                        entry.highWater(), Phase.INTENT, 1
                ));
            }
            case ADVANCED -> {
                PendingOperation active = requirePending(pending, entry, Phase.INTENT);
                if (entry.operation() == Operation.OWNER_LEDGER) {
                    throw unavailable("Owner-ledger operations do not have ADVANCED phase");
                }
                pending = Optional.of(active.advance(Phase.ADVANCED, 2));
            }
            case COMPLETE -> {
                Phase expected = entry.operation() == Operation.OWNER_LEDGER
                        ? Phase.INTENT : Phase.ADVANCED;
                requirePending(pending, entry, expected);
                pending = Optional.empty();
                if (entry.operation() != Operation.OWNER_LEDGER) {
                    currentKind = entry.projectedKind();
                    currentPointer = entry.candidatePointer();
                    currentHashes = entry.hashes();
                }
            }
            case CHECKPOINT_COMPLETE -> {
                if (entry.operation() != Operation.CHECKPOINT || pending.isPresent()) {
                    throw unavailable("Checkpoint overlaps an unresolved operation");
                }
                checkpoint = Optional.of(entry);
                sinceCheckpoint = 0;
                currentKind = entry.projectedKind();
                currentPointer = entry.candidatePointer();
                currentHashes = entry.hashes();
            }
            case ACTIVATION_COMPLETE -> throw unavailable("Duplicate activation entry");
        }

        ArrayList<Entry> chain = new ArrayList<>(snapshot.validatedChain());
        chain.add(entry);
        return new Snapshot(
                snapshot.journalInstance(), Optional.of(entry), checkpoint,
                sinceCheckpoint, pending, currentKind, currentPointer, currentHashes,
                Math.max(snapshot.highWater(), entry.highWater()), chain,
                snapshot.scannedSlots() + 1
        );
    }

    public Snapshot parse(AuthorityJournalProvider.Inspection inspection) {
        Objects.requireNonNull(inspection, "inspection");
        if (inspection.detection() == AuthorityJournalProvider.Detection.ABSENT) {
            if (!inspection.slots().isEmpty()) {
                throw unavailable("Absent journal inspection exposes slots");
            }
            return Snapshot.empty();
        }
        if (inspection.detection() == AuthorityJournalProvider.Detection.UNAVAILABLE) {
            throw unavailable("Journal provider reported unavailable: " + inspection.detail());
        }
        if (inspection.slots().isEmpty()
                || inspection.slots().size() > config.slotCount()
                || inspection.stagingObjectCount()
                > config.slotCount() * MAX_STAGING_OBJECTS_PER_SLOT) {
            throw unavailable("Journal inspection exceeded its bounded layout");
        }

        Set<Long> generations = new HashSet<>();
        Set<Integer> indices = new HashSet<>();
        ArrayList<Entry> entries = new ArrayList<>(inspection.slots().size());
        for (AuthorityJournalProvider.Slot slot : inspection.slots()) {
            if (!slot.receiptVerified() || !slot.immutable()
                    || slot.slotIndex() >= config.slotCount()
                    || !generations.add(slot.generation()) || !indices.add(slot.slotIndex())) {
                throw unavailable("Journal slot identity or adoption receipt is invalid");
            }
            Entry entry = decode(slot.canonicalEntry());
            if (entry.generation() != slot.generation()
                    || slotIndex(entry.generation()) != slot.slotIndex()) {
                throw unavailable("Journal slot generation does not match its ring index");
            }
            entries.add(entry);
        }
        entries.sort(Comparator.comparingLong(Entry::generation));

        int anchor = findAnchor(entries);
        Snapshot snapshot;
        Entry first = entries.get(anchor);
        if (first.generation() == 1) {
            snapshot = Snapshot.empty();
        } else {
            snapshot = snapshotFromCheckpoint(first, inspection.slots().size());
            anchor++;
        }
        for (int index = anchor; index < entries.size(); index++) {
            snapshot = apply(snapshot, entries.get(index));
        }
        if (snapshot.scannedSlots() != inspection.slots().size()) {
            snapshot = new Snapshot(
                    snapshot.journalInstance(), snapshot.head(), snapshot.checkpoint(),
                    snapshot.sinceCheckpoint(), snapshot.pending(), snapshot.currentKind(),
                    snapshot.currentPointer(), snapshot.currentHashes(), snapshot.highWater(),
                    snapshot.validatedChain(), inspection.slots().size()
            );
        }
        return snapshot;
    }

    public boolean requiresCheckpoint(Snapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        return Math.addExact(snapshot.sinceCheckpoint(), config.maxOperationEntries())
                > config.checkpointInterval();
    }

    public ReservationPlan preflight(Snapshot snapshot, String operationId) {
        Objects.requireNonNull(snapshot, "snapshot");
        requireToken(operationId, "operationId");
        throw unavailable("Capacity preflight requires an exact provider inspection");
    }

    public ReservationPlan preflight(
            Snapshot snapshot,
            AuthorityJournalProvider.Inspection inspection,
            String operationId
    ) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(inspection, "inspection");
        if (!snapshot.active() || snapshot.pending().isPresent()) {
            throw new IllegalStateException(
                    "Capacity can be reserved only for a resolved active journal"
            );
        }
        String safeOperationId = requireToken(operationId, "operationId");
        if (inspection.detection() != AuthorityJournalProvider.Detection.PRESENT) {
            throw unavailable("Capacity preflight requires a present journal inspection");
        }

        Snapshot inspected = parse(inspection);
        if (!inspected.active() || inspected.pending().isPresent()
                || !inspected.journalInstance().equals(snapshot.journalInstance())
                || inspected.headGeneration() != snapshot.headGeneration()
                || !inspected.headDigest().equals(snapshot.headDigest())) {
            throw unavailable("Capacity preflight snapshot is stale");
        }

        PreflightDecision decision = requiresCheckpoint(inspected)
                ? PreflightDecision.CHECKPOINT_REQUIRED
                : PreflightDecision.RESERVE_OPERATION;
        ArrayList<AuthorityJournalProvider.CapacityTarget> targets =
                new ArrayList<>(config.reservationWidth());
        for (int offset = 1; offset <= config.reservationWidth(); offset++) {
            long generation = Math.addExact(inspected.headGeneration(), offset);
            int targetIndex = slotIndex(generation);
            AuthorityJournalProvider.Slot occupied = inspection.slots().stream()
                    .filter(slot -> slot.slotIndex() == targetIndex)
                    .findFirst()
                    .orElse(null);
            if (occupied == null) {
                targets.add(AuthorityJournalProvider.CapacityTarget.absent(
                        generation, targetIndex
                ));
                continue;
            }
            if (occupied.fileIdentity().length == 0
                    || !checkpointProvesObsolete(inspected, occupied.generation())) {
                throw unavailable("Occupied journal slot is not proven obsolete");
            }
            Entry obsolete = decode(occupied.canonicalEntry());
            if (obsolete.generation() != occupied.generation()
                    || slotIndex(obsolete.generation()) != targetIndex) {
                throw unavailable("Occupied journal slot witness is inconsistent");
            }
            targets.add(new AuthorityJournalProvider.CapacityTarget(
                    generation,
                    targetIndex,
                    obsolete.generation(),
                    AuthorityJournalCodec.digestBytes(obsolete.entryDigest()),
                    occupied.fileIdentity()
            ));
        }

        byte[] headDigest = AuthorityJournalCodec.digestBytes(inspected.headDigest());
        byte[] receipt = AuthorityJournalCodec.encodeCapacityReceipt(
                decision, inspected.journalInstance(), safeOperationId,
                inspected.headGeneration(), headDigest, targets
        );
        return new ReservationPlan(
                decision, inspected.journalInstance(), safeOperationId,
                inspected.headGeneration(), headDigest, targets, receipt
        );
    }

    private boolean checkpointProvesObsolete(Snapshot snapshot, long generation) {
        // The activation and old phases may be outside the parsed anchor after rollover.
        // Two retained checkpoints beyond the recovery window make that exact slot non-authoritative.
        ArrayList<Entry> laterCheckpoints = new ArrayList<>();
        for (Entry entry : snapshot.validatedChain()) {
            if (entry.generation() > generation
                    && entry.operation() == Operation.CHECKPOINT
                    && entry.phase() == Phase.CHECKPOINT_COMPLETE) {
                laterCheckpoints.add(entry);
            }
        }
        if (laterCheckpoints.size() < config.retainedCheckpoints()) {
            return false;
        }
        Entry oldestRetained = laterCheckpoints.get(
                laterCheckpoints.size() - config.retainedCheckpoints()
        );
        return Math.addExact(generation, config.recoveryWindow())
                < oldestRetained.generation();
    }

    public int slotIndex(long generation) {
        if (generation <= 0) {
            throw new IllegalArgumentException("Journal generation must be positive");
        }
        return (int) ((generation - 1) % config.slotCount());
    }

    public byte[] encode(Entry entry) {
        return AuthorityJournalCodec.encode(Objects.requireNonNull(entry, "entry"));
    }

    public Entry decode(byte[] encoded) {
        return AuthorityJournalCodec.decode(encoded);
    }

    private Entry materialize(
            String journalInstance,
            long generation,
            String previousDigest,
            Operation operation,
            Phase phase,
            String operationId,
            String receiptDigest,
            byte[] attemptIdentity,
            byte[] priorPointer,
            byte[] candidatePointer,
            CurrentKind projectedKind,
            Hashes hashes,
            long highWater,
            long previousCheckpointGeneration,
            String previousCheckpointDigest
    ) {
        return AuthorityJournalCodec.materialize(
                journalInstance, generation, previousDigest, operation, phase,
                operationId, receiptDigest, attemptIdentity, priorPointer,
                candidatePointer, projectedKind, hashes, highWater,
                previousCheckpointGeneration, previousCheckpointDigest
        );
    }

    private int findAnchor(List<Entry> entries) {
        if (entries.get(0).generation() == 1) {
            if (entries.get(0).operation() != Operation.ACTIVATION
                    || entries.get(0).phase() != Phase.ACTIVATION_COMPLETE) {
                throw unavailable("Generation one is not the activation entry");
            }
            return 0;
        }
        ArrayList<Integer> checkpoints = new ArrayList<>();
        for (int index = 0; index < entries.size(); index++) {
            Entry entry = entries.get(index);
            if (entry.operation() == Operation.CHECKPOINT
                    && entry.phase() == Phase.CHECKPOINT_COMPLETE) {
                checkpoints.add(index);
            }
        }
        if (checkpoints.size() < config.retainedCheckpoints()) {
            throw unavailable("Rollover journal lacks retained complete checkpoints");
        }
        return checkpoints.get(checkpoints.size() - config.retainedCheckpoints());
    }

    private Snapshot snapshotFromCheckpoint(Entry checkpoint, int scannedSlots) {
        if (checkpoint.operation() != Operation.CHECKPOINT
                || checkpoint.phase() != Phase.CHECKPOINT_COMPLETE) {
            throw unavailable("Journal anchor is not a complete checkpoint");
        }
        return new Snapshot(
                checkpoint.journalInstance(), Optional.of(checkpoint),
                Optional.of(checkpoint), 0, Optional.empty(),
                checkpoint.projectedKind(), checkpoint.candidatePointer(),
                checkpoint.hashes(), checkpoint.highWater(), List.of(checkpoint),
                scannedSlots
        );
    }

    private void requireNext(Snapshot snapshot, Entry entry) {
        if (!entry.journalInstance().equals(snapshot.journalInstance())
                || entry.generation() != snapshot.headGeneration() + 1
                || !entry.previousDigest().equals(snapshot.headDigest())) {
            throw unavailable("Journal entry is not the exact next chain member");
        }
        long checkpointGeneration = snapshot.checkpoint().map(Entry::generation).orElse(0L);
        String checkpointDigest = snapshot.checkpoint()
                .map(Entry::entryDigest).orElse(ZERO_DIGEST);
        if (entry.previousCheckpointGeneration() != checkpointGeneration
                || !entry.previousCheckpointDigest().equals(checkpointDigest)) {
            throw unavailable("Journal entry checkpoint witness is stale");
        }
    }

    private PendingOperation requirePending(
            Optional<PendingOperation> pending,
            Entry entry,
            Phase expectedPhase
    ) {
        PendingOperation active = pending.orElseThrow(
                () -> unavailable("Journal phase has no matching pending operation")
        );
        if (!active.matches(entry) || active.phase() != expectedPhase) {
            throw unavailable("Journal phase crosses operation or receipt identity");
        }
        return active;
    }

    private static void validateShape(
            long generation,
            Operation operation,
            Phase phase,
            byte[] candidatePointer,
            CurrentKind kind,
            Hashes hashes,
            long previousCheckpointGeneration,
            String previousCheckpointDigest
    ) {
        if ((previousCheckpointGeneration == 0)
                != previousCheckpointDigest.equals(ZERO_DIGEST)) {
            throw new IllegalArgumentException("Checkpoint witness is incomplete");
        }
        if (operation == Operation.ACTIVATION) {
            if (generation != 1 || phase != Phase.ACTIVATION_COMPLETE
                    || kind != CurrentKind.NONE || !hashes.isEmpty()) {
                throw new IllegalArgumentException("Activation entry shape is invalid");
            }
            return;
        }
        if (operation == Operation.CHECKPOINT) {
            if (phase != Phase.CHECKPOINT_COMPLETE) {
                throw new IllegalArgumentException("Checkpoint phase is invalid");
            }
        } else if (phase != Phase.INTENT && phase != Phase.ADVANCED
                && phase != Phase.COMPLETE) {
            throw new IllegalArgumentException("Operation phase is invalid");
        }
        if (operation == Operation.OWNER_LEDGER && phase == Phase.ADVANCED) {
            throw new IllegalArgumentException("Owner-ledger ADVANCED phase is invalid");
        }
        if ((operation == Operation.PUBLISH || operation == Operation.REBIND)
                && (kind != CurrentKind.PUBLICATION || !hashes.isComplete())) {
            throw new IllegalArgumentException("Publication entry lacks its full hash set");
        }
        if (operation == Operation.RESET
                && (kind != CurrentKind.TOMBSTONE || !hashes.isEmpty()
                || candidatePointer.length != 0)) {
            throw new IllegalArgumentException("Reset entry is not an empty-hash tombstone");
        }
        if (kind == CurrentKind.PUBLICATION && !hashes.isComplete()) {
            throw new IllegalArgumentException("Publication checkpoint lacks its full hash set");
        }
        if (kind != CurrentKind.PUBLICATION && !hashes.isEmpty()
                && operation != Operation.OWNER_LEDGER) {
            throw new IllegalArgumentException("Non-publication entry carries publication hashes");
        }
    }

    static String requireToken(String value, String name) {
        Objects.requireNonNull(value, name);
        if (!value.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")) {
            throw new IllegalArgumentException(name + " is not a bounded safe token");
        }
        return value;
    }

    static String requireDigest(String value, String name) {
        Objects.requireNonNull(value, name);
        if (!value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(name + " is not a SHA-256 digest");
        }
        return value;
    }

    static byte[] boundedCopy(byte[] value, int maximum, String name) {
        Objects.requireNonNull(value, name);
        if (value.length > maximum) {
            throw new IllegalArgumentException(name + " exceeds its byte limit");
        }
        return value.clone();
    }

    static ContainerStorageException unavailable(String message) {
        return new ContainerStorageException(message);
    }

    static ContainerStorageException unavailable(String message, Throwable cause) {
        return new ContainerStorageException(message, cause);
    }
}
