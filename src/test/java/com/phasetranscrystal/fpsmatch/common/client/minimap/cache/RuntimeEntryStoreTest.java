package com.phasetranscrystal.fpsmatch.common.client.minimap.cache;

import com.phasetranscrystal.fpsmatch.core.minimap.format.Sha256Digest;
import com.phasetranscrystal.fpsmatch.core.minimap.model.MapKey;
import com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId;
import com.phasetranscrystal.fpsmatch.core.minimap.model.Sha256;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeEntryStoreTest {
    @Test
    void activatesMultipleEntriesAsOneRuntimeGeneration() {
        RuntimeEntryStore store = new RuntimeEntryStore();
        byte[] regions = "regions".getBytes(StandardCharsets.UTF_8);
        byte[] tile = "tile".getBytes(StandardCharsets.UTF_8);
        Sha256 runtimeHash = hash("runtime-a");

        store.activateGeneration(List.of(
                entry(runtimeHash, 1L, "regions-runtime.json", regions),
                entry(runtimeHash, 1L, "floors/ground/tiles/0/0_0.png", tile)
        ));

        RuntimeEntryStore.ActiveRuntime active = store.activeRuntime(map()).orElseThrow();
        assertEquals(1L, active.revision());
        assertEquals(runtimeHash, active.runtimeHash());
        assertArrayEquals(regions, active.entry("regions-runtime.json").orElseThrow());
        assertArrayEquals(tile, active.entry("floors/ground/tiles/0/0_0.png").orElseThrow());
    }

    @Test
    void incompleteNewGenerationDoesNotReplaceOldRuntime() {
        RuntimeEntryStore store = new RuntimeEntryStore();
        byte[] oldBytes = "old".getBytes(StandardCharsets.UTF_8);
        Sha256 oldHash = hash("runtime-old");
        store.activateGeneration(List.of(entry(oldHash, 1L, "regions-runtime.json", oldBytes)));

        byte[] newBytes = "new".getBytes(StandardCharsets.UTF_8);
        MinimapCacheKey staged = key(
                hash("runtime-new"), 2L, "regions-runtime.json", newBytes
        );
        assertTrue(store.stage(staged, newBytes));

        RuntimeEntryStore.ActiveRuntime active = store.activeRuntime(map()).orElseThrow();
        assertEquals(1L, active.revision());
        assertEquals(oldHash, active.runtimeHash());
        assertArrayEquals(oldBytes, active.entry("regions-runtime.json").orElseThrow());
    }

    @Test
    void stagedGenerationNeverCombinesDifferentServerDimensionOrDocumentIdentities() {
        RuntimeEntryStore store = new RuntimeEntryStore();
        Sha256 runtimeHash = hash("shared-runtime");
        byte[] regions = "regions".getBytes(StandardCharsets.UTF_8);
        byte[] tile = "tile".getBytes(StandardCharsets.UTF_8);
        MinimapCacheKey serverARegions = key(
                "server-a", "minecraft:overworld", "fpsmatch:dust2",
                runtimeHash, 1L, "regions-runtime.json", regions
        );
        MinimapCacheKey serverBTile = key(
                "server-b", "minecraft:the_nether", "fpsmatch:other",
                runtimeHash, 1L, "floors/ground/tiles/0/0_0.png", tile
        );

        assertTrue(store.stage(serverARegions, regions));
        assertTrue(store.stage(serverBTile, tile));

        assertFalse(store.activateStaged(
                serverARegions,
                List.of("regions-runtime.json", "floors/ground/tiles/0/0_0.png")
        ));
        assertTrue(store.activeRuntime(map()).isEmpty());
    }

    @Test
    void sameGenerationViewportActivationKeepsExistingEntriesAndAddsNewTiles() {
        RuntimeEntryStore store = new RuntimeEntryStore();
        Sha256 runtimeHash = hash("runtime-a");
        byte[] manifest = "manifest".getBytes(StandardCharsets.UTF_8);
        byte[] regions = "regions".getBytes(StandardCharsets.UTF_8);
        byte[] connections = "connections".getBytes(StandardCharsets.UTF_8);
        byte[] styles = "styles".getBytes(StandardCharsets.UTF_8);
        byte[] firstTile = "tile-a".getBytes(StandardCharsets.UTF_8);
        byte[] secondTile = "tile-b".getBytes(StandardCharsets.UTF_8);
        List<RuntimeEntryStore.ActiveEntry> initial = List.of(
                entry(runtimeHash, 1L, "runtime-manifest.json", manifest),
                entry(runtimeHash, 1L, "regions-runtime.json", regions),
                entry(runtimeHash, 1L, "connections.json", connections),
                entry(runtimeHash, 1L, "styles-runtime.json", styles),
                entry(runtimeHash, 1L, "floors/ground/tiles/0/0_0.png", firstTile)
        );
        store.activateGeneration(initial);

        MinimapCacheKey secondTileKey = key(
                runtimeHash, 1L, "floors/ground/tiles/0/1_0.png", secondTile
        );
        assertTrue(store.stage(secondTileKey, secondTile));
        assertTrue(store.activateStaged(
                secondTileKey,
                List.of(
                        "runtime-manifest.json",
                        "regions-runtime.json",
                        "connections.json",
                        "styles-runtime.json",
                        "floors/ground/tiles/0/1_0.png"
                )
        ));

        RuntimeEntryStore.ActiveRuntime active = store.activeRuntime(secondTileKey).orElseThrow();
        assertArrayEquals(
                firstTile,
                active.entry("floors/ground/tiles/0/0_0.png").orElseThrow()
        );
        assertArrayEquals(
                secondTile,
                active.entry("floors/ground/tiles/0/1_0.png").orElseThrow()
        );
    }

    @Test
    void clearRemovesActiveAndStagedRuntimeMemory() {
        RuntimeEntryStore store = new RuntimeEntryStore();
        Sha256 activeHash = hash("runtime-active");
        byte[] activeBytes = "active".getBytes(StandardCharsets.UTF_8);
        store.activateGeneration(List.of(entry(
                activeHash, 1L, "regions-runtime.json", activeBytes
        )));

        byte[] stagedBytes = "staged".getBytes(StandardCharsets.UTF_8);
        MinimapCacheKey staged = key(
                hash("runtime-staged"), 2L, "regions-runtime.json", stagedBytes
        );
        assertTrue(store.stage(staged, stagedBytes));

        store.clear();

        assertTrue(store.activeRuntime(map()).isEmpty());
        assertFalse(store.hasStaged(staged, List.of("regions-runtime.json")));
    }

    private static RuntimeEntryStore.ActiveEntry entry(
            Sha256 runtimeHash,
            long revision,
            String path,
            byte[] payload
    ) {
        return new RuntimeEntryStore.ActiveEntry(
                key(runtimeHash, revision, path, payload),
                payload
        );
    }

    private static MinimapCacheKey key(
            Sha256 runtimeHash,
            long revision,
            String path,
            byte[] payload
    ) {
        return key(
                "server-a", "minecraft:overworld", "fpsmatch:dust2",
                runtimeHash, revision, path, payload
        );
    }

    private static MinimapCacheKey key(
            String serverIdentity,
            String dimension,
            String documentId,
            Sha256 runtimeHash,
            long revision,
            String path,
            byte[] payload
    ) {
        return new MinimapCacheKey(
                serverIdentity,
                NamespacedId.parse(dimension),
                map(),
                NamespacedId.parse(documentId),
                revision,
                runtimeHash,
                Sha256Digest.of(payload),
                path
        );
    }

    private static MapKey map() {
        return new MapKey("cs", "dust2");
    }

    private static Sha256 hash(String value) {
        return Sha256Digest.of(value.getBytes(StandardCharsets.UTF_8));
    }
}
