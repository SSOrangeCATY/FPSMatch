package com.phasetranscrystal.fpsmatch.common.client.minimap;

import com.phasetranscrystal.fpsmatch.core.minimap.model.MapKey;
import com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId;
import com.phasetranscrystal.fpsmatch.core.minimap.model.Sha256;

import java.util.Objects;

/**
 * Immutable shared runtime generation tuple.
 * Commit paths require this generation to still be current.
 */
public record RuntimeGeneration(
        long connectionEpoch,
        String serverIdentity,
        MapKey mapKey,
        NamespacedId documentId,
        long revision,
        Sha256 runtimeHash,
        NamespacedId dimension,
        long localGeneration
) {
    public RuntimeGeneration {
        if (connectionEpoch < 0 || revision < 0 || localGeneration < 0) {
            throw new IllegalArgumentException("Runtime generation counters must be non-negative");
        }
        Objects.requireNonNull(serverIdentity, "serverIdentity");
        Objects.requireNonNull(mapKey, "mapKey");
        Objects.requireNonNull(documentId, "documentId");
        Objects.requireNonNull(runtimeHash, "runtimeHash");
        Objects.requireNonNull(dimension, "dimension");
    }
}