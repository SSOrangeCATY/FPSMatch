package com.phasetranscrystal.fpsmatch.core.spawn;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class DistanceSpawnStrategy implements SpawnStrategy {
    @Override
    public SpawnPlan createPlan(SpawnRequest request) {
        Objects.requireNonNull(request, "request");
        List<SpawnCandidate> candidates = request.candidates().stream()
                .filter(candidate -> isAllowed(candidate, request.constraints()))
                .toList();
        if (request.teams().isEmpty()) {
            return new SpawnPlan(true, false, List.of());
        }
        if (candidates.size() < request.teams().size()) {
            return SpawnPlan.failed(false);
        }

        double distance = Math.max(0.0d, request.constraints().minTeamDistance());
        double step = Math.max(1.0d, request.constraints().fallbackDistanceStep());
        boolean usedFallback = false;
        while (distance >= 0.0d) {
            Optional<List<SpawnAssignment>> assignments = assign(request.teams(), candidates, distance);
            if (assignments.isPresent()) {
                return new SpawnPlan(true, usedFallback, assignments.get());
            }
            distance -= step;
            usedFallback = true;
        }
        Optional<List<SpawnAssignment>> relaxedAssignments = assign(request.teams(), candidates, 0.0d);
        return relaxedAssignments
                .map(assignments -> new SpawnPlan(true, true, assignments))
                .orElseGet(() -> SpawnPlan.failed(true));
    }

    private static boolean isAllowed(SpawnCandidate candidate, SpawnConstraints constraints) {
        boolean outsideForbiddenZones = constraints.forbiddenZones().stream()
                .noneMatch(zone -> zone.contains(candidate.position()));
        boolean awayFromInterestPoints = constraints.protectedInterestPoints().stream()
                .noneMatch(point -> point.excludes(candidate.position()));
        return outsideForbiddenZones && awayFromInterestPoints;
    }

    private static Optional<List<SpawnAssignment>> assign(
            List<SpawnTeamRequest> teams,
            List<SpawnCandidate> candidates,
            double minDistance
    ) {
        return assignNext(teams, candidates, minDistance, 0, new ArrayList<>(), new HashSet<>());
    }

    private static Optional<List<SpawnAssignment>> assignNext(
            List<SpawnTeamRequest> teams,
            List<SpawnCandidate> candidates,
            double minDistance,
            int teamIndex,
            List<SpawnAssignment> assignments,
            Set<String> usedCandidateIds
    ) {
        if (teamIndex >= teams.size()) {
            return Optional.of(List.copyOf(assignments));
        }

        SpawnTeamRequest team = teams.get(teamIndex);
        for (SpawnCandidate candidate : candidates) {
            if (usedCandidateIds.contains(candidate.id()) || tooClose(candidate, assignments, minDistance)) {
                continue;
            }
            assignments.add(new SpawnAssignment(team.teamId(), candidate));
            usedCandidateIds.add(candidate.id());
            Optional<List<SpawnAssignment>> result = assignNext(
                    teams,
                    candidates,
                    minDistance,
                    teamIndex + 1,
                    assignments,
                    usedCandidateIds
            );
            if (result.isPresent()) {
                return result;
            }
            usedCandidateIds.remove(candidate.id());
            assignments.removeLast();
        }
        return Optional.empty();
    }

    private static boolean tooClose(SpawnCandidate candidate, List<SpawnAssignment> assignments, double minDistance) {
        return assignments.stream()
                .anyMatch(assignment -> candidate.position().distanceTo(assignment.candidate().position()) < minDistance);
    }
}
