package com.ptcrys.fpsmatch.common.client.minimap.generated;

import com.ptcrys.fpsmatch.core.minimap.model.NamespacedId;

import java.util.Objects;

/** Identifies the currently loaded client world, including a reconnect epoch. */
public record GeneratedWorldIdentity(NamespacedId dimension, long worldEpoch) {
    public GeneratedWorldIdentity {
        Objects.requireNonNull(dimension, "dimension");
        if (worldEpoch < 0) {
            throw new IllegalArgumentException("World epoch must be non-negative");
        }
    }
}
