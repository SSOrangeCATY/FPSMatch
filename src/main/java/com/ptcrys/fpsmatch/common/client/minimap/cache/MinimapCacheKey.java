package com.ptcrys.fpsmatch.common.client.minimap.cache;

import com.ptcrys.fpsmatch.core.minimap.model.MapKey;
import com.ptcrys.fpsmatch.core.minimap.model.NamespacedId;
import com.ptcrys.fpsmatch.core.minimap.model.Sha256;

import java.util.Objects;

public record MinimapCacheKey(
        String serverIdentity,
        NamespacedId dimension,
        MapKey mapKey,
        NamespacedId documentId,
        long revision,
        Sha256 runtimeHash,
        Sha256 objectHash,
        String stablePath
) {
    public MinimapCacheKey {
        Objects.requireNonNull(serverIdentity, "serverIdentity");
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(mapKey, "mapKey");
        Objects.requireNonNull(documentId, "documentId");
        Objects.requireNonNull(runtimeHash, "runtimeHash");
        Objects.requireNonNull(objectHash, "objectHash");
        Objects.requireNonNull(stablePath, "stablePath");
        if (revision < 0) {
            throw new IllegalArgumentException("revision must be non-negative");
        }
    }
}
