package com.phasetranscrystal.fpsmatch.core.damage;

import com.phasetranscrystal.fpsmatch.core.playerstate.MatchPlayerState;
import com.phasetranscrystal.fpsmatch.core.relation.TeamRelationPolicy;

import java.util.Objects;

public final class DamageRules {
    private DamageRules() {
    }

    public static DamageRule cancelFriendlyFire(TeamRelationPolicy relationPolicy) {
        Objects.requireNonNull(relationPolicy, "relationPolicy");
        return (context, decision) -> {
            if (decision.cancelled()) {
                return decision;
            }
            return context.attackerTeamId()
                    .filter(attackerTeam -> !relationPolicy.canDamage(attackerTeam, context.targetTeamId()))
                    .map(ignored -> decision.cancel())
                    .orElse(decision);
        };
    }

    public static DamageRule cancelWhenTargetStateIsNot(MatchPlayerState expectedState) {
        Objects.requireNonNull(expectedState, "expectedState");
        return (context, decision) -> context.targetState() == expectedState ? decision : decision.cancel();
    }

    public static DamageRule cancelPlayerDamageDuringPhase(String phase) {
        Objects.requireNonNull(phase, "phase");
        return (context, decision) -> {
            if (context.sourceType() == DamageSourceType.PLAYER && context.phase().equals(phase)) {
                return decision.cancel();
            }
            return decision;
        };
    }

    public static DamageRule scaleSource(DamageSourceType sourceType, float multiplier) {
        Objects.requireNonNull(sourceType, "sourceType");
        return (context, decision) -> context.sourceType() == sourceType ? decision.scale(multiplier) : decision;
    }
}
