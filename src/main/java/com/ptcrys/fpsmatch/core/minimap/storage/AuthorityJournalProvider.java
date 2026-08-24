package com.ptcrys.fpsmatch.core.minimap.storage;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Capability boundary for strict immutable authority journals. Implementations must
 * fail closed: none of these operations may fall back to path replacement or deletion.
 */
public interface AuthorityJournalProvider {
    enum Support {
        SUPPORTED,
        UNSUPPORTED
    }

    enum Detection {
        ABSENT,
        PRESENT,
        UNAVAILABLE
    }

    enum MutationStatus {
        APPLIED,
        ALREADY_APPLIED,
        UNAVAILABLE
    }

    enum StrictReservationKind {
        PUBLISH,
        REBIND,
        RESET,
        CHECKPOINT
    }

    enum StrictReservationDisposition {
        PRESENT,
        NEVER_RESERVED,
        RELEASED,
        UNAVAILABLE
    }

    enum StrictDiscoveryStatus {
        FOUND,
        ABSENT,
        UNAVAILABLE
    }

    enum FaultPoint {
        BEFORE_ACTIVATION,
        AFTER_ACTIVATION_LINK,
        AFTER_CAPACITY_RECEIPT_FORCE,
        BEFORE_OBSOLETE_RETIRE,
        AFTER_OBSOLETE_RETIRE,
        BEFORE_ENTRY_LINK,
        AFTER_ENTRY_LINK,
        AFTER_ENTRIES_SYNC,
        BEFORE_SOURCE_RETIRE,
        AFTER_SOURCE_RETIRE,
        BEFORE_RESERVATION_RELEASE,
        AFTER_RESERVATION_RELEASE
    }

    @FunctionalInterface
    interface FaultInjector {
        void inject(FaultPoint point, FaultContext context) throws IOException;

        static FaultInjector none() {
            return (point, context) -> { };
        }
    }

    record FaultContext(
            Path journalDirectory,
            String operationId,
            long generation,
            int slotIndex
    ) {
        public FaultContext {
            Objects.requireNonNull(journalDirectory, "journalDirectory");
            Objects.requireNonNull(operationId, "operationId");
        }
    }

    record Availability(Support support, String detail) {
        public Availability {
            Objects.requireNonNull(support, "support");
            detail = detail == null ? "" : detail;
        }

        public boolean supported() {
            return support == Support.SUPPORTED;
        }
    }

    record Slot(
            long generation,
            int slotIndex,
            byte[] canonicalEntry,
            byte[] fileIdentity,
            boolean receiptVerified,
            boolean immutable
    ) {
        public Slot {
            if (generation <= 0 || slotIndex < 0) {
                throw new IllegalArgumentException("Journal slot identity is invalid");
            }
            canonicalEntry = copy(canonicalEntry, "canonicalEntry");
            fileIdentity = copy(fileIdentity, "fileIdentity");
        }

        @Override
        public byte[] canonicalEntry() {
            return canonicalEntry.clone();
        }

        @Override
        public byte[] fileIdentity() {
            return fileIdentity.clone();
        }
    }

    record Inspection(
            Detection detection,
            List<Slot> slots,
            int stagingObjectCount,
            String detail
    ) {
        public Inspection {
            Objects.requireNonNull(detection, "detection");
            slots = List.copyOf(Objects.requireNonNull(slots, "slots"));
            if (stagingObjectCount < 0) {
                throw new IllegalArgumentException("Staging object count is negative");
            }
            detail = detail == null ? "" : detail;
        }

        public static Inspection absent() {
            return new Inspection(Detection.ABSENT, List.of(), 0, "");
        }

        public static Inspection unavailable(String detail) {
            return new Inspection(Detection.UNAVAILABLE, List.of(), 0, detail);
        }
    }

    record CapacityTarget(
            long generation,
            int slotIndex,
            long obsoleteGeneration,
            byte[] obsoleteDigest,
            byte[] obsoleteFileIdentity
    ) {
        public CapacityTarget {
            if (generation <= 0 || slotIndex < 0 || obsoleteGeneration < 0) {
                throw new IllegalArgumentException("Capacity target is invalid");
            }
            obsoleteDigest = copy(obsoleteDigest, "obsoleteDigest");
            obsoleteFileIdentity = copy(obsoleteFileIdentity, "obsoleteFileIdentity");
            if ((obsoleteGeneration == 0) != (obsoleteDigest.length == 0
                    && obsoleteFileIdentity.length == 0)) {
                throw new IllegalArgumentException("Obsolete slot witness is incomplete");
            }
        }

        public static CapacityTarget absent(long generation, int slotIndex) {
            return new CapacityTarget(generation, slotIndex, 0, new byte[0], new byte[0]);
        }

        public boolean expectsObsoleteSlot() {
            return obsoleteGeneration > 0;
        }

        @Override
        public byte[] obsoleteDigest() {
            return obsoleteDigest.clone();
        }

        @Override
        public byte[] obsoleteFileIdentity() {
            return obsoleteFileIdentity.clone();
        }
    }

    record ActivationRequest(
            String journalInstance,
            byte[] canonicalEntry,
            byte[] canonicalReceipt
    ) {
        public ActivationRequest {
            journalInstance = requireToken(journalInstance, "journalInstance");
            canonicalEntry = copy(canonicalEntry, "canonicalEntry");
            canonicalReceipt = copy(canonicalReceipt, "canonicalReceipt");
        }

        @Override
        public byte[] canonicalEntry() {
            return canonicalEntry.clone();
        }

        @Override
        public byte[] canonicalReceipt() {
            return canonicalReceipt.clone();
        }
    }

    record CapacityRequest(
            String journalInstance,
            String operationId,
            long expectedHeadGeneration,
            byte[] expectedHeadDigest,
            List<CapacityTarget> targets,
            byte[] canonicalReceipt
    ) {
        public CapacityRequest {
            journalInstance = requireToken(journalInstance, "journalInstance");
            operationId = requireToken(operationId, "operationId");
            if (expectedHeadGeneration < 0) {
                throw new IllegalArgumentException("Expected head generation is negative");
            }
            expectedHeadDigest = copy(expectedHeadDigest, "expectedHeadDigest");
            targets = List.copyOf(Objects.requireNonNull(targets, "targets"));
            canonicalReceipt = copy(canonicalReceipt, "canonicalReceipt");
            if (targets.isEmpty()) {
                throw new IllegalArgumentException("Capacity targets are empty");
            }
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

    record CapacityReceipt(
            String providerReceiptId,
            CapacityRequest request,
            byte[] receiptFileIdentity
    ) {
        public CapacityReceipt {
            providerReceiptId = requireToken(providerReceiptId, "providerReceiptId");
            Objects.requireNonNull(request, "request");
            receiptFileIdentity = copy(receiptFileIdentity, "receiptFileIdentity");
        }

        @Override
        public byte[] receiptFileIdentity() {
            return receiptFileIdentity.clone();
        }
    }

    record StrictManifestRow(
            int targetOrdinal,
            long generation,
            int slotIndex,
            MinimapAuthorityJournal.Operation operation,
            MinimapAuthorityJournal.Phase phase,
            byte[] canonicalEntryDigest
    ) {
        public StrictManifestRow {
            if (targetOrdinal < 0 || generation <= 0 || slotIndex < 0) {
                throw new IllegalArgumentException("Strict manifest target is invalid");
            }
            Objects.requireNonNull(operation, "operation");
            Objects.requireNonNull(phase, "phase");
            canonicalEntryDigest = copy(
                    canonicalEntryDigest, "canonicalEntryDigest"
            );
        }

        @Override
        public byte[] canonicalEntryDigest() {
            return canonicalEntryDigest.clone();
        }
    }

    record StrictReservationRequest(
            StrictReservationKind kind,
            CapacityRequest capacityRequest,
            byte[] canonicalAttempt,
            List<StrictManifestRow> manifest
    ) {
        public StrictReservationRequest {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(capacityRequest, "capacityRequest");
            canonicalAttempt = copy(canonicalAttempt, "canonicalAttempt");
            manifest = List.copyOf(Objects.requireNonNull(manifest, "manifest"));
            if (canonicalAttempt.length == 0 || manifest.isEmpty()) {
                throw new IllegalArgumentException("Strict reservation payload is empty");
            }
            for (StrictManifestRow row : manifest) {
                if (row.targetOrdinal() >= capacityRequest.targets().size()) {
                    throw new IllegalArgumentException(
                            "Strict manifest target is outside the capacity request"
                    );
                }
                CapacityTarget target =
                        capacityRequest.targets().get(row.targetOrdinal());
                if (row.generation() != target.generation()
                        || row.slotIndex() != target.slotIndex()) {
                    throw new IllegalArgumentException(
                            "Strict manifest target disagrees with capacity"
                    );
                }
            }
        }

        @Override
        public byte[] canonicalAttempt() {
            return canonicalAttempt.clone();
        }
    }

    record StrictReservationReceipt(
            String providerReceiptId,
            StrictReservationRequest request,
            byte[] carrierFileIdentity,
            byte[] wrapperDigest
    ) {
        public StrictReservationReceipt {
            providerReceiptId = requireToken(providerReceiptId, "providerReceiptId");
            Objects.requireNonNull(request, "request");
            carrierFileIdentity = copy(carrierFileIdentity, "carrierFileIdentity");
            wrapperDigest = copy(wrapperDigest, "wrapperDigest");
            if (carrierFileIdentity.length == 0 || wrapperDigest.length == 0) {
                throw new IllegalArgumentException("Strict reservation identity is empty");
            }
        }

        @Override
        public byte[] carrierFileIdentity() {
            return carrierFileIdentity.clone();
        }

        @Override
        public byte[] wrapperDigest() {
            return wrapperDigest.clone();
        }
    }

    record StrictReservationResult(
            MutationStatus status,
            Optional<StrictReservationReceipt> receipt,
            String detail
    ) {
        public StrictReservationResult {
            Objects.requireNonNull(status, "status");
            receipt = Objects.requireNonNull(receipt, "receipt");
            detail = detail == null ? "" : detail;
            if ((status != MutationStatus.UNAVAILABLE) != receipt.isPresent()) {
                throw new IllegalArgumentException(
                        "Strict reservation result receipt is inconsistent"
                );
            }
        }

        public static StrictReservationResult unavailable(String detail) {
            return new StrictReservationResult(
                    MutationStatus.UNAVAILABLE, Optional.empty(), detail
            );
        }
    }

    record StrictReservationInspection(
            StrictReservationDisposition disposition,
            Optional<StrictReservationReceipt> receipt,
            String detail
    ) {
        public StrictReservationInspection {
            Objects.requireNonNull(disposition, "disposition");
            receipt = Objects.requireNonNull(receipt, "receipt");
            detail = detail == null ? "" : detail;
            if ((disposition == StrictReservationDisposition.PRESENT)
                    != receipt.isPresent()) {
                throw new IllegalArgumentException(
                        "Strict reservation inspection receipt is inconsistent"
                );
            }
        }

        public static StrictReservationInspection unavailable(String detail) {
            return new StrictReservationInspection(
                    StrictReservationDisposition.UNAVAILABLE,
                    Optional.empty(),
                    detail
            );
        }
    }

    record StrictDiscoveryRequest(
            String journalInstance,
            long expectedHeadGeneration,
            byte[] expectedHeadDigest
    ) {
        public StrictDiscoveryRequest {
            journalInstance = requireToken(journalInstance, "journalInstance");
            if (expectedHeadGeneration < 0) {
                throw new IllegalArgumentException(
                        "Expected head generation is negative"
                );
            }
            expectedHeadDigest = copy(expectedHeadDigest, "expectedHeadDigest");
        }

        @Override
        public byte[] expectedHeadDigest() {
            return expectedHeadDigest.clone();
        }
    }

    record StrictDiscoveryResult(
            StrictDiscoveryStatus status,
            Optional<StrictReservationRequest> request,
            String detail
    ) {
        public StrictDiscoveryResult {
            Objects.requireNonNull(status, "status");
            request = Objects.requireNonNull(request, "request");
            detail = detail == null ? "" : detail;
            if ((status == StrictDiscoveryStatus.FOUND) != request.isPresent()) {
                throw new IllegalArgumentException(
                        "Strict discovery request is inconsistent"
                );
            }
        }

        public static StrictDiscoveryResult unavailable(String detail) {
            return new StrictDiscoveryResult(
                    StrictDiscoveryStatus.UNAVAILABLE, Optional.empty(), detail
            );
        }
    }

    record StrictAppendRequest(
            StrictReservationReceipt reservationReceipt,
            int manifestOrdinal,
            byte[] canonicalEntry,
            byte[] canonicalAdoptionReceipt
    ) {
        public StrictAppendRequest {
            Objects.requireNonNull(reservationReceipt, "reservationReceipt");
            if (manifestOrdinal < 0
                    || manifestOrdinal >= reservationReceipt.request().manifest().size()) {
                throw new IllegalArgumentException(
                        "Strict append manifest ordinal is invalid"
                );
            }
            canonicalEntry = copy(canonicalEntry, "canonicalEntry");
            canonicalAdoptionReceipt = copy(
                    canonicalAdoptionReceipt, "canonicalAdoptionReceipt"
            );
        }

        public StrictManifestRow manifestRow() {
            return reservationReceipt.request().manifest().get(manifestOrdinal);
        }

        @Override
        public byte[] canonicalEntry() {
            return canonicalEntry.clone();
        }

        @Override
        public byte[] canonicalAdoptionReceipt() {
            return canonicalAdoptionReceipt.clone();
        }
    }

    record AppendRequest(
            CapacityReceipt capacityReceipt,
            int targetOrdinal,
            byte[] canonicalEntry,
            byte[] canonicalAdoptionReceipt
    ) {
        public AppendRequest {
            Objects.requireNonNull(capacityReceipt, "capacityReceipt");
            if (targetOrdinal < 0
                    || targetOrdinal >= capacityReceipt.request().targets().size()) {
                throw new IllegalArgumentException("Target ordinal is outside the reservation");
            }
            canonicalEntry = copy(canonicalEntry, "canonicalEntry");
            canonicalAdoptionReceipt = copy(
                    canonicalAdoptionReceipt, "canonicalAdoptionReceipt"
            );
        }

        public CapacityTarget target() {
            return capacityReceipt.request().targets().get(targetOrdinal);
        }

        @Override
        public byte[] canonicalEntry() {
            return canonicalEntry.clone();
        }

        @Override
        public byte[] canonicalAdoptionReceipt() {
            return canonicalAdoptionReceipt.clone();
        }
    }

    record MutationResult(
            MutationStatus status,
            byte[] fileIdentity,
            String detail
    ) {
        public MutationResult {
            Objects.requireNonNull(status, "status");
            fileIdentity = copy(fileIdentity, "fileIdentity");
            detail = detail == null ? "" : detail;
        }

        public static MutationResult unavailable(String detail) {
            return new MutationResult(MutationStatus.UNAVAILABLE, new byte[0], detail);
        }

        @Override
        public byte[] fileIdentity() {
            return fileIdentity.clone();
        }
    }

    interface JournalHandle extends AutoCloseable {
        Path journalDirectory();

        Inspection inspect() throws IOException;

        MutationResult activate(ActivationRequest request) throws IOException;

        CapacityReceipt reserve(CapacityRequest request) throws IOException;

        default CapacityReceipt resumeReservation(CapacityRequest request)
                throws IOException {
            Objects.requireNonNull(request, "request");
            throw new IOException("Capacity reservation recovery is unavailable");
        }

        default StrictReservationResult reserveStrict(
                StrictReservationRequest request
        ) throws IOException {
            Objects.requireNonNull(request, "request");
            return StrictReservationResult.unavailable(
                    "Strict reservation is unsupported"
            );
        }

        default StrictReservationInspection inspectStrictReservation(
                StrictReservationRequest request
        ) throws IOException {
            Objects.requireNonNull(request, "request");
            return StrictReservationInspection.unavailable(
                    "Strict reservation inspection is unsupported"
            );
        }

        default StrictDiscoveryResult discoverStrictReservation(
                StrictDiscoveryRequest request
        ) throws IOException {
            Objects.requireNonNull(request, "request");
            return StrictDiscoveryResult.unavailable(
                    "Strict reservation discovery is unsupported"
            );
        }

        MutationResult append(AppendRequest request) throws IOException;

        default MutationResult appendStrict(StrictAppendRequest request)
                throws IOException {
            Objects.requireNonNull(request, "request");
            return MutationResult.unavailable("Strict append is unsupported");
        }

        MutationResult release(CapacityReceipt receipt) throws IOException;

        default MutationResult releaseStrict(StrictReservationReceipt receipt)
                throws IOException {
            Objects.requireNonNull(receipt, "receipt");
            return MutationResult.unavailable("Strict release is unsupported");
        }

        @Override
        void close() throws IOException;
    }

    Availability availability(Path journalDirectory);

    default RepositorySessionCapability repositorySessionCapability() {
        return RepositorySessionCapability.unsupported();
    }

    default Availability strictAvailability(Path mapDirectory) {
        Objects.requireNonNull(mapDirectory, "mapDirectory");
        return new Availability(
                Support.UNSUPPORTED,
                "Strict repository sessions are unsupported"
        );
    }

    JournalHandle open(
            Path journalDirectory,
            MinimapAuthorityJournal.Config config
    ) throws IOException;

    static AuthorityJournalProvider unsupported(String detail) {
        Availability availability = new Availability(Support.UNSUPPORTED, detail);
        return new AuthorityJournalProvider() {
            @Override
            public Availability availability(Path journalDirectory) {
                Objects.requireNonNull(journalDirectory, "journalDirectory");
                return availability;
            }

            @Override
            public JournalHandle open(
                    Path journalDirectory,
                    MinimapAuthorityJournal.Config config
            ) {
                Objects.requireNonNull(journalDirectory, "journalDirectory");
                Objects.requireNonNull(config, "config").validate();
                throw new UnsupportedOperationException(availability.detail());
            }
        };
    }

    private static byte[] copy(byte[] value, String name) {
        return Objects.requireNonNull(value, name).clone();
    }

    private static String requireToken(String value, String name) {
        Objects.requireNonNull(value, name);
        if (!value.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")) {
            throw new IllegalArgumentException(name + " is not a bounded safe token");
        }
        return value;
    }
}
