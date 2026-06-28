package com.phasetranscrystal.fpsmatch.core.visibility;

import com.phasetranscrystal.fpsmatch.core.objective.VisibilityPolicy;

import java.util.Objects;

public record ScopedPayload<T>(String id, T payload, VisibilityPolicy policy) {
    public ScopedPayload {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(policy, "policy");
    }

    public static <T> ScopedPayload<T> of(String id, T payload, VisibilityPolicy policy) {
        return new ScopedPayload<>(id, payload, policy);
    }
}
