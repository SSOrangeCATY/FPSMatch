package com.ptcrys.fpsmatch.common.client.minimap.generated;

import com.ptcrys.fpsmatch.core.minimap.editor.bake.BlockSampleRule;
import com.ptcrys.fpsmatch.core.minimap.editor.bake.SamplerProfile;
import com.ptcrys.fpsmatch.core.minimap.editor.bake.WorldColumnRasterizer;
import com.ptcrys.fpsmatch.core.minimap.editor.bake.WorldColumnSnapshot;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Runtime owner for generated tiles sourced only from loaded client chunks. */
public final class MinecraftClientGeneratedMinimapRuntime {
    private static final MinecraftClientGeneratedMinimapRuntime INSTANCE =
            new MinecraftClientGeneratedMinimapRuntime();

    private final GeneratedMinimapTileStore store = new GeneratedMinimapTileStore();
    private final GeneratedMinimapScheduler scheduler =
            new GeneratedMinimapScheduler(
                    new WorldColumnRasterizer(defaultProfile(), 0xFF808080),
                    Util.backgroundExecutor(),
                    store
            );
    private final Map<ChunkAddress, Long> revisions = new LinkedHashMap<>();
    private final Set<ChunkAddress> dirtyChunks = new LinkedHashSet<>();
    private ClientLevel level;
    private GeneratedWorldIdentity world;
    private long worldEpoch;
    private boolean loadedChunkScanPending;
    private boolean loadedChunkScanForce;

    private MinecraftClientGeneratedMinimapRuntime() {
    }

    public static MinecraftClientGeneratedMinimapRuntime instance() {
        return INSTANCE;
    }

    public synchronized void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        ClientLevel current = minecraft.level;
        if (current == null) {
            if (level != null) {
                reset();
            }
            return;
        }
        ensureLevel(current);
        scanLoadedChunks(current);
        flushDirtyChunks(current);
        scheduler.tick();
    }

    public synchronized void onChunkLoaded(LevelChunk chunk) {
        Objects.requireNonNull(chunk, "chunk");
        if (!(chunk.getLevel() instanceof ClientLevel clientLevel)) {
            return;
        }
        if (!acceptsLevel(clientLevel)) {
            return;
        }
        ChunkAddress address = new ChunkAddress(
                chunk.getPos().x, chunk.getPos().z
        );
        long revision = revisions.getOrDefault(address, -1L) + 1L;
        revisions.put(address, revision);
        scheduler.queue(MinecraftClientColumnSnapshotAdapter.snapshot(
                clientLevel, chunk, revision
        ));
    }

    public synchronized void onChunkLoaded(ClientLevel clientLevel, int chunkX, int chunkZ) {
        if (!acceptsLevel(clientLevel)) {
            return;
        }
        MinecraftClientColumnSnapshotAdapter.snapshot(
                clientLevel, chunkX, chunkZ,
                revisions.getOrDefault(new ChunkAddress(chunkX, chunkZ), -1L) + 1L
        ).ifPresent(snapshot -> {
            revisions.put(new ChunkAddress(chunkX, chunkZ), snapshot.chunkRevision());
            scheduler.queue(snapshot);
        });
    }

    public synchronized void onBlockChanged(ClientLevel clientLevel, BlockPos position) {
        Objects.requireNonNull(position, "position");
        if (!acceptsLevel(clientLevel)) {
            return;
        }
        int chunkX = position.getX() >> 4;
        int chunkZ = position.getZ() >> 4;
        ChunkAddress address = new ChunkAddress(chunkX, chunkZ);
        revisions.put(address, revisions.getOrDefault(address, -1L) + 1L);
        dirtyChunks.add(address);
    }

    public synchronized void onChunkUnloaded(ClientLevel clientLevel, LevelChunk chunk) {
        Objects.requireNonNull(chunk, "chunk");
        if (!acceptsLevel(clientLevel) || world == null) {
            return;
        }
        int chunkX = chunk.getPos().x;
        int chunkZ = chunk.getPos().z;
        ChunkAddress address = new ChunkAddress(chunkX, chunkZ);
        dirtyChunks.remove(address);
        revisions.remove(address);
        scheduler.unload(chunkX, chunkZ);
    }

    /** Compatibility bridge for callers that can only provide the chunk owner. */
    public synchronized void onChunkUnloaded(LevelChunk chunk) {
        Objects.requireNonNull(chunk, "chunk");
        if (chunk.getLevel() instanceof ClientLevel clientLevel) {
            onChunkUnloaded(clientLevel, chunk);
        }
    }

    public synchronized void bindRuntime(GeneratedMinimapRuntimeBinding binding) {
        Objects.requireNonNull(binding, "binding");
        // HUD rendering binds every frame; explicit same-binding recovery stays
        // available through GeneratedMinimapScheduler.bindRuntime.
        if (scheduler.runtimeBinding().filter(binding::equals).isPresent()) {
            return;
        }
        scheduler.bindRuntime(binding);
        // The binding may arrive after connect/reset, when the level has
        // already populated chunks without emitting another load event.
        loadedChunkScanPending = true;
        loadedChunkScanForce = true;
    }

    public synchronized void reset() {
        level = null;
        world = null;
        revisions.clear();
        dirtyChunks.clear();
        loadedChunkScanPending = false;
        loadedChunkScanForce = false;
        scheduler.reset();
    }

    public synchronized GeneratedMinimapTileStore store() {
        return store;
    }

    public synchronized Optional<GeneratedWorldIdentity> currentWorld() {
        return Optional.ofNullable(world);
    }

    private void ensureLevel(ClientLevel candidate) {
        Objects.requireNonNull(candidate, "candidate");
        if (candidate == level && world != null) {
            return;
        }
        level = candidate;
        worldEpoch++;
        world = new GeneratedWorldIdentity(
                MinecraftClientColumnSnapshotAdapter.dimension(candidate),
                worldEpoch
        );
        revisions.clear();
        loadedChunkScanPending = true;
        loadedChunkScanForce = true;
        scheduler.beginWorld(world);
    }

    private boolean acceptsLevel(ClientLevel candidate) {
        Minecraft minecraft = Minecraft.getInstance();
        if (candidate == null || minecraft == null || minecraft.level != candidate) {
            return false;
        }
        if (level == null || world == null) {
            ensureLevel(candidate);
        }
        return level == candidate && world != null;
    }

    private void flushDirtyChunks(ClientLevel current) {
        if (dirtyChunks.isEmpty()) {
            return;
        }
        Map<ChunkAddress, Long> work = new LinkedHashMap<>();
        for (ChunkAddress address : dirtyChunks) {
            work.put(address, revisions.getOrDefault(address, 0L));
        }
        dirtyChunks.clear();
        for (Map.Entry<ChunkAddress, Long> entry : work.entrySet()) {
            ChunkAddress address = entry.getKey();
            MinecraftClientColumnSnapshotAdapter.snapshot(
                    current, address.chunkX(), address.chunkZ(), entry.getValue()
            ).ifPresent(scheduler::queue);
        }
    }

    /**
     * Rehydrates snapshots for chunks that were loaded before this runtime was
     * bound or reset.  ClientChunkCache has no public iterator, so a bounded
     * lookup around the current view center is used; getChunkNow never loads a
     * missing chunk and therefore cannot expand the loaded-world scope.
     */
    private void scanLoadedChunks(ClientLevel current) {
        if (!loadedChunkScanPending) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null) {
            return;
        }
        int centerX = minecraft.player.blockPosition().getX() >> 4;
        int centerZ = minecraft.player.blockPosition().getZ() >> 4;
        int loadedCount = Math.max(
                1, current.getChunkSource().getLoadedChunksCount()
        );
        int estimatedRadius = (int) Math.ceil(Math.sqrt(loadedCount) / 2.0) + 1;
        int configuredRadius = minecraft.options.renderDistance().get() + 2;
        int radius = Math.max(estimatedRadius, configuredRadius);
        boolean force = loadedChunkScanForce;
        for (int chunkZ = centerZ - radius; chunkZ <= centerZ + radius; chunkZ++) {
            for (int chunkX = centerX - radius; chunkX <= centerX + radius; chunkX++) {
                ChunkAddress address = new ChunkAddress(chunkX, chunkZ);
                if (!force && revisions.containsKey(address)) {
                    continue;
                }
                LevelChunk chunk = current.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) {
                    continue;
                }
                long revision = revisions.getOrDefault(address, 0L);
                WorldColumnSnapshot snapshot =
                        MinecraftClientColumnSnapshotAdapter.snapshot(
                                current, chunk, revision
                        );
                revisions.put(address, snapshot.chunkRevision());
                scheduler.queue(snapshot);
            }
        }
        loadedChunkScanPending = false;
        loadedChunkScanForce = false;
    }

    private static SamplerProfile defaultProfile() {
        return new SamplerProfile(
                "runtime-default",
                0L,
                java.util.List.of(
                        BlockSampleRule.transparent("minecraft:air", 1),
                        BlockSampleRule.transparent("minecraft:cave_air", 2),
                        BlockSampleRule.transparent("minecraft:void_air", 3),
                        BlockSampleRule.color("minecraft:grass_block", 0xFF4E9B45, 10),
                        BlockSampleRule.color("minecraft:dirt", 0xFF8B5A2B, 11),
                        BlockSampleRule.color("minecraft:stone", 0xFF777777, 12),
                        BlockSampleRule.color("minecraft:deepslate", 0xFF3E4147, 13),
                        BlockSampleRule.color("minecraft:sand", 0xFFD8C27A, 14),
                        BlockSampleRule.color("minecraft:water", 0xFF3D78B5, 15),
                        BlockSampleRule.color("minecraft:oak_planks", 0xFFB18452, 16)
                )
        );
    }

    private record ChunkAddress(int chunkX, int chunkZ) {
    }
}
