package com.phasetranscrystal.fpsmatch.core.minimap.marker;

import com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.WireMarker;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Authoritative marker candidate produced by providers before server-side visibility filtering.
 */
public record MarkerCandidate(
        NamespacedId markerId,
        NamespacedId typeId,
        NamespacedId styleId,
        double x,
        double y,
        double z,
        float yaw,
        long updatedTick,
        Optional<Long> expiresTick,
        Optional<String> floorSlug,
        List<WireMarker.StateField> stateFields,
        String teamId,
        boolean deathEvent,
        boolean publicObjective
) {
    public MarkerCandidate {
        Objects.requireNonNull(markerId, "markerId");
        Objects.requireNonNull(typeId, "typeId");
        Objects.requireNonNull(styleId, "styleId");
        Objects.requireNonNull(expiresTick, "expiresTick");
        Objects.requireNonNull(floorSlug, "floorSlug");
        stateFields = List.copyOf(Objects.requireNonNull(stateFields, "stateFields"));
        Objects.requireNonNull(teamId, "teamId");
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || !Float.isFinite(yaw)) {
            throw new IllegalArgumentException("Marker pose must be finite");
        }
        if (updatedTick < 0) {
            throw new IllegalArgumentException("updatedTick must be non-negative");
        }
    }

    public MarkerCandidate(
            NamespacedId markerId,
            NamespacedId typeId,
            NamespacedId styleId,
            double x,
            double y,
            double z,
            float yaw,
            long updatedTick,
            Optional<Long> expiresTick,
            Optional<String> floorSlug,
            String teamId,
            boolean deathEvent,
            boolean publicObjective
    ) {
        this(
                markerId, typeId, styleId, x, y, z, yaw, updatedTick,
                expiresTick, floorSlug, List.of(), teamId, deathEvent, publicObjective
        );
    }

    public MarkerSnapshot.Marker toMarker() {
        return new MarkerSnapshot.Marker(
                markerId, typeId, styleId, x, y, z, yaw, updatedTick,
                expiresTick, floorSlug, stateFields
        );
    }
}
