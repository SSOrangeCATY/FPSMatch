package com.phasetranscrystal.fpsmatch.core.minimap.region;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BombSiteIdAssignerTest {
    @Test
    void assignsStableSiteNIdsIndependentOfDisplayNames() {
        List<BombSiteDefinition> sites = BombSiteIdAssigner.assignAnonymous(List.of(
                new WorldAxisAlignedBounds(0, 0, 0, 5, 3, 5),
                new WorldAxisAlignedBounds(10, 0, 10, 15, 3, 15)
        ));
        assertEquals(List.of("site_1", "site_2"), sites.stream().map(BombSiteDefinition::id).toList());
        assertTrue(sites.get(0).displayName().isEmpty());
        // reordering changes which geometry maps to which id; ids themselves stay the slug scheme
        assertEquals("site_1", BombSiteIdAssigner.stableIdForIndex(0));
        assertEquals("site_2", BombSiteIdAssigner.stableIdForIndex(1));
        assertNotEquals("site_a", sites.get(0).id());
    }

    @Test
    void emptyInputYieldsEmptySites() {
        assertTrue(BombSiteIdAssigner.assignAnonymous(List.of()).isEmpty());
    }
}