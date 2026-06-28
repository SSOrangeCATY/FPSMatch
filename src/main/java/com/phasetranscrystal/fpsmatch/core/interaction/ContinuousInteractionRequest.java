package com.phasetranscrystal.fpsmatch.core.interaction;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record ContinuousInteractionRequest(
        String interactionId,
        String actionId,
        String targetId,
        UUID actorId,
        Optional<UUID> teamId,
        int completionTicks,
        Map<String, String> metadata
) {
    public ContinuousInteractionRequest {
        interactionId = requireId(interactionId, "interactionId");
        actionId = requireId(actionId, "actionId");
        targetId = requireId(targetId, "targetId");
        Objects.requireNonNull(actorId, "actorId");
        teamId = Objects.requireNonNull(teamId, "teamId");
        if (completionTicks < 1) {
            throw new IllegalArgumentException("completionTicks must be positive");
        }
        metadata = Map.copyOf(Objects.requireNonNull(metadata, "metadata"));
    }

    private static String requireId(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
