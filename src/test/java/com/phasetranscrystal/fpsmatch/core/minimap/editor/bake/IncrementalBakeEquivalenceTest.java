package com.phasetranscrystal.fpsmatch.core.minimap.editor.bake;

import com.phasetranscrystal.fpsmatch.core.minimap.editor.document.EditorDocument;
import com.phasetranscrystal.fpsmatch.core.minimap.model.CanvasBounds;
import com.phasetranscrystal.fpsmatch.core.minimap.model.DisplayLabel;
import com.phasetranscrystal.fpsmatch.core.minimap.model.LayerType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IncrementalBakeEquivalenceTest {
    @Test
    void incrementalBakeMatchesFullBakeAndPreservesPaintAboveBase() {
        SamplerProfile profile = new SamplerProfile(
                "default",
                1L,
                List.of(
                        BlockSampleRule.transparent("minecraft:air", 100),
                        BlockSampleRule.color("minecraft:stone", 0xFF808080, 50)
                )
        );
        WorldSectionSnapshot section = sectionWithStone();
        WorldBakeRasterizer rasterizer = new WorldBakeRasterizer(profile);

        EditorDocument full = EditorDocument.createEmpty(new CanvasBounds(4, 4), 4, "ground", DisplayLabel.literal("G"));
        String fullLayer = rasterizer.bakeIntoDocument(full, "ground", "gen", List.of(section));

        EditorDocument incremental = EditorDocument.createEmpty(new CanvasBounds(4, 4), 4, "ground", DisplayLabel.literal("G"));
        String baseLayer = incremental.createLayer("ground", LayerType.WORLD_BAKE, DisplayLabel.literal("gen"));
        incremental.bindGenerator("ground", baseLayer, "gen");
        BakeGeneration generation = new BakeGeneration(1L);
        IncrementalBakeEngine engine = new IncrementalBakeEngine(rasterizer, generation);
        engine.applySection(incremental, "ground", baseLayer, section);
        assertEquals(
                full.tilePixels("ground", fullLayer, 0, 0)[1],
                incremental.tilePixels("ground", baseLayer, 0, 0)[1]
        );

        String paint = incremental.createLayer("ground", LayerType.RASTER_PAINT, DisplayLabel.literal("Paint"));
        int[] paintTile = new int[16];
        java.util.Arrays.fill(paintTile, 0xFFFF0000);
        incremental.putTilePixels("ground", paint, 0, 0, paintTile);
        // rebake base only
        engine.applySection(incremental, "ground", baseLayer, section);
        assertEquals(0xFFFF0000, incremental.tilePixels("ground", paint, 0, 0)[0]);
        assertTrue(engine.generation().value() >= 1L);
    }

    @Test
    void staleSnapshotCannotOverwriteNewerGeneration() {
        SamplerProfile profile = new SamplerProfile(
                "default", 1L,
                List.of(BlockSampleRule.color("minecraft:stone", 0xFF808080, 1))
        );
        IncrementalBakeEngine engine = new IncrementalBakeEngine(new WorldBakeRasterizer(profile), new BakeGeneration(5L));
        EditorDocument document = EditorDocument.createEmpty(new CanvasBounds(4, 4), 4, "ground", DisplayLabel.literal("G"));
        String layer = document.createLayer("ground", LayerType.WORLD_BAKE, DisplayLabel.literal("gen"));
        document.bindGenerator("ground", layer, "gen");
        boolean applied = engine.applySectionIfCurrent(document, "ground", layer, sectionWithStone(), 4L);
        assertEquals(false, applied);
    }

    private static WorldSectionSnapshot sectionWithStone() {
        return new WorldSectionSnapshot(
                new SectionCoord(0, 0, 0),
                3L,
                true,
                new SnapshotPalette(List.of("minecraft:air", "minecraft:stone")),
                new byte[] {0, 1, 1, 0, 0, 1, 1, 0, 0, 1, 1, 0, 0, 1, 1, 0},
                new short[16],
                new byte[16],
                new int[16]
        );
    }
}
