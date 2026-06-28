package com.phasetranscrystal.fpsmatch.core.connection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class PlayerConnectionRuntime {
    private final PlayerConnectionPolicy policy;
    private final Map<UUID, Integer> disconnectedPlayers = new LinkedHashMap<>();
    private final List<PlayerConnectionEvent> events = new ArrayList<>();

    public PlayerConnectionRuntime(PlayerConnectionPolicy policy) {
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    public static PlayerConnectionRuntime restore(
            PlayerConnectionPolicy policy,
            List<PlayerConnectionSnapshot> snapshots
    ) {
        PlayerConnectionRuntime runtime = new PlayerConnectionRuntime(policy);
        for (PlayerConnectionSnapshot snapshot : Objects.requireNonNull(snapshots, "snapshots")) {
            if (snapshot.ticksRemaining() > 0) {
                runtime.disconnectedPlayers.put(snapshot.playerId(), snapshot.ticksRemaining());
            }
        }
        return runtime;
    }

    public boolean disconnect(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        if (disconnectedPlayers.containsKey(playerId)) {
            return false;
        }
        disconnectedPlayers.put(playerId, policy.graceTicks());
        events.add(new PlayerConnectionEvent(playerId, PlayerConnectionEventType.DISCONNECTED, policy.graceTicks()));
        return true;
    }

    public boolean reconnect(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        if (disconnectedPlayers.remove(playerId) == null) {
            return false;
        }
        events.add(new PlayerConnectionEvent(playerId, PlayerConnectionEventType.RECONNECTED, 0));
        return true;
    }

    public boolean clear(UUID playerId) {
        return disconnectedPlayers.remove(Objects.requireNonNull(playerId, "playerId")) != null;
    }

    public void tick(int ticks) {
        if (ticks < 0) {
            throw new IllegalArgumentException("ticks must not be negative");
        }
        if (ticks == 0 || disconnectedPlayers.isEmpty()) {
            return;
        }
        List<UUID> expired = new ArrayList<>();
        disconnectedPlayers.replaceAll((playerId, ticksRemaining) -> {
            int updatedTicks = Math.max(0, ticksRemaining - ticks);
            if (updatedTicks == 0) {
                expired.add(playerId);
            }
            return updatedTicks;
        });
        for (UUID playerId : expired) {
            disconnectedPlayers.remove(playerId);
            events.add(new PlayerConnectionEvent(playerId, PlayerConnectionEventType.GRACE_EXPIRED, 0));
        }
    }

    public boolean isDisconnected(UUID playerId) {
        return disconnectedPlayers.containsKey(Objects.requireNonNull(playerId, "playerId"));
    }

    public int ticksRemaining(UUID playerId) {
        return disconnectedPlayers.getOrDefault(Objects.requireNonNull(playerId, "playerId"), 0);
    }

    public List<PlayerConnectionSnapshot> snapshot() {
        return disconnectedPlayers.entrySet().stream()
                .map(entry -> new PlayerConnectionSnapshot(entry.getKey(), entry.getValue()))
                .toList();
    }

    public List<PlayerConnectionEvent> drainEvents() {
        List<PlayerConnectionEvent> drained = List.copyOf(events);
        events.clear();
        return drained;
    }
}
