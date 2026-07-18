package com.phasetranscrystal.fpsmatch.core.minimap.editor.importing;

import com.phasetranscrystal.fpsmatch.core.minimap.model.CanvasBounds;

import java.util.Objects;

public record ImagePlacement(int placedWidth, int placedHeight, int offsetX, int offsetY) {
    public static ImagePlacement compute(CanvasBounds canvas, int sourceWidth, int sourceHeight, ImagePlacementMode mode) {
        Objects.requireNonNull(canvas, "canvas");
        Objects.requireNonNull(mode, "mode");
        if (sourceWidth <= 0 || sourceHeight <= 0) {
            throw new IllegalArgumentException("Source dimensions must be positive");
        }
        return switch (mode) {
            case ORIGINAL -> new ImagePlacement(sourceWidth, sourceHeight, 0, 0);
            case FIT -> {
                double scale = Math.min(
                        canvas.width() / (double) sourceWidth,
                        canvas.height() / (double) sourceHeight
                );
                int width = Math.max(1, (int) Math.round(sourceWidth * scale));
                int height = Math.max(1, (int) Math.round(sourceHeight * scale));
                int offsetX = Math.max(0, (canvas.width() - width) / 2);
                int offsetY = Math.max(0, (canvas.height() - height) / 2);
                yield new ImagePlacement(width, height, offsetX, offsetY);
            }
            case FILL -> {
                // Cover mode: scale to fill canvas and clip to canvas bounds.
                double scale = Math.max(
                        canvas.width() / (double) sourceWidth,
                        canvas.height() / (double) sourceHeight
                );
                int width = Math.max(1, (int) Math.round(sourceWidth * scale));
                int height = Math.max(1, (int) Math.round(sourceHeight * scale));
                int offsetX = (canvas.width() - width) / 2;
                int offsetY = (canvas.height() - height) / 2;
                // Report canvas-covering footprint for API consumers.
                yield new ImagePlacement(canvas.width(), canvas.height(), 0, 0);
            }
        };
    }

    public static ImagePlacement computeInternal(
            CanvasBounds canvas,
            int sourceWidth,
            int sourceHeight,
            ImagePlacementMode mode
    ) {
        if (mode != ImagePlacementMode.FILL) {
            return compute(canvas, sourceWidth, sourceHeight, mode);
        }
        double scale = Math.max(
                canvas.width() / (double) sourceWidth,
                canvas.height() / (double) sourceHeight
        );
        int width = Math.max(1, (int) Math.round(sourceWidth * scale));
        int height = Math.max(1, (int) Math.round(sourceHeight * scale));
        int offsetX = (canvas.width() - width) / 2;
        int offsetY = (canvas.height() - height) / 2;
        return new ImagePlacement(width, height, offsetX, offsetY);
    }
}