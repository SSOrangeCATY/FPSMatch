package com.phasetranscrystal.fpsmatch.core.objective;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObjectiveRuntimeTest {
    private static final UUID ALPHA = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID BRAVO = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    private static final UUID PLAYER_ONE = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID PLAYER_TWO = UUID.fromString("10000000-0000-0000-0000-000000000002");

    @Test
    void interactionProgressEventsCanBeDrainedInOrder() {
        ObjectiveRuntime runtime = new ObjectiveRuntime();
        ObjectiveInstance objective = runtime.addObjective(ObjectiveDefinition.builder("channel")
                .displayName("Channel")
                .completionTicks(2)
                .build());
        ObjectiveActor actor = ObjectiveActor.player(PLAYER_ONE, ALPHA);

        assertTrue(runtime.drainEvents().isEmpty());

        assertTrue(runtime.startInteraction(objective.id(), actor));
        runtime.tick();
        runtime.tick();

        List<ObjectiveEvent> events = runtime.drainEvents();

        assertEquals(List.of(
                ObjectiveEventType.INTERACTION_STARTED,
                ObjectiveEventType.PROGRESS_ADVANCED,
                ObjectiveEventType.PROGRESS_ADVANCED,
                ObjectiveEventType.COMPLETED
        ), events.stream().map(ObjectiveEvent::type).toList());
        assertEquals(objective.id(), events.getFirst().objectiveId());
        assertEquals(ObjectiveStatus.IN_PROGRESS, events.getFirst().status());
        assertEquals(actor, events.getFirst().actor().orElseThrow());
        assertEquals(ALPHA, events.getFirst().teamId().orElseThrow());
        assertEquals(0, events.getFirst().progressTicks());
        assertEquals(2, events.getFirst().completionTicks());
        assertEquals(1, events.get(1).progressTicks());
        assertEquals(2, events.get(2).progressTicks());
        assertEquals(ObjectiveStatus.COMPLETED, events.get(3).status());
        assertEquals(ALPHA, events.get(3).teamId().orElseThrow());
        assertEquals(2, events.get(3).progressTicks());
        assertTrue(runtime.drainEvents().isEmpty());
    }

    @Test
    void cancelledInteractionEmitsCancellationEvent() {
        ObjectiveRuntime runtime = new ObjectiveRuntime();
        ObjectiveInstance objective = runtime.addObjective(ObjectiveDefinition.builder("revive")
                .displayName("Revive")
                .completionTicks(3)
                .build());
        ObjectiveActor actor = ObjectiveActor.player(PLAYER_ONE, ALPHA);

        runtime.startInteraction(objective.id(), actor);
        runtime.tick();
        runtime.drainEvents();

        runtime.cancelInteraction(objective.id(), actor);

        List<ObjectiveEvent> events = runtime.drainEvents();

        assertEquals(List.of(ObjectiveEventType.CANCELLED), events.stream().map(ObjectiveEvent::type).toList());
        assertEquals(objective.id(), events.getFirst().objectiveId());
        assertEquals(ObjectiveStatus.AVAILABLE, events.getFirst().status());
        assertEquals(actor, events.getFirst().actor().orElseThrow());
        assertEquals(ALPHA, events.getFirst().teamId().orElseThrow());
        assertEquals(0, events.getFirst().progressTicks());
        assertEquals(3, events.getFirst().completionTicks());
    }

    @Test
    void zoneObjectiveEmitsContestedEventOnceWhenMultipleTeamsArePresent() {
        ObjectiveRuntime runtime = new ObjectiveRuntime();
        ObjectiveInstance extract = runtime.addObjective(ObjectiveDefinition.builder("extract")
                .displayName("Extract")
                .completionTicks(3)
                .controlPolicy(ControlPolicy.teamExclusive())
                .build());

        runtime.updatePresence(extract.id(), Set.of(ObjectiveActor.player(PLAYER_ONE, ALPHA)));
        runtime.tick();
        runtime.drainEvents();

        runtime.updatePresence(extract.id(), Set.of(
                ObjectiveActor.player(PLAYER_ONE, ALPHA),
                ObjectiveActor.player(PLAYER_TWO, BRAVO)
        ));
        runtime.tick();

        List<ObjectiveEvent> events = runtime.drainEvents();

        assertEquals(List.of(ObjectiveEventType.CONTESTED), events.stream().map(ObjectiveEvent::type).toList());
        assertEquals(extract.id(), events.getFirst().objectiveId());
        assertEquals(ObjectiveStatus.CONTESTED, events.getFirst().status());
        assertEquals(1, events.getFirst().progressTicks());
        assertEquals(3, events.getFirst().completionTicks());
        assertFalse(events.getFirst().actor().isPresent());
        assertFalse(events.getFirst().teamId().isPresent());

        runtime.tick();

        assertTrue(runtime.drainEvents().isEmpty());
    }

    @Test
    void zoneObjectiveEmitsResetEventWhenEmpty() {
        ObjectiveRuntime runtime = new ObjectiveRuntime();
        ObjectiveInstance extract = runtime.addObjective(ObjectiveDefinition.builder("extract")
                .displayName("Extract")
                .completionTicks(3)
                .controlPolicy(ControlPolicy.teamExclusive().resetWhenEmpty())
                .build());

        runtime.updatePresence(extract.id(), Set.of(ObjectiveActor.player(PLAYER_ONE, ALPHA)));
        runtime.tick();
        runtime.drainEvents();

        runtime.updatePresence(extract.id(), Set.of());
        runtime.tick();

        List<ObjectiveEvent> events = runtime.drainEvents();

        assertEquals(List.of(ObjectiveEventType.RESET), events.stream().map(ObjectiveEvent::type).toList());
        assertEquals(extract.id(), events.getFirst().objectiveId());
        assertEquals(ObjectiveStatus.AVAILABLE, events.getFirst().status());
        assertEquals(0, events.getFirst().progressTicks());
        assertEquals(3, events.getFirst().completionTicks());
        assertFalse(events.getFirst().actor().isPresent());
        assertFalse(events.getFirst().teamId().isPresent());

        runtime.tick();

        assertTrue(runtime.drainEvents().isEmpty());
    }

    @Test
    void completedParentUnlocksDependentObjective() {
        ObjectiveRuntime runtime = new ObjectiveRuntime();
        ObjectiveInstance clue = runtime.addObjective(ObjectiveDefinition.builder("clue")
                .displayName("Investigate")
                .completionTicks(2)
                .build());
        ObjectiveInstance lair = runtime.addObjective(ObjectiveDefinition.builder("lair")
                .displayName("Locate")
                .requires("clue")
                .completionTicks(1)
                .build());

        assertEquals(ObjectiveStatus.LOCKED, lair.status());

        runtime.startInteraction(clue.id(), ObjectiveActor.player(PLAYER_ONE, ALPHA));
        runtime.tick();
        runtime.tick();

        assertEquals(ObjectiveStatus.COMPLETED, clue.status());
        assertEquals(ObjectiveStatus.AVAILABLE, lair.status());
    }

    @Test
    void interactionProgressIsCancelledWhenActorLeaves() {
        ObjectiveRuntime runtime = new ObjectiveRuntime();
        ObjectiveInstance objective = runtime.addObjective(ObjectiveDefinition.builder("revive")
                .displayName("Revive")
                .completionTicks(3)
                .build());
        ObjectiveActor actor = ObjectiveActor.player(PLAYER_ONE, ALPHA);

        runtime.startInteraction(objective.id(), actor);
        runtime.tick();
        runtime.cancelInteraction(objective.id(), actor);
        runtime.tick();
        runtime.tick();

        assertEquals(ObjectiveStatus.AVAILABLE, objective.status());
        assertEquals(0, objective.progressTicks());
        assertFalse(objective.activeInteraction().isPresent());
    }

    @Test
    void runtimeSnapshotRestoresActiveInteractionProgressWithoutReplayingEvents() {
        ObjectiveRuntime runtime = new ObjectiveRuntime();
        ObjectiveInstance objective = runtime.addObjective(ObjectiveDefinition.builder("channel")
                .displayName("Channel")
                .completionTicks(3)
                .build());
        ObjectiveActor actor = ObjectiveActor.player(PLAYER_ONE, ALPHA);

        assertTrue(runtime.startInteraction(objective.id(), actor));
        runtime.tick();
        runtime.tick();

        ObjectiveRuntimeSnapshot snapshot = runtime.snapshot();
        ObjectiveRuntime restored = new ObjectiveRuntime();
        ObjectiveInstance restoredObjective = restored.addObjective(ObjectiveDefinition.builder("channel")
                .displayName("Channel")
                .completionTicks(3)
                .build());
        restored.restore(snapshot);

        assertEquals(ObjectiveStatus.IN_PROGRESS, restoredObjective.status());
        assertEquals(2, restoredObjective.progressTicks());
        assertEquals(actor, restoredObjective.activeInteraction().orElseThrow());
        assertEquals(ALPHA, restoredObjective.controllingTeam().orElseThrow());
        assertTrue(restored.drainEvents().isEmpty());

        restored.tick();

        List<ObjectiveEvent> events = restored.drainEvents();
        assertEquals(List.of(
                ObjectiveEventType.PROGRESS_ADVANCED,
                ObjectiveEventType.COMPLETED
        ), events.stream().map(ObjectiveEvent::type).toList());
        assertEquals(3, restoredObjective.progressTicks());
        assertEquals(ObjectiveStatus.COMPLETED, restoredObjective.status());
        assertEquals(ALPHA, events.getLast().teamId().orElseThrow());
    }

    @Test
    void zoneTimerPausesWhenContestedAndCompletesWhenOwned() {
        ObjectiveRuntime runtime = new ObjectiveRuntime();
        ObjectiveInstance extract = runtime.addObjective(ObjectiveDefinition.builder("extract")
                .displayName("Extract")
                .completionTicks(3)
                .controlPolicy(ControlPolicy.teamExclusive())
                .build());

        runtime.updatePresence(extract.id(), Set.of(ObjectiveActor.player(PLAYER_ONE, ALPHA)));
        runtime.tick();
        assertEquals(1, extract.progressTicks());

        runtime.updatePresence(extract.id(), Set.of(
                ObjectiveActor.player(PLAYER_ONE, ALPHA),
                ObjectiveActor.player(PLAYER_TWO, BRAVO)
        ));
        runtime.tick();
        assertEquals(ObjectiveStatus.CONTESTED, extract.status());
        assertEquals(1, extract.progressTicks());

        runtime.updatePresence(extract.id(), Set.of(ObjectiveActor.player(PLAYER_ONE, ALPHA)));
        runtime.tick();
        runtime.tick();

        assertEquals(ObjectiveStatus.COMPLETED, extract.status());
        assertEquals(ALPHA, extract.controllingTeam().orElseThrow());
    }

    @Test
    void zoneTimerCanResetWhenNoActorsRemainPresent() {
        ObjectiveRuntime runtime = new ObjectiveRuntime();
        ObjectiveInstance extract = runtime.addObjective(ObjectiveDefinition.builder("extract")
                .displayName("Extract")
                .completionTicks(3)
                .controlPolicy(ControlPolicy.teamExclusive().resetWhenEmpty())
                .build());

        runtime.updatePresence(extract.id(), Set.of(ObjectiveActor.player(PLAYER_ONE, ALPHA)));
        runtime.tick();
        assertEquals(1, extract.progressTicks());

        runtime.updatePresence(extract.id(), Set.of());
        runtime.tick();

        assertEquals(ObjectiveStatus.AVAILABLE, extract.status());
        assertEquals(0, extract.progressTicks());
        assertFalse(extract.controllingTeam().isPresent());
    }

    @Test
    void carryableObjectiveDropsAndCanBeTakenByAnotherTeam() {
        ObjectiveRuntime runtime = new ObjectiveRuntime();
        ObjectiveInstance token = runtime.addObjective(ObjectiveDefinition.builder("token")
                .displayName("Carry Token")
                .carryPolicy(CarryPolicy.singleHolderDropsOnDown())
                .build());

        runtime.pickUp(token.id(), ObjectiveActor.player(PLAYER_ONE, ALPHA));
        assertEquals(CarriableState.CARRIED, token.carriable().orElseThrow().state());
        assertEquals(PLAYER_ONE, token.carriable().orElseThrow().holder().orElseThrow().playerId());

        runtime.drop(token.id());
        assertEquals(CarriableState.DROPPED, token.carriable().orElseThrow().state());
        assertFalse(token.carriable().orElseThrow().holder().isPresent());

        runtime.pickUp(token.id(), ObjectiveActor.player(PLAYER_TWO, BRAVO));
        runtime.complete(token.id());

        assertEquals(ObjectiveStatus.COMPLETED, token.status());
        assertEquals(BRAVO, token.completedByTeam().orElseThrow());
    }

    @Test
    void snapshotListsOnlyObjectivesVisibleToViewer() {
        ObjectiveRuntime runtime = new ObjectiveRuntime();
        ObjectiveInstance publicObjective = runtime.addObjective(ObjectiveDefinition.builder("public")
                .displayName("Global")
                .visibility(VisibilityPolicy.global())
                .build());
        ObjectiveInstance privateObjective = runtime.addObjective(ObjectiveDefinition.builder("private")
                .displayName("Squad Intel")
                .visibility(VisibilityPolicy.teams(Set.of(ALPHA)))
                .build());

        ObjectiveSnapshot alphaView = runtime.snapshotFor(ObjectiveViewer.team(ALPHA));
        ObjectiveSnapshot bravoView = runtime.snapshotFor(ObjectiveViewer.team(BRAVO));

        assertEquals(List.of(publicObjective.id(), privateObjective.id()), alphaView.objectiveIds());
        assertEquals(List.of(publicObjective.id()), bravoView.objectiveIds());
    }
}
