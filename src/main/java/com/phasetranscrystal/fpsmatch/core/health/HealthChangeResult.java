package com.phasetranscrystal.fpsmatch.core.health;

import java.util.Objects;
import java.util.UUID;

public record HealthChangeResult(
        UUID playerId,
        int amount,
        int currentHealth,
        boolean depleted,
        HealthChangeType type
) {
    public HealthChangeResult {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(type, "type");
        if (amount < 0) {
            throw new IllegalArgumentException("amount must not be negative");
        }
        if (currentHealth < 0) {
            throw new IllegalArgumentException("currentHealth must not be negative");
        }
    }

    public static HealthChangeResult damaged(UUID playerId, int amount, int currentHealth, boolean depleted) {
        return new HealthChangeResult(playerId, amount, currentHealth, depleted, HealthChangeType.DAMAGED);
    }

    public static HealthChangeResult healed(UUID playerId, int amount, int currentHealth) {
        return new HealthChangeResult(playerId, amount, currentHealth, false, HealthChangeType.HEALED);
    }
}
