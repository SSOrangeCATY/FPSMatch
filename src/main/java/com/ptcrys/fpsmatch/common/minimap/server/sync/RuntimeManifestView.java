package com.ptcrys.fpsmatch.common.minimap.server.sync;

import com.ptcrys.fpsmatch.core.minimap.model.MapKey;
import com.ptcrys.fpsmatch.core.minimap.model.NamespacedId;
import com.ptcrys.fpsmatch.core.minimap.model.Sha256;

import java.util.Objects;

public record RuntimeManifestView(
        MapKey mapKey,
        NamespacedId documentId,
        long revision,
        Sha256 runtimeHash,
        String signature
) {
    public RuntimeManifestView {
        Objects.requireNonNull(mapKey, "mapKey");
        Objects.requireNonNull(documentId, "documentId");
        Objects.requireNonNull(runtimeHash, "runtimeHash");
        Objects.requireNonNull(signature, "signature");
        if (revision < 0) {
            throw new IllegalArgumentException("revision must be non-negative");
        }
    }
}