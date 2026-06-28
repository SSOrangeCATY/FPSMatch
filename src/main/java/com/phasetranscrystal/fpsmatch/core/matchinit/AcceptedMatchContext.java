package com.phasetranscrystal.fpsmatch.core.matchinit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;

public record AcceptedMatchContext(
        UUID matchId,
        String mapId,
        List<MatchTeamSeed> teams,
        List<MatchPlayerSeed> players,
        Map<String, String> metadata,
        String seedHash
) {
    public AcceptedMatchContext {
        Objects.requireNonNull(matchId, "matchId");
        Objects.requireNonNull(mapId, "mapId");
        teams = List.copyOf(Objects.requireNonNull(teams, "teams"));
        players = List.copyOf(Objects.requireNonNull(players, "players"));
        metadata = Map.copyOf(Objects.requireNonNull(metadata, "metadata"));
        Objects.requireNonNull(seedHash, "seedHash");
    }

    public static AcceptedMatchContext fromSeed(MatchInitSeed seed) {
        Objects.requireNonNull(seed, "seed");
        return new AcceptedMatchContext(
                seed.matchId(),
                seed.mapId(),
                seed.teams(),
                seed.players(),
                seed.metadata(),
                hashSeed(seed)
        );
    }

    private static String hashSeed(MatchInitSeed seed) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(canonical(seed).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 digest is unavailable", exception);
        }
    }

    private static String canonical(MatchInitSeed seed) {
        StringBuilder builder = new StringBuilder();
        builder.append("schema=").append(seed.schemaVersion()).append(';');
        builder.append("match=").append(seed.matchId()).append(';');
        builder.append("map=").append(seed.mapId()).append(';');
        builder.append("teams=[");
        seed.teams().stream()
                .sorted(Comparator.comparing(MatchTeamSeed::teamId))
                .forEach(team -> builder.append('{')
                        .append(team.teamId()).append('|')
                        .append(team.name()).append('|')
                        .append(team.limit()).append('|')
                        .append(team.players())
                        .append("}"));
        builder.append("];players=[");
        seed.players().stream()
                .sorted(Comparator.comparing(MatchPlayerSeed::playerId))
                .forEach(player -> builder.append('{')
                        .append(player.playerId()).append('|')
                        .append(player.teamId()).append('|')
                        .append(player.loadout().weapons()).append('|')
                        .append(player.loadout().tools()).append('|')
                        .append(sorted(player.loadout().metadata())).append('|')
                        .append(player.traits()).append('|')
                        .append(player.health().maxHealth()).append('|')
                        .append(player.health().chunks()).append('|')
                        .append(sorted(player.metadata()))
                        .append("}"));
        builder.append("];metadata=").append(sorted(seed.metadata()));
        return builder.toString();
    }

    private static Map<String, String> sorted(Map<String, String> map) {
        return new TreeMap<>(map);
    }
}
