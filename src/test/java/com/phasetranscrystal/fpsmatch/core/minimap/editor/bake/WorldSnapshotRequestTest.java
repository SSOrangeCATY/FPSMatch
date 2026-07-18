package com.phasetranscrystal.fpsmatch.core.minimap.editor.bake;

import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapHardLimits;
import com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldSnapshotRequestTest {
    @Test
    void rejectsUnauthorizedAndOutOfBoundsRequests() {
        WorldSnapshotQuota quota = new WorldSnapshotQuota(4, 1024, 100);
        WorldSnapshotService service = new WorldSnapshotService(quota, (actor, mapKey) -> false);
        WorldSnapshotRequest request = new WorldSnapshotRequest(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "actor",
                "map_a",
                NamespacedId.parse("minecraft:overworld"),
                List.of(new SectionCoord(0, 0, 0)),
                List.of(SnapshotChannelId.BLOCKS),
                UnloadedSectionPolicy.SKIP,
                0L
        );
        assertThrows(WorldSnapshotException.class, () -> service.request(request, fakeWorld(Set.of())));
    }

    @Test
    void enforcesSectionByteAndLoadedPolicies() {
        WorldSnapshotQuota quota = new WorldSnapshotQuota(1, 64, 1000);
        WorldSnapshotService service = new WorldSnapshotService(quota, (actor, mapKey) -> true);
        WorldSnapshotRequest tooMany = new WorldSnapshotRequest(
                UUID.randomUUID(), "actor", "map_a",
                NamespacedId.parse("minecraft:overworld"),
                List.of(new SectionCoord(0, 0, 0), new SectionCoord(1, 0, 0)),
                List.of(SnapshotChannelId.BLOCKS),
                UnloadedSectionPolicy.SKIP,
                0L
        );
        assertThrows(WorldSnapshotException.class, () -> service.request(tooMany, fakeWorld(Set.of(new SectionCoord(0,0,0)))));

        WorldSnapshotRequest request = new WorldSnapshotRequest(
                UUID.randomUUID(), "actor", "map_a",
                NamespacedId.parse("minecraft:overworld"),
                List.of(new SectionCoord(0, 0, 0), new SectionCoord(1, 0, 0)),
                List.of(SnapshotChannelId.BLOCKS),
                UnloadedSectionPolicy.SKIP,
                0L
        );
        // with higher section quota
        WorldSnapshotService larger = new WorldSnapshotService(
                new WorldSnapshotQuota(8, 4096, 1000), (a, m) -> true);
        WorldSnapshotManifest manifest = larger.request(
                request, fakeWorld(Set.of(new SectionCoord(0, 0, 0))));
        assertEquals(1, manifest.sections().size());
        assertTrue(manifest.sections().get(0).loaded());
        assertEquals(1, manifest.skippedUnloaded());
    }

    private static WorldDataSource fakeWorld(Set<SectionCoord> loaded) {
        return new InMemoryWorldDataSource(loaded, 7L);
    }
}
