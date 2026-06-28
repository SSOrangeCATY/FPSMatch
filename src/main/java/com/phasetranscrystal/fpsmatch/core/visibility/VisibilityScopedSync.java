package com.phasetranscrystal.fpsmatch.core.visibility;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class VisibilityScopedSync {
    private VisibilityScopedSync() {
    }

    public static <T> VisibilitySyncPlan<T> plan(
            List<VisibilityRecipient> recipients,
            List<ScopedPayload<T>> payloads
    ) {
        Objects.requireNonNull(recipients, "recipients");
        Objects.requireNonNull(payloads, "payloads");
        Map<java.util.UUID, List<ScopedPayload<T>>> deliveries = new LinkedHashMap<>();
        for (VisibilityRecipient recipient : recipients) {
            List<ScopedPayload<T>> visiblePayloads = payloads.stream()
                    .filter(payload -> payload.policy().canSee(recipient.viewer()))
                    .toList();
            deliveries.put(recipient.playerId(), visiblePayloads);
        }
        return new VisibilitySyncPlan<>(deliveries);
    }
}
