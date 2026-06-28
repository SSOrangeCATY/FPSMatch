package com.phasetranscrystal.fpsmatch.core.connection;

import java.util.Objects;
import java.util.UUID;

public record PlayerConnectionSnapshot(UUID playerId, int ticksRemaining) {
    public PlayerConnectionSnapshot {
        Objects.requireNonNull(playerId, "playerId");
        if (ticksRemaining < 0) {
            throw new IllegalArgumentException("ticksRemaining must not be negative");
        }
    }
}
