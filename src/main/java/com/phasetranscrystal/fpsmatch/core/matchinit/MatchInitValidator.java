package com.phasetranscrystal.fpsmatch.core.matchinit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class MatchInitValidator {
    private final int schemaVersion;

    private MatchInitValidator(int schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public static MatchInitValidator forSchema(int schemaVersion) {
        return new MatchInitValidator(schemaVersion);
    }

    public MatchInitValidationResult validate(MatchInitSeed seed) {
        Objects.requireNonNull(seed, "seed");
        List<String> errors = new ArrayList<>();

        if (seed.schemaVersion() != schemaVersion) {
            errors.add("Unsupported schema version: " + seed.schemaVersion());
        }
        if (seed.mapId().isBlank()) {
            errors.add("Map id is blank");
        }
        validateTeams(seed, errors);
        validatePlayers(seed, errors);

        if (!errors.isEmpty()) {
            return MatchInitValidationResult.rejected(errors);
        }
        return MatchInitValidationResult.accepted(AcceptedMatchContext.fromSeed(seed));
    }

    private static void validateTeams(MatchInitSeed seed, List<String> errors) {
        Set<UUID> teamIds = new HashSet<>();
        Set<String> teamNames = new HashSet<>();
        Set<UUID> assignedPlayers = new HashSet<>();
        for (MatchTeamSeed team : seed.teams()) {
            if (!teamIds.add(team.teamId())) {
                errors.add("Duplicate team: " + team.teamId());
            }
            String normalizedName = team.name().trim().toLowerCase(Locale.ROOT);
            if (normalizedName.isBlank()) {
                errors.add("Team name is blank: " + team.teamId());
            } else if ("spectator".equals(normalizedName)) {
                errors.add("Reserved team name: " + team.name());
            } else if (!teamNames.add(normalizedName)) {
                errors.add("Duplicate team name: " + team.name());
            }
            if (team.limit() < 0) {
                errors.add("Negative team limit: " + team.teamId());
            }
            if (team.limit() > 0 && team.players().size() > team.limit()) {
                errors.add("Team exceeds limit: " + team.teamId());
            }
            Set<UUID> playersInTeam = new HashSet<>();
            for (UUID playerId : team.players()) {
                if (!playersInTeam.add(playerId) || !assignedPlayers.add(playerId)) {
                    errors.add("Duplicate player in teams: " + playerId);
                }
            }
        }
    }

    private static void validatePlayers(MatchInitSeed seed, List<String> errors) {
        Set<UUID> teamIds = new HashSet<>();
        for (MatchTeamSeed team : seed.teams()) {
            teamIds.add(team.teamId());
        }

        Set<UUID> playerSeedIds = new HashSet<>();
        for (MatchPlayerSeed player : seed.players()) {
            playerSeedIds.add(player.playerId());
        }

        Map<UUID, UUID> teamAssignments = new HashMap<>();
        for (MatchTeamSeed team : seed.teams()) {
            for (UUID playerId : team.players()) {
                teamAssignments.put(playerId, team.teamId());
            }
        }

        Set<UUID> missingPlayerSeeds = new HashSet<>();
        for (MatchTeamSeed team : seed.teams()) {
            for (UUID playerId : team.players()) {
                if (!playerSeedIds.contains(playerId) && missingPlayerSeeds.add(playerId)) {
                    errors.add("Team player is missing player seed: " + playerId);
                }
            }
        }

        Set<UUID> playerIds = new HashSet<>();
        for (MatchPlayerSeed player : seed.players()) {
            if (!playerIds.add(player.playerId())) {
                errors.add("Duplicate player seed: " + player.playerId());
            }
            if (!teamIds.contains(player.teamId())) {
                errors.add("Unknown team for player: " + player.playerId());
            }
            UUID assignedTeam = teamAssignments.get(player.playerId());
            if (assignedTeam == null) {
                errors.add("Player is not assigned to a team: " + player.playerId());
            } else if (!assignedTeam.equals(player.teamId())) {
                errors.add("Player team mismatch: " + player.playerId());
            }
            if (player.health().maxHealth() <= 0) {
                errors.add("Player max health must be positive: " + player.playerId());
            }
            if (player.health().chunks().stream().anyMatch(chunk -> chunk < 0)) {
                errors.add("Player health chunks must not be negative: " + player.playerId());
            }
        }
    }
}
