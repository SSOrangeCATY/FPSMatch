package com.phasetranscrystal.fpsmatch.core.minimap.editor.bake;

import com.phasetranscrystal.fpsmatch.core.minimap.format.Sha256Digest;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorldSnapshotCodecTest {
    @Test
    void sectionSnapshotsAreImmutableAndCarryPaletteHeightLightBiome() {
        SnapshotPalette palette = new SnapshotPalette(List.of("minecraft:air", "minecraft:stone"));
        byte[] blocks = new byte[] {0, 1, 1, 0};
        WorldSectionSnapshot section = new WorldSectionSnapshot(
                new SectionCoord(1, 2, 3),
                9L,
                true,
                palette,
                blocks,
                new short[] {64, 65, 64, 63},
                new byte[] {15, 14, 13, 12},
                new int[] {1, 1, 2, 2}
        );
        assertEquals(9L, section.sectionRevision());
        assertArrayEquals(new byte[] {0, 1, 1, 0}, section.blockIndices());
        section.blockIndices()[0] = 99;
        assertEquals(0, section.blockIndices()[0]);
        assertEquals("minecraft:stone", section.palette().blockId(1));
        assertThrows(IndexOutOfBoundsException.class, () -> section.palette().blockId(9));
    }

    @Test
    void snapshotChunksExposeDeterministicContentHash() {
        WorldSectionSnapshot section = new WorldSectionSnapshot(
                new SectionCoord(0, 0, 0),
                1L,
                true,
                new SnapshotPalette(List.of("minecraft:air")),
                new byte[] {0},
                new short[] {0},
                new byte[] {0},
                new int[] {0}
        );
        SnapshotChunk chunk = SnapshotChunk.fromSections(
                UUID.fromString("00000000-0000-0000-0000-0000000000aa"),
                List.of(section)
        );
        assertEquals(1, chunk.sections().size());
        SnapshotChunk again = SnapshotChunk.fromSections(
                UUID.fromString("00000000-0000-0000-0000-0000000000aa"),
                List.of(section)
        );
        assertEquals(chunk.contentFingerprint(), again.contentFingerprint());
        assertEquals(64, chunk.contentFingerprint().length());
        assertNotSame(chunk.sections(), chunk.sections());
    }
}
