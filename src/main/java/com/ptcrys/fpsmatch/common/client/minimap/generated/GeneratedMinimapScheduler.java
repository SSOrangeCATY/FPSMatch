package com.ptcrys.fpsmatch.common.client.minimap.generated;

import com.ptcrys.fpsmatch.core.minimap.editor.bake.WorldColumnRasterizer;
import com.ptcrys.fpsmatch.core.minimap.editor.bake.WorldColumnSnapshot;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Coalesces client chunk events at tick boundaries and rasterizes immutable
 * snapshots away from the client thread.
 */
public final class GeneratedMinimapScheduler {
    private final WorldColumnRasterizer rasterizer;
    private final Executor backgroundExecutor;
    private final GeneratedMinimapTileStore store;
    private final String floorId;
    private final int zoom;
    private final Consumer<GeneratedMinimapTile> committed;
    private final Map<ChunkAddress, Pending> pending = new LinkedHashMap<>();
    private final Map<ChunkAddress, Pending> inFlight = new LinkedHashMap<>();
    private final Map<ChunkAddress, Long> revisions = new LinkedHashMap<>();
    private final java.util.Set<ChunkAddress> loaded = new java.util.LinkedHashSet<>();
    /** Active-load tokens are scoped to currently loaded chunks only. */
    private final Map<ChunkAddress, Long> loadTokens = new LinkedHashMap<>();
    private final Map<ChunkAddress, WorldColumnSnapshot> latestSnapshots = new LinkedHashMap<>();
    private GeneratedWorldIdentity world;
    private GeneratedMinimapRuntimeBinding runtimeBinding;
    private long loadSequence;

    public GeneratedMinimapScheduler(
            WorldColumnRasterizer rasterizer,
            Executor backgroundExecutor,
            GeneratedMinimapTileStore store,
            String floorId,
            int zoom,
            Consumer<GeneratedMinimapTile> committed
    ) {
        this.rasterizer = Objects.requireNonNull(rasterizer, "rasterizer");
        this.backgroundExecutor = Objects.requireNonNull(
                backgroundExecutor, "backgroundExecutor"
        );
        this.store = Objects.requireNonNull(store, "store");
        this.floorId = Objects.requireNonNull(floorId, "floorId");
        if (floorId.isBlank() || zoom < 0) {
            throw new IllegalArgumentException("Generated tile output identity is invalid");
        }
        this.zoom = zoom;
        this.committed = Objects.requireNonNull(committed, "committed");
    }

    public GeneratedMinimapScheduler(
            WorldColumnRasterizer rasterizer,
            Executor backgroundExecutor,
            GeneratedMinimapTileStore store
    ) {
        this(rasterizer, backgroundExecutor, store, "ground", 0, ignored -> { });
    }

    public synchronized void beginWorld(GeneratedWorldIdentity world) {
        Objects.requireNonNull(world, "world");
        if (!world.equals(this.world)) {
            pending.clear();
            inFlight.clear();
            revisions.clear();
            loaded.clear();
            loadTokens.clear();
            latestSnapshots.clear();
            this.world = world;
            store.beginWorld(world);
            if (runtimeBinding != null) {
                runtimeBinding = null;
                store.clearRuntimeBinding();
            }
        }
    }

    public synchronized void bindRuntime(GeneratedMinimapRuntimeBinding binding) {
        Objects.requireNonNull(binding, "binding");
        if (world != null
                && !world.dimension().equals(binding.generation().dimension())) {
            throw new IllegalArgumentException(
                    "Generated runtime dimension does not match the loaded client world"
            );
        }
        boolean changed = !binding.equals(runtimeBinding);
        if (changed) {
            pending.clear();
            revisions.clear();
            runtimeBinding = binding;
            store.bindRuntime(binding);
        }
        replayLoadedSnapshots(changed);
    }

    public synchronized java.util.Optional<GeneratedMinimapRuntimeBinding> runtimeBinding() {
        return java.util.Optional.ofNullable(runtimeBinding);
    }

    public synchronized void queue(WorldColumnSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        if (world == null) {
            throw new IllegalStateException("Generated scheduler has no active world");
        }
        GeneratedMinimapTileKey key = new GeneratedMinimapTileKey(
                world.dimension(), world.worldEpoch(),
                snapshot.chunkX(), snapshot.chunkZ(), snapshot.chunkRevision()
        );
        ChunkAddress address = new ChunkAddress(snapshot.chunkX(), snapshot.chunkZ());
        Long known = revisions.get(address);
        if (known != null && known > snapshot.chunkRevision()) {
            return;
        }
        if (known == null || known < snapshot.chunkRevision()) {
            store.invalidateChunk(world, snapshot.chunkX(), snapshot.chunkZ());
        }
        revisions.put(address, snapshot.chunkRevision());
        latestSnapshots.put(address, snapshot);
        long loadToken = loadTokens.getOrDefault(address, 0L);
        if (!loaded.contains(address)) {
            loadToken = ++loadSequence;
            loadTokens.put(address, loadToken);
        }
        loaded.add(address);
        pending.put(address, new Pending(key, snapshot, runtimeBinding, loadToken));
    }

    public synchronized void unload(int chunkX, int chunkZ) {
        ChunkAddress address = new ChunkAddress(chunkX, chunkZ);
        pending.remove(address);
        inFlight.remove(address);
        loaded.remove(address);
        latestSnapshots.remove(address);
        loadTokens.remove(address);
        revisions.remove(address);
        if (world != null) {
            store.invalidateChunk(world, chunkX, chunkZ);
        }
    }

    /** Schedules the latest snapshot for every dirty chunk and returns its count. */
    public int tick() {
        Map<ChunkAddress, Pending> work;
        synchronized (this) {
            if (world == null || pending.isEmpty()) {
                return 0;
            }
            work = new LinkedHashMap<>(pending);
            pending.clear();
        }
        for (Pending item : work.values()) {
            synchronized (this) {
                inFlight.put(item.address(), item);
            }
            try {
                backgroundExecutor.execute(() -> rasterize(item));
            } catch (RuntimeException failure) {
                synchronized (this) {
                    if (inFlight.get(item.address()) == item) {
                        inFlight.remove(item.address());
                    }
                }
                throw failure;
            }
        }
        return work.size();
    }

    public synchronized int pendingCount() {
        return pending.size();
    }

    public synchronized GeneratedWorldIdentity world() {
        return world;
    }

    public GeneratedMinimapTileStore store() {
        return store;
    }

    public synchronized void reset() {
        pending.clear();
        inFlight.clear();
        revisions.clear();
        loaded.clear();
        loadTokens.clear();
        latestSnapshots.clear();
        world = null;
        runtimeBinding = null;
        store.clear();
    }

    private void rasterize(Pending item) {
        try {
            int floorMinY = item.binding() == null
                    ? item.snapshot().minY()
                    : (int) Math.ceil(item.binding().floor().selection().minY());
            int floorMaxYExclusive = item.binding() == null
                    ? item.snapshot().maxYExclusive()
                    : (int) Math.ceil(item.binding().floor().selection().maxY());
            WorldColumnRasterizer.RasterizedTile raster =
                    rasterizer.rasterize(item.snapshot(), floorMinY, floorMaxYExclusive);
            String outputFloor = item.binding() == null
                    ? floorId : item.binding().floor().selection().id();
            int outputZoom = item.binding() == null ? zoom : item.binding().zoom();
            GeneratedMinimapTile tile = new GeneratedMinimapTile(
                    item.key(), outputFloor, outputZoom,
                    raster.width(), raster.height(), raster.rgba()
            );
            boolean accepted;
            synchronized (this) {
                accepted = world != null
                        && world.equals(item.key().world())
                        && loaded.contains(item.address())
                        && loadTokens.getOrDefault(item.address(), -1L)
                        == item.loadToken()
                        && revisions.getOrDefault(item.address(), -1L)
                        == item.key().chunkRevision()
                        && Objects.equals(runtimeBinding, item.binding())
                        && store.putIfCurrent(
                                tile, item.key().world(),
                                item.key().chunkRevision(), item.binding()
                        );
            }
            if (accepted) {
                committed.accept(tile);
            }
        } finally {
            synchronized (this) {
                if (inFlight.get(item.address()) == item) {
                    inFlight.remove(item.address());
                }
            }
        }
    }

    private void replayLoadedSnapshots(boolean bindingChanged) {
        if (world == null || runtimeBinding == null) {
            return;
        }
        for (Map.Entry<ChunkAddress, WorldColumnSnapshot> entry
                : latestSnapshots.entrySet()) {
            ChunkAddress address = entry.getKey();
            WorldColumnSnapshot snapshot = entry.getValue();
            Long loadToken = loadTokens.get(address);
            if (!loaded.contains(address) || loadToken == null) {
                continue;
            }
            if (!bindingChanged) {
                Pending queued = pending.get(address);
                if (queued != null
                        && queued.key().chunkRevision() == snapshot.chunkRevision()
                        && Objects.equals(queued.binding(), runtimeBinding)) {
                    continue;
                }
                Pending running = inFlight.get(address);
                if (running != null
                        && running.key().chunkRevision() == snapshot.chunkRevision()
                        && Objects.equals(running.binding(), runtimeBinding)) {
                    continue;
                }
                GeneratedMinimapTile existing = store.latest(
                        world, snapshot.chunkX(), snapshot.chunkZ()
                ).orElse(null);
                if (existing != null
                        && existing.key().chunkRevision() == snapshot.chunkRevision()
                        && existing.floorId().equals(runtimeBinding.floor().selection().id())
                        && existing.zoom() == runtimeBinding.zoom()) {
                    continue;
                }
            }
            revisions.put(address, snapshot.chunkRevision());
            GeneratedMinimapTileKey key = new GeneratedMinimapTileKey(
                    world.dimension(), world.worldEpoch(),
                    snapshot.chunkX(), snapshot.chunkZ(),
                    snapshot.chunkRevision()
            );
            pending.put(address, new Pending(
                    key, snapshot, runtimeBinding,
                    loadToken
            ));
        }
    }

    private record ChunkAddress(int chunkX, int chunkZ) {
    }

    private record Pending(
            GeneratedMinimapTileKey key,
            WorldColumnSnapshot snapshot,
            GeneratedMinimapRuntimeBinding binding,
            long loadToken
    ) {
        private ChunkAddress address() {
            return new ChunkAddress(key.chunkX(), key.chunkZ());
        }
    }
}
