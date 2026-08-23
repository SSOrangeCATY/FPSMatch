package com.ptcrys.fpsmatch.core.minimap.marker;

import com.ptcrys.fpsmatch.core.minimap.model.NamespacedId;

import java.util.Objects;

public sealed interface MarkerDelta permits MarkerDelta.Add, MarkerDelta.Update, MarkerDelta.Remove {
    record Add(MarkerSnapshot.Marker marker) implements MarkerDelta {
        public Add {
            Objects.requireNonNull(marker, "marker");
        }
    }

    record Update(MarkerSnapshot.Marker marker) implements MarkerDelta {
        public Update {
            Objects.requireNonNull(marker, "marker");
        }
    }

    record Remove(NamespacedId markerId) implements MarkerDelta {
        public Remove {
            Objects.requireNonNull(markerId, "markerId");
        }
    }
}