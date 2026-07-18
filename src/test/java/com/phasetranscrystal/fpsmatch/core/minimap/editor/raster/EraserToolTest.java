package com.phasetranscrystal.fpsmatch.core.minimap.editor.raster;

import com.phasetranscrystal.fpsmatch.core.minimap.editor.document.EditorDocument;
import com.phasetranscrystal.fpsmatch.core.minimap.editor.document.MissingTileSemantics;
import com.phasetranscrystal.fpsmatch.core.minimap.model.CanvasBounds;
import com.phasetranscrystal.fpsmatch.core.minimap.model.DisplayLabel;
import com.phasetranscrystal.fpsmatch.core.minimap.model.LayerType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EraserToolTest {
    @Test
    void eraserRestoresInheritOnRasterPaintLayer() {
        EditorDocument document = EditorDocument.createEmpty(
                new CanvasBounds(32, 32), 16, "ground", DisplayLabel.literal("Ground"));
        String layer = document.createLayer("ground", LayerType.RASTER_PAINT, DisplayLabel.literal("Paint"));
        RasterSurface surface = RasterSurface.bind(document, "ground", layer);
        new BrushStroke(BrushStamp.square(1, false), Rgba8.of(10, 20, 30, 255)).apply(surface, 4, 4);
        assertEquals(Rgba8.of(10, 20, 30, 255), surface.getPixel(4, 4));

        new EraserStroke(BrushStamp.square(1, false)).apply(surface, 4, 4);
        assertTrue(surface.isInherited(4, 4));
        assertEquals(0, surface.getPixel(4, 4));
        assertEquals(MissingTileSemantics.INHERIT,
                document.missingTileSemantics(LayerType.RASTER_PAINT));
    }
}
