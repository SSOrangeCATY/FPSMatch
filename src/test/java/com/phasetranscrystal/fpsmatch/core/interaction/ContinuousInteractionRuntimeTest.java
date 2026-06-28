package com.phasetranscrystal.fpsmatch.core.interaction;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContinuousInteractionRuntimeTest {
    private static final UUID ALPHA = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID PLAYER_ONE = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID PLAYER_TWO = UUID.fromString("10000000-0000-0000-0000-000000000002");

    @Test
    void startAndTickCompletesInteractionWithOrderedEvents() {
        ContinuousInteractionRuntime runtime = new ContinuousInteractionRuntime();
        ContinuousInteractionRequest request = request("revive:target", "revive", "player:target", PLAYER_ONE, 2);

        assertTrue(runtime.start(request));
        runtime.tick();
        runtime.tick();

        List<ContinuousInteractionEvent> events = runtime.drainEvents();

        assertEquals(List.of(
                ContinuousInteractionEventType.STARTED,
                ContinuousInteractionEventType.PROGRESS_ADVANCED,
                ContinuousInteractionEventType.PROGRESS_ADVANCED,
                ContinuousInteractionEventType.COMPLETED
        ), events.stream().map(ContinuousInteractionEvent::type).toList());
        assertEquals(request.interactionId(), events.getFirst().interactionId());
        assertEquals(request.actionId(), events.getFirst().actionId());
        assertEquals(request.targetId(), events.getFirst().targetId());
        assertEquals(PLAYER_ONE, events.getFirst().actorId());
        assertEquals(Optional.of(ALPHA), events.getFirst().teamId());
        assertEquals(0, events.getFirst().progressTicks());
        assertEquals(2, events.getFirst().completionTicks());
        assertEquals(2, events.getLast().progressTicks());
        assertFalse(runtime.isActive(request.interactionId()));
    }

    @Test
    void cannotStartSameInteractionTwiceUntilCancelled() {
        ContinuousInteractionRuntime runtime = new ContinuousInteractionRuntime();
        ContinuousInteractionRequest request = request("execute:target", "execute", "player:target", PLAYER_ONE, 3);
        ContinuousInteractionRequest conflict = request("execute:target", "execute", "player:target", PLAYER_TWO, 3);

        assertTrue(runtime.start(request));
        assertFalse(runtime.start(conflict));

        runtime.cancel(request.interactionId());

        assertTrue(runtime.start(conflict));
    }

    @Test
    void cancelRemovesActiveInteractionAndEmitsCancellation() {
        ContinuousInteractionRuntime runtime = new ContinuousInteractionRuntime();
        ContinuousInteractionRequest request = request("extinguish:target", "extinguish", "body:target", PLAYER_ONE, 3);
        runtime.start(request);
        runtime.tick();
        runtime.drainEvents();

        assertTrue(runtime.cancel(request.interactionId()));
        runtime.tick();

        List<ContinuousInteractionEvent> events = runtime.drainEvents();
        assertEquals(List.of(ContinuousInteractionEventType.CANCELLED),
                events.stream().map(ContinuousInteractionEvent::type).toList());
        assertEquals(1, events.getFirst().progressTicks());
        assertFalse(runtime.isActive(request.interactionId()));
    }

    @Test
    void cancelByActorRemovesAllActorInteractions() {
        ContinuousInteractionRuntime runtime = new ContinuousInteractionRuntime();
        ContinuousInteractionRequest first = request("revive:first", "revive", "player:first", PLAYER_ONE, 3);
        ContinuousInteractionRequest second = request("extinguish:first", "extinguish", "body:first", PLAYER_ONE, 3);
        ContinuousInteractionRequest other = request("revive:other", "revive", "player:other", PLAYER_TWO, 3);
        runtime.start(first);
        runtime.start(second);
        runtime.start(other);
        runtime.drainEvents();

        assertEquals(2, runtime.cancelByActor(PLAYER_ONE));

        assertFalse(runtime.isActive(first.interactionId()));
        assertFalse(runtime.isActive(second.interactionId()));
        assertTrue(runtime.isActive(other.interactionId()));
        assertEquals(List.of(
                ContinuousInteractionEventType.CANCELLED,
                ContinuousInteractionEventType.CANCELLED
        ), runtime.drainEvents().stream().map(ContinuousInteractionEvent::type).toList());
    }

    @Test
    void cancelByTargetRemovesAllTargetInteractions() {
        ContinuousInteractionRuntime runtime = new ContinuousInteractionRuntime();
        ContinuousInteractionRequest first = request("revive:first", "revive", "player:target", PLAYER_ONE, 3);
        ContinuousInteractionRequest second = request("inspect:first", "inspect", "player:target", PLAYER_TWO, 3);
        ContinuousInteractionRequest other = request("revive:other", "revive", "player:other", PLAYER_TWO, 3);
        runtime.start(first);
        runtime.start(second);
        runtime.start(other);
        runtime.drainEvents();

        assertEquals(2, runtime.cancelByTarget("player:target"));

        assertFalse(runtime.isActive(first.interactionId()));
        assertFalse(runtime.isActive(second.interactionId()));
        assertTrue(runtime.isActive(other.interactionId()));
        assertEquals(List.of(
                ContinuousInteractionEventType.CANCELLED,
                ContinuousInteractionEventType.CANCELLED
        ), runtime.drainEvents().stream().map(ContinuousInteractionEvent::type).toList());
    }

    @Test
    void explicitCompleteFinishesBeforeTimerEnds() {
        ContinuousInteractionRuntime runtime = new ContinuousInteractionRuntime();
        ContinuousInteractionRequest request = request("use:console", "use", "console:a", PLAYER_ONE, 10);
        runtime.start(request);
        runtime.tick();
        runtime.drainEvents();

        assertTrue(runtime.complete(request.interactionId()));

        List<ContinuousInteractionEvent> events = runtime.drainEvents();
        assertEquals(List.of(ContinuousInteractionEventType.COMPLETED),
                events.stream().map(ContinuousInteractionEvent::type).toList());
        assertEquals(10, events.getFirst().progressTicks());
        assertFalse(runtime.isActive(request.interactionId()));
    }

    @Test
    void snapshotRestoreContinuesWithoutReplayingOldEvents() {
        ContinuousInteractionRuntime runtime = new ContinuousInteractionRuntime();
        ContinuousInteractionRequest request = request("banish:boss", "banish", "boss:lair", PLAYER_ONE, 3);
        runtime.start(request);
        runtime.tick();

        ContinuousInteractionRuntime restored = ContinuousInteractionRuntime.restore(runtime.snapshot());

        assertTrue(restored.drainEvents().isEmpty());
        assertTrue(restored.isActive(request.interactionId()));

        restored.tick(2);

        List<ContinuousInteractionEvent> events = restored.drainEvents();
        assertEquals(List.of(
                ContinuousInteractionEventType.PROGRESS_ADVANCED,
                ContinuousInteractionEventType.PROGRESS_ADVANCED,
                ContinuousInteractionEventType.COMPLETED
        ), events.stream().map(ContinuousInteractionEvent::type).toList());
        assertFalse(restored.isActive(request.interactionId()));
    }

    @Test
    void activeSnapshotRejectsAlreadyCompletedProgress() {
        ContinuousInteractionRequest request = request("use:terminal", "use", "terminal:a", PLAYER_ONE, 3);

        assertThrows(IllegalArgumentException.class, () -> new ContinuousInteractionSnapshot(
                request.interactionId(),
                request.actionId(),
                request.targetId(),
                request.actorId(),
                request.teamId(),
                3,
                request.completionTicks(),
                request.metadata()
        ));
    }

    @Test
    void restoreRejectsDuplicateInteractionIds() {
        ContinuousInteractionRequest first = request("use:terminal", "use", "terminal:a", PLAYER_ONE, 3);
        ContinuousInteractionRequest duplicate = request("use:terminal", "use", "terminal:b", PLAYER_TWO, 3);

        assertThrows(IllegalArgumentException.class, () -> ContinuousInteractionRuntime.restore(List.of(
                new ContinuousInteractionSnapshot(
                        first.interactionId(),
                        first.actionId(),
                        first.targetId(),
                        first.actorId(),
                        first.teamId(),
                        1,
                        first.completionTicks(),
                        first.metadata()
                ),
                new ContinuousInteractionSnapshot(
                        duplicate.interactionId(),
                        duplicate.actionId(),
                        duplicate.targetId(),
                        duplicate.actorId(),
                        duplicate.teamId(),
                        1,
                        duplicate.completionTicks(),
                        duplicate.metadata()
                )
        )));
    }

    @Test
    void rejectsNegativeTickAmount() {
        ContinuousInteractionRuntime runtime = new ContinuousInteractionRuntime();

        assertThrows(IllegalArgumentException.class, () -> runtime.tick(-1));
    }

    private static ContinuousInteractionRequest request(
            String interactionId,
            String actionId,
            String targetId,
            UUID actorId,
            int completionTicks
    ) {
        return new ContinuousInteractionRequest(
                interactionId,
                actionId,
                targetId,
                actorId,
                Optional.of(ALPHA),
                completionTicks,
                Map.of("source", "test")
        );
    }
}
