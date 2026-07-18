package com.phasetranscrystal.fpsmatch.core.minimap.model;

import java.util.Objects;

public record BuiltinRuntimeBinding(
        MapKey binding,
        NamespacedId dimension,
        NamespacedId documentId,
        Sha256 runtimeHash
) {
    public BuiltinRuntimeBinding {
        Objects.requireNonNull(binding, "binding");
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(documentId, "documentId");
        Objects.requireNonNull(runtimeHash, "runtimeHash");
    }
}
