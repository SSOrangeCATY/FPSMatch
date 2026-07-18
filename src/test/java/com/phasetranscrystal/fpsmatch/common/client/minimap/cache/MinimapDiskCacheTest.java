package com.phasetranscrystal.fpsmatch.common.client.minimap.cache;

import com.phasetranscrystal.fpsmatch.core.minimap.format.Sha256Digest;
import com.phasetranscrystal.fpsmatch.core.minimap.model.MapKey;
import com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId;
import com.phasetranscrystal.fpsmatch.core.minimap.model.Sha256;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinimapDiskCacheTest {
    @TempDir
    Path temp;

    @Test
    void isolatesByServerDimensionMapDocumentRevisionAndHash() throws Exception {
        MinimapDiskCache cache = new MinimapDiskCache(temp, 4 * 1024L);
        byte[] payload = "runtime-entry".getBytes(StandardCharsets.UTF_8);
        MinimapCacheKey keyA = key("server-a", "minecraft:overworld", "cs", "dust2", "fpsmatch:d2", 1L, payload, "tiles/a.png");
        MinimapCacheKey keyB = key("server-b", "minecraft:overworld", "cs", "dust2", "fpsmatch:d2", 1L, payload, "tiles/a.png");
        cache.put(keyA, payload);
        assertTrue(cache.get(keyA).isPresent());
        assertTrue(cache.get(keyB).isEmpty());
        assertArrayEquals(payload, cache.get(keyA).orElseThrow());
    }

    @Test
    void isolatesSameRevisionAndEntryHashByRuntimeHash() {
        MinimapDiskCache cache = new MinimapDiskCache(temp, 4 * 1024L);
        byte[] payload = "runtime-entry".getBytes(StandardCharsets.UTF_8);
        Sha256 runtimeA = Sha256Digest.of("runtime-a".getBytes(StandardCharsets.UTF_8));
        Sha256 runtimeB = Sha256Digest.of("runtime-b".getBytes(StandardCharsets.UTF_8));
        MinimapCacheKey keyA = key(
                "server-a", "minecraft:overworld", "cs", "dust2", "fpsmatch:d2",
                1L, runtimeA, payload, "tiles/a.png"
        );
        MinimapCacheKey keyB = key(
                "server-a", "minecraft:overworld", "cs", "dust2", "fpsmatch:d2",
                1L, runtimeB, payload, "tiles/a.png"
        );

        assertTrue(cache.put(keyA, payload));
        assertTrue(cache.get(keyB).isEmpty());
    }

    @Test
    void rejectsHashMismatchAndCleansPartialFiles() throws Exception {
        MinimapDiskCache cache = new MinimapDiskCache(temp, 4 * 1024L);
        byte[] payload = "good".getBytes(StandardCharsets.UTF_8);
        MinimapCacheKey key = key("s", "minecraft:overworld", "cs", "dust2", "fpsmatch:d2", 2L, payload, "x.bin");
        // corrupt by writing wrong bytes under expected hash
        assertFalse(cache.put(key, "bad".getBytes(StandardCharsets.UTF_8)));
        assertTrue(cache.get(key).isEmpty());
        // no leftover temp files
        try (var stream = Files.walk(temp)) {
            long temps = stream.filter(path -> path.getFileName().toString().endsWith(".tmp")).count();
            assertEquals(0, temps);
        }
    }

    @Test
    void lruEvictsByPayloadBytesAndKeepsPinned() {
        MinimapDiskCache cache = new MinimapDiskCache(temp, 6L);
        byte[] a = "aaa".getBytes(StandardCharsets.UTF_8);
        byte[] b = "bbb".getBytes(StandardCharsets.UTF_8);
        byte[] c = "ccc".getBytes(StandardCharsets.UTF_8);
        MinimapCacheKey keyA = key("s", "minecraft:overworld", "cs", "a", "fpsmatch:a", 1L, a, "a");
        MinimapCacheKey keyB = key("s", "minecraft:overworld", "cs", "b", "fpsmatch:b", 1L, b, "b");
        MinimapCacheKey keyC = key("s", "minecraft:overworld", "cs", "c", "fpsmatch:c", 1L, c, "c");
        cache.put(keyA, a);
        cache.pin(keyA);
        cache.put(keyB, b);
        cache.put(keyC, c); // should evict B not A
        assertTrue(cache.get(keyA).isPresent());
        assertTrue(cache.get(keyB).isEmpty());
        assertTrue(cache.get(keyC).isPresent());
    }

    @Test
    void failedNewRevisionKeepsOldActiveEntry() {
        RuntimeEntryStore store = new RuntimeEntryStore();
        byte[] oldBytes = "old".getBytes(StandardCharsets.UTF_8);
        byte[] newBytes = "new".getBytes(StandardCharsets.UTF_8);
        MinimapCacheKey oldKey = key("s", "minecraft:overworld", "cs", "dust2", "fpsmatch:d2", 1L, oldBytes, "m");
        MinimapCacheKey newKey = key("s", "minecraft:overworld", "cs", "dust2", "fpsmatch:d2", 2L, newBytes, "m");
        store.activate(oldKey, oldBytes);
        assertFalse(store.tryActivate(newKey, newBytes, false)); // failed validation flag
        assertEquals(1L, store.active(oldKey.mapKey()).orElseThrow().revision());
        assertArrayEquals(oldBytes, store.active(oldKey.mapKey()).orElseThrow().payload());
    }

    @Test
    void rejectsDotSegmentsAndNeverWritesOutsideRoot() throws Exception {
        Path cacheRoot = temp.resolve("cache");
        MinimapDiskCache cache = new MinimapDiskCache(cacheRoot, 4 * 1024L);
        byte[] payload = "runtime-entry".getBytes(StandardCharsets.UTF_8);

        MinimapCacheKey parentServer = key(
                "..", "minecraft:overworld", "cs", "dust2", "fpsmatch:d2",
                1L, payload, "tiles/a.png"
        );
        MinimapCacheKey parentEntry = key(
                "server-a", "minecraft:overworld", "cs", "dust2", "fpsmatch:d2",
                1L, payload, ".."
        );

        assertFalse(cache.put(parentServer, payload));
        assertFalse(cache.put(parentEntry, payload));
        assertTrue(cache.get(parentServer).isEmpty());
        assertTrue(cache.get(parentEntry).isEmpty());
        try (var paths = Files.walk(temp)) {
            assertEquals(0, paths.filter(Files::isRegularFile).count());
        }
    }

    private static MinimapCacheKey key(
            String server,
            String dimension,
            String gameType,
            String mapName,
            String document,
            long revision,
            byte[] payload,
            String path
    ) {
        return key(
                server, dimension, gameType, mapName, document, revision,
                Sha256Digest.of((document + revision).getBytes(StandardCharsets.UTF_8)),
                payload, path
        );
    }

    private static MinimapCacheKey key(
            String server,
            String dimension,
            String gameType,
            String mapName,
            String document,
            long revision,
            Sha256 runtimeHash,
            byte[] payload,
            String path
    ) {
        return new MinimapCacheKey(
                server,
                NamespacedId.parse(dimension),
                new MapKey(gameType, mapName),
                NamespacedId.parse(document),
                revision,
                runtimeHash,
                Sha256Digest.of(payload),
                path
        );
    }
}
