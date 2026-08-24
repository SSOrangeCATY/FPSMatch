package com.ptcrys.fpsmatch.common.packet.mapselect;

import com.ptcrys.fpsmatch.core.minimap.model.NamespacedId;
import com.ptcrys.fpsmatch.core.minimap.model.Sha256;

import java.util.Objects;

/** Authoritative minimap publication identity projected into map-room DTOs. */
public record MapRoomMinimapIdentity(
        NamespacedId dimension,
        NamespacedId documentId,
        long revision,
        Sha256 sourceHash,
        Sha256 runtimeHash
) {
    public MapRoomMinimapIdentity {
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(documentId, "documentId");
        Objects.requireNonNull(sourceHash, "sourceHash");
        Objects.requireNonNull(runtimeHash, "runtimeHash");
        if (revision < 0) {
            throw new IllegalArgumentException("Minimap revision cannot be negative");
        }
    }
}
