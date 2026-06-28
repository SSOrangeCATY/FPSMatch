package com.phasetranscrystal.fpsmatch.core.area;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AreaTimerRuntimeTest {
    private static final UUID ALPHA = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID BRAVO = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    private static final UUID PLAYER_ONE = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID PLAYER_TWO = UUID.fromString("10000000-0000-0000-0000-000000000002");

    @Test
    void teamCountdownResetsWhenTeamLeavesArea() {
        AreaTimerRuntime runtime = new AreaTimerRuntime(AreaTimerPolicy.teamCountdown(3));

        runtime.updatePresence(Set.of(AreaActor.player(PLAYER_ONE, ALPHA)));
        runtime.tick();
        assertEquals(AreaTimerState.COUNTING_DOWN, runtime.statusForTeam(ALPHA).state());
        assertEquals(2, runtime.statusForTeam(ALPHA).ticksRemaining());

        runtime.updatePresence(Set.of());
        runtime.tick();

        assertEquals(AreaTimerState.IDLE, runtime.statusForTeam(ALPHA).state());
        assertEquals(0, runtime.statusForTeam(ALPHA).progressTicks());
    }

    @Test
    void teamCountdownPausesWhenContestedAndCompletesAfterContestClears() {
        AreaTimerRuntime runtime = new AreaTimerRuntime(AreaTimerPolicy.teamCountdown(3));

        runtime.updatePresence(Set.of(AreaActor.player(PLAYER_ONE, ALPHA)));
        runtime.tick();
        runtime.updatePresence(Set.of(
                AreaActor.player(PLAYER_ONE, ALPHA),
                AreaActor.player(PLAYER_TWO, BRAVO)
        ));
        runtime.tick();
        runtime.tick();

        assertEquals(AreaTimerState.CONTESTED, runtime.statusForTeam(ALPHA).state());
        assertEquals(2, runtime.statusForTeam(ALPHA).ticksRemaining());

        runtime.updatePresence(Set.of(AreaActor.player(PLAYER_ONE, ALPHA)));
        runtime.tick();
        runtime.tick();

        assertEquals(AreaTimerState.COMPLETED, runtime.statusForTeam(ALPHA).state());
        assertTrue(runtime.completedTeams().contains(ALPHA));
    }

    @Test
    void restoresProgressAndCompletedTeamsForRuntimePersistence() {
        AreaTimerRuntime runtime = new AreaTimerRuntime(AreaTimerPolicy.teamCountdown(3));

        runtime.restoreProgress(ALPHA, 2);
        runtime.restoreCompleted(BRAVO);
        runtime.updatePresence(Set.of(AreaActor.player(PLAYER_ONE, ALPHA)));
        runtime.tick();

        assertEquals(AreaTimerState.COMPLETED, runtime.statusForTeam(ALPHA).state());
        assertEquals(AreaTimerState.COMPLETED, runtime.statusForTeam(BRAVO).state());
        assertTrue(runtime.completedTeams().contains(ALPHA));
        assertTrue(runtime.completedTeams().contains(BRAVO));
    }

    @Test
    void restoresPresenceWithoutTickingForRuntimePersistence() {
        AreaTimerRuntime runtime = new AreaTimerRuntime(AreaTimerPolicy.teamCountdown(3));

        runtime.restorePresence(Set.of(
                AreaActor.player(PLAYER_ONE, ALPHA),
                AreaActor.player(PLAYER_TWO, BRAVO)
        ));

        assertEquals(2, runtime.presentActors().size());
        assertEquals(AreaTimerState.CONTESTED, runtime.statusForTeam(ALPHA).state());
        assertEquals(AreaTimerState.CONTESTED, runtime.statusForTeam(BRAVO).state());
    }
}
