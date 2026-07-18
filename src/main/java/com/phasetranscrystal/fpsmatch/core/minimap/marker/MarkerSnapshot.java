package com.phasetranscrystal.fpsmatch.core.minimap.marker;

import com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.WireMarker;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class MarkerSnapshot {
    private static final Comparator<Marker> CANONICAL_ORDER = (left, right) ->
            compareIds(left.markerId(), right.markerId());

    private final List<Marker> markers;

    private MarkerSnapshot(List<Marker> markers) {
        this.markers = List.copyOf(markers);
    }

    public static MarkerSnapshot of(List<Marker> markers) {
        Objects.requireNonNull(markers, "markers");
        if (isCanonical(markers)) {
            return new MarkerSnapshot(markers);
        }
        List<Marker> sorted = new ArrayList<>(markers);
        sorted.sort(CANONICAL_ORDER);
        return new MarkerSnapshot(sorted);
    }

    private static boolean isCanonical(List<Marker> markers) {
        for (int index = 1; index < markers.size(); index++) {
            if (CANONICAL_ORDER.compare(
                    markers.get(index - 1), markers.get(index)
            ) > 0) {
                return false;
            }
        }
        return true;
    }

    static int compareIds(NamespacedId left, NamespacedId right) {
        int namespace = left.namespace().compareTo(right.namespace());
        return namespace != 0 ? namespace : left.path().compareTo(right.path());
    }

    public List<Marker> markers() {
        return markers;
    }

    public record Marker(
            NamespacedId markerId,
            NamespacedId typeId,
            NamespacedId styleId,
            double x,
            double y,
            double z,
            float yaw,
            long updatedTick,
            Optional<Long> expiresTick,
            Optional<String> floorSlug,
            List<WireMarker.StateField> stateFields
    ) {
        public Marker(
                NamespacedId markerId,
                NamespacedId typeId,
                NamespacedId styleId,
                double x,
                double y,
                double z,
                float yaw,
                long updatedTick,
                Optional<Long> expiresTick,
                Optional<String> floorSlug
        ) {
            this(
                    markerId, typeId, styleId, x, y, z, yaw, updatedTick,
                    expiresTick, floorSlug, List.of()
            );
        }

        public Marker {
            Objects.requireNonNull(markerId, "markerId");
            Objects.requireNonNull(typeId, "typeId");
            Objects.requireNonNull(styleId, "styleId");
            Objects.requireNonNull(expiresTick, "expiresTick");
            Objects.requireNonNull(floorSlug, "floorSlug");
            stateFields = List.copyOf(Objects.requireNonNull(stateFields, "stateFields"));
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || !Float.isFinite(yaw)) {
                throw new IllegalArgumentException("Marker pose must be finite");
            }
            if (updatedTick < 0) {
                throw new IllegalArgumentException("updatedTick must be non-negative");
            }
            expiresTick.ifPresent(value -> {
                if (value < 0) {
                    throw new IllegalArgumentException("expiresTick must be non-negative");
                }
            });
        }
    }
}
