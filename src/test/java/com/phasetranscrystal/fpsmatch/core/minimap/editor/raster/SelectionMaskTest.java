package com.phasetranscrystal.fpsmatch.core.minimap.editor.raster;

import com.phasetranscrystal.fpsmatch.core.minimap.editor.document.EditorDocument;
import com.phasetranscrystal.fpsmatch.core.minimap.model.CanvasBounds;
import com.phasetranscrystal.fpsmatch.core.minimap.model.DisplayLabel;
import com.phasetranscrystal.fpsmatch.core.minimap.model.LayerType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelectionMaskTest {
    @Test
    void rectangleSelectionIsHalfOpen() {
        SelectionMask mask = new RectangleSelection(10, 20, 30, 40);
        assertTrue(mask.contains(10, 20));
        assertTrue(mask.contains(29, 39));
        assertFalse(mask.contains(30, 20));
        assertFalse(mask.contains(10, 40));
    }

    @Test
    void polygonSelectionUsesInclusiveFillOfBoundaryPixels() {
        SelectionMask mask = new PolygonSelection(List.of(
                new IntPoint(0, 0),
                new IntPoint(10, 0),
                new IntPoint(10, 10),
                new IntPoint(0, 10)
        ));
        assertTrue(mask.contains(0, 0));
        assertTrue(mask.contains(5, 5));
        assertTrue(mask.contains(10, 5));
        assertFalse(mask.contains(11, 5));
    }

    @Test
    void brushRespectsSelectionClip() {
        EditorDocument document = EditorDocument.createEmpty(
                new CanvasBounds(32, 32),
                16,
                "ground",
                DisplayLabel.literal("Ground")
        );
        String layer = document.createLayer("ground", LayerType.RASTER_PAINT, DisplayLabel.literal("Paint"));
        RasterSurface surface = RasterSurface.bind(document, "ground", layer);
        surface.setSelection(new RectangleSelection(2, 2, 4, 4));
        new BrushStroke(BrushStamp.square(1, false), Rgba8.of(1, 2, 3, 255)).apply(surface, 1, 1);
        new BrushStroke(BrushStamp.square(1, false), Rgba8.of(1, 2, 3, 255)).apply(surface, 2, 2);
        assertEquals(0, surface.getPixel(1, 1));
        assertEquals(Rgba8.of(1, 2, 3, 255), surface.getPixel(2, 2));
    }
}