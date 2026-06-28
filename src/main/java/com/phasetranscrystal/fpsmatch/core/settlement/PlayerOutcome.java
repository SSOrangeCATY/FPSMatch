package com.phasetranscrystal.fpsmatch.core.settlement;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record PlayerOutcome(
        UUID playerId,
        UUID teamId,
        OutcomeType outcome,
        Map<String, Integer> rewards,
        List<RewardEvent> rewardEvents,
        Map<String, String> metadata
) {
    public PlayerOutcome(UUID playerId, UUID teamId, OutcomeType outcome, Map<String, Integer> rewards) {
        this(
                playerId,
                teamId,
                outcome,
                rewards,
                rewards.entrySet().stream()
                        .map(entry -> new RewardEvent(entry.getKey(), entry.getValue(), Map.of()))
                        .toList(),
                Map.of()
        );
    }

    public PlayerOutcome(
            UUID playerId,
            UUID teamId,
            OutcomeType outcome,
            List<RewardEvent> rewardEvents,
            Map<String, String> metadata
    ) {
        this(playerId, teamId, outcome, rewardTotals(rewardEvents), rewardEvents, metadata);
    }

    public PlayerOutcome {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(teamId, "teamId");
        Objects.requireNonNull(outcome, "outcome");
        rewards = Map.copyOf(Objects.requireNonNull(rewards, "rewards"));
        rewardEvents = List.copyOf(Objects.requireNonNull(rewardEvents, "rewardEvents"));
        metadata = Map.copyOf(Objects.requireNonNull(metadata, "metadata"));
    }

    public int rewardTotal(String type) {
        return rewards.getOrDefault(type, 0);
    }

    private static Map<String, Integer> rewardTotals(List<RewardEvent> rewardEvents) {
        Objects.requireNonNull(rewardEvents, "rewardEvents");
        return rewardEvents.stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        RewardEvent::type,
                        RewardEvent::amount,
                        Integer::sum
                ));
    }
}
