package com.ptcrys.fpsmatch.common.client.minimap.cache;

import java.util.Objects;

public final class DecodedTile implements AutoCloseable {
    private final int width;
    private final int height;
    private byte[] rgba;
    private boolean closed;

    public DecodedTile(int width, int height, byte[] rgba) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Decoded tile dimensions must be positive");
        }
        this.width = width;
        this.height = height;
        this.rgba = Objects.requireNonNull(rgba, "rgba").clone();
        if (this.rgba.length != 4 * width * height) {
            throw new IllegalArgumentException("RGBA length mismatch");
        }
    }

    public int width() { return width; }
    public int height() { return height; }
    public byte[] rgba() {
        ensureOpen();
        return rgba.clone();
    }

    @Override
    public void close() {
        closed = true;
        rgba = null;
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("Decoded tile already closed");
        }
    }
}