package com.ptcrys.fpsmatch.core.minimap.region;

import com.ptcrys.fpsmatch.core.data.AreaData;
import com.ptcrys.fpsmatch.core.data.SpawnPointData;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Minecraft-facing projection of AreaData / SpawnPointData into pure minimap bounds.
 * Lives next to region providers so AreaData is never the minimap model.
 */
public final class AreaDataRegionProjection {
    private AreaDataRegionProjection() {
    }

    public static WorldAxisAlignedBounds toBounds(AreaData area) {
        Objects.requireNonNull(area, "area");
        var aabb = area.aabb();
        return new WorldAxisAlignedBounds(
                aabb.minX, aabb.minY, aabb.minZ,
                aabb.maxX, aabb.maxY, aabb.maxZ
        );
    }

    public static List<WorldAxisAlignedBounds> toBoundsList(List<AreaData> areas) {
        Objects.requireNonNull(areas, "areas");
        List<WorldAxisAlignedBounds> out = new ArrayList<>(areas.size());
        for (AreaData area : areas) {
            out.add(toBounds(area));
        }
        return List.copyOf(out);
    }

    public static Optional<WorldAxisAlignedBounds> mapBoundary(AreaData mapArea) {
        if (mapArea == null) {
            return Optional.empty();
        }
        return Optional.of(toBounds(mapArea));
    }

    public static List<BombSiteDefinition> bombSites(List<AreaData> anonymousBombAreas) {
        return BombSiteIdAssigner.assignAnonymous(toBoundsList(anonymousBombAreas));
    }

    public static Optional<WorldAxisAlignedBounds> shopUnion(List<AreaData> shopAreas) {
        return GameplayRegionAdapters.unionBoxes(toBoundsList(shopAreas));
    }

    public static Optional<WorldAxisAlignedBounds> spawnEnvelope(List<SpawnPointData> spawnPoints) {
        Objects.requireNonNull(spawnPoints, "spawnPoints");
        List<WorldAxisAlignedBounds.Point3> points = new ArrayList<>(spawnPoints.size());
        for (SpawnPointData point : spawnPoints) {
            Objects.requireNonNull(point, "spawnPoint");
            points.add(new WorldAxisAlignedBounds.Point3(point.getX(), point.getY(), point.getZ()));
        }
        return GameplayRegionAdapters.spawnEnvelope(points);
    }
}