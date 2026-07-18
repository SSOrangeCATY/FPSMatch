package com.phasetranscrystal.fpsmatch.core.minimap.editor.raster;

import com.phasetranscrystal.fpsmatch.core.minimap.editor.document.EditorDocument;
import com.phasetranscrystal.fpsmatch.core.minimap.model.CanvasBounds;
import com.phasetranscrystal.fpsmatch.core.minimap.model.DisplayLabel;
import com.phasetranscrystal.fpsmatch.core.minimap.model.LayerType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FloodFillTest {
    @Test
    void inheritedCellsOnlyConnectToInheritedCells() {
        EditorDocument document = EditorDocument.createEmpty(
                new CanvasBounds(16, 16), 16, "ground", DisplayLabel.literal("Ground"));
        String layer = document.createLayer("ground", LayerType.RASTER_PAINT, DisplayLabel.literal("Paint"));
        RasterSurface surface = RasterSurface.bind(document, "ground", layer);
        // paint a barrier
        for (int y = 0; y < 16; y++) {
            surface.setPixel(5, y, Rgba8.of(1, 2, 3, 255));
        }
        FloodFill fill = new FloodFill(new ColorTolerance(0), new FillBudget(10_000));
        int changed = fill.fill(surface, 1, 1, Rgba8.of(9, 9, 9, 255));
        assertTrue(changed > 0);
        assertEquals(Rgba8.of(9, 9, 9, 255), surface.getPixel(1, 1));
        assertEquals(Rgba8.of(9, 9, 9, 255), surface.getPixel(4, 8));
        assertEquals(0, surface.getPixel(6, 8)); // beyond barrier remains inherit/transparent
        assertEquals(Rgba8.of(1, 2, 3, 255), surface.getPixel(5, 8));
    }

    @Test
    void paintedCellsUseMaxChannelTolerance() {
        EditorDocument document = EditorDocument.createEmpty(
                new CanvasBounds(8, 8), 8, "ground", DisplayLabel.literal("Ground"));
        String layer = document.createLayer("ground", LayerType.RASTER_PAINT, DisplayLabel.literal("Paint"));
        RasterSurface surface = RasterSurface.bind(document, "ground", layer);
        surface.setPixel(2, 2, Rgba8.of(100, 100, 100, 255));
        surface.setPixel(3, 2, Rgba8.of(110, 100, 100, 255));
        surface.setPixel(4, 2, Rgba8.of(130, 100, 100, 255));

        FloodFill fill = new FloodFill(new ColorTolerance(10), new FillBudget(100));
        fill.fill(surface, 2, 2, Rgba8.of(0, 255, 0, 255));
        assertEquals(Rgba8.of(0, 255, 0, 255), surface.getPixel(2, 2));
        assertEquals(Rgba8.of(0, 255, 0, 255), surface.getPixel(3, 2));
        assertEquals(Rgba8.of(130, 100, 100, 255), surface.getPixel(4, 2));
    }

    @Test
    void fillBudgetRejectsUnboundedWorkWithoutPartialCommit() {
        EditorDocument document = EditorDocument.createEmpty(
                new CanvasBounds(64, 64), 64, "ground", DisplayLabel.literal("Ground"));
        String layer = document.createLayer("ground", LayerType.RASTER_PAINT, DisplayLabel.literal("Paint"));
        RasterSurface surface = RasterSurface.bind(document, "ground", layer);
        FloodFill fill = new FloodFill(new ColorTolerance(0), new FillBudget(10));
        try {
            fill.fill(surface, 0, 0, Rgba8.of(1, 1, 1, 255));
            org.junit.jupiter.api.Assertions.fail("expected budget exceeded");
        } catch (FillBudgetExceededException expected) {
            assertEquals(0, surface.getPixel(0, 0));
            assertTrue(surface.isInherited(0, 0));
        }
    }
}
