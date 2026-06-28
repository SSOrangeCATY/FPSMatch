package com.phasetranscrystal.fpsmatch.core.matchinit;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record MatchPlayerSeed(
        UUID playerId,
        UUID teamId,
        LoadoutSnapshot loadout,
        List<String> traits,
        HealthSnapshot health,
        Map<String, String> metadata
) {
    public MatchPlayerSeed {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(teamId, "teamId");
        Objects.requireNonNull(loadout, "loadout");
        traits = List.copyOf(Objects.requireNonNull(traits, "traits"));
        Objects.requireNonNull(health, "health");
        metadata = Map.copyOf(Objects.requireNonNull(metadata, "metadata"));
    }
}
