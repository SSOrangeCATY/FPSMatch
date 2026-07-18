package com.phasetranscrystal.fpsmatch.core.minimap.editor.bake;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DirtyBakePlannerTest {
    @Test
    void coalescesByDebounceAndOnlyReturnsIntersectingTiles() {
        DirtyBakePlanner planner = new DirtyBakePlanner(Duration.ofMillis(50), Duration.ofMillis(200), 4);
        planner.markSectionDirty(new SectionCoord(0, 0, 0), 1L, 0L);
        planner.markSectionDirty(new SectionCoord(0, 0, 0), 2L, 10L);
        planner.markSectionDirty(new SectionCoord(1, 0, 0), 1L, 20L);

        assertTrue(planner.plan(30L).isEmpty()); // debounce not elapsed for latest
        List<DirtyBakeJob> jobs = planner.plan(80L);
        assertEquals(2, jobs.size());
        assertEquals(2L, jobs.stream().filter(j -> j.section().equals(new SectionCoord(0,0,0))).findFirst().orElseThrow().sectionRevision());
        Set<TileCoord> tiles = jobs.get(0).intersectingTiles(16, 64, 0, 0);
        assertFalse(tiles.isEmpty());
    }

    @Test
    void maxDelayForcesPlanEvenIfDebounceNotMet() {
        DirtyBakePlanner planner = new DirtyBakePlanner(Duration.ofMillis(1000), Duration.ofMillis(100), 8);
        planner.markSectionDirty(new SectionCoord(2, 0, 0), 5L, 0L);
        assertTrue(planner.plan(50L).isEmpty());
        List<DirtyBakeJob> forced = planner.plan(120L);
        assertEquals(1, forced.size());
    }
}
