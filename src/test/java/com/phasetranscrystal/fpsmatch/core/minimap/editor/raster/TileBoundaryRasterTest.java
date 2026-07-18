package com.phasetranscrystal.fpsmatch.core.minimap.editor.raster;

import com.phasetranscrystal.fpsmatch.core.minimap.editor.document.EditorDocument;
import com.phasetranscrystal.fpsmatch.core.minimap.model.CanvasBounds;
import com.phasetranscrystal.fpsmatch.core.minimap.model.DisplayLabel;
import com.phasetranscrystal.fpsmatch.core.minimap.model.LayerType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TileBoundaryRasterTest {
    @Test
    void strokesCrossingTileBoundariesLeaveNoSeams() {
        EditorDocument document = EditorDocument.createEmpty(
                new CanvasBounds(48, 16), 16, "ground", DisplayLabel.literal("Ground"));
        String layer = document.createLayer("ground", LayerType.RASTER_PAINT, DisplayLabel.literal("Paint"));
        RasterSurface surface = RasterSurface.bind(document, "ground", layer);

        BrushStroke stroke = new BrushStroke(BrushStamp.square(3, false), Rgba8.of(9, 8, 7, 255));
        for (int x = 14; x <= 18; x++) {
            stroke.apply(surface, x, 8);
        }

        for (int x = 14; x <= 18; x++) {
            assertEquals(Rgba8.of(9, 8, 7, 255), surface.getPixel(x, 8));
        }
        // Neighbor pixels under the 3x3 stamp also continuous across the boundary at x=16
        assertEquals(Rgba8.of(9, 8, 7, 255), surface.getPixel(15, 7));
        assertEquals(Rgba8.of(9, 8, 7, 255), surface.getPixel(16, 7));
        assertEquals(Rgba8.of(9, 8, 7, 255), surface.getPixel(17, 7));
    }
}
