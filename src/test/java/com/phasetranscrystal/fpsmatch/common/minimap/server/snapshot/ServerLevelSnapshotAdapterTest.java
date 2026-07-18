package com.phasetranscrystal.fpsmatch.common.minimap.server.snapshot;

import com.phasetranscrystal.fpsmatch.common.minimap.server.WorldSectionKey;
import com.phasetranscrystal.fpsmatch.common.minimap.server.WorldSectionRevisionIndex;
import com.phasetranscrystal.fpsmatch.core.minimap.editor.bake.SectionCoord;
import com.phasetranscrystal.fpsmatch.core.minimap.editor.bake.SnapshotChannelId;
import com.phasetranscrystal.fpsmatch.core.minimap.editor.bake.WorldSectionSnapshot;
import com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerLevelSnapshotAdapterTest {
    private static final NamespacedId DIMENSION = NamespacedId.parse("minecraft:overworld");
    private static final SectionCoord SECTION = new SectionCoord(1, 0, 2);

    @Test
    void copiesImmutableSectionOnServerThreadWithRevisionStamp() {
        WorldSectionRevisionIndex index = new WorldSectionRevisionIndex();
        WorldSectionKey key = new WorldSectionKey(DIMENSION, 1, 0, 2);
        long revision = index.markMutated(key);

        RecordingSectionAccess access = new RecordingSectionAccess(true);
        ServerLevelSnapshotAdapter adapter = new ServerLevelSnapshotAdapter(
                DIMENSION, index, access, () -> true
        );

        Optional<WorldSectionSnapshot> copied = adapter.copySection(
                SECTION, List.of(SnapshotChannelId.BLOCKS, SnapshotChannelId.HEIGHT,
                        SnapshotChannelId.LIGHT, SnapshotChannelId.BIOME)
        );

        assertTrue(copied.isPresent());
        WorldSectionSnapshot section = copied.get();
        assertEquals(revision, section.sectionRevision());
        assertTrue(section.loaded());
        assertEquals("minecraft:stone", section.palette().blockId(1));
        assertArrayEquals(new byte[] {0, 1, 1, 0}, section.blockIndices());
        section.blockIndices()[0] = 9;
        assertEquals(0, section.blockIndices()[0]);
        assertEquals(1, access.copyCount.get());
        assertTrue(adapter.isSectionLoaded(SECTION));
        assertEquals(revision, adapter.sectionRevision(SECTION));
        assertFalse(section.stale());
    }

    @Test
    void marksStaleWhenSectionMutatesDuringCopy() {
        WorldSectionRevisionIndex index = new WorldSectionRevisionIndex();
        WorldSectionKey key = new WorldSectionKey(DIMENSION, 1, 0, 2);
        index.markMutated(key);

        AtomicInteger calls = new AtomicInteger();
        SectionAccess access = (coord, channels) -> {
            // Concurrent mutation between begin/finish revision stamps.
            if (calls.incrementAndGet() == 1) {
                index.markMutated(key);
            }
            return new SectionAccess.CopiedSection(
                    List.of("minecraft:air", "minecraft:stone"),
                    new byte[] {1},
                    new short[] {64},
                    new byte[] {15},
                    new int[] {1}
            );
        };

        ServerLevelSnapshotAdapter adapter = new ServerLevelSnapshotAdapter(
                DIMENSION, index, access, () -> true
        );
        WorldSectionSnapshot section = adapter.copySection(
                SECTION, List.of(SnapshotChannelId.BLOCKS)
        ).orElseThrow();
        assertTrue(section.stale());
    }

    @Test
    void rejectsCopyWhenCallerIsNotOnServerThread() {
        WorldSectionRevisionIndex index = new WorldSectionRevisionIndex();
        SectionAccess access = (coord, channels) -> new SectionAccess.CopiedSection(
                List.of("minecraft:air"),
                new byte[] {0},
                new short[] {0},
                new byte[] {0},
                new int[] {0}
        );
        ServerLevelSnapshotAdapter adapter = new ServerLevelSnapshotAdapter(
                DIMENSION, index, access, () -> false
        );
        Optional<WorldSectionSnapshot> result = adapter.copySection(
                SECTION, List.of(SnapshotChannelId.BLOCKS)
        );
        assertTrue(result.isEmpty());
    }

    @Test
    void returnsEmptyForUnloadedSectionsWithoutCallingCopy() {
        WorldSectionRevisionIndex index = new WorldSectionRevisionIndex();
        RecordingSectionAccess access = new RecordingSectionAccess(false);
        ServerLevelSnapshotAdapter adapter = new ServerLevelSnapshotAdapter(
                DIMENSION, index, access, () -> true
        );
        assertTrue(adapter.copySection(SECTION, List.of(SnapshotChannelId.BLOCKS)).isEmpty());
        assertEquals(0, access.copyCount.get());
        assertFalse(adapter.isSectionLoaded(SECTION));
    }

    private static final class RecordingSectionAccess implements SectionAccess {
        private final boolean loaded;
        private final AtomicInteger copyCount = new AtomicInteger();

        private RecordingSectionAccess(boolean loaded) {
            this.loaded = loaded;
        }

        @Override
        public boolean isSectionLoaded(SectionCoord coord) {
            return loaded;
        }

        @Override
        public CopiedSection copySection(SectionCoord coord, List<SnapshotChannelId> channels) {
            copyCount.incrementAndGet();
            return new CopiedSection(
                    List.of("minecraft:air", "minecraft:stone"),
                    new byte[] {0, 1, 1, 0},
                    new short[] {64, 64, 64, 64},
                    new byte[] {15, 15, 15, 15},
                    new int[] {1, 1, 1, 1}
            );
        }
    }
}