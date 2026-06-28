package com.phasetranscrystal.fpsmatch.core.health;

import com.phasetranscrystal.fpsmatch.core.matchinit.HealthSnapshot;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class HealthRuntime {
    private final Map<UUID, MutableHealth> players = new HashMap<>();

    public void addPlayer(UUID playerId, HealthSnapshot snapshot) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(snapshot, "snapshot");
        if (snapshot.maxHealth() < 1) {
            throw new IllegalArgumentException("maxHealth must be positive");
        }
        players.putIfAbsent(playerId, new MutableHealth(snapshot.maxHealth(), snapshot.initialHealth()));
    }

    public HealthChangeResult damage(UUID playerId, int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("amount must not be negative");
        }
        MutableHealth health = record(playerId);
        int applied = Math.min(amount, health.currentHealth);
        health.currentHealth -= applied;
        return HealthChangeResult.damaged(playerId, applied, health.currentHealth, health.currentHealth == 0);
    }

    public HealthChangeResult heal(UUID playerId, int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("amount must not be negative");
        }
        MutableHealth health = record(playerId);
        int missing = health.maxHealth - health.currentHealth;
        int applied = Math.min(amount, missing);
        health.currentHealth += applied;
        return HealthChangeResult.healed(playerId, applied, health.currentHealth);
    }

    public HealthChangeResult setCurrentHealth(UUID playerId, int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("amount must not be negative");
        }
        MutableHealth health = record(playerId);
        int nextHealth = Math.min(amount, health.maxHealth);
        int delta = nextHealth - health.currentHealth;
        health.currentHealth = nextHealth;
        if (delta >= 0) {
            return HealthChangeResult.healed(playerId, delta, health.currentHealth);
        }
        return HealthChangeResult.damaged(playerId, -delta, health.currentHealth, health.currentHealth == 0);
    }

    public int currentHealth(UUID playerId) {
        return record(playerId).currentHealth;
    }

    public int maxHealth(UUID playerId) {
        return record(playerId).maxHealth;
    }

    public boolean isDepleted(UUID playerId) {
        return record(playerId).currentHealth == 0;
    }

    private MutableHealth record(UUID playerId) {
        MutableHealth health = players.get(Objects.requireNonNull(playerId, "playerId"));
        if (health == null) {
            throw new IllegalArgumentException("Unknown player: " + playerId);
        }
        return health;
    }

    private static final class MutableHealth {
        private final int maxHealth;
        private int currentHealth;

        private MutableHealth(int maxHealth, int currentHealth) {
            this.maxHealth = maxHealth;
            this.currentHealth = currentHealth;
        }
    }
}
