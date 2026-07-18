package com.phasetranscrystal.fpsmatch.common.client.minimap.render;

import com.phasetranscrystal.fpsmatch.common.client.minimap.RuntimeGeneration;
import com.phasetranscrystal.fpsmatch.common.client.minimap.cache.DecodedTile;
import com.phasetranscrystal.fpsmatch.core.minimap.format.Sha256Digest;
import com.phasetranscrystal.fpsmatch.core.minimap.model.MapKey;
import com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinimapTextureCacheTest {
    @Test
    void renderThreadRegisterReleaseAndGenerationCas() {
        AtomicInteger registerCount = new AtomicInteger();
        AtomicInteger releaseCount = new AtomicInteger();
        MinimapTextureCache cache = new MinimapTextureCache(
                2,
                () -> true,
                id -> registerCount.incrementAndGet(),
                id -> releaseCount.incrementAndGet()
        );
        RuntimeGeneration gen = generation(1);
        DecodedTile tile = new DecodedTile(2, 2, new byte[16]);
        TextureLease lease = cache.register("tile-a", tile, gen);
        assertTrue(lease.isActive());
        assertEquals(1, registerCount.get());
        assertTrue(cache.activate("tile-a", gen));
        // stale generation rejected
        assertFalse(cache.activate("tile-a", generation(2)));
        lease.release();
        assertEquals(1, releaseCount.get());
        assertEquals(0, cache.activeCount());
    }

    @Test
    void resetReleasesAllAndRejectsLateCallback() {
        AtomicInteger releaseCount = new AtomicInteger();
        MinimapTextureCache cache = new MinimapTextureCache(
                4, () -> true, id -> {}, id -> releaseCount.incrementAndGet()
        );
        RuntimeGeneration gen = generation(1);
        cache.register("a", new DecodedTile(1, 1, new byte[4]), gen);
        cache.register("b", new DecodedTile(1, 1, new byte[4]), gen);
        cache.reset();
        assertEquals(2, releaseCount.get());
        assertFalse(cache.activate("a", gen));
        assertEquals(0, cache.activeCount());
    }

    private static RuntimeGeneration generation(long local) {
        return new RuntimeGeneration(
                1L,
                "server",
                new MapKey("cs", "dust2"),
                NamespacedId.parse("fpsmatch:dust2"),
                1L,
                Sha256Digest.of("r".getBytes(StandardCharsets.UTF_8)),
                NamespacedId.parse("minecraft:overworld"),
                local
        );
    }
}