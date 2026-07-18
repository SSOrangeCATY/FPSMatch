package com.phasetranscrystal.fpsmatch.core.minimap.editor.importing;

import com.phasetranscrystal.fpsmatch.core.minimap.editor.document.EditorDocument;
import com.phasetranscrystal.fpsmatch.core.minimap.format.BoundedPngReader;
import com.phasetranscrystal.fpsmatch.core.minimap.format.CanonicalPngCodecV1;
import com.phasetranscrystal.fpsmatch.core.minimap.model.CanvasBounds;
import com.phasetranscrystal.fpsmatch.core.minimap.model.DisplayLabel;
import com.phasetranscrystal.fpsmatch.core.minimap.model.LayerType;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PngImportWorkflowTest {
    @Test
    void importsBoundedCanonicalPngIntoEditableAssetAndLayer() {
        byte[] rgba = solidRgba(4, 4, (byte) 10, (byte) 20, (byte) 30, (byte) 255);
        byte[] png = CanonicalPngCodecV1.encode(4, 4, rgba);
        EditorDocument document = EditorDocument.createEmpty(
                new CanvasBounds(8, 8), 4, "ground", DisplayLabel.literal("Ground"));
        PngImportService service = new PngImportService();

        ImportedImageAsset asset = service.importCanonicalPng(
                document, "ground", "asset_a", png, ImagePlacementMode.ORIGINAL);

        assertEquals("asset_a", asset.assetId());
        assertEquals(4, asset.width());
        assertEquals(4, asset.height());
        assertTrue(document.floor("ground").layerIds().stream().anyMatch(id ->
                document.layer("ground", id).type() == LayerType.IMPORTED_IMAGE));
        BoundedPngReader.DecodedPng decoded = BoundedPngReader.decode(asset.canonicalPngBytes());
        assertEquals(10, decoded.rgba()[0] & 0xFF);
    }

    @Test
    void rejectsAssetSlugCollisionsAndOversizedDecodedImages() {
        EditorDocument document = EditorDocument.createEmpty(
                new CanvasBounds(8, 8), 4, "ground", DisplayLabel.literal("Ground"));
        PngImportService service = new PngImportService(Set.of("asset_a"));
        byte[] png = CanonicalPngCodecV1.encode(2, 2, solidRgba(2, 2, (byte) 1, (byte) 2, (byte) 3, (byte) 255));
        assertThrows(IllegalArgumentException.class,
                () -> service.importCanonicalPng(document, "ground", "asset_a", png, ImagePlacementMode.FIT));

        byte[] huge = new byte[1025 * 1025 * 4];
        Arrays.fill(huge, (byte) 1);
        assertThrows(IllegalArgumentException.class,
                () -> service.importRgba(document, "ground", "asset_b", 1025, 1025, huge, ImagePlacementMode.FIT));
    }

    private static byte[] solidRgba(int width, int height, byte r, byte g, byte b, byte a) {
        byte[] rgba = new byte[width * height * 4];
        for (int index = 0; index < width * height; index++) {
            int offset = index * 4;
            rgba[offset] = r;
            rgba[offset + 1] = g;
            rgba[offset + 2] = b;
            rgba[offset + 3] = a;
        }
        return rgba;
    }
}
