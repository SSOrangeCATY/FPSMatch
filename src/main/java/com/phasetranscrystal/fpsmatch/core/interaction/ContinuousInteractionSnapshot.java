package com.phasetranscrystal.fpsmatch.core.interaction;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record ContinuousInteractionSnapshot(
        String interactionId,
        String actionId,
        String targetId,
        UUID actorId,
        Optional<UUID> teamId,
        int progressTicks,
        int completionTicks,
        Map<String, String> metadata
) {
    public ContinuousInteractionSnapshot {
        interactionId = requireId(interactionId, "interactionId");
        actionId = requireId(actionId, "actionId");
        targetId = requireId(targetId, "targetId");
        Objects.requireNonNull(actorId, "actorId");
        teamId = Objects.requireNonNull(teamId, "teamId");
        if (progressTicks < 0) {
            throw new IllegalArgumentException("progressTicks must not be negative");
        }
        if (completionTicks < 1) {
            throw new IllegalArgumentException("completionTicks must be positive");
        }
        if (progressTicks > completionTicks) {
            throw new IllegalArgumentException("progressTicks must not exceed completionTicks");
        }
        if (progressTicks == completionTicks) {
            throw new IllegalArgumentException("active interaction progressTicks must be less than completionTicks");
        }
        metadata = Map.copyOf(Objects.requireNonNull(metadata, "metadata"));
    }

    static ContinuousInteractionSnapshot from(ContinuousInteractionRequest request, int progressTicks) {
        return new ContinuousInteractionSnapshot(
                request.interactionId(),
                request.actionId(),
                request.targetId(),
                request.actorId(),
                request.teamId(),
                progressTicks,
                request.completionTicks(),
                request.metadata()
        );
    }

    ContinuousInteractionRequest toRequest() {
        return new ContinuousInteractionRequest(
                interactionId,
                actionId,
                targetId,
                actorId,
                teamId,
                completionTicks,
                metadata
        );
    }

    private static String requireId(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
