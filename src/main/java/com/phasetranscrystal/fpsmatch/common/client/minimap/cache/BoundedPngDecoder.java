package com.phasetranscrystal.fpsmatch.common.client.minimap.cache;

import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapHardLimits;
import com.phasetranscrystal.fpsmatch.core.minimap.format.BoundedPngReader;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public final class BoundedPngDecoder {
    private final long maxCompressedBytes;
    private final long maxDecodedBytes;
    private final AtomicBoolean cancelled = new AtomicBoolean();

    public BoundedPngDecoder(long maxCompressedBytes, long maxDecodedBytes) {
        if (maxCompressedBytes <= 0 || maxDecodedBytes <= 0) {
            throw new IllegalArgumentException("Decode budgets must be positive");
        }
        this.maxCompressedBytes = maxCompressedBytes;
        this.maxDecodedBytes = maxDecodedBytes;
    }

    public void cancel() {
        cancelled.set(true);
    }

    public DecodedTile decode(byte[] pngBytes) {
        Objects.requireNonNull(pngBytes, "pngBytes");
        if (cancelled.get()) {
            throw new DecodeBudgetException("Decode cancelled");
        }
        if (pngBytes.length > maxCompressedBytes) {
            throw new DecodeBudgetException("Compressed PNG exceeds budget");
        }
        try {
            BoundedPngReader.DecodedPng decoded = BoundedPngReader.decode(pngBytes);
            long decodedBytes = 4L * decoded.width() * decoded.height();
            if (decodedBytes > maxDecodedBytes || decodedBytes > MinimapHardLimits.MAX_DECODED_TILE_BYTES) {
                throw new DecodeBudgetException("Decoded PNG exceeds budget");
            }
            if (cancelled.get()) {
                throw new DecodeBudgetException("Decode cancelled");
            }
            return new DecodedTile(decoded.width(), decoded.height(), decoded.rgba());
        } catch (RuntimeException exception) {
            if (exception instanceof DecodeBudgetException) {
                throw exception;
            }
            throw new DecodeBudgetException("Hostile or invalid PNG: " + exception.getMessage());
        }
    }
}