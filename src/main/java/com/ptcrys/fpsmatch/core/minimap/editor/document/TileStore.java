package com.ptcrys.fpsmatch.core.minimap.editor.document;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class TileStore {
    private final Map<TileKey, int[]> tiles = new LinkedHashMap<>();

    public Optional<int[]> pixelsOptional(int tileX, int tileY) {
        int[] pixels = tiles.get(new TileKey(tileX, tileY));
        if (pixels == null) {
            return Optional.empty();
        }
        return Optional.of(pixels.clone());
    }

    public int[] pixelsOrNull(int tileX, int tileY) {
        return tiles.get(new TileKey(tileX, tileY));
    }

    public int[] requirePixels(int tileX, int tileY) {
        int[] pixels = tiles.get(new TileKey(tileX, tileY));
        if (pixels == null) {
            throw new IllegalArgumentException("Tile is missing");
        }
        return pixels.clone();
    }

    public void put(int tileX, int tileY, int[] pixels) {
        Objects.requireNonNull(pixels, "pixels");
        if (pixels.length == 0) {
            throw new IllegalArgumentException("Tile pixels must not be empty");
        }
        tiles.put(new TileKey(tileX, tileY), pixels.clone());
    }

    public void remove(int tileX, int tileY) {
        tiles.remove(new TileKey(tileX, tileY));
    }

    public boolean contains(int tileX, int tileY) {
        return tiles.containsKey(new TileKey(tileX, tileY));
    }

    public Map<TileKey, int[]> snapshot() {
        Map<TileKey, int[]> copy = new LinkedHashMap<>();
        tiles.forEach((key, value) -> copy.put(key, value.clone()));
        return Collections.unmodifiableMap(copy);
    }

    public TileStore copy() {
        TileStore clone = new TileStore();
        tiles.forEach((key, value) -> clone.tiles.put(key, value.clone()));
        return clone;
    }

    public void clear() {
        tiles.clear();
    }
}
