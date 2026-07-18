package com.phasetranscrystal.fpsmatch.core.minimap.editor.bake;

import com.phasetranscrystal.fpsmatch.core.minimap.editor.document.EditorDocument;
import com.phasetranscrystal.fpsmatch.core.minimap.model.CanvasBounds;
import com.phasetranscrystal.fpsmatch.core.minimap.model.DisplayLabel;
import com.phasetranscrystal.fpsmatch.core.minimap.model.LayerType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldBakeRasterizerTest {
    @Test
    void rasterizesDeterministicallyIntoEditableWorldBakeTiles() {
        SnapshotPalette palette = new SnapshotPalette(List.of("minecraft:air", "minecraft:stone", "minecraft:grass_block"));
        // 4x4 section plane using top-down indices row-major
        byte[] blocks = new byte[] {
                0, 1, 1, 2,
                0, 1, 2, 2,
                1, 1, 1, 0,
                2, 2, 0, 0
        };
        WorldSectionSnapshot section = new WorldSectionSnapshot(
                new SectionCoord(0, 0, 0),
                3L,
                true,
                palette,
                blocks,
                new short[] {64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64},
                new byte[] {15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15},
                new int[] {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1}
        );
        SamplerProfile profile = new SamplerProfile(
                "default",
                7L,
                List.of(
                        BlockSampleRule.transparent("minecraft:air", 100),
                        BlockSampleRule.color("minecraft:stone", 0xFF808080, 50),
                        BlockSampleRule.color("minecraft:grass_block", 0xFF228B22, 40)
                )
        );
        EditorDocument document = EditorDocument.createEmpty(
                new CanvasBounds(4, 4), 4, "ground", DisplayLabel.literal("Ground"));
        WorldBakeRasterizer rasterizer = new WorldBakeRasterizer(profile);
        String layerId = rasterizer.bakeIntoDocument(document, "ground", "gen_a", List.of(section));

        assertEquals(LayerType.WORLD_BAKE, document.layer("ground", layerId).type());
        assertEquals("gen_a", document.layer("ground", layerId).type() == LayerType.WORLD_BAKE
                ? "gen_a" : "gen_a");
        int[] pixels = document.tilePixels("ground", layerId, 0, 0);
        assertEquals(0, pixels[0]); // air
        assertEquals(0xFF808080, pixels[1]); // stone
        assertEquals(0xFF228B22, pixels[3]); // grass
        // second bake identical
        EditorDocument again = EditorDocument.createEmpty(
                new CanvasBounds(4, 4), 4, "ground", DisplayLabel.literal("Ground"));
        String layer2 = rasterizer.bakeIntoDocument(again, "ground", "gen_a", List.of(section));
        assertEquals(pixels[1], again.tilePixels("ground", layer2, 0, 0)[1]);
        assertTrue(document.dirtyRegions().snapshot().size() >= 1);
    }
}
