package com.ptcrys.fpsmatch.core.minimap.storage;

import static com.ptcrys.fpsmatch.core.minimap.storage.WindowsAuthorityJournalNative.*;
import static com.ptcrys.fpsmatch.core.minimap.storage.WindowsAuthorityJournalEvidence.*;

import com.sun.jna.platform.win32.WinNT;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Windows implementation of the strict journal capability. Named authority slots are
 * claimed only with CreateHardLinkW and retired only through an exact open handle.
 */
public final class WindowsAuthorityJournalProvider implements AuthorityJournalProvider {
    private static final boolean WINDOWS =
            System.getProperty("os.name", "").startsWith("Windows");
    private final FaultInjector faults;
    private final ConcurrentMap<Path, Availability> probes = new ConcurrentHashMap<>();

    private WindowsAuthorityJournalProvider(FaultInjector faults) {
        this.faults = Objects.requireNonNull(faults, "faults");
    }

    public static WindowsAuthorityJournalProvider create() {
        return create(FaultInjector.none());
    }

    public static WindowsAuthorityJournalProvider create(FaultInjector faults) {
        return new WindowsAuthorityJournalProvider(faults);
    }

    @Override
    public Availability availability(Path journalDirectory) {
        Path normalized = normalize(journalDirectory);
        if (!WINDOWS) {
            return new Availability(Support.UNSUPPORTED, "Win32 authority journal is unavailable");
        }
        Path probeParent = nearestExistingParent(normalized);
        if (probeParent == null) {
            return new Availability(Support.UNSUPPORTED, "No existing journal parent is available");
        }
        return probes.computeIfAbsent(probeParent, this::selfTest);
    }

    @Override
    public RepositorySessionCapability repositorySessionCapability() {
        return WINDOWS
                ? RepositorySessionCapability.strict(
                        RepositorySessionComposition.productionProvider()
                )
                : RepositorySessionCapability.unsupported();
    }

    @Override
    public Availability strictAvailability(Path mapDirectory) {
        Objects.requireNonNull(mapDirectory, "mapDirectory");
        return WINDOWS
                ? WindowsStrictAvailabilityProbe.probe(mapDirectory)
                : new Availability(
                        Support.UNSUPPORTED,
                        "Win32 strict repository sessions are unavailable"
                );
    }

    @Override
    public JournalHandle open(
            Path journalDirectory,
            MinimapAuthorityJournal.Config config
    ) throws IOException {
        Path normalized = normalize(journalDirectory);
        Objects.requireNonNull(config, "config").validate();
        Availability availability = availability(normalized);
        if (!availability.supported()) {
            throw new UnsupportedOperationException(availability.detail());
        }
        return new Handle(normalized, config);
    }

    private Availability selfTest(Path parent) {
        Path test = parent.resolve(".fpsm-authority-selftest-" + UUID.randomUUID());
        WindowsAuthorityJournalNative.Handle sourceHandle = null;
        WindowsAuthorityJournalNative.Handle linkHandle = null;
        try {
            requirePlainDirectory(parent);
            Files.createDirectory(test);
            Path source = test.resolve("source.bin");
            Path link = test.resolve("linked.bin");
            Path occupied = test.resolve("occupied.bin");
            sourceHandle = createLinkableFile(source, new byte[]{7, 1, 9});
            byte[] sourceIdentity = sourceHandle.identity();
            if (!createHardLink(link, source)) {
                throw new IOException("Self-test hard-link target was unexpectedly occupied");
            }
            try (WindowsAuthorityJournalNative.Handle occupiedHandle =
                         createProtectedFile(occupied, new byte[]{3})) {
                if (createHardLink(occupied, source)) {
                    throw new IOException("CreateHardLinkW replaced an occupied target");
                }
                occupiedHandle.dispose();
            }
            syncDirectory(test);
            hardenFile(source, sourceHandle);
            linkHandle = openPlain(
                    link, WinNT.GENERIC_READ,
                    WinNT.FILE_SHARE_READ | WinNT.FILE_SHARE_WRITE
                            | WinNT.FILE_SHARE_DELETE,
                    false
            );
            if (!Arrays.equals(sourceIdentity, linkHandle.identity())) {
                throw new IOException("Hard-link file identity changed");
            }
            requireImmutable(source);
            requireImmutable(link);
            sourceHandle.dispose();
            sourceHandle.close();
            sourceHandle = null;
            linkHandle.close();
            linkHandle = openPlain(link, READ_DELETE, SHARE_READ_ONLY, false);
            linkHandle.dispose();
            linkHandle.close();
            linkHandle = null;
            syncDirectory(test);
            Files.delete(test);
            syncDirectory(parent);
            return new Availability(Support.SUPPORTED, "Win32 journal self-test passed");
        } catch (Throwable failure) {
            closeQuietly(sourceHandle);
            closeQuietly(linkHandle);
            retireTestObject(test.resolve("source.bin"));
            retireTestObject(test.resolve("linked.bin"));
            retireTestObject(test.resolve("occupied.bin"));
            try {
                Files.deleteIfExists(test);
            } catch (IOException ignored) {
            }
            return new Availability(
                    Support.UNSUPPORTED,
                    "Win32 journal self-test failed: " + failure.getClass().getSimpleName()
                            + ": " + String.valueOf(failure.getMessage())
            );
        }
    }

    private final class Handle implements JournalHandle {
        private final Path journal;
        private final Path entries;
        private final Path staging;
        private final MinimapAuthorityJournal.Config config;
        private final WindowsStrictReservationSupport strictReservations =
                new WindowsStrictReservationSupport();
        private boolean closed;

        private Handle(Path journal, MinimapAuthorityJournal.Config config) {
            this.journal = journal;
            this.entries = journal.resolve("entries");
            this.staging = journal.resolve("staging");
            this.config = config;
        }

        @Override
        public Path journalDirectory() {
            return journal;
        }

        @Override
        public Inspection inspect() throws IOException {
            requireOpen();
            try {
                requireReadableAttributes(journal);
                requirePlainDirectory(journal);
            } catch (NoSuchFileException missing) {
                return Inspection.absent();
            } catch (IOException | RuntimeException failure) {
                return Inspection.unavailable(failure.getMessage());
            }
            try {
                requireReadableAttributes(entries);
                requirePlainDirectory(entries);
                requireReadableAttributes(staging);
                requirePlainDirectory(staging);
                requireExactRootChildren(journal, Set.of("entries", "staging"));
                ReceiptIndex receipts = scanReceipts(staging, config.slotCount() * 4);
                ArrayList<Slot> slots = new ArrayList<>();
                Set<Integer> indices = new HashSet<>();
                try (var stream = Files.list(entries)) {
                    List<Path> paths = stream.sorted().toList();
                    if (paths.size() > config.slotCount()) {
                        return Inspection.unavailable("Authority entry count exceeds slot bound");
                    }
                    for (Path path : paths) {
                        int index = parseSlotName(path.getFileName().toString(), config.slotCount());
                        if (!indices.add(index)) {
                            return Inspection.unavailable("Duplicate authority slot index");
                        }
                        requireReadableAttributes(path);
                        requirePlainFile(path);
                        byte[] bytes = readBounded(path, MinimapAuthorityJournal.MAX_ENTRY_BYTES);
                        MinimapAuthorityJournal.Entry entry =
                                new MinimapAuthorityJournal(config).decode(bytes);
                        byte[] identity;
                        boolean immutable;
                        try (WindowsAuthorityJournalNative.Handle handle = openPlain(
                                path, READ_DELETE, SHARE_READ_ONLY, false
                        )) {
                            identity = handle.identity();
                            immutable = immutable(path);
                        }
                        AdoptionEnvelope receipt = receipts.byGeneration().get(entry.generation());
                        boolean verified = receipt != null
                                && receipt.generation() == entry.generation()
                                && receipt.slotIndex() == index
                                && receipt.entryDigest().equals(entry.entryDigest())
                                && Arrays.equals(receipt.sourceIdentity(), identity)
                                && (entry.operation() == MinimapAuthorityJournal.Operation.ACTIVATION
                                || AuthorityJournalCodec.digestHex(receipt.logicalReceipt())
                                .equals(entry.receiptDigest()));
                        slots.add(new Slot(
                                entry.generation(), index, bytes, identity, verified, immutable
                        ));
                    }
                }
                return new Inspection(
                        Detection.PRESENT, slots, receipts.objectCount(), ""
                );
            } catch (IOException | RuntimeException failure) {
                return Inspection.unavailable(failure.getMessage());
            }
        }

        @Override
        public MutationResult activate(ActivationRequest request) throws IOException {
            requireOpen();
            faults.inject(FaultPoint.BEFORE_ACTIVATION,
                    context(request.journalInstance(), 1, 0));
            if (Files.exists(journal, LinkOption.NOFOLLOW_LINKS)) {
                Inspection inspection = inspect();
                if (inspection.detection() != Detection.PRESENT) {
                    return MutationResult.unavailable(inspection.detail());
                }
                MinimapAuthorityJournal.Snapshot snapshot;
                try {
                    snapshot = new MinimapAuthorityJournal(config).parse(inspection);
                } catch (ContainerStorageException failure) {
                    throw new IOException("Existing authority journal state is invalid", failure);
                }
                if (!snapshot.journalInstance().equals(request.journalInstance())) {
                    return MutationResult.unavailable("A different journal is already active");
                }
                return new MutationResult(
                        MutationStatus.ALREADY_APPLIED,
                        inspection.slots().get(0).fileIdentity(), ""
                );
            }
            requirePlainDirectory(journal.getParent());
            Files.createDirectory(journal);
            syncDirectory(journal.getParent());
            Files.createDirectory(entries);
            Files.createDirectory(staging);
            syncDirectory(journal);
            Path operationDirectory = staging.resolve(
                    "activation-" + request.journalInstance() + "-"
                            + AuthorityJournalCodec.digestHex(request.canonicalReceipt())
                            .substring(0, 16)
            );
            Files.createDirectory(operationDirectory);
            syncDirectory(staging);
            MutationResult result = appendImmutable(
                    operationDirectory, "activation", 1, 0,
                    request.canonicalEntry(), request.canonicalReceipt(),
                    FaultPoint.AFTER_ACTIVATION_LINK
            );
            return result;
        }

        @Override
        public CapacityReceipt reserve(CapacityRequest request) throws IOException {
            try {
                requireOpen();
                requireCanonicalCapacityRequest(request);
                requireRequestHead(request);
                if (request.targets().size() != config.reservationWidth()) {
                    throw new IOException("Capacity reservation must contain four bounded targets");
                }
                for (CapacityTarget target : request.targets()) {
                    if (target.slotIndex() >= config.slotCount()
                            || target.slotIndex() != slotIndex(target.generation())) {
                        throw new IOException("Capacity target ring index is invalid");
                    }
                    Path slot = slotPath(target.slotIndex());
                    if (Files.exists(slot, LinkOption.NOFOLLOW_LINKS)) {
                        if (!target.expectsObsoleteSlot()) {
                            throw new IOException("A reserved target is occupied");
                        }
                        retireExpectedObsolete(target, slot);
                    } else if (target.expectsObsoleteSlot()) {
                        throw new IOException("Expected obsolete authority slot is absent");
                    }
                }
                for (CapacityTarget target : request.targets()) {
                    if (Files.exists(slotPath(target.slotIndex()), LinkOption.NOFOLLOW_LINKS)) {
                        throw new IOException("Capacity target changed before reservation");
                    }
                }
                String receiptId = capacityReceiptId(request);
                Path operationDirectory = staging.resolve(receiptId);
                boolean createdOperationDirectory = false;
                if (!Files.exists(operationDirectory, LinkOption.NOFOLLOW_LINKS)) {
                    Files.createDirectory(operationDirectory);
                    syncDirectory(staging);
                    createdOperationDirectory = true;
                } else {
                    requirePlainDirectory(operationDirectory);
                }
                byte[] receiptIdentity;
                try (WindowsAuthorityJournalNative.Handle receipt =
                             openExactCapacityReceipt(operationDirectory, receiptId, request, null)) {
                    receiptIdentity = receipt.identity();
                } catch (NoSuchFileException missing) {
                    if (!createdOperationDirectory) {
                        throw missing;
                    }
                    Path receiptPath = operationDirectory.resolve("capacity.receipt");
                    byte[] envelope = capacityEnvelope(
                            request.canonicalReceipt(), directoryIdentity(entries)
                    );
                    try (WindowsAuthorityJournalNative.Handle receipt =
                                 createProtectedFile(receiptPath, envelope)) {
                        byte[] createdReceiptIdentity = receipt.identity();
                        byte[] operationDirectoryIdentity =
                                directoryIdentity(operationDirectory);
                        Path witnessPath = operationDirectory.resolve("capacity.witness");
                        try (WindowsAuthorityJournalNative.Handle ignored =
                                     createProtectedFile(
                                             witnessPath,
                                             witnessIdentity -> capacityWitness(
                                                     receiptId,
                                                     createdReceiptIdentity,
                                                     witnessIdentity,
                                                     operationDirectoryIdentity,
                                                     envelope
                                             )
                                     )) {
                            // Keep the receipt exact-open until its self-bound witness is sealed.
                        }
                        receiptIdentity = createdReceiptIdentity;
                    }
                    syncDirectory(operationDirectory);
                }
                faults.inject(FaultPoint.AFTER_CAPACITY_RECEIPT_FORCE,
                        context(request.operationId(), request.expectedHeadGeneration(), -1));
                return new CapacityReceipt(receiptId, request, receiptIdentity);
            } catch (ContainerStorageException failure) {
                throw new IOException("Capacity reservation journal state is invalid", failure);
            }
        }

        @Override
        public MutationResult append(AppendRequest request) throws IOException {
            requireOpen();
            CapacityReceipt capacity = request.capacityReceipt();
            Path operationDirectory = staging.resolve(capacity.providerReceiptId());
            try (WindowsAuthorityJournalNative.Handle ignored = openExactCapacityReceipt(
                    operationDirectory, capacity.providerReceiptId(), capacity.request(),
                    capacity.receiptFileIdentity()
            )) {
                CapacityTarget target = request.target();
                MinimapAuthorityJournal.Entry entry =
                        new MinimapAuthorityJournal(config).decode(request.canonicalEntry());
                if (entry.generation() != target.generation()
                        || slotIndex(entry.generation()) != target.slotIndex()) {
                    return MutationResult.unavailable("Entry is outside its capacity reservation");
                }
                return appendImmutable(
                        operationDirectory,
                        "entry-" + String.format("%020d", entry.generation()),
                        entry.generation(), target.slotIndex(), request.canonicalEntry(),
                        request.canonicalAdoptionReceipt(), FaultPoint.AFTER_ENTRY_LINK
                );
            }
        }

        @Override
        public CapacityReceipt resumeReservation(CapacityRequest request) throws IOException {
            try {
                requireOpen();
                requireCanonicalCapacityRequest(request);
                String receiptId = capacityReceiptId(request);
                Path operationDirectory = staging.resolve(receiptId);
                try (WindowsAuthorityJournalNative.Handle receipt =
                             openExactCapacityReceipt(
                                     operationDirectory, receiptId, request, null
                             )) {
                    return new CapacityReceipt(receiptId, request, receipt.identity());
                }
            } catch (ContainerStorageException failure) {
                throw new IOException("Capacity reservation recovery state is invalid", failure);
            }
        }

        @Override
        public StrictReservationResult reserveStrict(
                StrictReservationRequest request
        ) throws IOException {
            requireOpen();
            return strictReservations.reserve(this, request);
        }

        @Override
        public StrictReservationInspection inspectStrictReservation(
                StrictReservationRequest request
        ) throws IOException {
            requireOpen();
            return strictReservations.inspect(this, request);
        }

        @Override
        public MutationResult appendStrict(StrictAppendRequest request)
                throws IOException {
            requireOpen();
            return strictReservations.append(this, request);
        }

        @Override
        public MutationResult releaseStrict(StrictReservationReceipt receipt)
                throws IOException {
            requireOpen();
            return strictReservations.release(this, receipt);
        }

        @Override
        public MutationResult release(CapacityReceipt receipt) throws IOException {
            requireOpen();
            Path operationDirectory = staging.resolve(receipt.providerReceiptId());
            requirePlainDirectory(operationDirectory);
            Path receiptPath = operationDirectory.resolve("capacity.receipt");
            faults.inject(FaultPoint.BEFORE_RESERVATION_RELEASE,
                    context(receipt.request().operationId(),
                            receipt.request().expectedHeadGeneration(), -1));
            try {
                try (WindowsAuthorityJournalNative.Handle handle = openExactCapacityReceipt(
                        operationDirectory, receipt.providerReceiptId(), receipt.request(),
                        receipt.receiptFileIdentity()
                )) {
                    handle.dispose();
                }
            } catch (NoSuchFileException missing) {
                if (receiptPath.toString().equals(missing.getFile())) {
                    return new MutationResult(MutationStatus.ALREADY_APPLIED, new byte[0], "");
                }
                throw missing;
            }
            syncDirectory(operationDirectory);
            retireObsoleteStaging();
            faults.inject(FaultPoint.AFTER_RESERVATION_RELEASE,
                    context(receipt.request().operationId(),
                            receipt.request().expectedHeadGeneration(), -1));
            return new MutationResult(MutationStatus.APPLIED, new byte[0], "");
        }

        @Override
        public void close() {
            closed = true;
        }

        private MutationResult appendImmutable(
                Path operationDirectory,
                String stem,
                long generation,
                int slotIndex,
                byte[] canonicalEntry,
                byte[] logicalReceipt,
                FaultPoint afterLink
        ) throws IOException {
            Path slot = slotPath(slotIndex);
            Path source = operationDirectory.resolve(stem + ".source");
            Path receipt = operationDirectory.resolve(stem + ".receipt");
            MinimapAuthorityJournal.Entry decoded =
                    new MinimapAuthorityJournal(config).decode(canonicalEntry);
            if (decoded.generation() != generation) {
                return MutationResult.unavailable("Entry generation is invalid");
            }
            if (Files.exists(slot, LinkOption.NOFOLLOW_LINKS)) {
                return classifyExisting(
                        slot, receipt, canonicalEntry, logicalReceipt, generation, slotIndex
                );
            }
            WindowsAuthorityJournalNative.Handle sourceHandle;
            if (!Files.exists(source, LinkOption.NOFOLLOW_LINKS)) {
                sourceHandle = createLinkableFile(source, canonicalEntry);
            } else {
                sourceHandle = openVerifiedLinkSource(
                        source, canonicalEntry, null,
                        MinimapAuthorityJournal.MAX_ENTRY_BYTES
                );
            }
            try (sourceHandle) {
                byte[] sourceIdentity = sourceHandle.identity();
                WindowsAuthorityJournalNative.Handle receiptHandle;
                boolean createdReceipt;
                if (!Files.exists(receipt, LinkOption.NOFOLLOW_LINKS)) {
                    receiptHandle = createProtectedFile(
                            receipt,
                            receiptIdentity -> adoptionEnvelope(
                                    generation,
                                    slotIndex,
                                    decoded.entryDigest(),
                                    sourceIdentity,
                                    receiptIdentity,
                                    logicalReceipt
                            )
                    );
                    createdReceipt = true;
                } else {
                    AdoptionEnvelopeFile receiptFile = readAdoptionEnvelope(receipt);
                    byte[] expectedEnvelope = adoptionEnvelope(
                            generation,
                            slotIndex,
                            decoded.entryDigest(),
                            sourceIdentity,
                            receiptFile.identity(),
                            logicalReceipt
                    );
                    if (!Arrays.equals(receiptFile.bytes(), expectedEnvelope)) {
                        throw new IOException("Existing adoption receipt is not exact");
                    }
                    receiptHandle = openAdoptionEnvelope(
                            receipt, expectedEnvelope, receiptFile.identity()
                    );
                    createdReceipt = false;
                }
                try (receiptHandle) {
                    if (createdReceipt) {
                        syncDirectory(operationDirectory);
                    }
                    faults.inject(FaultPoint.BEFORE_ENTRY_LINK,
                            context(decoded.operationId(), generation, slotIndex));
                    if (!createHardLink(slot, source)) {
                        receiptHandle.close();
                        return classifyExisting(
                                slot, receipt, canonicalEntry, logicalReceipt,
                                generation, slotIndex
                        );
                    }
                    faults.inject(afterLink,
                            context(decoded.operationId(), generation, slotIndex));
                    syncDirectory(entries);
                    hardenFile(source, sourceHandle);
                    faults.inject(FaultPoint.AFTER_ENTRIES_SYNC,
                            context(decoded.operationId(), generation, slotIndex));
                    byte[] slotIdentity;
                    try (WindowsAuthorityJournalNative.Handle linked = openPlain(
                            slot, WinNT.GENERIC_READ,
                            WinNT.FILE_SHARE_READ | WinNT.FILE_SHARE_WRITE
                                    | WinNT.FILE_SHARE_DELETE,
                            false
                    )) {
                        slotIdentity = linked.identity();
                        if (!Arrays.equals(slotIdentity, sourceIdentity)) {
                            return MutationResult.unavailable("Authority link identity changed");
                        }
                    }
                    faults.inject(FaultPoint.BEFORE_SOURCE_RETIRE,
                            context(decoded.operationId(), generation, slotIndex));
                    sourceHandle.dispose();
                    sourceHandle.close();
                    syncDirectory(operationDirectory);
                    faults.inject(FaultPoint.AFTER_SOURCE_RETIRE,
                            context(decoded.operationId(), generation, slotIndex));
                    return new MutationResult(MutationStatus.APPLIED, slotIdentity, "");
                }
            }
        }

        private MutationResult classifyExisting(
                Path slot,
                Path receipt,
                byte[] canonicalEntry,
                byte[] logicalReceipt,
                long generation,
                int slotIndex
        ) throws IOException {
            requirePlainFile(slot);
            if (!Arrays.equals(readBounded(slot, MinimapAuthorityJournal.MAX_ENTRY_BYTES),
                    canonicalEntry)) {
                return MutationResult.unavailable("A foreign authority slot won the reservation");
            }
            AdoptionEnvelopeFile receiptFile;
            try {
                receiptFile = readAdoptionEnvelope(receipt);
            } catch (NoSuchFileException missing) {
                return MutationResult.unavailable("A foreign authority slot won the reservation");
            }
            AdoptionEnvelope envelope = receiptFile.envelope();
            MinimapAuthorityJournal.Entry entry =
                    new MinimapAuthorityJournal(config).decode(canonicalEntry);
            byte[] identity;
            try (WindowsAuthorityJournalNative.Handle exact = openVerifiedLinkSource(
                    slot, canonicalEntry, envelope.sourceIdentity(),
                    MinimapAuthorityJournal.MAX_ENTRY_BYTES
            )) {
                identity = exact.identity();
                if (envelope.generation() != generation
                        || envelope.slotIndex() != slotIndex
                        || !Arrays.equals(identity, envelope.sourceIdentity())) {
                    return MutationResult.unavailable(
                            "Existing authority receipt is not exact"
                    );
                }
                byte[] expectedEnvelope = adoptionEnvelope(
                        generation,
                        slotIndex,
                        entry.entryDigest(),
                        identity,
                        receiptFile.identity(),
                        logicalReceipt
                );
                if (!Arrays.equals(receiptFile.bytes(), expectedEnvelope)) {
                    return MutationResult.unavailable(
                            "Existing authority receipt is not exact"
                    );
                }
                if (!immutable(slot)) {
                    hardenFile(slot, exact);
                    syncDirectory(entries);
                }
            }
            return new MutationResult(MutationStatus.ALREADY_APPLIED, identity, "");
        }

        private WindowsAuthorityJournalNative.Handle openExactCapacityReceipt(
                Path operationDirectory,
                String receiptId,
                CapacityRequest request,
                byte[] expectedReceiptIdentity
        ) throws IOException {
            requirePlainDirectory(operationDirectory);
            requireCanonicalCapacityRequest(request);
            CapacityWitnessFile witnessFile = readCapacityWitness(operationDirectory);
            CapacityWitness witness = witnessFile.witness();
            if (!receiptId.equals(witness.providerReceiptId())
                    || !Arrays.equals(
                    directoryIdentity(operationDirectory), witness.operationDirectoryIdentity()
            )) {
                throw new IOException("Capacity receipt directory witness changed");
            }
            byte[] expectedEnvelope = capacityEnvelope(
                    request.canonicalReceipt(), directoryIdentity(entries)
            );
            if (!Arrays.equals(expectedEnvelope, witness.capacityEnvelope())) {
                throw new IOException("Capacity receipt request or directory witness changed");
            }
            if (expectedReceiptIdentity != null && expectedReceiptIdentity.length > 0
                    && !Arrays.equals(expectedReceiptIdentity, witness.receiptIdentity())) {
                throw new IOException("Capacity receipt identity changed");
            }
            Path receiptPath = operationDirectory.resolve("capacity.receipt");
            return openCapacityEnvelope(
                    receiptPath, expectedEnvelope, witness.receiptIdentity()
            );
        }

        private void retireObsoleteStaging() throws IOException {
            Inspection inspection = inspect();
            if (inspection.detection() != Detection.PRESENT) {
                throw new IOException("Journal is unavailable while retiring staging");
            }
            MinimapAuthorityJournal.Snapshot snapshot;
            try {
                snapshot = new MinimapAuthorityJournal(config).parse(inspection);
            } catch (ContainerStorageException failure) {
                throw new IOException("Journal state is invalid while retiring staging", failure);
            }
            if (snapshot.pending().isPresent()) {
                return;
            }
            long oldestCheckpoint = oldestRetainedCheckpoint(snapshot);
            if (oldestCheckpoint == 0) {
                return;
            }
            Set<Long> visibleGenerations = new HashSet<>();
            for (Slot slot : inspection.slots()) {
                visibleGenerations.add(slot.generation());
            }
            List<StagingDirectory> candidates = new ArrayList<>();
            try (var directories = Files.list(staging)) {
                for (Path directory : directories.sorted().toList()) {
                    StagingDirectory candidate = retirableStagingDirectory(
                            directory, oldestCheckpoint, visibleGenerations
                    );
                    if (candidate != null) {
                        candidates.add(candidate);
                    }
                }
            }
            for (StagingDirectory candidate : candidates) {
                retireExactStagingDirectory(candidate);
            }
        }

        private long oldestRetainedCheckpoint(MinimapAuthorityJournal.Snapshot snapshot) {
            ArrayList<MinimapAuthorityJournal.Entry> checkpoints = new ArrayList<>();
            for (MinimapAuthorityJournal.Entry entry : snapshot.validatedChain()) {
                if (entry.operation() == MinimapAuthorityJournal.Operation.CHECKPOINT
                        && entry.phase()
                        == MinimapAuthorityJournal.Phase.CHECKPOINT_COMPLETE) {
                    checkpoints.add(entry);
                }
            }
            if (checkpoints.size() < config.retainedCheckpoints()) {
                return 0;
            }
            return checkpoints.get(checkpoints.size() - config.retainedCheckpoints())
                    .generation();
        }

        private StagingDirectory retirableStagingDirectory(
                Path directory,
                long oldestCheckpoint,
                Set<Long> visibleGenerations
        ) throws IOException {
            requireReadableAttributes(directory);
            requirePlainDirectory(directory);
            Path witnessPath = directory.resolve("capacity.witness");
            if (!Files.exists(witnessPath, LinkOption.NOFOLLOW_LINKS)) {
                return null;
            }
            byte[] stagingIdentity = directoryIdentity(directory);
            CapacityWitnessFile witnessFile = readCapacityWitness(directory);
            CapacityWitness witness = witnessFile.witness();
            if (!directory.getFileName().toString().equals(witness.providerReceiptId())
                    || !Arrays.equals(stagingIdentity, witness.operationDirectoryIdentity())) {
                throw new IOException("Staging directory witness changed");
            }
            CapacityEnvelope envelope = parseCapacityEnvelope(witness.capacityEnvelope());
            if (!Arrays.equals(envelope.entriesIdentity(), directoryIdentity(entries))
                    || !witness.providerReceiptId().endsWith("-"
                    + AuthorityJournalCodec.digestHex(envelope.logicalReceipt())
                    .substring(0, 16))) {
                throw new IOException("Staging capacity witness is not canonical");
            }

            ArrayList<StagingChild> children = new ArrayList<>();
            children.add(new StagingChild(
                    witnessPath, witnessFile.bytes(), witnessFile.identity(), false
            ));
            Map<String, AdoptionEnvelope> adoptionReceipts = new HashMap<>();
            Map<String, StagingChild> sources = new HashMap<>();
            try (var files = Files.list(directory)) {
                for (Path child : files.sorted().toList()) {
                    String name = child.getFileName().toString();
                    if (name.equals("capacity.witness")) {
                        continue;
                    }
                    if (name.equals("capacity.receipt")) {
                        children.add(readCapacityEnvelopeChild(
                                child, witness.capacityEnvelope(), witness.receiptIdentity()
                        ));
                        continue;
                    }
                    if (name.endsWith(".receipt")) {
                        String stem = name.substring(0, name.length() - ".receipt".length());
                        if (stem.isEmpty()) {
                            return null;
                        }
                        AdoptionEnvelopeFile receiptFile = readAdoptionEnvelope(child);
                        StagingChild childWitness = new StagingChild(
                                child, receiptFile.bytes(), receiptFile.identity(), false
                        );
                        AdoptionEnvelope receipt = receiptFile.envelope();
                        if (adoptionReceipts.putIfAbsent(stem, receipt) != null) {
                            throw new IOException("Duplicate adoption receipt in staging");
                        }
                        children.add(childWitness);
                        continue;
                    }
                    if (name.endsWith(".source")) {
                        String stem = name.substring(0, name.length() - ".source".length());
                        if (stem.isEmpty()) {
                            return null;
                        }
                        sources.put(stem, new StagingChild(child, readBounded(
                                child, MinimapAuthorityJournal.MAX_ENTRY_BYTES
                        ), new byte[0], true));
                        continue;
                    }
                    return null;
                }
            }
            if (adoptionReceipts.isEmpty()) {
                return null;
            }
            for (Map.Entry<String, StagingChild> source : sources.entrySet()) {
                AdoptionEnvelope receipt = adoptionReceipts.get(source.getKey());
                if (receipt == null) {
                    return null;
                }
                StagingChild sourceWitness = readLinkSourceChild(
                        source.getValue().path(), source.getValue().bytes(), receipt.sourceIdentity()
                );
                children.add(sourceWitness);
            }
            for (AdoptionEnvelope receipt : adoptionReceipts.values()) {
                // A visible ring slot still needs its reciprocal receipt even when a
                // checkpoint no longer needs that generation for state recovery.
                if (visibleGenerations.contains(receipt.generation())
                        || !checkpointProvesStagingGenerationObsolete(
                        receipt.generation(), oldestCheckpoint
                )) {
                    return null;
                }
            }
            return new StagingDirectory(directory, stagingIdentity, children);
        }

        private boolean checkpointProvesStagingGenerationObsolete(
                long generation,
                long oldestCheckpoint
        ) {
            try {
                return Math.addExact(generation, config.recoveryWindow()) < oldestCheckpoint;
            } catch (ArithmeticException overflow) {
                return false;
            }
        }

        private void retireExactStagingDirectory(StagingDirectory directory)
                throws IOException {
            Set<String> expectedChildren = new HashSet<>();
            for (StagingChild child : directory.children()) {
                expectedChildren.add(child.path().getFileName().toString());
            }
            try (WindowsAuthorityJournalNative.Handle exactDirectory =
                         openDirectoryForRetirement(directory.path())) {
                if (!Arrays.equals(exactDirectory.identity(), directory.identity())) {
                    throw new IOException("Staging directory identity changed");
                }
                requireExactChildren(directory.path(), expectedChildren);
                for (StagingChild child : directory.children()) {
                    try (WindowsAuthorityJournalNative.Handle exactChild = child.linkSource()
                            ? openVerifiedLinkSource(
                            child.path(), child.bytes(), child.identity(),
                            MinimapAuthorityJournal.MAX_ENTRY_BYTES
                    ) : openVerifiedWitness(
                            child.path(), child.bytes(), child.identity(), child.bytes().length
                    )) {
                        exactChild.dispose();
                    }
                }
                requireExactChildren(directory.path(), Set.of());
                exactDirectory.dispose();
            }
            syncDirectory(staging);
        }

        private StagingChild readCapacityEnvelopeChild(
                Path path,
                byte[] expectedBytes,
                byte[] expectedIdentity
        ) throws IOException {
            try (WindowsAuthorityJournalNative.Handle handle = openCapacityEnvelope(
                    path, expectedBytes, expectedIdentity
            )) {
                return new StagingChild(path, expectedBytes, handle.identity(), false);
            }
        }

        private StagingChild readLinkSourceChild(
                Path path,
                byte[] bytes,
                byte[] expectedIdentity
        ) throws IOException {
            try (WindowsAuthorityJournalNative.Handle handle = openVerifiedLinkSource(
                    path, bytes, expectedIdentity, MinimapAuthorityJournal.MAX_ENTRY_BYTES
            )) {
                return new StagingChild(path, bytes, handle.identity(), true);
            }
        }

        private void retireExpectedObsolete(CapacityTarget target, Path slot)
                throws IOException {
            faults.inject(FaultPoint.BEFORE_OBSOLETE_RETIRE,
                    context("capacity-retire", target.obsoleteGeneration(), target.slotIndex()));
            byte[] bytes = readBounded(slot, MinimapAuthorityJournal.MAX_ENTRY_BYTES);
            MinimapAuthorityJournal.Entry old = new MinimapAuthorityJournal(config).decode(bytes);
            if (old.generation() != target.obsoleteGeneration()
                    || !Arrays.equals(
                    AuthorityJournalCodec.digestBytes(old.entryDigest()),
                    target.obsoleteDigest()
            )) {
                throw new IOException("Obsolete authority witness does not match");
            }
            Inspection inspection = inspect();
            long laterCheckpoints = inspection.slots().stream()
                    .map(Slot::canonicalEntry)
                    .map(bytesValue -> new MinimapAuthorityJournal(config).decode(bytesValue))
                    .filter(entry -> entry.operation()
                            == MinimapAuthorityJournal.Operation.CHECKPOINT)
                    .filter(entry -> entry.phase()
                            == MinimapAuthorityJournal.Phase.CHECKPOINT_COMPLETE)
                    .filter(entry -> entry.generation() > old.generation())
                    .count();
            MinimapAuthorityJournal.Snapshot snapshot =
                    new MinimapAuthorityJournal(config).parse(inspection);
            if (laterCheckpoints < config.retainedCheckpoints()
                    || snapshot.headGeneration() - old.generation()
                    <= config.recoveryWindow()) {
                throw new IOException("Obsolete authority slot is still within retention");
            }
            try (WindowsAuthorityJournalNative.Handle handle = openVerifiedWitness(
                    slot, bytes, target.obsoleteFileIdentity(),
                    MinimapAuthorityJournal.MAX_ENTRY_BYTES
            )) {
                handle.dispose();
            }
            syncDirectory(entries);
            faults.inject(FaultPoint.AFTER_OBSOLETE_RETIRE,
                    context("capacity-retire", target.obsoleteGeneration(), target.slotIndex()));
        }

        private void requireRequestHead(CapacityRequest request) throws IOException {
            Inspection inspection = inspect();
            if (inspection.detection() != Detection.PRESENT) {
                throw new IOException("Journal is unavailable for capacity reservation");
            }
            MinimapAuthorityJournal.Snapshot snapshot =
                    new MinimapAuthorityJournal(config).parse(inspection);
            if (!snapshot.journalInstance().equals(request.journalInstance())
                    || snapshot.headGeneration() != request.expectedHeadGeneration()
                    || !Arrays.equals(
                    AuthorityJournalCodec.digestBytes(snapshot.headDigest()),
                    request.expectedHeadDigest()
            ) || snapshot.pending().isPresent()) {
                throw new IOException("Capacity reservation head witness is stale");
            }
        }

        private Path slotPath(int index) {
            return entries.resolve(String.format("slot-%03d.journal", index));
        }

        private int slotIndex(long generation) {
            return (int) ((generation - 1) % config.slotCount());
        }

        private void requireOpen() throws IOException {
            if (closed) {
                throw new IOException("Journal handle is closed");
            }
        }

        private FaultContext context(String operationId, long generation, int slotIndex) {
            return new FaultContext(journal, operationId, generation, slotIndex);
        }

        private record StagingChild(
                Path path,
                byte[] bytes,
                byte[] identity,
                boolean linkSource
        ) {
            private StagingChild {
                path = Objects.requireNonNull(path, "path");
                bytes = Objects.requireNonNull(bytes, "bytes").clone();
                identity = Objects.requireNonNull(identity, "identity").clone();
            }

            @Override
            public byte[] bytes() {
                return bytes.clone();
            }

            @Override
            public byte[] identity() {
                return identity.clone();
            }
        }

        private record StagingDirectory(
                Path path,
                byte[] identity,
                List<StagingChild> children
        ) {
            private StagingDirectory {
                path = Objects.requireNonNull(path, "path");
                identity = Objects.requireNonNull(identity, "identity").clone();
                children = List.copyOf(Objects.requireNonNull(children, "children"));
            }

            @Override
            public byte[] identity() {
                return identity.clone();
            }
        }
    }

    private static String capacityReceiptId(CapacityRequest request) throws IOException {
        requireCanonicalCapacityRequest(request);
        return "reservation-" + request.operationId() + "-"
                + AuthorityJournalCodec.digestHex(request.canonicalReceipt()).substring(0, 16);
    }

    private static void requireCanonicalCapacityRequest(CapacityRequest request)
            throws IOException {
        if (request == null) {
            throw new IOException("Capacity request is missing");
        }
        try {
            byte[] actual = request.canonicalReceipt();
            for (MinimapAuthorityJournal.PreflightDecision decision
                    : MinimapAuthorityJournal.PreflightDecision.values()) {
                byte[] expected = AuthorityJournalCodec.encodeCapacityReceipt(
                        decision,
                        request.journalInstance(),
                        request.operationId(),
                        request.expectedHeadGeneration(),
                        request.expectedHeadDigest(),
                        request.targets()
                );
                if (Arrays.equals(expected, actual)) {
                    return;
                }
            }
        } catch (RuntimeException failure) {
            throw new IOException("Capacity request is not canonical", failure);
        }
        throw new IOException("Capacity request receipt is not canonical");
    }

}
