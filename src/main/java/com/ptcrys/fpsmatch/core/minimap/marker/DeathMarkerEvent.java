package com.ptcrys.fpsmatch.core.minimap.marker;

import com.ptcrys.fpsmatch.core.minimap.contract.MinimapFormatContract;
import com.ptcrys.fpsmatch.core.minimap.model.NamespacedId;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Authoritative death-event marker input captured at server DeathContext time.
 */
public record DeathMarkerEvent(
        UUID playerId,
        String teamId,
        double x,
        double y,
        double z,
        float yaw,
        long deathTick,
        long expiresTick,
        Optional<String> floorSlug
) {
    public DeathMarkerEvent {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(teamId, "teamId");
        if (teamId.isBlank()) {
            throw new IllegalArgumentException("teamId cannot be blank");
        }
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || !Float.isFinite(yaw)) {
            throw new IllegalArgumentException("Death pose must be finite");
        }
        if (deathTick < 0 || expiresTick < deathTick) {
            throw new IllegalArgumentException("Death ticks invalid");
        }
        floorSlug = Objects.requireNonNull(floorSlug, "floorSlug");
        floorSlug.ifPresent(slug -> {
            if (!MinimapFormatContract.isInternalSlug(slug)) {
                throw new IllegalArgumentException("floorSlug must be an internal slug");
            }
        });
    }

    public static NamespacedId markerIdFor(UUID playerId) {
        return NamespacedId.parse("fpsmatch:event/death/" + playerId);
    }

    public static final NamespacedId TYPE_ID = NamespacedId.parse("fpsmatch:type/death");
    public static final NamespacedId STYLE_ID = NamespacedId.parse("fpsmatch:style/death");

    public MarkerCandidate toCandidate() {
        return new MarkerCandidate(
                markerIdFor(playerId),
                TYPE_ID,
                STYLE_ID,
                x, y, z, yaw,
                deathTick,
                Optional.of(expiresTick),
                floorSlug,
                teamId,
                true,
                false
        );
    }
}