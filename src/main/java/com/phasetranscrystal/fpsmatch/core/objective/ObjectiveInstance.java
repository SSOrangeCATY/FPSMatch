package com.phasetranscrystal.fpsmatch.core.objective;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class ObjectiveInstance {
    private final ObjectiveDefinition definition;
    private final CarriableObjectiveState carriable;
    private ObjectiveStatus status = ObjectiveStatus.AVAILABLE;
    private int progressTicks;
    private ObjectiveActor activeInteraction;
    private UUID controllingTeam;
    private UUID completedByTeam;
    private final Set<ObjectiveActor> presentActors = new LinkedHashSet<>();

    ObjectiveInstance(ObjectiveDefinition definition) {
        this.definition = Objects.requireNonNull(definition, "definition");
        this.carriable = definition.carryPolicy().enabled() ? new CarriableObjectiveState() : null;
    }

    public String id() {
        return definition.id();
    }

    public ObjectiveDefinition definition() {
        return definition;
    }

    public ObjectiveStatus status() {
        return status;
    }

    public int progressTicks() {
        return progressTicks;
    }

    public Optional<ObjectiveActor> activeInteraction() {
        return Optional.ofNullable(activeInteraction);
    }

    public Optional<UUID> controllingTeam() {
        return Optional.ofNullable(controllingTeam);
    }

    public Optional<UUID> completedByTeam() {
        return Optional.ofNullable(completedByTeam);
    }

    public Optional<CarriableObjectiveState> carriable() {
        return Optional.ofNullable(carriable);
    }

    public Set<ObjectiveActor> presentActors() {
        return Collections.unmodifiableSet(presentActors);
    }

    void setStatus(ObjectiveStatus status) {
        this.status = Objects.requireNonNull(status, "status");
    }

    void setProgressTicks(int progressTicks) {
        this.progressTicks = Math.max(0, progressTicks);
    }

    void setActiveInteraction(ObjectiveActor activeInteraction) {
        this.activeInteraction = activeInteraction;
    }

    void setControllingTeam(UUID controllingTeam) {
        this.controllingTeam = controllingTeam;
    }

    void setCompletedByTeam(UUID completedByTeam) {
        this.completedByTeam = completedByTeam;
    }

    void replacePresence(Set<ObjectiveActor> actors) {
        presentActors.clear();
        presentActors.addAll(actors);
    }

    void restore(ObjectiveInstanceSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        if (!id().equals(snapshot.objectiveId())) {
            throw new IllegalArgumentException("Snapshot objective id does not match instance: " + snapshot.objectiveId());
        }
        setStatus(snapshot.status());
        setProgressTicks(snapshot.progressTicks());
        setActiveInteraction(snapshot.activeInteraction().orElse(null));
        setControllingTeam(snapshot.controllingTeam().orElse(null));
        setCompletedByTeam(snapshot.completedByTeam().orElse(null));
        replacePresence(snapshot.presentActors());
        if (carriable == null) {
            if (snapshot.carriable().isPresent()) {
                throw new IllegalArgumentException("Snapshot has carriable state for non-carryable objective: " + id());
            }
            return;
        }
        CarriableObjectiveSnapshot carriableSnapshot = snapshot.carriable()
                .orElse(new CarriableObjectiveSnapshot(CarriableState.AVAILABLE, Optional.empty()));
        carriable.restore(carriableSnapshot.state(), carriableSnapshot.holder().orElse(null));
    }
}
