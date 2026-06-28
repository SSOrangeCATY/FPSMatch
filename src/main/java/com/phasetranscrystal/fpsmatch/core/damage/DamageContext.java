package com.phasetranscrystal.fpsmatch.core.damage;

import com.phasetranscrystal.fpsmatch.core.playerstate.MatchPlayerState;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record DamageContext(
        Optional<UUID> attackerPlayerId,
        Optional<UUID> attackerTeamId,
        UUID targetPlayerId,
        UUID targetTeamId,
        DamageSourceType sourceType,
        String phase,
        MatchPlayerState targetState,
        float amount
) {
    public DamageContext {
        attackerPlayerId = Objects.requireNonNull(attackerPlayerId, "attackerPlayerId");
        attackerTeamId = Objects.requireNonNull(attackerTeamId, "attackerTeamId");
        Objects.requireNonNull(targetPlayerId, "targetPlayerId");
        Objects.requireNonNull(targetTeamId, "targetTeamId");
        Objects.requireNonNull(sourceType, "sourceType");
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(targetState, "targetState");
    }
}
