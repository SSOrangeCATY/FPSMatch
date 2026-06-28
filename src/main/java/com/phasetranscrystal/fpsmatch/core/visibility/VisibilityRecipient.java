package com.phasetranscrystal.fpsmatch.core.visibility;

import com.phasetranscrystal.fpsmatch.core.objective.ObjectiveViewer;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record VisibilityRecipient(UUID playerId, Optional<UUID> teamId, boolean spectator) {
    public VisibilityRecipient {
        Objects.requireNonNull(playerId, "playerId");
        teamId = Objects.requireNonNull(teamId, "teamId");
    }

    public static VisibilityRecipient player(UUID playerId, UUID teamId) {
        return new VisibilityRecipient(
                Objects.requireNonNull(playerId, "playerId"),
                Optional.of(Objects.requireNonNull(teamId, "teamId")),
                false
        );
    }

    public static VisibilityRecipient spectator(UUID playerId) {
        return new VisibilityRecipient(Objects.requireNonNull(playerId, "playerId"), Optional.empty(), true);
    }

    public ObjectiveViewer viewer() {
        if (spectator) {
            return ObjectiveViewer.spectator();
        }
        return ObjectiveViewer.player(playerId, teamId.orElseThrow());
    }
}
