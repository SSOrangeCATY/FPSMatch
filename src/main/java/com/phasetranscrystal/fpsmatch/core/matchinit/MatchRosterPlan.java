package com.phasetranscrystal.fpsmatch.core.matchinit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record MatchRosterPlan(
        UUID matchId,
        String mapId,
        String seedHash,
        List<MatchRosterTeam> teams,
        Map<UUID, MatchRosterTeam> teamsById,
        Map<UUID, MatchPlayerSeed> playersById,
        Map<UUID, MatchRosterTeam> teamsByPlayer
) {
    public MatchRosterPlan {
        Objects.requireNonNull(matchId, "matchId");
        Objects.requireNonNull(mapId, "mapId");
        Objects.requireNonNull(seedHash, "seedHash");
        teams = List.copyOf(Objects.requireNonNull(teams, "teams"));
        teamsById = Map.copyOf(Objects.requireNonNull(teamsById, "teamsById"));
        playersById = Map.copyOf(Objects.requireNonNull(playersById, "playersById"));
        teamsByPlayer = Map.copyOf(Objects.requireNonNull(teamsByPlayer, "teamsByPlayer"));
    }

    public static MatchRosterPlan from(AcceptedMatchContext context) {
        Objects.requireNonNull(context, "context");
        Map<UUID, MatchPlayerSeed> playersById = new LinkedHashMap<>();
        context.players().forEach(player -> playersById.put(player.playerId(), player));

        List<MatchRosterTeam> rosterTeams = new ArrayList<>();
        Map<UUID, MatchRosterTeam> teamsById = new LinkedHashMap<>();
        Map<UUID, MatchRosterTeam> teamsByPlayer = new LinkedHashMap<>();
        Map<String, UUID> teamIdsByName = new LinkedHashMap<>();
        for (MatchTeamSeed team : context.teams()) {
            String normalizedName = normalizedTeamName(team.name());
            UUID existingTeamId = teamIdsByName.putIfAbsent(normalizedName, team.teamId());
            if (existingTeamId != null) {
                throw new IllegalArgumentException("Duplicate team name: " + team.name());
            }

            List<MatchPlayerSeed> players = team.players().stream()
                    .map(playersById::get)
                    .filter(Objects::nonNull)
                    .toList();
            MatchRosterTeam rosterTeam = new MatchRosterTeam(
                    team.teamId(),
                    team.name(),
                    team.limit(),
                    players
            );
            rosterTeams.add(rosterTeam);
            teamsById.put(team.teamId(), rosterTeam);
            players.forEach(player -> teamsByPlayer.put(player.playerId(), rosterTeam));
        }

        return new MatchRosterPlan(
                context.matchId(),
                context.mapId(),
                context.seedHash(),
                rosterTeams,
                teamsById,
                playersById,
                teamsByPlayer
        );
    }

    public Optional<MatchRosterTeam> team(UUID teamId) {
        Objects.requireNonNull(teamId, "teamId");
        return Optional.ofNullable(teamsById.get(teamId));
    }

    public Optional<MatchPlayerSeed> player(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return Optional.ofNullable(playersById.get(playerId));
    }

    public Optional<MatchRosterTeam> teamForPlayer(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return Optional.ofNullable(teamsByPlayer.get(playerId));
    }

    public void applyTo(MatchRosterBinder binder) {
        Objects.requireNonNull(binder, "binder");
        for (MatchRosterTeam team : teams) {
            binder.ensureTeam(team);
            for (MatchPlayerSeed player : team.players()) {
                binder.reservePlayer(team, player);
            }
        }
    }

    private static String normalizedTeamName(String teamName) {
        String normalizedName = Objects.requireNonNull(teamName, "teamName").trim().toLowerCase(Locale.ROOT);
        if (normalizedName.isBlank()) {
            throw new IllegalArgumentException("Team name is blank");
        }
        if ("spectator".equals(normalizedName)) {
            throw new IllegalArgumentException("Reserved team name: " + teamName);
        }
        return normalizedName;
    }

    public record MatchRosterTeam(
            UUID teamId,
            String name,
            int limit,
            List<MatchPlayerSeed> players
    ) {
        public MatchRosterTeam {
            Objects.requireNonNull(teamId, "teamId");
            Objects.requireNonNull(name, "name");
            players = List.copyOf(Objects.requireNonNull(players, "players"));
        }
    }
}
