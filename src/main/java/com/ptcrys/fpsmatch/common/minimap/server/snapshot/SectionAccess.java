package com.ptcrys.fpsmatch.common.minimap.server.snapshot;

import com.ptcrys.fpsmatch.core.minimap.editor.bake.SectionCoord;
import com.ptcrys.fpsmatch.core.minimap.editor.bake.SnapshotChannelId;

import java.util.List;
import java.util.Objects;

/**
 * Platform-facing section read surface. Minecraft adapters implement this and must only
 * be invoked on the server thread; copied arrays become immutable {@code WorldSectionSnapshot}s.
 */
public interface SectionAccess {
    default boolean isSectionLoaded(SectionCoord coord) {
        return true;
    }

    CopiedSection copySection(SectionCoord coord, List<SnapshotChannelId> channels);

    record CopiedSection(
            List<String> paletteBlockIds,
            byte[] blockIndices,
            short[] heights,
            byte[] light,
            int[] biomes
    ) {
        public CopiedSection {
            Objects.requireNonNull(paletteBlockIds, "paletteBlockIds");
            Objects.requireNonNull(blockIndices, "blockIndices");
            Objects.requireNonNull(heights, "heights");
            Objects.requireNonNull(light, "light");
            Objects.requireNonNull(biomes, "biomes");
            paletteBlockIds = List.copyOf(paletteBlockIds);
            if (paletteBlockIds.isEmpty()) {
                throw new IllegalArgumentException("Copied section palette must not be empty");
            }
            blockIndices = blockIndices.clone();
            heights = heights.clone();
            light = light.clone();
            biomes = biomes.clone();
        }
    }
}