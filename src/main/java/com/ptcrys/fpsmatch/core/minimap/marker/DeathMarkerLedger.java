package com.ptcrys.fpsmatch.core.minimap.marker;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * In-memory death marker ledger keyed by player. Pure; no Minecraft types.
 */
public final class DeathMarkerLedger {
    private final Map<UUID, DeathMarkerEvent> events = new LinkedHashMap<>();

    public void record(DeathMarkerEvent event) {
        Objects.requireNonNull(event, "event");
        events.put(event.playerId(), event);
    }

    public Optional<DeathMarkerEvent> get(UUID playerId) {
        return Optional.ofNullable(events.get(playerId));
    }

    public void clearPlayer(UUID playerId) {
        events.remove(playerId);
    }

    public void clearAll() {
        events.clear();
    }

    public List<DeathMarkerEvent> activeAt(long nowTick) {
        List<DeathMarkerEvent> out = new ArrayList<>();
        for (DeathMarkerEvent event : events.values()) {
            if (nowTick <= event.expiresTick()) {
                out.add(event);
            }
        }
        return List.copyOf(out);
    }

    public void purgeExpired(long nowTick) {
        events.entrySet().removeIf(entry -> nowTick > entry.getValue().expiresTick());
    }

    public int size() {
        return events.size();
    }
}