package com.phasetranscrystal.fpsmatch.core.spawn;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpawnStrategyTest {
    private static final UUID ALPHA = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID BRAVO = UUID.fromString("00000000-0000-0000-0000-0000000000b2");

    @Test
    void assignsTeamsToDistantAllowedClusters() {
        SpawnRequest request = new SpawnRequest(
                List.of(new SpawnTeamRequest(ALPHA, 2), new SpawnTeamRequest(BRAVO, 2)),
                List.of(
                        new SpawnCandidate("west", new SpawnVector(0, 64, 0), Set.of("edge")),
                        new SpawnCandidate("east", new SpawnVector(200, 64, 0), Set.of("edge")),
                        new SpawnCandidate("boss", new SpawnVector(50, 64, 0), Set.of("hot"))
                ),
                new SpawnConstraints(120, List.of(new SpawnForbiddenZone(new SpawnVector(50, 64, 0), 40)), 20)
        );

        SpawnPlan plan = new DistanceSpawnStrategy().createPlan(request);

        assertTrue(plan.success());
        assertEquals("west", plan.assignmentFor(ALPHA).orElseThrow().candidate().id());
        assertEquals("east", plan.assignmentFor(BRAVO).orElseThrow().candidate().id());
        assertFalse(plan.usedFallback());
    }

    @Test
    void relaxesDistanceWhenMapHasTooFewValidClusters() {
        SpawnRequest request = new SpawnRequest(
                List.of(new SpawnTeamRequest(ALPHA, 1), new SpawnTeamRequest(BRAVO, 1)),
                List.of(
                        new SpawnCandidate("a", new SpawnVector(0, 64, 0), Set.of()),
                        new SpawnCandidate("b", new SpawnVector(60, 64, 0), Set.of())
                ),
                new SpawnConstraints(120, List.of(), 30)
        );

        SpawnPlan plan = new DistanceSpawnStrategy().createPlan(request);

        assertTrue(plan.success());
        assertTrue(plan.usedFallback());
        assertEquals(2, plan.assignments().size());
    }

    @Test
    void keepsTeamsAwayFromProtectedInterestPoints() {
        SpawnRequest request = new SpawnRequest(
                List.of(new SpawnTeamRequest(ALPHA, 1)),
                List.of(
                        new SpawnCandidate("near-cache", new SpawnVector(95, 64, 0), Set.of("edge")),
                        new SpawnCandidate("far-edge", new SpawnVector(220, 64, 0), Set.of("edge"))
                ),
                new SpawnConstraints(
                        0,
                        List.of(),
                        20,
                        List.of(new SpawnInterestPoint("cache", new SpawnVector(100, 64, 0), 40))
                )
        );

        SpawnPlan plan = new DistanceSpawnStrategy().createPlan(request);

        assertTrue(plan.success());
        assertEquals("far-edge", plan.assignmentFor(ALPHA).orElseThrow().candidate().id());
    }
}
