package com.ptcrys.fpsmatch.common.client.minimap.generated;

import com.ptcrys.fpsmatch.core.minimap.editor.bake.SnapshotPalette;
import com.ptcrys.fpsmatch.core.minimap.editor.bake.WorldColumnSnapshot;
import com.ptcrys.fpsmatch.core.minimap.model.NamespacedId;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Client-thread adapter.  It copies a loaded LevelChunk into plain arrays;
 * callers must pass only the returned snapshot to worker code.
 */
public final class MinecraftClientColumnSnapshotAdapter {
    private MinecraftClientColumnSnapshotAdapter() {
    }

    public static NamespacedId dimension(ClientLevel level) {
        Objects.requireNonNull(level, "level");
        ResourceLocation location = level.dimension().location();
        return NamespacedId.parse(location.toString());
    }

    public static Optional<WorldColumnSnapshot> snapshot(
            ClientLevel level,
            int chunkX,
            int chunkZ,
            long revision
    ) {
        Objects.requireNonNull(level, "level");
        LevelChunk chunk = level.getChunkSource().getChunk(
                chunkX,
                chunkZ,
                net.minecraft.world.level.chunk.ChunkStatus.FULL,
                false
        );
        return chunk == null
                ? Optional.empty()
                : Optional.of(snapshot(level, chunk, revision));
    }

    public static WorldColumnSnapshot snapshot(
            ClientLevel level,
            LevelChunk chunk,
            long revision
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(chunk, "chunk");
        if (revision < 0) {
            throw new IllegalArgumentException("Chunk revision must be non-negative");
        }
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight();
        int chunkX = chunk.getPos().x;
        int chunkZ = chunk.getPos().z;
        int height = maxY - minY;
        byte[] indices = new byte[height * WorldColumnSnapshot.CHUNK_EDGE
                * WorldColumnSnapshot.CHUNK_EDGE];
        Map<String, Integer> palette = new LinkedHashMap<>();
        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;
        for (int y = minY; y < maxY; y++) {
            for (int localZ = 0; localZ < WorldColumnSnapshot.CHUNK_EDGE; localZ++) {
                for (int localX = 0; localX < WorldColumnSnapshot.CHUNK_EDGE; localX++) {
                    position.set(baseX + localX, y, baseZ + localZ);
                    BlockState state = chunk.getBlockState(position);
                    Block block = state.getBlock();
                    ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries
                            .BLOCK.getKey(block);
                    String blockId = id == null ? "minecraft:air" : id.toString();
                    Integer paletteIndex = palette.get(blockId);
                    if (paletteIndex == null) {
                        paletteIndex = palette.size();
                        if (paletteIndex > 255) {
                            throw new IllegalStateException(
                                    "Loaded chunk palette exceeds 256 entries"
                            );
                        }
                        palette.put(blockId, paletteIndex);
                    }
                    int index = ((y - minY) * WorldColumnSnapshot.CHUNK_EDGE + localZ)
                            * WorldColumnSnapshot.CHUNK_EDGE + localX;
                    indices[index] = (byte) (paletteIndex & 0xFF);
                }
            }
        }
        return new WorldColumnSnapshot(
                chunkX,
                chunkZ,
                revision,
                true,
                minY,
                maxY,
                new SnapshotPalette(new ArrayList<>(palette.keySet())),
                indices
        );
    }
}
