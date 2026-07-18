package com.phasetranscrystal.fpsmatch.core.minimap.marker;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record MarkerStreamUpdate(
        Kind kind,
        UUID streamEpoch,
        long sequence,
        List<MarkerSnapshot.Marker> markers,
        List<MarkerDelta> operations
) {
    public enum Kind {
        RESET,
        DELTA
    }

    public MarkerStreamUpdate {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(streamEpoch, "streamEpoch");
        if (sequence < 0) {
            throw new IllegalArgumentException("sequence must be non-negative");
        }
        markers = List.copyOf(markers);
        operations = List.copyOf(operations);
        if (kind == Kind.RESET && sequence != 0) {
            throw new IllegalArgumentException("RESET sequence must be 0");
        }
        if (kind == Kind.RESET && !operations.isEmpty()) {
            throw new IllegalArgumentException("RESET cannot carry delta operations");
        }
        if (kind == Kind.DELTA && !markers.isEmpty()) {
            throw new IllegalArgumentException("DELTA cannot carry full marker snapshot list");
        }
    }

    public static MarkerStreamUpdate reset(UUID epoch, List<MarkerSnapshot.Marker> markers) {
        return new MarkerStreamUpdate(Kind.RESET, epoch, 0L, markers, List.of());
    }

    public static MarkerStreamUpdate delta(UUID epoch, long sequence, List<MarkerDelta> operations) {
        return new MarkerStreamUpdate(Kind.DELTA, epoch, sequence, List.of(), operations);
    }
}