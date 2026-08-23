package com.ptcrys.fpsmatch.core.minimap.storage;

import com.ptcrys.fpsmatch.core.minimap.model.MapKey;
import com.ptcrys.fpsmatch.core.minimap.model.NamespacedId;

import java.util.Objects;

public record PublishTarget(
        MapKey mapKey,
        NamespacedId dimension,
        NamespacedId documentId
) {
    public PublishTarget {
        Objects.requireNonNull(mapKey, "mapKey");
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(documentId, "documentId");
    }
}
