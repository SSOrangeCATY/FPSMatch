package com.phasetranscrystal.fpsmatch.core.match;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoundLifecycleTest {
    @Test
    void waitingPhaseStartsRoundAfterDuration() {
        List<String> events = new ArrayList<>();
        RoundLifecycle<String, String> lifecycle = RoundLifecycle.<String, String>builder()
                .waitingTicks(2)
                .roundTicks(10)
                .roundEndTicks(3)
                .onRoundStart(() -> events.add("round-start"))
                .build();

        assertEquals(RoundPhase.WAITING, lifecycle.phase());

        lifecycle.tick();
        assertEquals(RoundPhase.WAITING, lifecycle.phase());

        lifecycle.tick();
        assertEquals(RoundPhase.ACTIVE_ROUND, lifecycle.phase());
        assertEquals(List.of("round-start"), events);
        assertEquals(0, lifecycle.roundElapsedTicks());
    }

    @Test
    void ruleResultEntersRoundEndAndNextRoundIsRequestedAfterDelay() {
        List<RoundResult<String, String>> results = new ArrayList<>();
        int[] nextRoundRequests = {0};
        RoundRule<String, String> rule = lifecycle -> lifecycle.roundElapsedTicks() == 1
                ? Optional.of(new RoundResult<>("ct", "aced"))
                : Optional.empty();
        RoundLifecycle<String, String> lifecycle = RoundLifecycle.<String, String>builder()
                .waitingTicks(0)
                .roundTicks(20)
                .roundEndTicks(2)
                .addRule(rule)
                .onRoundEnd(results::add)
                .onNextRoundRequested(() -> nextRoundRequests[0]++)
                .build();

        lifecycle.tick();
        assertEquals(RoundPhase.ACTIVE_ROUND, lifecycle.phase());

        lifecycle.tick();
        assertEquals(RoundPhase.ROUND_END_WAITING, lifecycle.phase());
        assertEquals(List.of(new RoundResult<>("ct", "aced")), results);
        assertEquals(0, nextRoundRequests[0]);

        lifecycle.tick();
        assertEquals(0, nextRoundRequests[0]);

        lifecycle.tick();
        assertEquals(1, nextRoundRequests[0]);
        assertEquals(RoundPhase.ROUND_END_WAITING, lifecycle.phase());
    }

    @Test
    void roundTimeoutUsesConfiguredResult() {
        List<RoundResult<String, String>> results = new ArrayList<>();
        RoundLifecycle<String, String> lifecycle = RoundLifecycle.<String, String>builder()
                .waitingTicks(0)
                .roundTicks(2)
                .roundEndTicks(1)
                .timeoutResult(() -> new RoundResult<>("ct", "timeout"))
                .onRoundEnd(results::add)
                .build();

        lifecycle.tick();
        lifecycle.tick();
        assertEquals(RoundPhase.ACTIVE_ROUND, lifecycle.phase());

        lifecycle.tick();
        assertEquals(RoundPhase.ROUND_END_WAITING, lifecycle.phase());
        assertEquals(List.of(new RoundResult<>("ct", "timeout")), results);
    }

    @Test
    void pauseBlocksWaitingAndRoundTimers() {
        RoundLifecycle<String, String> lifecycle = RoundLifecycle.<String, String>builder()
                .waitingTicks(1)
                .roundTicks(2)
                .roundEndTicks(1)
                .build();

        lifecycle.setPaused(true);
        lifecycle.tick();
        assertTrue(lifecycle.isPaused());
        assertEquals(RoundPhase.PAUSED, lifecycle.phase());
        assertEquals(0, lifecycle.phaseElapsedTicks());

        lifecycle.setPaused(false);
        lifecycle.tick();
        assertFalse(lifecycle.isPaused());
        assertEquals(RoundPhase.ACTIVE_ROUND, lifecycle.phase());
        assertEquals(0, lifecycle.roundElapsedTicks());
    }

    @Test
    void resetForNextRoundReturnsToWaitingAndClearsResult() {
        RoundLifecycle<String, String> lifecycle = RoundLifecycle.<String, String>builder()
                .waitingTicks(0)
                .roundTicks(1)
                .roundEndTicks(1)
                .timeoutResult(() -> new RoundResult<>("ct", "timeout"))
                .build();

        lifecycle.tick();
        lifecycle.tick();
        assertTrue(lifecycle.lastResult().isPresent());

        lifecycle.resetForNextRound();

        assertEquals(RoundPhase.WAITING, lifecycle.phase());
        assertEquals(0, lifecycle.phaseElapsedTicks());
        assertEquals(0, lifecycle.roundElapsedTicks());
        assertTrue(lifecycle.lastResult().isEmpty());
    }

    @Test
    void waitingTickHookFiresDuringWaitingPhase() {
        List<Integer> ticks = new ArrayList<>();
        AtomicReference<RoundLifecycle<String, String>> ref = new AtomicReference<>();
        RoundLifecycle<String, String> lifecycle = RoundLifecycle.<String, String>builder()
                .waitingTicks(2)
                .roundTicks(1)
                .roundEndTicks(1)
                .onWaitingTick(ctx -> ticks.add(ref.get().phaseElapsedTicks()))
                .build();
        ref.set(lifecycle);

        lifecycle.tick();
        lifecycle.tick();

        assertEquals(List.of(1, 2), ticks);
    }

    @Test
    void roundTickHookFiresDuringActiveRound() {
        List<Integer> ticks = new ArrayList<>();
        AtomicReference<RoundLifecycle<String, String>> ref = new AtomicReference<>();
        RoundLifecycle<String, String> lifecycle = RoundLifecycle.<String, String>builder()
                .waitingTicks(0)
                .roundTicks(2)
                .roundEndTicks(1)
                .onRoundTick(ctx -> ticks.add(ref.get().roundElapsedTicks()))
                .build();
        ref.set(lifecycle);

        lifecycle.tick();
        lifecycle.tick();
        lifecycle.tick();

        assertEquals(List.of(0, 1, 2), ticks);
    }
}
