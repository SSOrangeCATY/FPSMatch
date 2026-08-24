package com.ptcrys.fpsmatch.common.client.minimap.generated;

import java.util.Arrays;
import java.util.Objects;

/** Immutable RGBA tile produced from a loaded client chunk column. */
public record GeneratedMinimapTile(
        GeneratedMinimapTileKey key,
        String floorId,
        int zoom,
        int width,
        int height,
        byte[] rgba
) {
    public GeneratedMinimapTile {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(floorId, "floorId");
        if (floorId.isBlank()) {
            throw new IllegalArgumentException("Generated tile floor id must not be blank");
        }
        if (zoom < 0 || width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Generated tile dimensions are invalid");
        }
        Objects.requireNonNull(rgba, "rgba");
        if (rgba.length != width * height * 4) {
            throw new IllegalArgumentException("Generated tile RGBA length mismatch");
        }
        rgba = Arrays.copyOf(rgba, rgba.length);
    }

    public GeneratedMinimapTile(
            GeneratedMinimapTileKey key,
            int width,
            int height,
            byte[] rgba
    ) {
        this(key, "ground", 0, width, height, rgba);
    }

    @Override
    public byte[] rgba() {
        return Arrays.copyOf(rgba, rgba.length);
    }

    public String texturePath() {
        return "floors/" + floorId + "/tiles/" + zoom + "/"
                + key.chunkX() + "_" + key.chunkZ() + ".png";
    }
}
