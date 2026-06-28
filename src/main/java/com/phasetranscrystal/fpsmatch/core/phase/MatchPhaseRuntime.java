package com.phasetranscrystal.fpsmatch.core.phase;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class MatchPhaseRuntime {
    private final List<MatchPhaseDefinition> definitions;
    private final List<MatchPhaseEvent> events = new ArrayList<>();
    private int currentPhaseIndex;
    private int elapsedTicks;
    private int phaseElapsedTicks;

    private MatchPhaseRuntime(List<MatchPhaseDefinition> definitions) {
        if (definitions.isEmpty()) {
            throw new IllegalArgumentException("definitions must not be empty");
        }
        this.definitions = List.copyOf(definitions);
        skipZeroDurationPhases();
    }

    public static MatchPhaseRuntime sequence(List<MatchPhaseDefinition> definitions) {
        return new MatchPhaseRuntime(List.copyOf(Objects.requireNonNull(definitions, "definitions")));
    }

    public static MatchPhaseRuntime restore(List<MatchPhaseDefinition> definitions, MatchPhaseSnapshot snapshot) {
        MatchPhaseRuntime runtime = new MatchPhaseRuntime(definitions);
        Objects.requireNonNull(snapshot, "snapshot");
        if (snapshot.currentPhaseIndex() >= runtime.definitions.size()) {
            throw new IllegalArgumentException("snapshot currentPhaseIndex is out of bounds");
        }
        MatchPhaseDefinition definition = runtime.definitions.get(snapshot.currentPhaseIndex());
        if (!definition.phaseId().equals(snapshot.currentPhaseId())) {
            throw new IllegalArgumentException("snapshot currentPhaseId does not match definitions");
        }
        runtime.currentPhaseIndex = snapshot.currentPhaseIndex();
        runtime.elapsedTicks = snapshot.elapsedTicks();
        runtime.phaseElapsedTicks = snapshot.phaseElapsedTicks();
        runtime.events.clear();
        runtime.skipZeroDurationPhases();
        return runtime;
    }

    public void tick(int ticks) {
        if (ticks < 0) {
            throw new IllegalArgumentException("ticks must not be negative");
        }
        for (int index = 0; index < ticks; index++) {
            elapsedTicks++;
            phaseElapsedTicks++;
            advanceCompletedPhase();
        }
    }

    public String currentPhaseId() {
        return currentDefinition().phaseId();
    }

    public int elapsedTicks() {
        return elapsedTicks;
    }

    public int phaseElapsedTicks() {
        return phaseElapsedTicks;
    }

    public List<MatchPhaseDefinition> definitions() {
        return definitions;
    }

    public MatchPhaseSnapshot snapshot() {
        return new MatchPhaseSnapshot(currentPhaseId(), currentPhaseIndex, elapsedTicks, phaseElapsedTicks);
    }

    public List<MatchPhaseEvent> drainEvents() {
        List<MatchPhaseEvent> drained = List.copyOf(events);
        events.clear();
        return drained;
    }

    private MatchPhaseDefinition currentDefinition() {
        return definitions.get(currentPhaseIndex);
    }

    private void advanceCompletedPhase() {
        MatchPhaseDefinition current = currentDefinition();
        if (current.openEnded() || phaseElapsedTicks < current.durationTicks()) {
            return;
        }
        String previousPhaseId = current.phaseId();
        advanceToNextPhase();
        skipZeroDurationPhases();
        if (!previousPhaseId.equals(currentPhaseId())) {
            events.add(new MatchPhaseEvent(previousPhaseId, currentPhaseId(), elapsedTicks));
        }
    }

    private void advanceToNextPhase() {
        if (currentPhaseIndex < definitions.size() - 1) {
            currentPhaseIndex++;
            phaseElapsedTicks = 0;
        }
    }

    private void skipZeroDurationPhases() {
        while (!currentDefinition().openEnded()
                && currentDefinition().durationTicks() == 0
                && currentPhaseIndex < definitions.size() - 1) {
            currentPhaseIndex++;
            phaseElapsedTicks = 0;
        }
    }
}
