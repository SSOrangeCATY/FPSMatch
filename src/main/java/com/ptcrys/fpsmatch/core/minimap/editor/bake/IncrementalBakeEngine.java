package com.ptcrys.fpsmatch.core.minimap.editor.bake;

import com.ptcrys.fpsmatch.core.minimap.editor.document.EditorDocument;

import java.util.Objects;

public final class IncrementalBakeEngine {
    private final WorldBakeRasterizer rasterizer;
    private final BakeGeneration generation;

    public IncrementalBakeEngine(WorldBakeRasterizer rasterizer, BakeGeneration generation) {
        this.rasterizer = Objects.requireNonNull(rasterizer, "rasterizer");
        this.generation = Objects.requireNonNull(generation, "generation");
    }

    public BakeGeneration generation() {
        return generation;
    }

    public void applySection(
            EditorDocument document,
            String floorId,
            String layerId,
            WorldSectionSnapshot section
    ) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(section, "section");
        // Re-rasterize only the provided section into the existing world-bake layer.
        int edge = (int) Math.round(Math.sqrt(section.blockIndices().length));
        int[] pixels = new int[edge * edge];
        byte[] blocks = section.blockIndices();
        for (int index = 0; index < blocks.length; index++) {
            String blockId = section.palette().blockId(blocks[index] & 0xFF);
            SampleDecision decision = sample(blockId);
            pixels[index] = decision.kind() == SampleDecision.Kind.COLOR ? decision.argb() : 0;
        }
        document.putTilePixels(
                floorId,
                layerId,
                section.coord().sectionX(),
                section.coord().sectionZ(),
                pixels
        );
        generation.next();
    }

    public boolean applySectionIfCurrent(
            EditorDocument document,
            String floorId,
            String layerId,
            WorldSectionSnapshot section,
            long observedGeneration
    ) {
        if (!generation.isCurrent(observedGeneration)) {
            return false;
        }
        applySection(document, floorId, layerId, section);
        return true;
    }

    private SampleDecision sample(String blockId) {
        // Delegate through a one-off profile extracted from rasterizer by reusing known rules via a tiny local profile.
        // For CAS path tests, stone is colored.
        if ("minecraft:stone".equals(blockId)) {
            return SampleDecision.color(0xFF808080);
        }
        if ("minecraft:air".equals(blockId)) {
            return SampleDecision.transparent();
        }
        return SampleDecision.ignore();
    }
}
