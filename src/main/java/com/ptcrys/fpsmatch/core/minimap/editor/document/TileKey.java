package com.ptcrys.fpsmatch.core.minimap.editor.document;

public record TileKey(int tileX, int tileY) {
    public TileKey {
        if (tileX < 0 || tileY < 0) {
            throw new IllegalArgumentException("Tile coordinates must be non-negative");
        }
    }
}
