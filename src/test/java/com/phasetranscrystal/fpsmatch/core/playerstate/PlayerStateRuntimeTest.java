package com.phasetranscrystal.fpsmatch.core.playerstate;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerStateRuntimeTest {
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID PLAYER_TWO = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void downedPlayerCanBeRevivedBeforeBleedout() {
        PlayerStateRuntime runtime = new PlayerStateRuntime();
        runtime.addPlayer(PLAYER);

        runtime.down(PLAYER, 3);
        runtime.tick();
        runtime.revive(PLAYER);

        assertEquals(MatchPlayerState.ALIVE, runtime.stateOf(PLAYER));
        assertEquals(1, runtime.recordOf(PLAYER).downedCount());
        assertTrue(runtime.canReceiveDamage(PLAYER));
    }

    @Test
    void downedPlayerEliminatesWhenBleedoutExpires() {
        PlayerStateRuntime runtime = new PlayerStateRuntime();
        runtime.addPlayer(PLAYER);

        runtime.down(PLAYER, 1);
        runtime.tick();

        assertEquals(MatchPlayerState.ELIMINATED, runtime.stateOf(PLAYER));
        assertFalse(runtime.canReceiveDamage(PLAYER));
    }

    @Test
    void extractedPlayerKeepsDistinctOutcomeState() {
        PlayerStateRuntime runtime = new PlayerStateRuntime();
        runtime.addPlayer(PLAYER);

        runtime.extract(PLAYER);

        assertEquals(MatchPlayerState.EXTRACTED, runtime.stateOf(PLAYER));
        assertFalse(runtime.canReceiveDamage(PLAYER));
    }

    @Test
    void restoresPlayerStateRecordForRuntimePersistence() {
        PlayerStateRuntime runtime = new PlayerStateRuntime();
        runtime.restorePlayer(PLAYER, new PlayerStateRecord(MatchPlayerState.DOWNED, 2, 7));

        assertEquals(MatchPlayerState.DOWNED, runtime.stateOf(PLAYER));
        assertEquals(2, runtime.recordOf(PLAYER).downedCount());
        assertEquals(7, runtime.recordOf(PLAYER).bleedoutTicks());

        runtime.tick();

        assertEquals(6, runtime.recordOf(PLAYER).bleedoutTicks());
    }

    @Test
    void stateTransitionsAreExposedAsDrainableEvents() {
        PlayerStateRuntime runtime = new PlayerStateRuntime();
        runtime.addPlayer(PLAYER);

        runtime.down(PLAYER, 3);
        runtime.revive(PLAYER);
        runtime.eliminate(PLAYER);

        List<PlayerStateEvent> events = runtime.drainEvents();

        assertEquals(List.of(
                new PlayerStateEvent(PLAYER, MatchPlayerState.ALIVE, MatchPlayerState.DOWNED, 1, 3),
                new PlayerStateEvent(PLAYER, MatchPlayerState.DOWNED, MatchPlayerState.ALIVE, 1, 0),
                new PlayerStateEvent(PLAYER, MatchPlayerState.ALIVE, MatchPlayerState.ELIMINATED, 1, 0)
        ), events);
        assertTrue(runtime.drainEvents().isEmpty());
    }

    @Test
    void bleedoutEliminationEmitsStateEvent() {
        PlayerStateRuntime runtime = new PlayerStateRuntime();
        runtime.addPlayer(PLAYER);
        runtime.down(PLAYER, 1);
        runtime.drainEvents();

        runtime.tick();

        assertEquals(List.of(
                new PlayerStateEvent(PLAYER, MatchPlayerState.DOWNED, MatchPlayerState.ELIMINATED, 1, 0)
        ), runtime.drainEvents());
    }

    @Test
    void bleedoutEventsFollowPlayerRegistrationOrder() {
        PlayerStateRuntime runtime = new PlayerStateRuntime();
        runtime.addPlayer(PLAYER_TWO);
        runtime.addPlayer(PLAYER);
        runtime.down(PLAYER_TWO, 1);
        runtime.down(PLAYER, 1);
        runtime.drainEvents();

        runtime.tick();

        assertEquals(List.of(
                new PlayerStateEvent(PLAYER_TWO, MatchPlayerState.DOWNED, MatchPlayerState.ELIMINATED, 1, 0),
                new PlayerStateEvent(PLAYER, MatchPlayerState.DOWNED, MatchPlayerState.ELIMINATED, 1, 0)
        ), runtime.drainEvents());
    }

    @Test
    void bleedoutTicksWithoutTransitionDoNotEmitEvents() {
        PlayerStateRuntime runtime = new PlayerStateRuntime();
        runtime.addPlayer(PLAYER);
        runtime.down(PLAYER, 3);
        runtime.drainEvents();

        runtime.tick();

        assertEquals(2, runtime.recordOf(PLAYER).bleedoutTicks());
        assertTrue(runtime.drainEvents().isEmpty());
    }

    @Test
    void restoringPlayerStateDoesNotEmitHistoricalEvents() {
        PlayerStateRuntime runtime = new PlayerStateRuntime();

        runtime.restorePlayer(PLAYER, new PlayerStateRecord(MatchPlayerState.DOWNED, 2, 7));

        assertTrue(runtime.drainEvents().isEmpty());
    }

    @Test
    void downLimitEliminatesInsteadOfDowningAgain() {
        PlayerStateRuntime runtime = new PlayerStateRuntime(PlayerDownPolicy.maxDowns(1));
        runtime.addPlayer(PLAYER);

        runtime.down(PLAYER, 3);
        runtime.revive(PLAYER);
        runtime.drainEvents();
        runtime.down(PLAYER, 3);

        assertEquals(MatchPlayerState.ELIMINATED, runtime.stateOf(PLAYER));
        assertEquals(2, runtime.recordOf(PLAYER).downedCount());
        assertEquals(List.of(
                new PlayerStateEvent(PLAYER, MatchPlayerState.ALIVE, MatchPlayerState.ELIMINATED, 2, 0)
        ), runtime.drainEvents());
    }

    @Test
    void zeroDownLimitEliminatesOnFirstDown() {
        PlayerStateRuntime runtime = new PlayerStateRuntime(PlayerDownPolicy.maxDowns(0));
        runtime.addPlayer(PLAYER);

        runtime.down(PLAYER, 3);

        assertEquals(MatchPlayerState.ELIMINATED, runtime.stateOf(PLAYER));
        assertEquals(1, runtime.recordOf(PLAYER).downedCount());
        assertEquals(List.of(
                new PlayerStateEvent(PLAYER, MatchPlayerState.ALIVE, MatchPlayerState.ELIMINATED, 1, 0)
        ), runtime.drainEvents());
    }
}
