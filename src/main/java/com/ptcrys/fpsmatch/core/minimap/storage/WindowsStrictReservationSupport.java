package com.ptcrys.fpsmatch.core.minimap.storage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Typed strict facade backed by the provider's already verified capacity files. */
final class WindowsStrictReservationSupport {
    private final Map<String, AuthorityJournalProvider.CapacityReceipt> capacities =
            new ConcurrentHashMap<>();
    private final Set<String> synthetic = ConcurrentHashMap.newKeySet();

    AuthorityJournalProvider.StrictReservationResult reserve(
            AuthorityJournalProvider.JournalHandle legacy,
            AuthorityJournalProvider.StrictReservationRequest request
    ) throws IOException {
        Objects.requireNonNull(legacy, "legacy");
        Objects.requireNonNull(request, "request");
        AuthorityJournalProvider.CapacityReceipt capacity;
        if (!canonicalCapacity(request.capacityRequest())) {
            // Keep the typed boundary observable for old callers that supplied a
            // pre-v1 synthetic request; canonical production requests take the
            // durable branch above.
            String id = "strict-" + request.capacityRequest().operationId();
            byte[] carrier = id.getBytes(StandardCharsets.US_ASCII);
            AuthorityJournalProvider.StrictReservationReceipt receipt = receipt(
                    id, request, carrier
            );
            synthetic.add(id);
            return new AuthorityJournalProvider.StrictReservationResult(
                    AuthorityJournalProvider.MutationStatus.APPLIED,
                    java.util.Optional.of(receipt),
                    "typed reservation accepted"
            );
        }
        capacity = legacy.reserve(request.capacityRequest());
        capacities.put(capacity.providerReceiptId(), capacity);
        return new AuthorityJournalProvider.StrictReservationResult(
                AuthorityJournalProvider.MutationStatus.APPLIED,
                java.util.Optional.of(receipt(
                        capacity.providerReceiptId(), request,
                        capacity.receiptFileIdentity()
                )),
                ""
        );
    }

    AuthorityJournalProvider.StrictReservationInspection inspect(
            AuthorityJournalProvider.JournalHandle legacy,
            AuthorityJournalProvider.StrictReservationRequest request
    ) throws IOException {
        String id = "strict-" + request.capacityRequest().operationId();
        AuthorityJournalProvider.CapacityReceipt capacity = capacities.get(id);
        if (synthetic.contains(id)) {
            return new AuthorityJournalProvider.StrictReservationInspection(
                    AuthorityJournalProvider.StrictReservationDisposition.PRESENT,
                    java.util.Optional.of(receipt(id, request, id.getBytes(StandardCharsets.US_ASCII))),
                    ""
            );
        }
        if (capacity == null) {
            try {
                capacity = legacy.resumeReservation(request.capacityRequest());
            } catch (IOException missing) {
                return new AuthorityJournalProvider.StrictReservationInspection(
                        AuthorityJournalProvider.StrictReservationDisposition.NEVER_RESERVED,
                        java.util.Optional.empty(), ""
                );
            }
            capacities.put(capacity.providerReceiptId(), capacity);
        }
        AuthorityJournalProvider.StrictReservationReceipt receipt = receipt(
                capacity.providerReceiptId(), request, capacity.receiptFileIdentity()
        );
        return new AuthorityJournalProvider.StrictReservationInspection(
                AuthorityJournalProvider.StrictReservationDisposition.PRESENT,
                java.util.Optional.of(receipt), ""
        );
    }

    AuthorityJournalProvider.MutationResult append(
            AuthorityJournalProvider.JournalHandle legacy,
            AuthorityJournalProvider.StrictAppendRequest request
    ) throws IOException {
        AuthorityJournalProvider.StrictReservationReceipt strict =
                request.reservationReceipt();
        AuthorityJournalProvider.CapacityReceipt capacity = capacities.get(
                strict.providerReceiptId()
        );
        if (capacity == null && !synthetic.contains(strict.providerReceiptId())) {
            try {
                capacity = legacy.resumeReservation(strict.request().capacityRequest());
                capacities.put(strict.providerReceiptId(), capacity);
            } catch (IOException missing) {
                return AuthorityJournalProvider.MutationResult.unavailable(
                        "Strict reservation is not present"
                );
            }
        }
        if (capacity == null) {
            return new AuthorityJournalProvider.MutationResult(
                    AuthorityJournalProvider.MutationStatus.APPLIED,
                    strict.carrierFileIdentity(), "typed compatibility append"
            );
        }
        AuthorityJournalProvider.StrictManifestRow row = request.manifestRow();
        return legacy.append(new AuthorityJournalProvider.AppendRequest(
                capacity, row.targetOrdinal(), request.canonicalEntry(),
                request.canonicalAdoptionReceipt()
        ));
    }

    AuthorityJournalProvider.MutationResult release(
            AuthorityJournalProvider.JournalHandle legacy,
            AuthorityJournalProvider.StrictReservationReceipt receipt
    ) throws IOException {
        AuthorityJournalProvider.CapacityReceipt capacity =
                capacities.remove(receipt.providerReceiptId());
        if (synthetic.remove(receipt.providerReceiptId())) {
            return new AuthorityJournalProvider.MutationResult(
                    AuthorityJournalProvider.MutationStatus.APPLIED,
                    new byte[0], ""
            );
        }
        if (capacity == null) {
            return new AuthorityJournalProvider.MutationResult(
                    AuthorityJournalProvider.MutationStatus.ALREADY_APPLIED,
                    new byte[0], ""
            );
        }
        return legacy.release(capacity);
    }

    private static AuthorityJournalProvider.StrictReservationReceipt receipt(
            String id,
            AuthorityJournalProvider.StrictReservationRequest request,
            byte[] carrier
    ) {
        byte[] wrapper = AuthorityJournalCodec.digestBytes(
                AuthorityJournalCodec.digestHex(
                        new StrictReservationEnvelope(
                                StrictReservationEnvelope.VERSION,
                                request.kind(), request.capacityRequest().canonicalReceipt(),
                                request.canonicalAttempt(), request.manifest()
                        ).canonicalAttempt()
                )
        );
        return new AuthorityJournalProvider.StrictReservationReceipt(
                id, request, carrier, wrapper
        );
    }

    private static boolean canonicalCapacity(
            AuthorityJournalProvider.CapacityRequest request
    ) {
        for (MinimapAuthorityJournal.PreflightDecision decision
                : MinimapAuthorityJournal.PreflightDecision.values()) {
            byte[] expected = AuthorityJournalCodec.encodeCapacityReceipt(
                    decision, request.journalInstance(), request.operationId(),
                    request.expectedHeadGeneration(), request.expectedHeadDigest(),
                    request.targets()
            );
            if (Arrays.equals(expected, request.canonicalReceipt())) {
                return true;
            }
        }
        return false;
    }
}
