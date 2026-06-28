package com.phasetranscrystal.fpsmatch.core.relation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class TeamRelationPolicy {
    private final TeamRelation defaultRelation;
    private final Map<TeamPair, TeamRelation> explicitRelations;
    private final Map<TeamPair, Integer> temporaryAlliances;
    private final Set<UUID> spectatorTeams;

    private TeamRelationPolicy(
            TeamRelation defaultRelation,
            Map<TeamPair, TeamRelation> explicitRelations,
            Map<TeamPair, Integer> temporaryAlliances,
            Set<UUID> spectatorTeams
    ) {
        this.defaultRelation = Objects.requireNonNull(defaultRelation, "defaultRelation");
        this.explicitRelations = new HashMap<>(explicitRelations);
        this.temporaryAlliances = new HashMap<>(temporaryAlliances);
        this.spectatorTeams = new HashSet<>(spectatorTeams);
    }

    public static TeamRelationPolicy hostileByDefault() {
        return builder().defaultRelation(TeamRelation.HOSTILE).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public TeamRelation relation(UUID firstTeam, UUID secondTeam) {
        Objects.requireNonNull(firstTeam, "firstTeam");
        Objects.requireNonNull(secondTeam, "secondTeam");
        if (spectatorTeams.contains(firstTeam) || spectatorTeams.contains(secondTeam)) {
            return TeamRelation.SPECTATOR;
        }
        if (firstTeam.equals(secondTeam)) {
            return TeamRelation.FRIENDLY;
        }
        TeamPair pair = TeamPair.of(firstTeam, secondTeam);
        if (temporaryAlliances.containsKey(pair)) {
            return TeamRelation.FRIENDLY;
        }
        return explicitRelations.getOrDefault(pair, defaultRelation);
    }

    public boolean canDamage(UUID attackerTeam, UUID targetTeam) {
        return relation(attackerTeam, targetTeam) == TeamRelation.HOSTILE;
    }

    public void tick() {
        temporaryAlliances.replaceAll((pair, ticks) -> ticks - 1);
        temporaryAlliances.values().removeIf(ticks -> ticks <= 0);
    }

    public static final class Builder {
        private TeamRelation defaultRelation = TeamRelation.HOSTILE;
        private final Map<TeamPair, TeamRelation> explicitRelations = new HashMap<>();
        private final Map<TeamPair, Integer> temporaryAlliances = new HashMap<>();
        private final Set<UUID> spectatorTeams = new HashSet<>();

        public Builder defaultRelation(TeamRelation defaultRelation) {
            this.defaultRelation = Objects.requireNonNull(defaultRelation, "defaultRelation");
            return this;
        }

        public Builder neutral(UUID firstTeam, UUID secondTeam) {
            explicitRelations.put(TeamPair.of(firstTeam, secondTeam), TeamRelation.NEUTRAL);
            return this;
        }

        public Builder spectator(UUID teamId) {
            spectatorTeams.add(Objects.requireNonNull(teamId, "teamId"));
            return this;
        }

        public Builder alliedForTicks(UUID firstTeam, UUID secondTeam, int ticks) {
            if (ticks > 0) {
                temporaryAlliances.put(TeamPair.of(firstTeam, secondTeam), ticks);
            }
            return this;
        }

        public TeamRelationPolicy build() {
            return new TeamRelationPolicy(defaultRelation, explicitRelations, temporaryAlliances, spectatorTeams);
        }
    }

    private record TeamPair(UUID first, UUID second) {
        private TeamPair {
            Objects.requireNonNull(first, "first");
            Objects.requireNonNull(second, "second");
        }

        private static TeamPair of(UUID first, UUID second) {
            Objects.requireNonNull(first, "first");
            Objects.requireNonNull(second, "second");
            return first.compareTo(second) <= 0 ? new TeamPair(first, second) : new TeamPair(second, first);
        }
    }
}
