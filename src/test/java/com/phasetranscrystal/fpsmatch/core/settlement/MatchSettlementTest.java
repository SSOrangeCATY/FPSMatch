package com.phasetranscrystal.fpsmatch.core.settlement;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MatchSettlementTest {
    private static final UUID MATCH = UUID.fromString("00000000-0000-0000-0000-000000000999");
    private static final UUID ALPHA = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID BRAVO = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    private static final UUID PLAYER_ONE = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID PLAYER_TWO = UUID.fromString("10000000-0000-0000-0000-000000000002");

    @Test
    void supportsDifferentOutcomesPerTeamAndPlayer() {
        MatchSettlement settlement = MatchSettlement.builder(MATCH, "seed-hash")
                .team(ALPHA, OutcomeType.EXTRACTED)
                .team(BRAVO, OutcomeType.ELIMINATED)
                .player(PLAYER_ONE, ALPHA, OutcomeType.EXTRACTED, Map.of("bounty", 1))
                .player(PLAYER_TWO, BRAVO, OutcomeType.ELIMINATED, Map.of("kills", 2))
                .objective("token", OutcomeType.COMPLETED_OBJECTIVE, ALPHA)
                .build();

        assertEquals(OutcomeType.EXTRACTED, settlement.team(ALPHA).orElseThrow().outcome());
        assertEquals(OutcomeType.ELIMINATED, settlement.player(PLAYER_TWO).orElseThrow().outcome());
        assertEquals("seed-hash", settlement.seedHash());
        assertEquals(ALPHA, settlement.objectives().getFirst().completedByTeam().orElseThrow());
    }

    @Test
    void recordsTimeoutCarryRewardsAndMetadata() {
        MatchSettlement settlement = MatchSettlement.builder(MATCH, "seed-hash")
                .team(BRAVO, OutcomeType.TIMED_OUT)
                .player(
                        PLAYER_TWO,
                        BRAVO,
                        OutcomeType.CARRIED_OBJECTIVE,
                        List.of(new RewardEvent("bounty", 2, Map.of("source", "token"))),
                        Map.of("extracted", "false")
                )
                .objective("token", OutcomeType.CARRIED_OBJECTIVE, BRAVO, Map.of("carrier", PLAYER_TWO.toString()))
                .metadata("durationTicks", "72000")
                .build();

        assertEquals(OutcomeType.TIMED_OUT, settlement.team(BRAVO).orElseThrow().outcome());
        assertEquals(2, settlement.player(PLAYER_TWO).orElseThrow().rewardTotal("bounty"));
        assertEquals("false", settlement.player(PLAYER_TWO).orElseThrow().metadata().get("extracted"));
        assertEquals(PLAYER_TWO.toString(), settlement.objectives().getFirst().metadata().get("carrier"));
        assertEquals("72000", settlement.metadata().get("durationTicks"));
    }
}
