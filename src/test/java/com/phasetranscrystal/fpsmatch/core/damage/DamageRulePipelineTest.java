package com.phasetranscrystal.fpsmatch.core.damage;

import com.phasetranscrystal.fpsmatch.core.playerstate.MatchPlayerState;
import com.phasetranscrystal.fpsmatch.core.relation.TeamRelationPolicy;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DamageRulePipelineTest {
    private static final UUID ALPHA = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID BRAVO = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    private static final UUID PLAYER_ONE = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID PLAYER_TWO = UUID.fromString("10000000-0000-0000-0000-000000000002");

    @Test
    void cancelsFriendlyFireAndDownedTargetDamage() {
        DamageRulePipeline pipeline = DamageRulePipeline.create()
                .add(DamageRules.cancelFriendlyFire(TeamRelationPolicy.hostileByDefault()))
                .add(DamageRules.cancelWhenTargetStateIsNot(MatchPlayerState.ALIVE));

        DamageDecision friendly = pipeline.evaluate(new DamageContext(
                Optional.of(PLAYER_ONE), Optional.of(ALPHA), PLAYER_TWO, ALPHA,
                DamageSourceType.PLAYER, "live", MatchPlayerState.ALIVE, 10.0f
        ));
        DamageDecision downed = pipeline.evaluate(new DamageContext(
                Optional.of(PLAYER_ONE), Optional.of(ALPHA), PLAYER_TWO, BRAVO,
                DamageSourceType.PLAYER, "live", MatchPlayerState.DOWNED, 10.0f
        ));

        assertTrue(friendly.cancelled());
        assertTrue(downed.cancelled());
    }

    @Test
    void phaseAndSourceRulesCanScaleOrCancelDamage() {
        DamageRulePipeline pipeline = DamageRulePipeline.create()
                .add(DamageRules.cancelPlayerDamageDuringPhase("warmup"))
                .add(DamageRules.scaleSource(DamageSourceType.PVE, 1.5f));

        DamageDecision warmup = pipeline.evaluate(new DamageContext(
                Optional.of(PLAYER_ONE), Optional.of(ALPHA), PLAYER_TWO, BRAVO,
                DamageSourceType.PLAYER, "warmup", MatchPlayerState.ALIVE, 20.0f
        ));
        DamageDecision pve = pipeline.evaluate(new DamageContext(
                Optional.empty(), Optional.empty(), PLAYER_TWO, BRAVO,
                DamageSourceType.PVE, "live", MatchPlayerState.ALIVE, 20.0f
        ));

        assertTrue(warmup.cancelled());
        assertEquals(30.0f, pve.amount(), 0.001f);
    }
}
