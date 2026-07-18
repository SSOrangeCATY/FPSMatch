package com.phasetranscrystal.fpsmatch.common.minimap.server.sync;

import com.phasetranscrystal.fpsmatch.core.minimap.model.MapKey;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.WireIdentity;

import java.util.Objects;
import java.util.UUID;

public record ScheduledFragment(
        UUID playerId,
        MapKey mapKey,
        WireIdentity.Scope scope,
        FragmentJob job
) {
    public ScheduledFragment {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(mapKey, "mapKey");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(job, "job");
    }
}