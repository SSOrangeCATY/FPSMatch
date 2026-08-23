package com.ptcrys.fpsmatch.core.minimap.hud;

import com.ptcrys.fpsmatch.core.minimap.model.MapKey;

import java.util.Objects;
import java.util.Set;

public record HudSafeAreaDiagnostic(MapKey mapKey, String requestId, Set<String> conflictIds) {
    public HudSafeAreaDiagnostic {
        Objects.requireNonNull(mapKey, "mapKey");
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(conflictIds, "conflictIds");
        conflictIds = Set.copyOf(conflictIds);
    }
}