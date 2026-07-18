package com.phasetranscrystal.fpsmatch.core.minimap.editor.document;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DirtyRegionSetTest {
    @Test
    void mergesOverlappingAndAdjacentRegionsDeterministically() {
        DirtyRegionSet dirty = new DirtyRegionSet();
        dirty.add(new DirtyRegion(10, 10, 20, 20));
        dirty.add(new DirtyRegion(15, 15, 30, 30));
        dirty.add(new DirtyRegion(40, 40, 50, 50));
        dirty.add(new DirtyRegion(50, 40, 60, 50));

        List<DirtyRegion> regions = dirty.snapshot();
        assertEquals(List.of(
                new DirtyRegion(10, 10, 30, 30),
                new DirtyRegion(40, 40, 60, 50)
        ), regions);
    }

    @Test
    void ignoresEmptyRegionsAndPreservesInsertionOrderOfDisjointBoxes() {
        DirtyRegionSet dirty = new DirtyRegionSet();
        dirty.add(new DirtyRegion(0, 0, 0, 10));
        dirty.add(new DirtyRegion(100, 0, 110, 10));
        dirty.add(new DirtyRegion(0, 100, 10, 110));
        dirty.add(new DirtyRegion(5, 5, 5, 5));

        assertEquals(List.of(
                new DirtyRegion(100, 0, 110, 10),
                new DirtyRegion(0, 100, 10, 110)
        ), dirty.snapshot());
    }

    @Test
    void documentRecordsDirtyRegionsForTileWritesWithoutImplicitFlatten() {
        EditorDocument document = EditorDocument.createEmpty(
                new com.phasetranscrystal.fpsmatch.core.minimap.model.CanvasBounds(128, 128),
                64,
                "ground",
                com.phasetranscrystal.fpsmatch.core.minimap.model.DisplayLabel.literal("Ground")
        );
        String layer = document.createLayer(
                "ground",
                com.phasetranscrystal.fpsmatch.core.minimap.model.LayerType.RASTER_PAINT,
                com.phasetranscrystal.fpsmatch.core.minimap.model.DisplayLabel.literal("Paint")
        );
        int[] pixels = new int[64 * 64];
        java.util.Arrays.fill(pixels, 0xFFFFFFFF);
        document.putTilePixels("ground", layer, 0, 0, pixels);
        document.putTilePixels("ground", layer, 1, 0, pixels);

        List<DirtyRegion> regions = document.dirtyRegions().snapshot();
        assertEquals(List.of(new DirtyRegion(0, 0, 128, 64)), regions);
        assertTrue(document.isFlattened() == false);
    }
}