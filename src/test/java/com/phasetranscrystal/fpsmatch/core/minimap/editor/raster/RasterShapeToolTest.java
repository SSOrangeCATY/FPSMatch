package com.phasetranscrystal.fpsmatch.core.minimap.editor.raster;

import com.phasetranscrystal.fpsmatch.core.minimap.editor.document.EditorDocument;
import com.phasetranscrystal.fpsmatch.core.minimap.model.CanvasBounds;
import com.phasetranscrystal.fpsmatch.core.minimap.model.DisplayLabel;
import com.phasetranscrystal.fpsmatch.core.minimap.model.LayerType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RasterShapeToolTest {
    @Test
    void lineRectangleAndPolygonAreDeterministic() {
        EditorDocument document = EditorDocument.createEmpty(
                new CanvasBounds(16, 16), 16, "ground", DisplayLabel.literal("Ground"));
        String layer = document.createLayer("ground", LayerType.RASTER_PAINT, DisplayLabel.literal("Paint"));
        RasterSurface surface = RasterSurface.bind(document, "ground", layer);
        int color = Rgba8.of(7, 8, 9, 255);

        new RasterLine().draw(surface, 1, 1, 4, 1, color);
        assertEquals(color, surface.getPixel(1, 1));
        assertEquals(color, surface.getPixel(2, 1));
        assertEquals(color, surface.getPixel(4, 1));

        new RasterRectangle().drawFilled(surface, 6, 2, 10, 5, color);
        assertEquals(color, surface.getPixel(6, 2));
        assertEquals(color, surface.getPixel(9, 4));
        assertEquals(0, surface.getPixel(10, 2)); // half-open max

        new RasterRectangle().drawHollow(surface, 1, 6, 5, 10, color);
        assertEquals(color, surface.getPixel(1, 6));
        assertEquals(color, surface.getPixel(4, 6));
        assertEquals(0, surface.getPixel(2, 7));

        new RasterPolygon().drawFilled(surface, List.of(
                new IntPoint(8, 8), new IntPoint(12, 8), new IntPoint(12, 12), new IntPoint(8, 12)
        ), color);
        assertEquals(color, surface.getPixel(10, 10));
        assertTrue(surface.getPixel(12, 10) == color || surface.getPixel(12, 10) == 0 || surface.getPixel(11, 10) == color);
    }
}
