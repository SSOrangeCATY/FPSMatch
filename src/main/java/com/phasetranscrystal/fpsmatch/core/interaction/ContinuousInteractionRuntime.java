package com.phasetranscrystal.fpsmatch.core.interaction;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class ContinuousInteractionRuntime {
    private final Map<String, MutableInteraction> activeInteractions = new LinkedHashMap<>();
    private final List<ContinuousInteractionEvent> events = new ArrayList<>();

    public static ContinuousInteractionRuntime restore(List<ContinuousInteractionSnapshot> snapshots) {
        ContinuousInteractionRuntime runtime = new ContinuousInteractionRuntime();
        for (ContinuousInteractionSnapshot snapshot : Objects.requireNonNull(snapshots, "snapshots")) {
            MutableInteraction previous = runtime.activeInteractions.putIfAbsent(
                    snapshot.interactionId(),
                    new MutableInteraction(snapshot.toRequest(), snapshot.progressTicks())
            );
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate interactionId in snapshots: " + snapshot.interactionId());
            }
        }
        return runtime;
    }

    public boolean start(ContinuousInteractionRequest request) {
        Objects.requireNonNull(request, "request");
        if (activeInteractions.containsKey(request.interactionId())) {
            return false;
        }
        MutableInteraction interaction = new MutableInteraction(request, 0);
        activeInteractions.put(request.interactionId(), interaction);
        emit(ContinuousInteractionEventType.STARTED, interaction);
        return true;
    }

    public void tick() {
        tick(1);
    }

    public void tick(int ticks) {
        if (ticks < 0) {
            throw new IllegalArgumentException("ticks must not be negative");
        }
        for (int index = 0; index < ticks; index++) {
            tickOnce();
        }
    }

    public boolean cancel(String interactionId) {
        MutableInteraction interaction = activeInteractions.remove(Objects.requireNonNull(interactionId, "interactionId"));
        if (interaction == null) {
            return false;
        }
        emit(ContinuousInteractionEventType.CANCELLED, interaction);
        return true;
    }

    public int cancelByActor(UUID actorId) {
        Objects.requireNonNull(actorId, "actorId");
        List<String> interactionIds = activeInteractions.values().stream()
                .filter(interaction -> interaction.request.actorId().equals(actorId))
                .map(interaction -> interaction.request.interactionId())
                .toList();
        return cancelAll(interactionIds);
    }

    public int cancelByTarget(String targetId) {
        Objects.requireNonNull(targetId, "targetId");
        List<String> interactionIds = activeInteractions.values().stream()
                .filter(interaction -> interaction.request.targetId().equals(targetId))
                .map(interaction -> interaction.request.interactionId())
                .toList();
        return cancelAll(interactionIds);
    }

    public boolean complete(String interactionId) {
        MutableInteraction interaction = activeInteractions.remove(Objects.requireNonNull(interactionId, "interactionId"));
        if (interaction == null) {
            return false;
        }
        interaction.progressTicks = interaction.request.completionTicks();
        emit(ContinuousInteractionEventType.COMPLETED, interaction);
        return true;
    }

    public boolean isActive(String interactionId) {
        return activeInteractions.containsKey(Objects.requireNonNull(interactionId, "interactionId"));
    }

    public List<ContinuousInteractionSnapshot> snapshot() {
        return activeInteractions.values().stream()
                .map(interaction -> ContinuousInteractionSnapshot.from(interaction.request, interaction.progressTicks))
                .toList();
    }

    public List<ContinuousInteractionEvent> drainEvents() {
        List<ContinuousInteractionEvent> drained = List.copyOf(events);
        events.clear();
        return drained;
    }

    private int cancelAll(List<String> interactionIds) {
        int cancelled = 0;
        for (String interactionId : interactionIds) {
            if (cancel(interactionId)) {
                cancelled++;
            }
        }
        return cancelled;
    }

    private void tickOnce() {
        List<String> completed = new ArrayList<>();
        for (MutableInteraction interaction : activeInteractions.values()) {
            interaction.progressTicks++;
            emit(ContinuousInteractionEventType.PROGRESS_ADVANCED, interaction);
            if (interaction.progressTicks >= interaction.request.completionTicks()) {
                completed.add(interaction.request.interactionId());
            }
        }
        for (String interactionId : completed) {
            MutableInteraction interaction = activeInteractions.remove(interactionId);
            if (interaction != null) {
                interaction.progressTicks = interaction.request.completionTicks();
                emit(ContinuousInteractionEventType.COMPLETED, interaction);
            }
        }
    }

    private void emit(ContinuousInteractionEventType type, MutableInteraction interaction) {
        ContinuousInteractionRequest request = interaction.request;
        events.add(new ContinuousInteractionEvent(
                type,
                request.interactionId(),
                request.actionId(),
                request.targetId(),
                request.actorId(),
                request.teamId(),
                interaction.progressTicks,
                request.completionTicks(),
                request.metadata()
        ));
    }

    private static final class MutableInteraction {
        private final ContinuousInteractionRequest request;
        private int progressTicks;

        private MutableInteraction(ContinuousInteractionRequest request, int progressTicks) {
            this.request = Objects.requireNonNull(request, "request");
            this.progressTicks = progressTicks;
        }
    }
}
