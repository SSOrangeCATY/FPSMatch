package com.phasetranscrystal.fpsmatch.core.minimap.editor.bake;

import com.phasetranscrystal.fpsmatch.core.minimap.editor.document.EditorDocument;
import com.phasetranscrystal.fpsmatch.core.minimap.model.DisplayLabel;
import com.phasetranscrystal.fpsmatch.core.minimap.model.LayerType;

import java.util.List;
import java.util.Objects;

public final class WorldBakeRasterizer {
    private final SamplerProfile profile;

    public WorldBakeRasterizer(SamplerProfile profile) {
        this.profile = Objects.requireNonNull(profile, "profile");
    }

    public String bakeIntoDocument(
            EditorDocument document,
            String floorId,
            String generatorId,
            List<WorldSectionSnapshot> sections
    ) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(sections, "sections");
        String layerId = document.createLayer(floorId, LayerType.WORLD_BAKE, DisplayLabel.literal(generatorId));
        document.bindGenerator(floorId, layerId, generatorId);
        for (WorldSectionSnapshot section : sections) {
            if (!section.loaded()) {
                continue;
            }
            int edge = (int) Math.round(Math.sqrt(section.blockIndices().length));
            if (edge * edge != section.blockIndices().length) {
                throw new IllegalArgumentException("Section block plane must be square");
            }
            int[] pixels = new int[edge * edge];
            byte[] blocks = section.blockIndices();
            for (int index = 0; index < blocks.length; index++) {
                String blockId = section.palette().blockId(blocks[index] & 0xFF);
                SampleDecision decision = profile.sample(blockId, List.of(), null);
                pixels[index] = switch (decision.kind()) {
                    case COLOR -> decision.argb();
                    case TRANSPARENT, IGNORE -> 0;
                };
            }
            // place section plane into tile (0,0) for unit tests / single-section bakes
            document.putTilePixels(floorId, layerId, 0, 0, pixels);
        }
        return layerId;
    }
}
