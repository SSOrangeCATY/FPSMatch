package com.ptcrys.fpsmatch.common.client.minimap.render;

import com.ptcrys.fpsmatch.common.client.minimap.RuntimeGeneration;
import com.ptcrys.fpsmatch.common.client.minimap.cache.BoundedPngDecoder;
import com.ptcrys.fpsmatch.common.client.minimap.cache.DecodedTile;
import com.ptcrys.fpsmatch.common.client.minimap.cache.DecodeBudgetException;

import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class MinimapTileUploadQueue {
    private final BoundedPngDecoder decoder;
    private final Executor backgroundExecutor;
    private final Consumer<Runnable> renderExecutor;
    private final Supplier<Optional<RuntimeGeneration>> currentGeneration;
    private final MinecraftMinimapTextureManager textures;
    private final int maxPending;
    private final Set<RequestKey> pending = new HashSet<>();
    private long epoch;

    public MinimapTileUploadQueue(
            BoundedPngDecoder decoder,
            Executor backgroundExecutor,
            Consumer<Runnable> renderExecutor,
            Supplier<Optional<RuntimeGeneration>> currentGeneration,
            MinecraftMinimapTextureManager textures,
            int maxPending
    ) {
        this.decoder = Objects.requireNonNull(decoder, "decoder");
        this.backgroundExecutor = Objects.requireNonNull(
                backgroundExecutor, "backgroundExecutor"
        );
        this.renderExecutor = Objects.requireNonNull(renderExecutor, "renderExecutor");
        this.currentGeneration = Objects.requireNonNull(
                currentGeneration, "currentGeneration"
        );
        this.textures = Objects.requireNonNull(textures, "textures");
        if (maxPending <= 0) {
            throw new IllegalArgumentException("maxPending must be positive");
        }
        this.maxPending = maxPending;
    }

    public synchronized boolean request(
            RuntimeGeneration generation,
            String textureKey,
            byte[] pngBytes
    ) {
        Objects.requireNonNull(generation, "generation");
        Objects.requireNonNull(textureKey, "textureKey");
        Objects.requireNonNull(pngBytes, "pngBytes");
        if (currentGeneration.get().filter(generation::equals).isEmpty()
                || pending.size() >= maxPending) {
            return false;
        }
        RequestKey key = new RequestKey(generation, textureKey);
        if (!pending.add(key)) {
            return false;
        }
        byte[] payload = pngBytes.clone();
        long requestEpoch = epoch;
        backgroundExecutor.execute(() -> decode(key, requestEpoch, payload));
        return true;
    }

    public synchronized int pendingCount() {
        return pending.size();
    }

    public synchronized void reset() {
        epoch++;
        pending.clear();
    }

    private void decode(RequestKey key, long requestEpoch, byte[] pngBytes) {
        DecodedTile tile;
        try {
            tile = decoder.decode(pngBytes);
        } catch (DecodeBudgetException invalid) {
            synchronized (this) {
                pending.remove(key);
            }
            return;
        }
        renderExecutor.accept(() -> upload(key, requestEpoch, tile));
    }

    private void upload(RequestKey key, long requestEpoch, DecodedTile tile) {
        try (tile) {
            synchronized (this) {
                if (requestEpoch != epoch
                        || !pending.remove(key)
                        || currentGeneration.get()
                        .filter(key.generation()::equals)
                        .isEmpty()) {
                    return;
                }
            }
            textures.upload(key.textureKey(), tile, key.generation());
        }
    }

    private record RequestKey(
            RuntimeGeneration generation,
            String textureKey
    ) {
    }
}
