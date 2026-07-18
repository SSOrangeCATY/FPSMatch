package com.phasetranscrystal.fpsmatch.core.minimap.editor.raster;

import com.phasetranscrystal.fpsmatch.core.minimap.editor.document.EditorDocument;
import com.phasetranscrystal.fpsmatch.core.minimap.model.CanvasBounds;
import com.phasetranscrystal.fpsmatch.core.minimap.model.DisplayLabel;
import com.phasetranscrystal.fpsmatch.core.minimap.model.LayerType;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EyedropperTest {
    @Test
    void samplesPaintedPixelAndReportsInheritedAsEmpty() {
        EditorDocument document = EditorDocument.createEmpty(
                new CanvasBounds(8, 8), 8, "ground", DisplayLabel.literal("Ground"));
        String layer = document.createLayer("ground", LayerType.RASTER_PAINT, DisplayLabel.literal("Paint"));
        RasterSurface surface = RasterSurface.bind(document, "ground", layer);
        surface.setPixel(3, 3, Rgba8.of(1, 2, 3, 255));
        Eyedropper dropper = new Eyedropper();
        assertEquals(Optional.of(Rgba8.of(1, 2, 3, 255)), dropper.sample(surface, 3, 3));
        assertTrue(dropper.sample(surface, 0, 0).isEmpty());
    }
}
