package com.phasetranscrystal.fpsmatch.core.spawn;

import java.util.List;
import java.util.Objects;

public record SpawnConstraints(
        double minTeamDistance,
        List<SpawnForbiddenZone> forbiddenZones,
        double fallbackDistanceStep,
        List<SpawnInterestPoint> protectedInterestPoints
) {
    public SpawnConstraints(double minTeamDistance, List<SpawnForbiddenZone> forbiddenZones, double fallbackDistanceStep) {
        this(minTeamDistance, forbiddenZones, fallbackDistanceStep, List.of());
    }

    public SpawnConstraints {
        forbiddenZones = List.copyOf(Objects.requireNonNull(forbiddenZones, "forbiddenZones"));
        protectedInterestPoints = List.copyOf(Objects.requireNonNull(protectedInterestPoints, "protectedInterestPoints"));
    }
}
