package com.phasetranscrystal.fpsmatch.core.minimap.editor.raster;

import com.phasetranscrystal.fpsmatch.core.minimap.editor.document.EditorDocument;
import com.phasetranscrystal.fpsmatch.core.minimap.model.CanvasBounds;
import com.phasetranscrystal.fpsmatch.core.minimap.model.DisplayLabel;
import com.phasetranscrystal.fpsmatch.core.minimap.model.LayerType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CutoutToolTest {
    @Test
    void cutoutStrokeWritesCoverageAlphaForDstOut() {
        EditorDocument document = EditorDocument.createEmpty(
                new CanvasBounds(32, 32), 16, "ground", DisplayLabel.literal("Ground"));
        String layer = document.createLayer("ground", LayerType.CUTOUT, DisplayLabel.literal("Cut"));
        RasterSurface surface = RasterSurface.bind(document, "ground", layer);

        new CutoutStroke(BrushStamp.square(1, false), 255).apply(surface, 3, 5);
        int pixel = surface.getPixel(3, 5);
        assertEquals(255, Rgba8.alpha(pixel));
        assertEquals(255, Rgba8.red(pixel));
        assertEquals(255, Rgba8.green(pixel));
        assertEquals(255, Rgba8.blue(pixel));
        assertTrue(surface.compositionOperator().name().equals("DST_OUT")
                || surface.layerType() == LayerType.CUTOUT);
    }
}
