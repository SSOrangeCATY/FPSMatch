package com.phasetranscrystal.fpsmatch.core.visibility;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record VisibilitySyncPlan<T>(Map<UUID, List<ScopedPayload<T>>> deliveries) {
    public VisibilitySyncPlan {
        Objects.requireNonNull(deliveries, "deliveries");
        deliveries = deliveries.entrySet().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        entry -> List.copyOf(entry.getValue())
                ));
    }

    public List<ScopedPayload<T>> payloadsFor(UUID recipientPlayerId) {
        return deliveries.getOrDefault(recipientPlayerId, List.of());
    }

    public List<String> payloadIdsFor(UUID recipientPlayerId) {
        return payloadsFor(recipientPlayerId).stream()
                .map(ScopedPayload::id)
                .toList();
    }
}
