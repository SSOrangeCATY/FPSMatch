package com.phasetranscrystal.fpsmatch.core.minimap.marker;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Client marker store. Applies RESET/DELTA with stream-epoch and contiguous sequence checks.
 * Hidden markers never arrive; this store only projects already-authorized server data.
 */
public final class ClientMarkerStore {
    private final Map<String, MarkerSnapshot.Marker> markers = new LinkedHashMap<>();
    private UUID streamEpoch;
    private long lastSequence = -1L;

    public void applyReset(UUID streamEpoch, long sequence, List<MarkerSnapshot.Marker> markers) {
        Objects.requireNonNull(streamEpoch, "streamEpoch");
        Objects.requireNonNull(markers, "markers");
        if (sequence != 0L) {
            throw new MarkerStreamException("RESET sequence must be 0");
        }
        this.streamEpoch = streamEpoch;
        this.lastSequence = 0L;
        this.markers.clear();
        for (MarkerSnapshot.Marker marker : MarkerSnapshot.of(markers).markers()) {
            this.markers.put(marker.markerId().toString(), marker);
        }
    }

    public void applyDelta(UUID streamEpoch, long sequence, List<MarkerDelta> operations) {
        Objects.requireNonNull(streamEpoch, "streamEpoch");
        Objects.requireNonNull(operations, "operations");
        if (this.streamEpoch == null || !this.streamEpoch.equals(streamEpoch)) {
            throw new MarkerStreamException("Delta stream epoch mismatch; request full resync");
        }
        if (sequence != lastSequence + 1L) {
            throw new MarkerStreamException(
                    "Stale or gapped marker sequence: expected " + (lastSequence + 1L) + " but was " + sequence
            );
        }
        for (MarkerDelta operation : operations) {
            if (operation instanceof MarkerDelta.Add add) {
                markers.put(add.marker().markerId().toString(), add.marker());
            } else if (operation instanceof MarkerDelta.Update update) {
                markers.put(update.marker().markerId().toString(), update.marker());
            } else if (operation instanceof MarkerDelta.Remove remove) {
                markers.remove(remove.markerId().toString());
            }
        }
        lastSequence = sequence;
    }

    public List<MarkerSnapshot.Marker> markers() {
        return List.copyOf(markers.values());
    }

    public UUID streamEpoch() {
        return streamEpoch;
    }

    public long lastSequence() {
        return lastSequence;
    }

    public void clear() {
        markers.clear();
        streamEpoch = null;
        lastSequence = -1L;
    }
}
