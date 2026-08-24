package com.ptcrys.fpsmatch.common.client.minimap.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.ptcrys.fpsmatch.common.client.minimap.RuntimeGeneration;
import com.ptcrys.fpsmatch.common.client.minimap.cache.DecodedTile;
import com.ptcrys.fpsmatch.core.minimap.format.Sha256Digest;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public final class MinecraftMinimapTextureManager
        implements MinimapTextureResolver, AutoCloseable {
    private final Supplier<Optional<RuntimeGeneration>> currentGeneration;
    private final BooleanSupplier renderThread;
    private final TexturePlatform platform;
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private final Map<String, StaticRecord> staticRecords = new LinkedHashMap<>();

    public MinecraftMinimapTextureManager(
            Supplier<Optional<RuntimeGeneration>> currentGeneration,
            BooleanSupplier renderThread,
            TexturePlatform platform
    ) {
        this.currentGeneration = Objects.requireNonNull(
                currentGeneration, "currentGeneration"
        );
        this.renderThread = Objects.requireNonNull(renderThread, "renderThread");
        this.platform = Objects.requireNonNull(platform, "platform");
    }

    public static MinecraftMinimapTextureManager createDefault(
            Supplier<Optional<RuntimeGeneration>> currentGeneration
    ) {
        return new MinecraftMinimapTextureManager(
                currentGeneration,
                RenderSystem::isOnRenderThreadOrInit,
                new MinecraftTexturePlatform()
        );
    }

    public synchronized Optional<TextureHandle> upload(
            String textureKey,
            DecodedTile tile,
            RuntimeGeneration generation
    ) {
        Objects.requireNonNull(textureKey, "textureKey");
        Objects.requireNonNull(tile, "tile");
        Objects.requireNonNull(generation, "generation");
        requireRenderThread();
        if (currentGeneration.get().filter(generation::equals).isEmpty()) {
            return Optional.empty();
        }
        byte[] rgba = tile.rgba();
        boolean visible = hasVisiblePixels(rgba);
        staticRecords.put(textureKey, new StaticRecord(generation, visible));
        Entry existing = entries.get(textureKey);
        if (existing != null && existing.generation().equals(generation)
                && existing.source() == TextureSource.STATIC) {
            return Optional.of(existing.handle());
        }
        if (existing != null && existing.generation().equals(generation)
                && existing.source() == TextureSource.GENERATED && !visible) {
            // Keep loaded-world pixels when an authored transparent placeholder
            // finishes decoding after the generated fallback.
            return Optional.of(existing.handle());
        }
        if (existing != null) {
            platform.release(existing.handle().location());
        }
        ResourceLocation location = textureLocation(textureKey, generation);
        platform.upload(location, tile.width(), tile.height(), rgba);
        TextureHandle handle = new TextureHandle(
                location, tile.width(), tile.height()
        );
        entries.put(textureKey, new Entry(
                generation, TextureSource.STATIC, handle, visible
        ));
        return Optional.of(handle);
    }

    /**
     * Drops one texture generation so a changed generated tile can be uploaded
     * again even when its texture key remains stable.
     */
    public synchronized void invalidate(
            String textureKey,
            RuntimeGeneration generation
    ) {
        Objects.requireNonNull(textureKey, "textureKey");
        Objects.requireNonNull(generation, "generation");
        requireRenderThread();
        Entry existing = entries.get(textureKey);
        if (existing != null && existing.generation().equals(generation)
                && existing.source() == TextureSource.GENERATED) {
            platform.release(existing.handle().location());
            entries.remove(textureKey);
        }
    }

    public synchronized void invalidateGenerated(
            String textureKey,
            RuntimeGeneration generation
    ) {
        Objects.requireNonNull(textureKey, "textureKey");
        Objects.requireNonNull(generation, "generation");
        requireRenderThread();
        Entry existing = entries.get(textureKey);
        if (existing != null && existing.generation().equals(generation)
                && existing.source() == TextureSource.GENERATED) {
            platform.release(existing.handle().location());
            entries.remove(textureKey);
        }
    }

    /** Uploads raw generated RGBA pixels through the same render-thread gate. */
    public synchronized Optional<TextureHandle> uploadGenerated(
            String textureKey,
            int width,
            int height,
            byte[] rgba,
            RuntimeGeneration generation
    ) {
        Objects.requireNonNull(rgba, "rgba");
        Objects.requireNonNull(textureKey, "textureKey");
        Objects.requireNonNull(generation, "generation");
        requireRenderThread();
        if (currentGeneration.get().filter(generation::equals).isEmpty()) {
            return Optional.empty();
        }
        Entry existing = entries.get(textureKey);
        if (existing != null && existing.generation().equals(generation)) {
            if (existing.source() == TextureSource.STATIC
                    && existing.hasVisiblePixels()) {
                return Optional.of(existing.handle());
            }
            if (existing.source() == TextureSource.GENERATED) {
                return Optional.of(existing.handle());
            }
            // A transparent authored placeholder must not mask loaded-world pixels.
            platform.release(existing.handle().location());
            entries.remove(textureKey);
        }
        try (DecodedTile tile = new DecodedTile(width, height, rgba)) {
            if (existing != null && entries.containsKey(textureKey)) {
                platform.release(existing.handle().location());
                entries.remove(textureKey);
            }
            ResourceLocation location = textureLocation(textureKey, generation);
            platform.upload(location, tile.width(), tile.height(), tile.rgba());
            TextureHandle handle = new TextureHandle(location, tile.width(), tile.height());
            entries.put(textureKey, new Entry(
                    generation, TextureSource.GENERATED, handle,
                    hasVisiblePixels(rgba)
            ));
            return Optional.of(handle);
        }
    }

    public synchronized boolean isStatic(
            String textureKey,
            RuntimeGeneration generation
    ) {
        Entry entry = entries.get(Objects.requireNonNull(textureKey, "textureKey"));
        return entry != null && entry.source() == TextureSource.STATIC
                && entry.generation().equals(generation);
    }

    public synchronized boolean hasStaticRecord(
            String textureKey,
            RuntimeGeneration generation
    ) {
        StaticRecord record = staticRecords.get(
                Objects.requireNonNull(textureKey, "textureKey")
        );
        return record != null && record.generation().equals(generation);
    }

    public synchronized boolean isVisibleStatic(
            String textureKey,
            RuntimeGeneration generation
    ) {
        Entry entry = entries.get(Objects.requireNonNull(textureKey, "textureKey"));
        return entry != null && entry.source() == TextureSource.STATIC
                && entry.hasVisiblePixels() && entry.generation().equals(generation);
    }

    public synchronized boolean isGenerated(
            String textureKey,
            RuntimeGeneration generation
    ) {
        Entry entry = entries.get(Objects.requireNonNull(textureKey, "textureKey"));
        return entry != null && entry.source() == TextureSource.GENERATED
                && entry.generation().equals(generation);
    }

    public synchronized void clearGenerated() {
        requireRenderThread();
        for (String key : new java.util.ArrayList<>(entries.keySet())) {
            Entry entry = entries.get(key);
            if (entry != null && entry.source() == TextureSource.GENERATED) {
                platform.release(entry.handle().location());
                entries.remove(key);
            }
        }
    }

    @Override
    public synchronized Optional<TextureHandle> resolve(String textureKey) {
        Entry entry = entries.get(Objects.requireNonNull(textureKey, "textureKey"));
        if (entry == null
                || currentGeneration.get().filter(entry.generation()::equals).isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(entry.handle());
    }

    public synchronized void reset() {
        requireRenderThread();
        for (Entry entry : entries.values()) {
            platform.release(entry.handle().location());
        }
        entries.clear();
        staticRecords.clear();
    }

    @Override
    public void close() {
        reset();
    }

    private void requireRenderThread() {
        if (!renderThread.getAsBoolean()) {
            throw new IllegalStateException("Texture operations require the render thread");
        }
    }

    private static ResourceLocation textureLocation(
            String textureKey,
            RuntimeGeneration generation
    ) {
        String identity = generation.serverIdentity()
                + '\n' + generation.dimension()
                + '\n' + generation.mapKey().gameType()
                + '\n' + generation.mapKey().mapName()
                + '\n' + generation.documentId()
                + '\n' + generation.revision()
                + '\n' + generation.runtimeHash()
                + '\n' + textureKey;
        String digest = Sha256Digest.of(identity.getBytes(StandardCharsets.UTF_8)).value();
        return new ResourceLocation("fpsmatch", "minimap/runtime/" + digest);
    }

    public interface TexturePlatform {
        void upload(
                ResourceLocation location,
                int width,
                int height,
                byte[] rgba
        );

        void release(ResourceLocation location);
    }

    private record Entry(
            RuntimeGeneration generation,
            TextureSource source,
            TextureHandle handle,
            boolean hasVisiblePixels
    ) {
    }

    private record StaticRecord(
            RuntimeGeneration generation,
            boolean hasVisiblePixels
    ) {
    }

    private static boolean hasVisiblePixels(byte[] rgba) {
        for (int index = 3; index < rgba.length; index += 4) {
            if ((rgba[index] & 0xFF) != 0) {
                return true;
            }
        }
        return false;
    }

    private enum TextureSource {
        STATIC,
        GENERATED
    }

    private static final class MinecraftTexturePlatform implements TexturePlatform {
        @Override
        public void upload(
                ResourceLocation location,
                int width,
                int height,
                byte[] rgba
        ) {
            NativeImage image = new NativeImage(width, height, true);
            DynamicTexture texture = null;
            try {
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        int index = (y * width + x) * 4;
                        int red = rgba[index] & 0xff;
                        int green = rgba[index + 1] & 0xff;
                        int blue = rgba[index + 2] & 0xff;
                        int alpha = rgba[index + 3] & 0xff;
                        image.setPixelRGBA(
                                x, y,
                                alpha << 24 | blue << 16 | green << 8 | red
                        );
                    }
                }
                texture = new DynamicTexture(image);
                Minecraft.getInstance().getTextureManager().register(
                        location, texture
                );
            } catch (RuntimeException failure) {
                if (texture != null) {
                    texture.close();
                } else {
                    image.close();
                }
                throw failure;
            }
        }

        @Override
        public void release(ResourceLocation location) {
            Minecraft.getInstance().getTextureManager().release(location);
        }
    }
}
