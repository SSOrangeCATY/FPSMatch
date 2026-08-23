package com.ptcrys.fpsmatch.common.minimap.server.sync;

import com.ptcrys.fpsmatch.core.minimap.model.NamespacedId;
import com.ptcrys.fpsmatch.core.minimap.model.Sha256;
import com.ptcrys.fpsmatch.core.minimap.wire.WireIdentity;

import java.util.Objects;

public record RuntimeAuthority(
        WireIdentity.MapTarget target,
        NamespacedId documentId,
        long revision,
        Sha256 sourceHash,
        Sha256 runtimeHash
) {
    public RuntimeAuthority {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(documentId, "documentId");
        Objects.requireNonNull(sourceHash, "sourceHash");
        Objects.requireNonNull(runtimeHash, "runtimeHash");
        if (revision < 0) {
            throw new IllegalArgumentException("revision must be non-negative");
        }
    }
}
