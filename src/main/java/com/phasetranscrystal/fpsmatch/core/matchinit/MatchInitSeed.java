package com.phasetranscrystal.fpsmatch.core.matchinit;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record MatchInitSeed(
        int schemaVersion,
        UUID matchId,
        String mapId,
        List<MatchTeamSeed> teams,
        List<MatchPlayerSeed> players,
        Map<String, String> metadata
) {
    public MatchInitSeed {
        Objects.requireNonNull(matchId, "matchId");
        Objects.requireNonNull(mapId, "mapId");
        teams = List.copyOf(Objects.requireNonNull(teams, "teams"));
        players = List.copyOf(Objects.requireNonNull(players, "players"));
        metadata = Map.copyOf(Objects.requireNonNull(metadata, "metadata"));
    }
}
