package com.phasetranscrystal.fpsmatch.common.client.minimap;

import com.phasetranscrystal.fpsmatch.core.minimap.wire.WireIdentity;

import java.util.Objects;

public record MinimapScopeLease(
        WireIdentity.Scope scope,
        long scopeEpoch,
        long runtimeGeneration
) {
    public MinimapScopeLease {
        Objects.requireNonNull(scope, "scope");
        if (scopeEpoch < 0 || runtimeGeneration < 0) {
            throw new IllegalArgumentException("Lease counters must be non-negative");
        }
    }

    public WireIdentity.ScopeLease toWire() {
        return new WireIdentity.ScopeLease(scope, scopeEpoch, runtimeGeneration);
    }
}