package com.phasetranscrystal.fpsmatch.common.client.minimap.render;

import com.phasetranscrystal.fpsmatch.common.client.minimap.RuntimeGeneration;
import com.phasetranscrystal.fpsmatch.common.client.minimap.cache.DecodedTile;
import com.phasetranscrystal.fpsmatch.core.minimap.format.Sha256Digest;
import com.phasetranscrystal.fpsmatch.core.minimap.model.MapKey;
import com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinecraftMinimapTextureManagerTest {
    @Test
    void uploadsIdempotentlyAndRejectsStaleGeneration() {
        AtomicReference<RuntimeGeneration> current = new AtomicReference<>(generation(1));
        RecordingPlatform platform = new RecordingPlatform();
        MinecraftMinimapTextureManager manager = new MinecraftMinimapTextureManager(
                () -> Optional.ofNullable(current.get()),
                () -> true,
                platform
        );
        byte[] rgba = new byte[]{1, 2, 3, 4, 5, 6, 7, 8};
        DecodedTile tile = new DecodedTile(2, 1, rgba);

        MinimapTextureResolver.TextureHandle first = manager.upload(
                "floors/ground/tiles/0/0_0.png", tile, current.get()
        ).orElseThrow();
        MinimapTextureResolver.TextureHandle second = manager.upload(
                "floors/ground/tiles/0/0_0.png", tile, current.get()
        ).orElseThrow();

        assertEquals(first, second);
        assertEquals(1, platform.uploads.size());
        assertArrayEquals(rgba, platform.uploads.get(0).rgba());
        assertEquals(Optional.of(first), manager.resolve(
                "floors/ground/tiles/0/0_0.png"
        ));

        RuntimeGeneration stale = generation(2);
        assertTrue(manager.upload("stale", tile, stale).isEmpty());
        assertEquals(1, platform.uploads.size());
    }

    @Test
    void newGenerationReplacesOldTextureAndResetReleasesEverything() {
        AtomicReference<RuntimeGeneration> current = new AtomicReference<>(generation(1));
        RecordingPlatform platform = new RecordingPlatform();
        MinecraftMinimapTextureManager manager = new MinecraftMinimapTextureManager(
                () -> Optional.ofNullable(current.get()),
                () -> true,
                platform
        );
        DecodedTile tile = new DecodedTile(1, 1, new byte[]{0, 0, 0, 0});
        MinimapTextureResolver.TextureHandle first = manager.upload(
                "tile", tile, current.get()
        ).orElseThrow();

        current.set(generation(2));
        MinimapTextureResolver.TextureHandle second = manager.upload(
                "tile", tile, current.get()
        ).orElseThrow();

        assertEquals(List.of(first.location()), platform.releases);
        manager.reset();
        assertEquals(List.of(first.location(), second.location()), platform.releases);
        assertTrue(manager.resolve("tile").isEmpty());
    }

    private static RuntimeGeneration generation(long localGeneration) {
        return new RuntimeGeneration(
                1L,
                "server-a",
                new MapKey("cs", "dust2"),
                NamespacedId.parse("fpsmatch:dust2"),
                localGeneration,
                Sha256Digest.of(("runtime-" + localGeneration).getBytes(
                        StandardCharsets.UTF_8
                )),
                NamespacedId.parse("minecraft:overworld"),
                localGeneration
        );
    }

    private static final class RecordingPlatform
            implements MinecraftMinimapTextureManager.TexturePlatform {
        private final List<Upload> uploads = new ArrayList<>();
        private final List<ResourceLocation> releases = new ArrayList<>();

        @Override
        public void upload(
                ResourceLocation location,
                int width,
                int height,
                byte[] rgba
        ) {
            uploads.add(new Upload(location, width, height, rgba.clone()));
        }

        @Override
        public void release(ResourceLocation location) {
            releases.add(location);
        }
    }

    private record Upload(
            ResourceLocation location,
            int width,
            int height,
            byte[] rgba
    ) {
        private Upload {
            rgba = rgba.clone();
        }

        @Override
        public byte[] rgba() {
            return rgba.clone();
        }
    }
}
