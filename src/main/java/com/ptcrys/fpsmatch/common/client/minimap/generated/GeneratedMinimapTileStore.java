package com.ptcrys.fpsmatch.common.client.minimap.generated;

import com.ptcrys.fpsmatch.core.minimap.model.NamespacedId;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Thread-safe generated tile cache with epoch and revision write guards. */
public final class GeneratedMinimapTileStore {
    private final Map<GeneratedMinimapTileKey, GeneratedMinimapTile> tiles =
            new LinkedHashMap<>();
    private GeneratedWorldIdentity currentWorld;
    private GeneratedMinimapRuntimeBinding runtimeBinding;

    /** Binds generated pixels to the currently authoritative published runtime. */
    public synchronized void bindRuntime(GeneratedMinimapRuntimeBinding binding) {
        Objects.requireNonNull(binding, "binding");
        if (currentWorld != null
                && !currentWorld.dimension().equals(binding.generation().dimension())) {
            throw new IllegalArgumentException(
                    "Generated runtime dimension does not match the loaded client world"
            );
        }
        if (!binding.equals(runtimeBinding)) {
            tiles.clear();
            runtimeBinding = binding;
        }
    }

    public synchronized Optional<GeneratedMinimapRuntimeBinding> runtimeBinding() {
        return Optional.ofNullable(runtimeBinding);
    }

    synchronized void clearRuntimeBinding() {
        tiles.clear();
        runtimeBinding = null;
    }

    public synchronized void beginWorld(GeneratedWorldIdentity world) {
        Objects.requireNonNull(world, "world");
        if (!world.equals(currentWorld)) {
            tiles.clear();
            currentWorld = world;
            runtimeBinding = null;
        }
    }

    public synchronized Optional<GeneratedWorldIdentity> currentWorld() {
        return Optional.ofNullable(currentWorld);
    }

    public synchronized Optional<GeneratedMinimapTile> get(
            GeneratedMinimapTileKey key
    ) {
        Objects.requireNonNull(key, "key");
        return Optional.ofNullable(tiles.get(key));
    }

    /** Returns the newest revision for the same chunk in the current world. */
    public synchronized Optional<GeneratedMinimapTile> latest(
            GeneratedWorldIdentity world,
            int chunkX,
            int chunkZ
    ) {
        if (!world.equals(currentWorld)) {
            return Optional.empty();
        }
        GeneratedMinimapTile result = null;
        for (GeneratedMinimapTile tile : tiles.values()) {
            GeneratedMinimapTileKey key = tile.key();
            if (key.chunkX() == chunkX && key.chunkZ() == chunkZ
                    && key.world().equals(world)
                    && (result == null || key.chunkRevision()
                    > result.key().chunkRevision())) {
                result = tile;
            }
        }
        return Optional.ofNullable(result);
    }

    public synchronized boolean put(GeneratedMinimapTile tile) {
        Objects.requireNonNull(tile, "tile");
        if (currentWorld == null || !tile.key().world().equals(currentWorld)) {
            return false;
        }
        return putCurrent(tile);
    }

    /**
     * Commits a worker result only if the caller still observes the same world
     * and the result is not older than the cached chunk revision.
     */
    public synchronized boolean putIfCurrent(
            GeneratedMinimapTile tile,
            GeneratedWorldIdentity world,
            long currentChunkRevision
    ) {
        return putIfCurrent(tile, world, currentChunkRevision, null);
    }

    public synchronized boolean putIfCurrent(
            GeneratedMinimapTile tile,
            GeneratedWorldIdentity world,
            long currentChunkRevision,
            GeneratedMinimapRuntimeBinding binding
    ) {
        Objects.requireNonNull(tile, "tile");
        Objects.requireNonNull(world, "world");
        if (!world.equals(currentWorld)
                || !world.equals(tile.key().world())
                || tile.key().chunkRevision() != currentChunkRevision
                || runtimeBinding != null && !runtimeBinding.equals(binding)
                || binding != null && !binding.equals(runtimeBinding)) {
            return false;
        }
        return putCurrent(tile);
    }

    private boolean putCurrent(GeneratedMinimapTile tile) {
        GeneratedMinimapTileKey key = tile.key();
        GeneratedMinimapTile existing = tiles.get(key);
        if (existing != null) {
            tiles.put(key, tile);
            return true;
        }
        for (GeneratedMinimapTileKey other : new ArrayList<>(tiles.keySet())) {
            if (other.sameChunk(key)
                    && other.chunkRevision() > key.chunkRevision()) {
                return false;
            }
            if (other.sameChunk(key)
                    && other.chunkRevision() < key.chunkRevision()) {
                tiles.remove(other);
            }
        }
        tiles.put(key, tile);
        return true;
    }

    public synchronized int invalidateChunk(
            GeneratedWorldIdentity world,
            int chunkX,
            int chunkZ
    ) {
        Objects.requireNonNull(world, "world");
        int removed = 0;
        for (GeneratedMinimapTileKey key : new ArrayList<>(tiles.keySet())) {
            if (key.world().equals(world)
                    && key.chunkX() == chunkX && key.chunkZ() == chunkZ) {
                tiles.remove(key);
                removed++;
            }
        }
        return removed;
    }

    public synchronized int invalidateDimension(NamespacedId dimension) {
        Objects.requireNonNull(dimension, "dimension");
        int removed = 0;
        for (GeneratedMinimapTileKey key : new ArrayList<>(tiles.keySet())) {
            if (key.dimension().equals(dimension)) {
                tiles.remove(key);
                removed++;
            }
        }
        return removed;
    }

    public synchronized void clear() {
        tiles.clear();
        currentWorld = null;
        runtimeBinding = null;
    }

    public synchronized int size() {
        return tiles.size();
    }

    public synchronized List<GeneratedMinimapTile> snapshot() {
        return List.copyOf(tiles.values());
    }
}
