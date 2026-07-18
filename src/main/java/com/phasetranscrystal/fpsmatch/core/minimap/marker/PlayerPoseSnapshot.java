package com.phasetranscrystal.fpsmatch.core.minimap.marker;

import com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Pure living-player pose snapshot for marker providers.
 */
public record PlayerPoseSnapshot(
        UUID playerId,
        String teamId,
        double x,
        double y,
        double z,
        float yaw,
        long updatedTick,
        Optional<String> floorSlug,
        boolean living
) {
    public PlayerPoseSnapshot {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(teamId, "teamId");
        if (teamId.isBlank()) {
            throw new IllegalArgumentException("teamId cannot be blank");
        }
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || !Float.isFinite(yaw)) {
            throw new IllegalArgumentException("Player pose must be finite");
        }
        if (updatedTick < 0) {
            throw new IllegalArgumentException("updatedTick must be non-negative");
        }
        floorSlug = Objects.requireNonNull(floorSlug, "floorSlug");
    }

    public static NamespacedId markerIdFor(UUID playerId) {
        return NamespacedId.parse("fpsmatch:player/" + playerId);
    }

    public static final NamespacedId TYPE_ID = NamespacedId.parse("fpsmatch:type/player");
    public static final NamespacedId STYLE_ID = NamespacedId.parse("fpsmatch:style/player");

    public MarkerCandidate toLivingCandidate() {
        if (!living) {
            throw new IllegalStateException("Cannot emit living candidate for dead player");
        }
        return new MarkerCandidate(
                markerIdFor(playerId),
                TYPE_ID,
                STYLE_ID,
                x, y, z, yaw,
                updatedTick,
                Optional.empty(),
                floorSlug,
                teamId,
                false,
                false
        );
    }
}