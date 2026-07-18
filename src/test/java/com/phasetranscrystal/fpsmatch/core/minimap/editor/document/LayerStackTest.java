package com.phasetranscrystal.fpsmatch.core.minimap.editor.document;

import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapHardLimits;
import com.phasetranscrystal.fpsmatch.core.minimap.model.BlendMode;
import com.phasetranscrystal.fpsmatch.core.minimap.model.CanvasBounds;
import com.phasetranscrystal.fpsmatch.core.minimap.model.DisplayLabel;
import com.phasetranscrystal.fpsmatch.core.minimap.model.LayerType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LayerStackTest {
    @Test
    void createRenameDuplicateReorderAndVisibilityControls() {
        EditorDocument document = baseDocument();
        String bottom = document.createLayer("ground", LayerType.WORLD_BAKE, DisplayLabel.literal("World"));
        String middle = document.createLayer("ground", LayerType.RASTER_PAINT, DisplayLabel.literal("Paint"));
        String top = document.createLayer("ground", LayerType.CUTOUT, DisplayLabel.literal("Cut"));

        assertEquals(List.of(bottom, middle, top), document.floor("ground").layerIds());

        document.renameLayer("ground", middle, DisplayLabel.literal("Detail"));
        assertEquals("Detail", document.layer("ground", middle).label().value());

        String copy = document.duplicateLayer("ground", middle);
        assertEquals(List.of(bottom, middle, top, copy), document.floor("ground").layerIds());
        assertEquals(LayerType.RASTER_PAINT, document.layer("ground", copy).type());
        assertEquals("Detail", document.layer("ground", copy).label().value());
        assertFalse(copy.equals(middle));

        document.reorderLayer("ground", copy, 1);
        assertEquals(List.of(bottom, copy, middle, top), document.floor("ground").layerIds());

        document.setLayerVisible("ground", middle, false);
        document.setLayerLocked("ground", top, true);
        document.setLayerOpacity("ground", bottom, 0.5);
        document.setLayerBlend("ground", bottom, BlendMode.MULTIPLY);
        document.setLayerMaskEnabled("ground", middle, true);

        assertFalse(document.layer("ground", middle).visible());
        assertTrue(document.layer("ground", top).locked());
        assertEquals(0.5, document.layer("ground", bottom).opacity());
        assertEquals(BlendMode.MULTIPLY, document.layer("ground", bottom).blendMode());
        assertTrue(document.layer("ground", middle).maskEnabled());
        assertTrue(document.layer("ground", middle).presentInSource());
    }

    @Test
    void lockedLayersRejectMutationsAndCutoutRejectsMaskAndColorBlend() {
        EditorDocument document = baseDocument();
        String paint = document.createLayer("ground", LayerType.RASTER_PAINT, DisplayLabel.literal("Paint"));
        String cutout = document.createLayer("ground", LayerType.CUTOUT, DisplayLabel.literal("Cut"));
        document.setLayerLocked("ground", paint, true);

        assertThrows(IllegalStateException.class,
                () -> document.setLayerOpacity("ground", paint, 0.2));
        assertThrows(IllegalStateException.class,
                () -> document.putTilePixels("ground", paint, 0, 0, solidTile(64, 64, 0xFF0000FF)));
        assertThrows(IllegalArgumentException.class,
                () -> document.setLayerMaskEnabled("ground", cutout, true));
        assertThrows(IllegalArgumentException.class,
                () -> document.setLayerBlend("ground", cutout, BlendMode.MULTIPLY));
    }

    @Test
    void enforcesLayerBudgetAndStableIds() {
        EditorDocument document = baseDocument();
        for (int index = 0; index < MinimapHardLimits.MAX_SOURCE_LAYERS; index++) {
            document.createLayer("ground", LayerType.RASTER_PAINT, DisplayLabel.literal("L" + index));
        }
        assertThrows(IllegalStateException.class,
                () -> document.createLayer("ground", LayerType.RASTER_PAINT, DisplayLabel.literal("Overflow")));

        EditorDocument second = baseDocument();
        String first = second.createLayer("ground", LayerType.VECTOR, DisplayLabel.literal("A"));
        String other = second.createLayer("ground", LayerType.VECTOR, DisplayLabel.literal("B"));
        assertTrue(MinimapHardLimits.MAX_INTERNAL_SLUG_UTF8_BYTES >= first.length());
        assertFalse(first.isBlank());
        assertFalse(first.equals(other));
    }

    private static EditorDocument baseDocument() {
        return EditorDocument.createEmpty(
                new CanvasBounds(128, 128),
                64,
                "ground",
                DisplayLabel.literal("Ground")
        );
    }

    private static int[] solidTile(int width, int height, int rgba) {
        int[] pixels = new int[width * height];
        java.util.Arrays.fill(pixels, rgba);
        return pixels;
    }
}
