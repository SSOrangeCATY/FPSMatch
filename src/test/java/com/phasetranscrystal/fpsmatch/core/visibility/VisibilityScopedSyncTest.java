package com.phasetranscrystal.fpsmatch.core.visibility;

import com.phasetranscrystal.fpsmatch.core.objective.VisibilityPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VisibilityScopedSyncTest {
    private static final UUID ALPHA = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID BRAVO = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    private static final UUID PLAYER_ONE = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID PLAYER_TWO = UUID.fromString("10000000-0000-0000-0000-000000000002");

    @Test
    void buildsPerRecipientDeliveriesFromVisibilityPolicies() {
        VisibilitySyncPlan<String> plan = VisibilityScopedSync.plan(
                List.of(
                        VisibilityRecipient.player(PLAYER_ONE, ALPHA),
                        VisibilityRecipient.player(PLAYER_TWO, BRAVO)
                ),
                List.of(
                        ScopedPayload.of("global_timer", "timer", VisibilityPolicy.global()),
                        ScopedPayload.of("alpha_intel", "intel", VisibilityPolicy.teams(Set.of(ALPHA)))
                )
        );

        assertEquals(List.of("global_timer", "alpha_intel"), plan.payloadIdsFor(PLAYER_ONE));
        assertEquals(List.of("global_timer"), plan.payloadIdsFor(PLAYER_TWO));
    }

    @Test
    void supportsPlayerScopedPayloads() {
        VisibilitySyncPlan<String> plan = VisibilityScopedSync.plan(
                List.of(
                        VisibilityRecipient.player(PLAYER_ONE, ALPHA),
                        VisibilityRecipient.player(PLAYER_TWO, ALPHA)
                ),
                List.of(
                        ScopedPayload.of("private_prompt", "prompt", VisibilityPolicy.players(Set.of(PLAYER_ONE))),
                        ScopedPayload.of("team_marker", "marker", VisibilityPolicy.teams(Set.of(ALPHA)))
                )
        );

        assertEquals(List.of("private_prompt", "team_marker"), plan.payloadIdsFor(PLAYER_ONE));
        assertEquals(List.of("team_marker"), plan.payloadIdsFor(PLAYER_TWO));
    }
}
