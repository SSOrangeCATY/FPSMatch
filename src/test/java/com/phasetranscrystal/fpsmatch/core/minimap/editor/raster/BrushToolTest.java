package com.phasetranscrystal.fpsmatch.core.minimap.editor.raster;

import com.phasetranscrystal.fpsmatch.core.minimap.editor.document.EditorDocument;
import com.phasetranscrystal.fpsmatch.core.minimap.model.CanvasBounds;
import com.phasetranscrystal.fpsmatch.core.minimap.model.DisplayLabel;
import com.phasetranscrystal.fpsmatch.core.minimap.model.LayerType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrushToolTest {
    @Test
    void hardSquareAndRoundBrushesSupportSizesOneThroughSixtyFour() {
        assertEquals(1, BrushStamp.MIN_SIZE);
        assertEquals(64, BrushStamp.MAX_SIZE);

        BrushStamp square = BrushStamp.square(1, false);
        assertEquals(1.0f, square.coverage(0, 0));

        BrushStamp round = BrushStamp.round(3, false);
        assertEquals(1.0f, round.coverage(0, 0));
        assertEquals(0.0f, round.coverage(2, 0));

        assertThrows(IllegalArgumentException.class, () -> BrushStamp.square(0, false));
        assertThrows(IllegalArgumentException.class, () -> BrushStamp.round(65, true));
    }

    @Test
    void optionalAntialiasProducesPartialEdgeCoverageInFreeCanvasMode() {
        BrushStamp hard = BrushStamp.round(5, false);
        BrushStamp soft = BrushStamp.round(5, true);
        float hardEdge = hard.coverage(2, 0);
        float softEdge = soft.coverage(2, 0);
        assertEquals(0.0f, hardEdge);
        assertTrue(softEdge > 0.0f && softEdge < 1.0f);
    }

    @Test
    void brushStrokeCompositesStraightAlphaAndZerosTransparentRgb() {
        EditorDocument document = document();
        String layer = document.createLayer("ground", LayerType.RASTER_PAINT, DisplayLabel.literal("Paint"));
        RasterSurface surface = RasterSurface.bind(document, "ground", layer);

        BrushStroke stroke = new BrushStroke(BrushStamp.square(1, false), Rgba8.of(255, 0, 0, 128));
        stroke.apply(surface, 1, 1);

        int pixel = surface.getPixel(1, 1);
        assertEquals(128, Rgba8.alpha(pixel));
        assertEquals(255, Rgba8.red(pixel));
        assertEquals(0, Rgba8.green(pixel));
        assertEquals(0, Rgba8.blue(pixel));

        BrushStroke clearish = new BrushStroke(BrushStamp.square(1, false), Rgba8.of(0, 255, 0, 0));
        clearish.apply(surface, 2, 2);
        assertEquals(0, surface.getPixel(2, 2));
    }

    private static EditorDocument document() {
        return EditorDocument.createEmpty(new CanvasBounds(32, 32), 16, "ground", DisplayLabel.literal("Ground"));
    }
}
