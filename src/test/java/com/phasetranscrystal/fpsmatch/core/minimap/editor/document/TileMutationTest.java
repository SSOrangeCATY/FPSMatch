package com.phasetranscrystal.fpsmatch.core.minimap.editor.document;

import com.phasetranscrystal.fpsmatch.core.minimap.model.CanvasBounds;
import com.phasetranscrystal.fpsmatch.core.minimap.model.DisplayLabel;
import com.phasetranscrystal.fpsmatch.core.minimap.model.LayerType;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TileMutationTest {
    @Test
    void missingTilesFollowLayerTypeSemantics() {
        EditorDocument document = baseDocument();
        String raster = document.createLayer("ground", LayerType.RASTER_PAINT, DisplayLabel.literal("Paint"));
        String world = document.createLayer("ground", LayerType.WORLD_BAKE, DisplayLabel.literal("World"));
        String cutout = document.createLayer("ground", LayerType.CUTOUT, DisplayLabel.literal("Cut"));
        String imported = document.createLayer("ground", LayerType.IMPORTED_IMAGE, DisplayLabel.literal("Import"));
        document.bindImportedAsset("ground", imported, "asset_a");

        assertEquals(MissingTileSemantics.INHERIT, document.missingTileSemantics(LayerType.RASTER_PAINT));
        assertEquals(MissingTileSemantics.TRANSPARENT, document.missingTileSemantics(LayerType.WORLD_BAKE));
        assertEquals(MissingTileSemantics.ZERO_CUTOUT, document.missingTileSemantics(LayerType.CUTOUT));
        assertEquals(MissingTileSemantics.TRANSPARENT, document.missingTileSemantics(LayerType.IMPORTED_IMAGE));

        assertTrue(document.tilePixelsOptional("ground", raster, 0, 0).isEmpty());
        assertTrue(document.tilePixelsOptional("ground", world, 0, 0).isEmpty());
        assertTrue(document.tilePixelsOptional("ground", cutout, 0, 0).isEmpty());
        assertTrue(document.tilePixelsOptional("ground", imported, 0, 0).isEmpty());
        assertEquals(MissingTileSemantics.INHERIT, document.resolveMissingTile("ground", raster, 0, 0));
        assertEquals(MissingTileSemantics.TRANSPARENT, document.resolveMissingTile("ground", world, 0, 0));
        assertEquals(MissingTileSemantics.ZERO_CUTOUT, document.resolveMissingTile("ground", cutout, 0, 0));
    }

    @Test
    void tileWritesAreCopyOnWriteAndCallerBuffersAreNotRetained() {
        EditorDocument document = baseDocument();
        String layer = document.createLayer("ground", LayerType.RASTER_PAINT, DisplayLabel.literal("Paint"));
        int[] written = solidTile(64, 64, 0xAABBCCDD);
        document.putTilePixels("ground", layer, 0, 0, written);
        written[0] = 0x00000000;

        int[] firstRead = document.tilePixels("ground", layer, 0, 0);
        assertEquals(0xAABBCCDD, firstRead[0]);
        firstRead[1] = 0x11111111;
        int[] secondRead = document.tilePixels("ground", layer, 0, 0);
        assertEquals(0xAABBCCDD, secondRead[1]);
        assertNotSame(firstRead, secondRead);

        TileStore store = document.tileStore("ground", layer);
        int[] shared = store.pixelsOrNull(0, 0);
        int[] again = store.pixelsOrNull(0, 0);
        assertSame(shared, again);
        assertEquals(0xAABBCCDD, shared[0]);
    }

    @Test
    void maskTilesDefaultToOpaqueWhenEnabledAndMissing() {
        EditorDocument document = baseDocument();
        String layer = document.createLayer("ground", LayerType.RASTER_PAINT, DisplayLabel.literal("Paint"));
        document.setLayerMaskEnabled("ground", layer, true);
        assertTrue(document.maskPixelsOptional("ground", layer, 0, 0).isEmpty());
        assertEquals(1.0, document.resolveMaskAlpha("ground", layer, 0, 0, 0, 0));

        int[] mask = solidTile(64, 64, 0xFFFFFFFF);
        mask[0] = 0x00FFFFFF;
        document.putMaskPixels("ground", layer, 0, 0, mask);
        assertEquals(0, (document.maskPixels("ground", layer, 0, 0)[0] >>> 24) & 0xFF);
        assertEquals(0.0, document.resolveMaskAlpha("ground", layer, 0, 0, 0, 0));
    }

    @Test
    void edgeTilesMayBePartialAndInteriorTilesMustMatchEdge() {
        EditorDocument document = EditorDocument.createEmpty(
                new CanvasBounds(100, 50),
                64,
                "ground",
                DisplayLabel.literal("Ground")
        );
        String layer = document.createLayer("ground", LayerType.RASTER_PAINT, DisplayLabel.literal("Paint"));

        document.putTilePixels("ground", layer, 1, 0, solidTile(36, 50, 0xFF00FF00));
        assertEquals(36 * 50, document.tilePixels("ground", layer, 1, 0).length);

        assertThrows(IllegalArgumentException.class,
                () -> document.putTilePixels("ground", layer, 0, 0, solidTile(32, 32, 0xFF0000FF)));
        document.putTilePixels("ground", layer, 0, 0, solidTile(64, 50, 0xFF0000FF));
        assertEquals(64 * 50, document.tilePixels("ground", layer, 0, 0).length);
    }

    @Test
    void duplicateLayerCopiesTileDataIndependently() {
        EditorDocument document = baseDocument();
        String source = document.createLayer("ground", LayerType.RASTER_PAINT, DisplayLabel.literal("Paint"));
        document.putTilePixels("ground", source, 0, 0, solidTile(64, 64, 0x01020304));
        String copy = document.duplicateLayer("ground", source);
        document.putTilePixels("ground", copy, 0, 0, solidTile(64, 64, 0x0A0B0C0D));

        assertEquals(0x01020304, document.tilePixels("ground", source, 0, 0)[0]);
        assertEquals(0x0A0B0C0D, document.tilePixels("ground", copy, 0, 0)[0]);
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
        Arrays.fill(pixels, rgba);
        return pixels;
    }
}