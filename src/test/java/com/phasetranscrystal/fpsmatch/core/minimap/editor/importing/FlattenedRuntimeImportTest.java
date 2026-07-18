package com.phasetranscrystal.fpsmatch.core.minimap.editor.importing;

import com.phasetranscrystal.fpsmatch.core.minimap.editor.document.EditorDocument;
import com.phasetranscrystal.fpsmatch.core.minimap.model.CanvasBounds;
import com.phasetranscrystal.fpsmatch.core.minimap.model.DisplayLabel;
import com.phasetranscrystal.fpsmatch.core.minimap.model.LayerType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlattenedRuntimeImportTest {
    @Test
    void flattenedRuntimeImportCreatesNewRasterSourceWithoutOriginalLayerClaims() {
        EditorDocument source = EditorDocument.createEmpty(
                new CanvasBounds(8, 8), 4, "ground", DisplayLabel.literal("Ground"));
        source.createLayer("ground", LayerType.WORLD_BAKE, DisplayLabel.literal("World"));
        source.createLayer("ground", LayerType.RASTER_PAINT, DisplayLabel.literal("Paint"));
        source.createLayer("ground", LayerType.VECTOR, DisplayLabel.literal("Vectors"));

        int[] flat = new int[8 * 8];
        java.util.Arrays.fill(flat, 0xFF112233);
        FlattenedRuntimeImportService service = new FlattenedRuntimeImportService();
        EditorDocument imported = service.importFlattenedRuntime(
                source.canvas(),
                source.tileEdge(),
                "ground",
                flat,
                "flattened_base"
        );

        assertEquals(1, imported.floor("ground").layerIds().size());
        String layerId = imported.floor("ground").layerIds().get(0);
        assertEquals(LayerType.RASTER_PAINT, imported.layer("ground", layerId).type());
        assertFalse(imported.floor("ground").layers().stream()
                .anyMatch(layer -> layer.type() == LayerType.WORLD_BAKE
                        || layer.type() == LayerType.VECTOR
                        || layer.type() == LayerType.IMPORTED_IMAGE));
        assertEquals(0xFF112233, imported.tilePixels("ground", layerId, 0, 0)[0]);
        assertTrue(imported.isFlattened() == false); // editable source, not a flattened runtime view
    }
}
