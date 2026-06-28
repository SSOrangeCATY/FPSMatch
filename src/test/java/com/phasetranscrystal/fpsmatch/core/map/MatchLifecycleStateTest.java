package com.phasetranscrystal.fpsmatch.core.map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatchLifecycleStateTest {
    @Test
    void cancelledStartDoesNotMarkMatchStarted() {
        MatchLifecycleState state = new MatchLifecycleState();

        assertFalse(state.acceptStart(false));

        assertFalse(state.isStarted());
    }

    @Test
    void acceptedStartMarksMatchStarted() {
        MatchLifecycleState state = new MatchLifecycleState();

        assertTrue(state.acceptStart(true));

        assertTrue(state.isStarted());
    }

    @Test
    void acceptedStartWithoutPreparationDoesNotMarkMatchStarted() {
        MatchLifecycleState state = new MatchLifecycleState();

        assertFalse(state.acceptStart(true, false));

        assertFalse(state.isStarted());
        assertTrue(state.acceptStart(true, true));
        assertTrue(state.isStarted());
    }

    @Test
    void startIsIdempotentAfterMatchHasStarted() {
        MatchLifecycleState state = new MatchLifecycleState();

        assertTrue(state.acceptStart(true));
        assertTrue(state.acceptStart(false));

        assertTrue(state.isStarted());
    }

    @Test
    void resetClearsStartedStateAndAllowsAnotherStart() {
        MatchLifecycleState state = new MatchLifecycleState();

        assertTrue(state.acceptStart(true));
        state.reset();

        assertFalse(state.isStarted());
        assertFalse(state.acceptStart(false, true));
        assertFalse(state.isStarted());
        assertTrue(state.acceptStart(true, true));
        assertTrue(state.isStarted());
    }
}
