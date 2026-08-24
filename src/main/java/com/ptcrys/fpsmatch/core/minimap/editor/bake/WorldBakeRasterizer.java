package com.ptcrys.fpsmatch.core.minimap.editor.bake;

import com.ptcrys.fpsmatch.core.minimap.editor.document.EditorDocument;
import com.ptcrys.fpsmatch.core.minimap.model.DisplayLabel;
import com.ptcrys.fpsmatch.core.minimap.model.LayerType;

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
            // Section coordinates are already expressed in the editable tile
            // grid.  Keeping the world coordinate here is important when a
            // bake contains more than one loaded section; writing every
            // section to (0,0) silently loses all but the last section.
            document.putTilePixels(
                    floorId,
                    layerId,
                    section.coord().sectionX(),
                    section.coord().sectionZ(),
                    pixels
            );
        }
        return layerId;
    }
}
