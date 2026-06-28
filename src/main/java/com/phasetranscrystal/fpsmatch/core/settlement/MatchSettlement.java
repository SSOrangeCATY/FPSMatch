package com.phasetranscrystal.fpsmatch.core.settlement;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record MatchSettlement(
        UUID matchId,
        String seedHash,
        Map<UUID, TeamOutcome> teams,
        Map<UUID, PlayerOutcome> players,
        List<ObjectiveOutcome> objectives,
        Map<String, String> metadata
) {
    public MatchSettlement {
        Objects.requireNonNull(matchId, "matchId");
        Objects.requireNonNull(seedHash, "seedHash");
        teams = Map.copyOf(Objects.requireNonNull(teams, "teams"));
        players = Map.copyOf(Objects.requireNonNull(players, "players"));
        objectives = List.copyOf(Objects.requireNonNull(objectives, "objectives"));
        metadata = Map.copyOf(Objects.requireNonNull(metadata, "metadata"));
    }

    public static Builder builder(UUID matchId, String seedHash) {
        return new Builder(matchId, seedHash);
    }

    public Optional<TeamOutcome> team(UUID teamId) {
        return Optional.ofNullable(teams.get(teamId));
    }

    public Optional<PlayerOutcome> player(UUID playerId) {
        return Optional.ofNullable(players.get(playerId));
    }

    public static final class Builder {
        private final UUID matchId;
        private final String seedHash;
        private final Map<UUID, TeamOutcome> teams = new LinkedHashMap<>();
        private final Map<UUID, PlayerOutcome> players = new LinkedHashMap<>();
        private final List<ObjectiveOutcome> objectives = new ArrayList<>();
        private final Map<String, String> metadata = new LinkedHashMap<>();

        private Builder(UUID matchId, String seedHash) {
            this.matchId = Objects.requireNonNull(matchId, "matchId");
            this.seedHash = Objects.requireNonNull(seedHash, "seedHash");
        }

        public Builder team(UUID teamId, OutcomeType outcome) {
            teams.put(teamId, new TeamOutcome(teamId, outcome));
            return this;
        }

        public Builder player(UUID playerId, UUID teamId, OutcomeType outcome, Map<String, Integer> rewards) {
            players.put(playerId, new PlayerOutcome(playerId, teamId, outcome, rewards));
            return this;
        }

        public Builder player(
                UUID playerId,
                UUID teamId,
                OutcomeType outcome,
                List<RewardEvent> rewardEvents,
                Map<String, String> metadata
        ) {
            players.put(playerId, new PlayerOutcome(playerId, teamId, outcome, rewardEvents, metadata));
            return this;
        }

        public Builder objective(String objectiveId, OutcomeType outcome, UUID completedByTeam) {
            objectives.add(new ObjectiveOutcome(objectiveId, outcome, Optional.ofNullable(completedByTeam)));
            return this;
        }

        public Builder objective(String objectiveId, OutcomeType outcome, UUID completedByTeam, Map<String, String> metadata) {
            objectives.add(new ObjectiveOutcome(objectiveId, outcome, Optional.ofNullable(completedByTeam), metadata));
            return this;
        }

        public Builder metadata(String key, String value) {
            metadata.put(Objects.requireNonNull(key, "key"), Objects.requireNonNull(value, "value"));
            return this;
        }

        public MatchSettlement build() {
            return new MatchSettlement(matchId, seedHash, teams, players, objectives, metadata);
        }
    }
}
