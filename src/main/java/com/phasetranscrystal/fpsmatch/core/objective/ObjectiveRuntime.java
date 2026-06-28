package com.phasetranscrystal.fpsmatch.core.objective;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class ObjectiveRuntime {
    private final Map<String, ObjectiveInstance> objectives = new LinkedHashMap<>();
    private final List<ObjectiveEvent> events = new ArrayList<>();

    public ObjectiveInstance addObjective(ObjectiveDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        if (objectives.containsKey(definition.id())) {
            throw new IllegalArgumentException("Objective already exists: " + definition.id());
        }
        ObjectiveInstance objective = new ObjectiveInstance(definition);
        objectives.put(definition.id(), objective);
        refreshLocks();
        return objective;
    }

    public Optional<ObjectiveInstance> get(String objectiveId) {
        return Optional.ofNullable(objectives.get(objectiveId));
    }

    public Collection<ObjectiveInstance> objectives() {
        return List.copyOf(objectives.values());
    }

    public List<ObjectiveEvent> drainEvents() {
        List<ObjectiveEvent> drained = List.copyOf(events);
        events.clear();
        return drained;
    }

    public ObjectiveRuntimeSnapshot snapshot() {
        return new ObjectiveRuntimeSnapshot(objectives.values().stream()
                .map(ObjectiveInstanceSnapshot::from)
                .toList());
    }

    public void restore(ObjectiveRuntimeSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        events.clear();
        for (ObjectiveInstanceSnapshot objectiveSnapshot : snapshot.objectives()) {
            ObjectiveInstance objective = objectives.get(objectiveSnapshot.objectiveId());
            if (objective == null) {
                throw new IllegalArgumentException("Unknown objective in snapshot: " + objectiveSnapshot.objectiveId());
            }
            objective.restore(objectiveSnapshot);
        }
        refreshLocks();
    }

    public boolean startInteraction(String objectiveId, ObjectiveActor actor) {
        ObjectiveInstance objective = requireObjective(objectiveId);
        Objects.requireNonNull(actor, "actor");
        if (!canProgress(objective)) {
            return false;
        }
        objective.setActiveInteraction(actor);
        objective.setControllingTeam(actor.teamId());
        objective.setStatus(ObjectiveStatus.IN_PROGRESS);
        emit(ObjectiveEventType.INTERACTION_STARTED, objective, actor, actor.teamId());
        return true;
    }

    public void cancelInteraction(String objectiveId, ObjectiveActor actor) {
        ObjectiveInstance objective = requireObjective(objectiveId);
        if (objective.activeInteraction().filter(actor::equals).isPresent()) {
            objective.setActiveInteraction(null);
            objective.setProgressTicks(0);
            objective.setControllingTeam(null);
            if (objective.status() != ObjectiveStatus.COMPLETED && objective.status() != ObjectiveStatus.FAILED) {
                objective.setStatus(ObjectiveStatus.AVAILABLE);
            }
            emit(ObjectiveEventType.CANCELLED, objective, actor, actor.teamId());
        }
    }

    public void updatePresence(String objectiveId, Set<ObjectiveActor> actors) {
        ObjectiveInstance objective = requireObjective(objectiveId);
        Objects.requireNonNull(actors, "actors");
        objective.replacePresence(actors);
    }

    public boolean pickUp(String objectiveId, ObjectiveActor actor) {
        ObjectiveInstance objective = requireObjective(objectiveId);
        Objects.requireNonNull(actor, "actor");
        Optional<CarriableObjectiveState> carriable = objective.carriable();
        if (carriable.isEmpty() || objective.status() == ObjectiveStatus.COMPLETED || objective.status() == ObjectiveStatus.FAILED) {
            return false;
        }
        if (objective.definition().carryPolicy().singleHolder() && carriable.get().holder().isPresent()) {
            return false;
        }
        carriable.get().pickUp(actor);
        objective.setControllingTeam(actor.teamId());
        return true;
    }

    public boolean drop(String objectiveId) {
        ObjectiveInstance objective = requireObjective(objectiveId);
        Optional<CarriableObjectiveState> carriable = objective.carriable();
        if (carriable.isEmpty() || carriable.get().holder().isEmpty()) {
            return false;
        }
        carriable.get().drop();
        objective.setControllingTeam(null);
        return true;
    }

    public void complete(String objectiveId) {
        ObjectiveInstance objective = requireObjective(objectiveId);
        UUID team = objective.controllingTeam().orElseGet(() ->
                objective.carriable().flatMap(CarriableObjectiveState::holder).map(ObjectiveActor::teamId).orElse(null));
        complete(objective, team);
    }

    public void fail(String objectiveId) {
        ObjectiveInstance objective = requireObjective(objectiveId);
        objective.setActiveInteraction(null);
        objective.setStatus(ObjectiveStatus.FAILED);
        refreshLocks();
    }

    public void tick() {
        for (ObjectiveInstance objective : objectives.values()) {
            if (objective.status() == ObjectiveStatus.COMPLETED || objective.status() == ObjectiveStatus.FAILED) {
                continue;
            }
            if (objective.definition().controlPolicy().enabled()) {
                tickControlObjective(objective);
            } else if (objective.activeInteraction().isPresent()) {
                advanceProgress(objective, objective.activeInteraction().orElseThrow().teamId());
            }
        }
        refreshLocks();
    }

    public ObjectiveSnapshot snapshotFor(ObjectiveViewer viewer) {
        List<ObjectiveView> views = objectives.values().stream()
                .filter(objective -> objective.definition().visibility().canSee(viewer))
                .map(ObjectiveView::from)
                .toList();
        List<String> ids = views.stream().map(ObjectiveView::id).toList();
        return new ObjectiveSnapshot(ids, views);
    }

    private void tickControlObjective(ObjectiveInstance objective) {
        if (!canProgress(objective)) {
            return;
        }
        Set<UUID> teams = objective.presentActors().stream()
                .map(ObjectiveActor::teamId)
                .collect(Collectors.toSet());
        if (teams.isEmpty()) {
            boolean shouldEmitReset = objective.definition().controlPolicy().resetsWhenEmpty()
                    && (objective.progressTicks() > 0
                    || objective.controllingTeam().isPresent()
                    || objective.status() == ObjectiveStatus.IN_PROGRESS
                    || objective.status() == ObjectiveStatus.CONTESTED);
            if (objective.status() == ObjectiveStatus.IN_PROGRESS || objective.status() == ObjectiveStatus.CONTESTED) {
                objective.setStatus(ObjectiveStatus.AVAILABLE);
            }
            if (objective.definition().controlPolicy().resetsWhenEmpty()) {
                objective.setProgressTicks(0);
                objective.setControllingTeam(null);
                if (shouldEmitReset) {
                    emit(ObjectiveEventType.RESET, objective, null, null);
                }
            }
            return;
        }
        if (teams.size() > 1 && objective.definition().controlPolicy().pauseWhenContested()) {
            boolean alreadyContested = objective.status() == ObjectiveStatus.CONTESTED;
            objective.setStatus(ObjectiveStatus.CONTESTED);
            if (!alreadyContested) {
                emit(ObjectiveEventType.CONTESTED, objective, null, null);
            }
            return;
        }
        UUID team = new ArrayList<>(teams).getFirst();
        objective.setControllingTeam(team);
        objective.setStatus(ObjectiveStatus.IN_PROGRESS);
        advanceProgress(objective, team);
    }

    private void advanceProgress(ObjectiveInstance objective, UUID teamId) {
        if (!canProgress(objective)) {
            return;
        }
        int completionTicks = objective.definition().completionTicks();
        if (completionTicks <= 0) {
            complete(objective, teamId);
            return;
        }
        objective.setProgressTicks(objective.progressTicks() + 1);
        objective.setStatus(ObjectiveStatus.IN_PROGRESS);
        emit(ObjectiveEventType.PROGRESS_ADVANCED, objective, objective.activeInteraction().orElse(null), teamId);
        if (objective.progressTicks() >= completionTicks) {
            complete(objective, teamId);
        }
    }

    private void complete(ObjectiveInstance objective, UUID teamId) {
        objective.setProgressTicks(objective.definition().completionTicks());
        objective.setActiveInteraction(null);
        objective.setCompletedByTeam(teamId);
        objective.setStatus(ObjectiveStatus.COMPLETED);
        objective.carriable().ifPresent(CarriableObjectiveState::resolve);
        emit(ObjectiveEventType.COMPLETED, objective, null, teamId);
        refreshLocks();
    }

    private void emit(ObjectiveEventType type, ObjectiveInstance objective, ObjectiveActor actor, UUID teamId) {
        events.add(new ObjectiveEvent(
                type,
                objective.id(),
                objective.status(),
                Optional.ofNullable(actor),
                Optional.ofNullable(teamId),
                objective.progressTicks(),
                objective.definition().completionTicks()
        ));
    }

    private boolean canProgress(ObjectiveInstance objective) {
        return objective.status() != ObjectiveStatus.LOCKED
                && objective.status() != ObjectiveStatus.COMPLETED
                && objective.status() != ObjectiveStatus.FAILED
                && requirementsCompleted(objective.definition());
    }

    private boolean requirementsCompleted(ObjectiveDefinition definition) {
        for (String requirement : definition.requirements()) {
            ObjectiveInstance required = objectives.get(requirement);
            if (required == null || required.status() != ObjectiveStatus.COMPLETED) {
                return false;
            }
        }
        return true;
    }

    private void refreshLocks() {
        for (ObjectiveInstance objective : objectives.values()) {
            if (objective.status() == ObjectiveStatus.COMPLETED || objective.status() == ObjectiveStatus.FAILED) {
                continue;
            }
            if (requirementsCompleted(objective.definition())) {
                if (objective.status() == ObjectiveStatus.LOCKED) {
                    objective.setStatus(ObjectiveStatus.AVAILABLE);
                }
            } else {
                objective.setActiveInteraction(null);
                objective.setProgressTicks(0);
                objective.setStatus(ObjectiveStatus.LOCKED);
            }
        }
    }

    private ObjectiveInstance requireObjective(String objectiveId) {
        ObjectiveInstance objective = objectives.get(objectiveId);
        if (objective == null) {
            throw new IllegalArgumentException("Unknown objective: " + objectiveId);
        }
        return objective;
    }
}
