package com.phasetranscrystal.fpsmatch.core.area;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class AreaTimerRuntime {
    private final AreaTimerPolicy policy;
    private final Set<AreaActor> presentActors = new LinkedHashSet<>();
    private final Map<UUID, Integer> progressByTeam = new LinkedHashMap<>();
    private final Set<UUID> completedTeams = new LinkedHashSet<>();
    private Set<UUID> activeTeams = Set.of();
    private boolean contested;

    public AreaTimerRuntime(AreaTimerPolicy policy) {
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    public void updatePresence(Collection<AreaActor> actors) {
        Objects.requireNonNull(actors, "actors");
        presentActors.clear();
        presentActors.addAll(actors);
    }

    public void restorePresence(Collection<AreaActor> actors) {
        updatePresence(actors);
    }

    public Set<AreaActor> presentActors() {
        return Set.copyOf(presentActors);
    }

    public void restoreProgress(UUID teamId, int progressTicks) {
        Objects.requireNonNull(teamId, "teamId");
        if (progressTicks < 0) {
            throw new IllegalArgumentException("progressTicks must not be negative");
        }
        if (progressTicks >= policy.completionTicks()) {
            completedTeams.add(teamId);
            progressByTeam.put(teamId, policy.completionTicks());
            return;
        }
        progressByTeam.put(teamId, progressTicks);
    }

    public void restoreCompleted(UUID teamId) {
        Objects.requireNonNull(teamId, "teamId");
        completedTeams.add(teamId);
        progressByTeam.put(teamId, policy.completionTicks());
    }

    public void tick() {
        Set<UUID> presentTeams = presentActors.stream()
                .map(AreaActor::teamId)
                .filter(teamId -> !completedTeams.contains(teamId))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        activeTeams = Set.copyOf(presentTeams);
        contested = presentTeams.size() > 1 && policy.pauseWhenContested();
        if (presentTeams.isEmpty()) {
            if (policy.resetWhenEmpty()) {
                progressByTeam.clear();
            }
            return;
        }
        if (contested) {
            return;
        }
        UUID teamId = presentTeams.iterator().next();
        int progress = progressByTeam.getOrDefault(teamId, 0) + 1;
        progressByTeam.put(teamId, progress);
        if (progress >= policy.completionTicks()) {
            completedTeams.add(teamId);
            progressByTeam.put(teamId, policy.completionTicks());
        }
    }

    public AreaTimerStatus statusForTeam(UUID teamId) {
        Objects.requireNonNull(teamId, "teamId");
        Set<UUID> presentTeams = presentActors.stream()
                .map(AreaActor::teamId)
                .filter(presentTeamId -> !completedTeams.contains(presentTeamId))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        boolean teamPresent = presentTeams.contains(teamId);
        boolean currentlyContested = presentTeams.size() > 1 && policy.pauseWhenContested();
        if (completedTeams.contains(teamId)) {
            return new AreaTimerStatus(AreaTimerState.COMPLETED, policy.completionTicks(), 0);
        }
        int progress = progressByTeam.getOrDefault(teamId, 0);
        int remaining = teamPresent || progress > 0 ? Math.max(0, policy.completionTicks() - progress) : 0;
        if (teamPresent && currentlyContested) {
            return new AreaTimerStatus(AreaTimerState.CONTESTED, progress, remaining);
        }
        if (teamPresent) {
            return new AreaTimerStatus(AreaTimerState.COUNTING_DOWN, progress, remaining);
        }
        if (!activeTeams.contains(teamId) && progress > 0 && !policy.resetWhenEmpty()) {
            return new AreaTimerStatus(AreaTimerState.IDLE, progress, remaining);
        }
        return AreaTimerStatus.idle();
    }

    public Set<UUID> completedTeams() {
        return Set.copyOf(completedTeams);
    }
}
