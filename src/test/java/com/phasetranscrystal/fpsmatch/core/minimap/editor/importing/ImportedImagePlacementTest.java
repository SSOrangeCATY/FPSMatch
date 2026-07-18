package com.phasetranscrystal.fpsmatch.core.minimap.editor.importing;

import com.phasetranscrystal.fpsmatch.core.minimap.editor.document.EditorDocument;
import com.phasetranscrystal.fpsmatch.core.minimap.editor.raster.Rgba8;
import com.phasetranscrystal.fpsmatch.core.minimap.model.CanvasBounds;
import com.phasetranscrystal.fpsmatch.core.minimap.model.DisplayLabel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ImportedImagePlacementTest {
    @Test
    void fitFillAndOriginalPlacementAreIndependentOfWorldCalibration() {
        byte[] rgba = new byte[2 * 2 * 4];
        for (int i = 0; i < 4; i++) {
            rgba[i * 4] = (byte) 255;
            rgba[i * 4 + 3] = (byte) 255;
        }
        EditorDocument document = EditorDocument.createEmpty(
                new CanvasBounds(8, 4), 4, "ground", DisplayLabel.literal("Ground"));
        PngImportService service = new PngImportService();

        ImportedImageAsset original = service.importRgba(
                document, "ground", "orig", 2, 2, rgba, ImagePlacementMode.ORIGINAL);
        assertEquals(2, original.placedWidth());
        assertEquals(2, original.placedHeight());
        assertEquals(0, original.offsetX());
        assertEquals(0, original.offsetY());

        ImportedImageAsset fit = service.importRgba(
                document, "ground", "fit", 2, 2, rgba, ImagePlacementMode.FIT);
        assertEquals(4, fit.placedWidth());
        assertEquals(4, fit.placedHeight());

        ImportedImageAsset fill = service.importRgba(
                document, "ground", "fill", 2, 2, rgba, ImagePlacementMode.FILL);
        assertEquals(8, fill.placedWidth());
        assertEquals(4, fill.placedHeight());

        // Placement does not mutate canvas calibration metadata; document canvas stays authoritative.
        assertEquals(8, document.canvas().width());
        assertEquals(4, document.canvas().height());
        assertNotEquals(0, Rgba8.alpha(document.tilePixels("ground",
                document.floor("ground").layerIds().get(0), 0, 0)[0])
                + 0); // layer 0 may be first import
    }
}
