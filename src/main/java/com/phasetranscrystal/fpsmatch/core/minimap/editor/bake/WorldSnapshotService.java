package com.phasetranscrystal.fpsmatch.core.minimap.editor.bake;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiPredicate;

public final class WorldSnapshotService {
    private final WorldSnapshotQuota quota;
    private final BiPredicate<String, String> permissionCheck;

    public WorldSnapshotService(WorldSnapshotQuota quota, BiPredicate<String, String> permissionCheck) {
        this.quota = Objects.requireNonNull(quota, "quota");
        this.permissionCheck = Objects.requireNonNull(permissionCheck, "permissionCheck");
    }

    public WorldSnapshotManifest request(WorldSnapshotRequest request, WorldDataSource world) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(world, "world");
        if (!permissionCheck.test(request.actorId(), request.mapKey())) {
            throw new WorldSnapshotException("Snapshot request rejected: permission denied");
        }
        if (request.sections().size() > quota.maxSections()) {
            throw new WorldSnapshotException("Snapshot request exceeds section quota");
        }
        List<WorldSnapshotSectionStatus> statuses = new ArrayList<>();
        int skipped = 0;
        long declaredBytes = 0L;
        for (SectionCoord coord : request.sections()) {
            boolean loaded = world.isSectionLoaded(coord);
            if (!loaded) {
                if (request.unloadedPolicy() == UnloadedSectionPolicy.REJECT) {
                    throw new WorldSnapshotException("Snapshot rejected unloaded section " + coord);
                }
                skipped++;
                continue;
            }
            long revision = world.sectionRevision(coord);
            // conservative estimate: 256 cells * 8 bytes across channels
            declaredBytes += 256L * 8L;
            if (declaredBytes > quota.maxBytes()) {
                throw new WorldSnapshotException("Snapshot request exceeds byte quota");
            }
            statuses.add(new WorldSnapshotSectionStatus(coord, revision, true));
        }
        return new WorldSnapshotManifest(UUID.randomUUID(), statuses, skipped, declaredBytes);
    }
}
