package com.phasetranscrystal.fpsmatch.core.minimap.editor.document;

import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapHardLimits;
import com.phasetranscrystal.fpsmatch.core.minimap.model.BlendMode;
import com.phasetranscrystal.fpsmatch.core.minimap.model.CanvasBounds;
import com.phasetranscrystal.fpsmatch.core.minimap.model.DisplayLabel;
import com.phasetranscrystal.fpsmatch.core.minimap.model.LayerType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EditorDocumentTest {
    @Test
    void createsEmptyDocumentWithStableCanvasAndTileEdge() {
        EditorDocument document = EditorDocument.createEmpty(
                new CanvasBounds(512, 256),
                256,
                "ground",
                DisplayLabel.literal("Ground")
        );

        assertEquals(512, document.canvas().width());
        assertEquals(256, document.canvas().height());
        assertEquals(256, document.tileEdge());
        assertEquals(List.of("ground"), document.floorIds());
        assertTrue(document.floor("ground").layers().isEmpty());
        assertFalse(document.isFlattened());
    }

    @Test
    void rejectsInvalidTileEdgeAndTooManyFloors() {
        assertThrows(IllegalArgumentException.class,
                () -> EditorDocument.createEmpty(new CanvasBounds(64, 64), 0, "ground", DisplayLabel.literal("G")));
        assertThrows(IllegalArgumentException.class,
                () -> EditorDocument.createEmpty(new CanvasBounds(64, 64), MinimapHardLimits.MAX_TILE_EDGE + 1,
                        "ground", DisplayLabel.literal("G")));

        EditorDocument document = EditorDocument.createEmpty(
                new CanvasBounds(64, 64), 32, "f0", DisplayLabel.literal("F0"));
        for (int index = 1; index < MinimapHardLimits.MAX_FLOORS; index++) {
            document.addFloor("f" + index, DisplayLabel.literal("F" + index));
        }
        assertThrows(IllegalStateException.class,
                () -> document.addFloor("overflow", DisplayLabel.literal("Overflow")));
    }

    @Test
    void floorOperationsAreIsolatedAndDoNotFlatten() {
        EditorDocument document = EditorDocument.createEmpty(
                new CanvasBounds(128, 128), 64, "ground", DisplayLabel.literal("Ground"));
        document.addFloor("upper", DisplayLabel.literal("Upper"));

        String groundLayer = document.createLayer("ground", LayerType.RASTER_PAINT, DisplayLabel.literal("Paint"));
        String upperLayer = document.createLayer("upper", LayerType.CUTOUT, DisplayLabel.literal("Cut"));

        assertEquals(List.of(groundLayer), document.floor("ground").layerIds());
        assertEquals(List.of(upperLayer), document.floor("upper").layerIds());
        assertThrows(IllegalArgumentException.class, () -> document.layer("ground", upperLayer));
        assertThrows(IllegalArgumentException.class, () -> document.layer("upper", groundLayer));
        assertFalse(document.isFlattened());
        assertThrows(UnsupportedOperationException.class, document::flattenSource);
    }

    @Test
    void renameFloorKeepsLayerIdsAndTileData() {
        EditorDocument document = EditorDocument.createEmpty(
                new CanvasBounds(64, 64), 32, "ground", DisplayLabel.literal("Ground"));
        String layerId = document.createLayer("ground", LayerType.RASTER_PAINT, DisplayLabel.literal("Paint"));
        document.putTilePixels("ground", layerId, 0, 0, solidTile(32, 32, 0x11223344));

        document.renameFloor("ground", "base", DisplayLabel.literal("Base"));

        assertEquals(List.of("base"), document.floorIds());
        assertEquals(List.of(layerId), document.floor("base").layerIds());
        assertEquals(0x11223344, document.tilePixels("base", layerId, 0, 0)[0]);
        assertThrows(IllegalArgumentException.class, () -> document.floor("ground"));
    }

    private static int[] solidTile(int width, int height, int rgba) {
        int[] pixels = new int[width * height];
        java.util.Arrays.fill(pixels, rgba);
        return pixels;
    }
}