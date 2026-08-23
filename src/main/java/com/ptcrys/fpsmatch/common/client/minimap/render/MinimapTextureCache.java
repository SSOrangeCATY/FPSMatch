package com.ptcrys.fpsmatch.common.client.minimap.render;

import com.ptcrys.fpsmatch.common.client.minimap.RuntimeGeneration;
import com.ptcrys.fpsmatch.common.client.minimap.cache.DecodedTile;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Pure texture lifecycle cache. Actual GPU ids are managed by callbacks so unit tests stay platform-free.
 * Registration/activation must run on the render thread (checked via supplier).
 */
public final class MinimapTextureCache {
    private final int maxTextures;
    private final BooleanSupplier renderThreadCheck;
    private final Consumer<String> registerHook;
    private final Consumer<String> releaseHook;
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private final Map<String, Entry> active = new LinkedHashMap<>();

    public MinimapTextureCache(
            int maxTextures,
            BooleanSupplier renderThreadCheck,
            Consumer<String> registerHook,
            Consumer<String> releaseHook
    ) {
        if (maxTextures <= 0) {
            throw new IllegalArgumentException("maxTextures must be positive");
        }
        this.maxTextures = maxTextures;
        this.renderThreadCheck = Objects.requireNonNull(renderThreadCheck, "renderThreadCheck");
        this.registerHook = Objects.requireNonNull(registerHook, "registerHook");
        this.releaseHook = Objects.requireNonNull(releaseHook, "releaseHook");
    }

    public synchronized TextureLease register(String textureId, DecodedTile tile, RuntimeGeneration generation) {
        Objects.requireNonNull(textureId, "textureId");
        Objects.requireNonNull(tile, "tile");
        Objects.requireNonNull(generation, "generation");
        requireRenderThread();
        if (entries.size() >= maxTextures && !entries.containsKey(textureId)) {
            // evict inactive oldest
            String victim = entries.entrySet().stream()
                    .filter(e -> !active.containsKey(e.getKey()))
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElse(null);
            if (victim != null) {
                forceRemove(victim);
            }
        }
        Entry existing = entries.get(textureId);
        if (existing != null) {
            existing.lease.retain();
            return existing.lease;
        }
        registerHook.accept(textureId);
        TextureLease lease = new TextureLease(textureId, () -> forceRemove(textureId));
        entries.put(textureId, new Entry(lease, generation));
        return lease;
    }

    public synchronized boolean activate(String textureId, RuntimeGeneration generation) {
        Objects.requireNonNull(textureId, "textureId");
        Objects.requireNonNull(generation, "generation");
        requireRenderThread();
        Entry entry = entries.get(textureId);
        if (entry == null || !entry.generation.equals(generation) || !entry.lease.isActive()) {
            return false;
        }
        active.put(textureId, entry);
        return true;
    }

    public synchronized int activeCount() {
        return active.size();
    }

    public synchronized void reset() {
        for (String id : entries.keySet().toArray(String[]::new)) {
            forceRemove(id);
        }
        active.clear();
    }

    private void forceRemove(String textureId) {
        Entry entry = entries.remove(textureId);
        active.remove(textureId);
        if (entry != null) {
            releaseHook.accept(textureId);
        }
    }

    private void requireRenderThread() {
        if (!renderThreadCheck.getAsBoolean()) {
            throw new IllegalStateException("Texture operations require the render thread");
        }
    }

    private record Entry(TextureLease lease, RuntimeGeneration generation) {
    }
}