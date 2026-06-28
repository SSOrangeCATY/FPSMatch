package com.phasetranscrystal.fpsmatch.core.playerstate;

import java.util.Objects;
import java.util.UUID;

public record PlayerStateEvent(
        UUID playerId,
        MatchPlayerState previousState,
        MatchPlayerState currentState,
        int downedCount,
        int bleedoutTicks
) {
    public PlayerStateEvent {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(previousState, "previousState");
        Objects.requireNonNull(currentState, "currentState");
        if (downedCount < 0) {
            throw new IllegalArgumentException("downedCount must be >= 0");
        }
        if (bleedoutTicks < 0) {
            throw new IllegalArgumentException("bleedoutTicks must be >= 0");
        }
    }
}
