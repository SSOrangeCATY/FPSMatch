package com.ptcrys.fpsmatch.common.client.minimap.editor;

import com.ptcrys.fpsmatch.common.packet.mapselect.MapRoomDetail;
import com.ptcrys.fpsmatch.common.packet.mapselect.MapRoomSummary;
import com.ptcrys.fpsmatch.core.minimap.model.MapKey;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Process-local publication gates shared by every map-room screen instance.
 * The authoritative detail path is the only path allowed to remove a gate.
 */
public final class MinimapPublishRefreshRegistry {
    private static final MinimapPublishRefreshRegistry GLOBAL =
            new MinimapPublishRefreshRegistry();

    private final Map<MapKey, MinimapPublishRefreshGate> gates = new LinkedHashMap<>();

    public static MinimapPublishRefreshRegistry global() {
        return GLOBAL;
    }

    public synchronized void await(MinimapPublishRefreshGate gate) {
        Objects.requireNonNull(gate, "gate");
        gates.put(gate.mapKey(), gate);
    }

    public synchronized Optional<MinimapPublishRefreshGate> pending(MapKey mapKey) {
        return Optional.ofNullable(gates.get(Objects.requireNonNull(mapKey, "mapKey")));
    }

    /** Returns maps represented by a snapshot; this is a refresh hint, never an unlock. */
    public synchronized List<MapKey> pendingMaps(List<MapRoomSummary> summaries) {
        Objects.requireNonNull(summaries, "summaries");
        List<MapKey> result = new ArrayList<>();
        for (MapRoomSummary summary : summaries) {
            Objects.requireNonNull(summary, "summary");
            MapKey mapKey = new MapKey(summary.gameType(), summary.mapName());
            if (gates.containsKey(mapKey) && !result.contains(mapKey)) {
                result.add(mapKey);
            }
        }
        return List.copyOf(result);
    }

    /** Applies only a server-authored detail with the exact committed identity. */
    public synchronized boolean acceptAuthoritativeDetail(MapRoomDetail detail) {
        Objects.requireNonNull(detail, "detail");
        MapRoomSummary summary = detail.summary();
        MapKey mapKey = new MapKey(summary.gameType(), summary.mapName());
        MinimapPublishRefreshGate gate = gates.get(mapKey);
        if (gate == null || !gate.accepts(summary)) {
            return false;
        }
        gates.remove(mapKey);
        return true;
    }

    public synchronized void clear(MapKey mapKey) {
        gates.remove(Objects.requireNonNull(mapKey, "mapKey"));
    }

    public synchronized void clearAll() {
        gates.clear();
    }

    public synchronized int size() {
        return gates.size();
    }

    public synchronized boolean isEmpty() {
        return gates.isEmpty();
    }
}
