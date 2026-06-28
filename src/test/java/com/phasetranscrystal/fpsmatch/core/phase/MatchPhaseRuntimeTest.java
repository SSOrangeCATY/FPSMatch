package com.phasetranscrystal.fpsmatch.core.phase;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatchPhaseRuntimeTest {
    @Test
    void zeroDurationPhaseIsSkippedAtStart() {
        MatchPhaseRuntime runtime = MatchPhaseRuntime.sequence(List.of(
                MatchPhaseDefinition.timed("spawn_protection", 0),
                MatchPhaseDefinition.timed("live", 3),
                MatchPhaseDefinition.openEnded("settled")
        ));

        assertEquals("live", runtime.currentPhaseId());
        assertEquals(0, runtime.elapsedTicks());
        assertEquals(0, runtime.phaseElapsedTicks());
        assertTrue(runtime.drainEvents().isEmpty());
    }

    @Test
    void timedPhasesAdvanceAndEmitTransitionEvents() {
        MatchPhaseRuntime runtime = MatchPhaseRuntime.sequence(List.of(
                MatchPhaseDefinition.timed("spawn_protection", 2),
                MatchPhaseDefinition.timed("live", 3),
                MatchPhaseDefinition.openEnded("settled")
        ));

        runtime.tick(1);
        assertEquals("spawn_protection", runtime.currentPhaseId());
        assertTrue(runtime.drainEvents().isEmpty());

        runtime.tick(1);
        assertEquals("live", runtime.currentPhaseId());
        assertEquals(List.of(new MatchPhaseEvent("spawn_protection", "live", 2)), runtime.drainEvents());

        runtime.tick(3);
        assertEquals("settled", runtime.currentPhaseId());
        assertEquals(List.of(new MatchPhaseEvent("live", "settled", 5)), runtime.drainEvents());
    }

    @Test
    void restoresSnapshotAndContinuesFromSavedPhase() {
        MatchPhaseRuntime runtime = MatchPhaseRuntime.sequence(List.of(
                MatchPhaseDefinition.timed("spawn_protection", 2),
                MatchPhaseDefinition.timed("live", 3),
                MatchPhaseDefinition.openEnded("settled")
        ));
        runtime.tick(3);

        MatchPhaseRuntime restored = MatchPhaseRuntime.restore(runtime.definitions(), runtime.snapshot());

        assertEquals("live", restored.currentPhaseId());
        assertEquals(3, restored.elapsedTicks());
        assertEquals(1, restored.phaseElapsedTicks());

        restored.tick(2);

        assertEquals("settled", restored.currentPhaseId());
        assertEquals(List.of(new MatchPhaseEvent("live", "settled", 5)), restored.drainEvents());
    }

    @Test
    void openEndedPhaseDoesNotAutoAdvance() {
        MatchPhaseRuntime runtime = MatchPhaseRuntime.sequence(List.of(
                MatchPhaseDefinition.openEnded("live"),
                MatchPhaseDefinition.openEnded("settled")
        ));

        runtime.tick(100);

        assertEquals("live", runtime.currentPhaseId());
        assertEquals(100, runtime.phaseElapsedTicks());
        assertTrue(runtime.drainEvents().isEmpty());
    }

    @Test
    void rejectsNegativeTickAmount() {
        MatchPhaseRuntime runtime = MatchPhaseRuntime.sequence(List.of(
                MatchPhaseDefinition.openEnded("live")
        ));

        assertThrows(IllegalArgumentException.class, () -> runtime.tick(-1));
    }
}
