package com.phasetranscrystal.fpsmatch.core.minimap.region;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameplayRegionAdaptersTest {
    @Test
    void bombSitesUseStableSiteNIds() {
        List<BombSiteDefinition> sites = GameplayRegionAdapters.bombSitesFromAnonymousAreas(List.of(
                new WorldAxisAlignedBounds(0, 0, 0, 1, 1, 1),
                new WorldAxisAlignedBounds(5, 0, 5, 6, 1, 6)
        ));
        assertEquals(List.of("site_1", "site_2"), sites.stream().map(BombSiteDefinition::id).toList());
    }

    @Test
    void spawnEnvelopePadsPoints() {
        Optional<WorldAxisAlignedBounds> envelope = GameplayRegionAdapters.spawnEnvelope(List.of(
                new WorldAxisAlignedBounds.Point3(0, 64, 0),
                new WorldAxisAlignedBounds.Point3(10, 66, 4)
        ), 1.0, 0.5);
        assertTrue(envelope.isPresent());
        assertEquals(new WorldAxisAlignedBounds(-1, 63.5, -1, 11, 66.5, 5), envelope.get());
    }

    @Test
    void emptySourcesYieldEmpty() {
        assertTrue(GameplayRegionAdapters.spawnEnvelope(List.of()).isEmpty());
        assertTrue(GameplayRegionAdapters.unionBoxes(List.of()).isEmpty());
        assertTrue(GameplayRegionAdapters.mapBoundary(Optional.empty()).isEmpty());
    }

    @Test
    void unionBoxesMergesShopAreas() {
        Optional<WorldAxisAlignedBounds> union = GameplayRegionAdapters.unionBoxes(List.of(
                new WorldAxisAlignedBounds(0, 0, 0, 2, 1, 2),
                new WorldAxisAlignedBounds(1, 0, 1, 4, 2, 3)
        ));
        assertEquals(Optional.of(new WorldAxisAlignedBounds(0, 0, 0, 4, 2, 3)), union);
    }
}