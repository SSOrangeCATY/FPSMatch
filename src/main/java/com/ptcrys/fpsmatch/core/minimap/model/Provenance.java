package com.ptcrys.fpsmatch.core.minimap.model;

import java.util.Objects;

public record Provenance(
        NamespacedId originDocumentId,
        MapKey originBinding,
        NamespacedId originDimension,
        long originRevision,
        Sha256 originSourceHash
) {
    public Provenance {
        Objects.requireNonNull(originDocumentId, "originDocumentId");
        Objects.requireNonNull(originBinding, "originBinding");
        Objects.requireNonNull(originDimension, "originDimension");
        Objects.requireNonNull(originSourceHash, "originSourceHash");
        if (originRevision < 0) {
            throw new IllegalArgumentException("Origin revision must be non-negative");
        }
    }
}
