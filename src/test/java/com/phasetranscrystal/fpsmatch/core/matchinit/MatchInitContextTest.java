package com.phasetranscrystal.fpsmatch.core.matchinit;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatchInitContextTest {
    private static final UUID MATCH_ID = UUID.fromString("00000000-0000-0000-0000-000000000123");
    private static final UUID TEAM_ALPHA = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID TEAM_BRAVO = UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final UUID PLAYER_ONE = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID PLAYER_TWO = UUID.fromString("20000000-0000-0000-0000-000000000002");

    @Test
    void acceptsExternallySuppliedTeamsPlayersAndSnapshots() {
        MatchInitSeed seed = validSeed();

        MatchInitValidationResult result = MatchInitValidator.forSchema(1).validate(seed);

        assertTrue(result.accepted());
        AcceptedMatchContext context = result.context().orElseThrow();
        assertEquals(MATCH_ID, context.matchId());
        assertEquals("map.large", context.mapId());
        assertEquals(TEAM_ALPHA, context.teams().getFirst().teamId());
        assertEquals(PLAYER_ONE, context.players().getFirst().playerId());
        assertEquals(List.of("trait.fast_interact"), context.players().getFirst().traits());
        assertEquals(150, context.players().getFirst().health().maxHealth());
        assertEquals("backend-room-42", context.metadata().get("room"));
        assertEquals(context.seedHash(), AcceptedMatchContext.fromSeed(seed).seedHash());
    }

    @Test
    void buildsRosterPlanForMapTeamBootstrap() {
        MatchInitSeed seed = new MatchInitSeed(
                1,
                MATCH_ID,
                "map.large",
                List.of(
                        new MatchTeamSeed(TEAM_ALPHA, "alpha", 2, List.of(PLAYER_ONE)),
                        new MatchTeamSeed(TEAM_BRAVO, "bravo", 1, List.of(PLAYER_TWO))
                ),
                List.of(
                        playerSeed(PLAYER_ONE, TEAM_ALPHA),
                        playerSeed(PLAYER_TWO, TEAM_BRAVO)
                ),
                Map.of()
        );

        MatchRosterPlan plan = MatchRosterPlan.from(AcceptedMatchContext.fromSeed(seed));

        assertEquals(List.of("alpha", "bravo"), plan.teams().stream().map(MatchRosterPlan.MatchRosterTeam::name).toList());
        assertEquals(List.of(2, 1), plan.teams().stream().map(MatchRosterPlan.MatchRosterTeam::limit).toList());
        assertEquals(List.of(PLAYER_ONE), plan.team(TEAM_ALPHA).orElseThrow()
                .players().stream().map(MatchPlayerSeed::playerId).toList());
        assertEquals("bravo", plan.teamForPlayer(PLAYER_TWO).orElseThrow().name());
        assertEquals(PLAYER_TWO, plan.player(PLAYER_TWO).orElseThrow().playerId());
        assertTrue(plan.teamForPlayer(UUID.fromString("20000000-0000-0000-0000-000000000099")).isEmpty());
    }

    @Test
    void appliesRosterPlanToGenericBinderInSeedOrder() {
        MatchInitSeed seed = new MatchInitSeed(
                1,
                MATCH_ID,
                "map.large",
                List.of(
                        new MatchTeamSeed(TEAM_ALPHA, "alpha", 2, List.of(PLAYER_ONE)),
                        new MatchTeamSeed(TEAM_BRAVO, "bravo", 1, List.of(PLAYER_TWO))
                ),
                List.of(
                        playerSeed(PLAYER_ONE, TEAM_ALPHA),
                        playerSeed(PLAYER_TWO, TEAM_BRAVO)
                ),
                Map.of()
        );
        MatchRosterPlan plan = MatchRosterPlan.from(AcceptedMatchContext.fromSeed(seed));
        RecordingRosterBinder binder = new RecordingRosterBinder();

        plan.applyTo(binder);

        assertEquals(List.of(
                "team:" + TEAM_ALPHA + ":alpha:2",
                "player:" + TEAM_ALPHA + ":" + PLAYER_ONE,
                "team:" + TEAM_BRAVO + ":bravo:1",
                "player:" + TEAM_BRAVO + ":" + PLAYER_TWO
        ), binder.operations());
    }

    @Test
    void rejectsDuplicatePlayersAcrossTeams() {
        MatchInitSeed seed = new MatchInitSeed(
                1,
                MATCH_ID,
                "map.large",
                List.of(
                        new MatchTeamSeed(TEAM_ALPHA, "alpha", 2, List.of(PLAYER_ONE)),
                        new MatchTeamSeed(UUID.fromString("10000000-0000-0000-0000-000000000002"), "bravo", 2, List.of(PLAYER_ONE))
                ),
                List.of(playerSeed()),
                Map.of()
        );

        MatchInitValidationResult result = MatchInitValidator.forSchema(1).validate(seed);

        assertFalse(result.accepted());
        assertTrue(result.errors().contains("Duplicate player in teams: " + PLAYER_ONE));
    }

    @Test
    void rejectsDuplicateTeamNamesBeforeMapTeamsBootstrap() {
        MatchInitSeed seed = new MatchInitSeed(
                1,
                MATCH_ID,
                "map.large",
                List.of(
                        new MatchTeamSeed(TEAM_ALPHA, "alpha", 2, List.of(PLAYER_ONE)),
                        new MatchTeamSeed(TEAM_BRAVO, "alpha", 1, List.of(PLAYER_TWO))
                ),
                List.of(
                        playerSeed(PLAYER_ONE, TEAM_ALPHA),
                        playerSeed(PLAYER_TWO, TEAM_BRAVO)
                ),
                Map.of()
        );

        MatchInitValidationResult result = MatchInitValidator.forSchema(1).validate(seed);

        assertFalse(result.accepted());
        assertTrue(result.errors().contains("Duplicate team name: alpha"));
    }

    @Test
    void rejectsTeamRosterPlayersWithoutPlayerSeed() {
        MatchInitSeed seed = new MatchInitSeed(
                1,
                MATCH_ID,
                "map.large",
                List.of(new MatchTeamSeed(TEAM_ALPHA, "alpha", 2, List.of(PLAYER_ONE, PLAYER_TWO))),
                List.of(playerSeed(PLAYER_ONE, TEAM_ALPHA)),
                Map.of()
        );

        MatchInitValidationResult result = MatchInitValidator.forSchema(1).validate(seed);

        assertFalse(result.accepted());
        assertTrue(result.errors().contains("Team player is missing player seed: " + PLAYER_TWO));
    }

    @Test
    void rejectsReservedSpectatorTeamNameBeforeMapTeamsBootstrap() {
        MatchInitSeed seed = new MatchInitSeed(
                1,
                MATCH_ID,
                "map.large",
                List.of(new MatchTeamSeed(TEAM_ALPHA, "spectator", 1, List.of(PLAYER_ONE))),
                List.of(playerSeed()),
                Map.of()
        );

        MatchInitValidationResult result = MatchInitValidator.forSchema(1).validate(seed);

        assertFalse(result.accepted());
        assertTrue(result.errors().contains("Reserved team name: spectator"));
    }

    @Test
    void rejectsNegativeHealthChunks() {
        MatchPlayerSeed player = new MatchPlayerSeed(
                PLAYER_ONE,
                TEAM_ALPHA,
                new LoadoutSnapshot(List.of("weapon.rifle"), List.of("tool.medkit"), Map.of("skin", "plain")),
                List.of("trait.fast_interact"),
                new HealthSnapshot(150, List.of(50, -25, 50)),
                Map.of("hunterId", "hunter-001")
        );
        MatchInitSeed seed = new MatchInitSeed(
                1,
                MATCH_ID,
                "map.large",
                List.of(new MatchTeamSeed(TEAM_ALPHA, "alpha", 2, List.of(PLAYER_ONE))),
                List.of(player),
                Map.of()
        );

        MatchInitValidationResult result = MatchInitValidator.forSchema(1).validate(seed);

        assertFalse(result.accepted());
        assertTrue(result.errors().contains("Player health chunks must not be negative: " + PLAYER_ONE));
    }

    private static MatchInitSeed validSeed() {
        return new MatchInitSeed(
                1,
                MATCH_ID,
                "map.large",
                List.of(new MatchTeamSeed(TEAM_ALPHA, "alpha", 2, List.of(PLAYER_ONE))),
                List.of(playerSeed()),
                Map.of("room", "backend-room-42")
        );
    }

    private static MatchPlayerSeed playerSeed() {
        return playerSeed(PLAYER_ONE, TEAM_ALPHA);
    }

    private static MatchPlayerSeed playerSeed(UUID playerId, UUID teamId) {
        return new MatchPlayerSeed(
                playerId,
                teamId,
                new LoadoutSnapshot(List.of("weapon.rifle"), List.of("tool.medkit"), Map.of("skin", "plain")),
                List.of("trait.fast_interact"),
                new HealthSnapshot(150, List.of(50, 50, 50)),
            Map.of("hunterId", "hunter-001")
        );
    }

    private static final class RecordingRosterBinder implements MatchRosterBinder {
        private final List<String> operations = new java.util.ArrayList<>();

        @Override
        public void ensureTeam(MatchRosterPlan.MatchRosterTeam team) {
            operations.add("team:" + team.teamId() + ":" + team.name() + ":" + team.limit());
        }

        @Override
        public void reservePlayer(MatchRosterPlan.MatchRosterTeam team, MatchPlayerSeed player) {
            operations.add("player:" + team.teamId() + ":" + player.playerId());
        }

        List<String> operations() {
            return List.copyOf(operations);
        }
    }
}
