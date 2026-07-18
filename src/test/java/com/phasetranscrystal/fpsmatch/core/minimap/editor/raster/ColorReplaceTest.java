package com.phasetranscrystal.fpsmatch.core.minimap.editor.raster;

import com.phasetranscrystal.fpsmatch.core.minimap.editor.document.EditorDocument;
import com.phasetranscrystal.fpsmatch.core.minimap.model.CanvasBounds;
import com.phasetranscrystal.fpsmatch.core.minimap.model.DisplayLabel;
import com.phasetranscrystal.fpsmatch.core.minimap.model.LayerType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ColorReplaceTest {
    @Test
    void replacesMatchingPixelsInsideSelectionOnly() {
        EditorDocument document = EditorDocument.createEmpty(
                new CanvasBounds(8, 8), 8, "ground", DisplayLabel.literal("Ground"));
        String layer = document.createLayer("ground", LayerType.RASTER_PAINT, DisplayLabel.literal("Paint"));
        RasterSurface surface = RasterSurface.bind(document, "ground", layer);
        surface.setPixel(1, 1, Rgba8.of(10, 10, 10, 255));
        surface.setPixel(5, 5, Rgba8.of(10, 10, 10, 255));
        surface.setSelection(new RectangleSelection(0, 0, 4, 4));
        int changed = new ColorReplace(new ColorTolerance(0))
                .replace(surface, Rgba8.of(10, 10, 10, 255), Rgba8.of(20, 20, 20, 255));
        assertEquals(1, changed);
        assertEquals(Rgba8.of(20, 20, 20, 255), surface.getPixel(1, 1));
        assertEquals(Rgba8.of(10, 10, 10, 255), surface.getPixel(5, 5));
    }
}
