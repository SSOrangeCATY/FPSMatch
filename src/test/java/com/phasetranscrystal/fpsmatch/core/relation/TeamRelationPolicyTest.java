package com.phasetranscrystal.fpsmatch.core.relation;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeamRelationPolicyTest {
    private static final UUID ALPHA = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID BRAVO = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    private static final UUID PVE = UUID.fromString("00000000-0000-0000-0000-0000000000c3");
    private static final UUID SPEC = UUID.fromString("00000000-0000-0000-0000-0000000000d4");

    @Test
    void resolvesHostileNeutralFriendlyAndSpectatorRelations() {
        TeamRelationPolicy policy = TeamRelationPolicy.builder()
                .defaultRelation(TeamRelation.HOSTILE)
                .neutral(ALPHA, PVE)
                .spectator(SPEC)
                .build();

        assertEquals(TeamRelation.FRIENDLY, policy.relation(ALPHA, ALPHA));
        assertEquals(TeamRelation.HOSTILE, policy.relation(ALPHA, BRAVO));
        assertEquals(TeamRelation.NEUTRAL, policy.relation(ALPHA, PVE));
        assertEquals(TeamRelation.SPECTATOR, policy.relation(ALPHA, SPEC));
        assertTrue(policy.canDamage(ALPHA, BRAVO));
    }

    @Test
    void temporaryAllianceExpiresAfterTicks() {
        TeamRelationPolicy policy = TeamRelationPolicy.builder()
                .defaultRelation(TeamRelation.HOSTILE)
                .alliedForTicks(ALPHA, BRAVO, 2)
                .build();

        assertEquals(TeamRelation.FRIENDLY, policy.relation(ALPHA, BRAVO));
        policy.tick();
        assertEquals(TeamRelation.FRIENDLY, policy.relation(ALPHA, BRAVO));
        policy.tick();
        assertEquals(TeamRelation.HOSTILE, policy.relation(ALPHA, BRAVO));
    }
}
