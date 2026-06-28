package com.phasetranscrystal.fpsmatch.core.objective;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record ObjectiveViewer(Optional<UUID> playerId, Optional<UUID> teamId, boolean spectatorView) {
    public ObjectiveViewer(Optional<UUID> teamId, boolean spectatorView) {
        this(Optional.empty(), teamId, spectatorView);
    }

    public ObjectiveViewer {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(teamId, "teamId");
    }

    public static ObjectiveViewer team(UUID teamId) {
        return new ObjectiveViewer(Optional.empty(), Optional.of(Objects.requireNonNull(teamId, "teamId")), false);
    }

    public static ObjectiveViewer player(UUID playerId, UUID teamId) {
        return new ObjectiveViewer(
                Optional.of(Objects.requireNonNull(playerId, "playerId")),
                Optional.of(Objects.requireNonNull(teamId, "teamId")),
                false
        );
    }

    public static ObjectiveViewer spectator() {
        return new ObjectiveViewer(Optional.empty(), Optional.empty(), true);
    }

    public boolean isSpectator() {
        return spectatorView;
    }
}
