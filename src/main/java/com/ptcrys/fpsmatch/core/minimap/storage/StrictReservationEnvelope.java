package com.ptcrys.fpsmatch.core.minimap.storage;

import java.util.List;
import java.util.Objects;

record StrictReservationEnvelope(
        String version,
        AuthorityJournalProvider.StrictReservationKind kind,
        byte[] capacityCore,
        byte[] canonicalAttempt,
        List<AuthorityJournalProvider.StrictManifestRow> manifest
) {
    static final String VERSION = "STRICT_RESERVATION_V1";

    StrictReservationEnvelope {
        version = Objects.requireNonNull(version, "version");
        Objects.requireNonNull(kind, "kind");
        capacityCore = Objects.requireNonNull(capacityCore, "capacityCore").clone();
        canonicalAttempt =
                Objects.requireNonNull(canonicalAttempt, "canonicalAttempt").clone();
        manifest = List.copyOf(Objects.requireNonNull(manifest, "manifest"));
    }

    static StrictReservationEnvelope from(
            AuthorityJournalProvider.StrictReservationRequest request
    ) {
        Objects.requireNonNull(request, "request");
        return new StrictReservationEnvelope(
                VERSION,
                request.kind(),
                request.capacityRequest().canonicalReceipt(),
                request.canonicalAttempt(),
                request.manifest()
        );
    }

    @Override
    public byte[] capacityCore() {
        return capacityCore.clone();
    }

    @Override
    public byte[] canonicalAttempt() {
        return canonicalAttempt.clone();
    }
}
