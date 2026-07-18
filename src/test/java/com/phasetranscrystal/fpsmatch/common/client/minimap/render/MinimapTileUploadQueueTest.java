package com.phasetranscrystal.fpsmatch.common.client.minimap.render;

import com.phasetranscrystal.fpsmatch.common.client.minimap.RuntimeGeneration;
import com.phasetranscrystal.fpsmatch.common.client.minimap.cache.BoundedPngDecoder;
import com.phasetranscrystal.fpsmatch.core.minimap.format.CanonicalPngCodecV1;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinimapTileUploadQueueTest {
    @Test
    void decodesOffThreadDeduplicatesAndUploadsOnlyOnRenderExecutor() {
        AtomicReference<RuntimeGeneration> current = new AtomicReference<>(generation(1));
        List<Runnable> background = new ArrayList<>();
        List<Runnable> render = new ArrayList<>();
        RecordingPlatform platform = new RecordingPlatform();
        MinecraftMinimapTextureManager textures = new MinecraftMinimapTextureManager(
                () -> Optional.of(current.get()), () -> true, platform
        );
        MinimapTileUploadQueue queue = new MinimapTileUploadQueue(
                new BoundedPngDecoder(16_384, 16_384),
                background::add,
                render::add,
                () -> Optional.of(current.get()),
                textures,
                8
        );
        byte[] png = CanonicalPngCodecV1.encode(2, 2, new byte[16]);

        assertTrue(queue.request(current.get(), "tile", png));
        assertFalse(queue.request(current.get(), "tile", png));
        assertEquals(1, background.size());
        assertEquals(0, platform.uploads);

        background.remove(0).run();
        assertEquals(1, render.size());
        assertEquals(0, platform.uploads);

        render.remove(0).run();
        assertEquals(1, platform.uploads);
        assertEquals(0, queue.pendingCount());
        assertTrue(textures.resolve("tile").isPresent());
    }

    @Test
    void staleRenderCallbackAndResetCannotCommitDecodedTile() {
        AtomicReference<RuntimeGeneration> current = new AtomicReference<>(generation(1));
        List<Runnable> background = new ArrayList<>();
        List<Runnable> render = new ArrayList<>();
        RecordingPlatform platform = new RecordingPlatform();
        MinecraftMinimapTextureManager textures = new MinecraftMinimapTextureManager(
                () -> Optional.of(current.get()), () -> true, platform
        );
        MinimapTileUploadQueue queue = new MinimapTileUploadQueue(
                new BoundedPngDecoder(16_384, 16_384),
                background::add,
                render::add,
                () -> Optional.of(current.get()),
                textures,
                8
        );
        byte[] png = CanonicalPngCodecV1.encode(1, 1, new byte[4]);
        assertTrue(queue.request(current.get(), "tile", png));
        background.remove(0).run();

        current.set(generation(2));
        queue.reset();
        render.remove(0).run();

        assertEquals(0, platform.uploads);
        assertEquals(0, queue.pendingCount());
        assertTrue(textures.resolve("tile").isEmpty());
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
        private int uploads;

        @Override
        public void upload(
                ResourceLocation location,
                int width,
                int height,
                byte[] rgba
        ) {
            uploads++;
        }

        @Override
        public void release(ResourceLocation location) {
        }
    }
}
