package com.phasetranscrystal.fpsmatch.core.connection;

import java.util.Objects;
import java.util.UUID;

public record PlayerConnectionEvent(UUID playerId, PlayerConnectionEventType type, int ticksRemaining) {
    public PlayerConnectionEvent {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(type, "type");
        if (ticksRemaining < 0) {
            throw new IllegalArgumentException("ticksRemaining must not be negative");
        }
    }
}
