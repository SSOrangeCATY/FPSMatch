package com.phasetranscrystal.fpsmatch.common.minimap.server.sync;

import com.phasetranscrystal.fpsmatch.core.minimap.model.MapKey;
import com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId;
import com.phasetranscrystal.fpsmatch.core.minimap.model.Sha256;

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