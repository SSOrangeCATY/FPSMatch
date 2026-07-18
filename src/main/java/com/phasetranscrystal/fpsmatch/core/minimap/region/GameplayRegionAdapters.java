package com.phasetranscrystal.fpsmatch.core.minimap.region;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Pure adapters that turn capability-facing geometry into minimap region sources.
 * Callers convert AreaData / SpawnPointData into WorldAxisAlignedBounds / Point3 outside this class.
 */
public final class GameplayRegionAdapters {
    public static final double DEFAULT_SPAWN_PADDING = 1.5;
    public static final double DEFAULT_SPAWN_VERTICAL_PADDING = 1.0;

    private GameplayRegionAdapters() {
    }

    public static Optional<WorldAxisAlignedBounds> mapBoundary(Optional<WorldAxisAlignedBounds> mapArea) {
        Objects.requireNonNull(mapArea, "mapArea");
        return mapArea;
    }

    public static List<BombSiteDefinition> bombSitesFromAnonymousAreas(List<WorldAxisAlignedBounds> areas) {
        return BombSiteIdAssigner.assignAnonymous(areas);
    }

    public static Optional<WorldAxisAlignedBounds> spawnEnvelope(
            Collection<WorldAxisAlignedBounds.Point3> spawnPoints
    ) {
        return spawnEnvelope(spawnPoints, DEFAULT_SPAWN_PADDING, DEFAULT_SPAWN_VERTICAL_PADDING);
    }

    public static Optional<WorldAxisAlignedBounds> spawnEnvelope(
            Collection<WorldAxisAlignedBounds.Point3> spawnPoints,
            double horizontalPadding,
            double verticalPadding
    ) {
        Objects.requireNonNull(spawnPoints, "spawnPoints");
        if (spawnPoints.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(WorldAxisAlignedBounds.envelopeOfPoints(spawnPoints).expand(horizontalPadding, verticalPadding));
    }

    public static Optional<WorldAxisAlignedBounds> unionBoxes(Collection<WorldAxisAlignedBounds> boxes) {
        Objects.requireNonNull(boxes, "boxes");
        if (boxes.isEmpty()) {
            return Optional.empty();
        }
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (WorldAxisAlignedBounds box : boxes) {
            Objects.requireNonNull(box, "box");
            minX = Math.min(minX, box.minX());
            minY = Math.min(minY, box.minY());
            minZ = Math.min(minZ, box.minZ());
            maxX = Math.max(maxX, box.maxX());
            maxY = Math.max(maxY, box.maxY());
            maxZ = Math.max(maxZ, box.maxZ());
        }
        return Optional.of(new WorldAxisAlignedBounds(minX, minY, minZ, maxX, maxY, maxZ));
    }

    public static List<WorldAxisAlignedBounds> copyBoxes(List<WorldAxisAlignedBounds> boxes) {
        Objects.requireNonNull(boxes, "boxes");
        return List.copyOf(new ArrayList<>(boxes));
    }
}