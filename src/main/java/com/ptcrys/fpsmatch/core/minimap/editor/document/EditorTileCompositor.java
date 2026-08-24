package com.ptcrys.fpsmatch.core.minimap.editor.document;

import com.ptcrys.fpsmatch.core.minimap.editor.raster.RasterSurface;

import java.util.Objects;

/** Canonical visible-layer composition shared by editor preview and runtime compilation. */
public final class EditorTileCompositor {
    private EditorTileCompositor() {
    }

    public static CompositedTile composite(
            EditorDocument document,
            String floorId,
            int tileX,
            int tileY
    ) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(floorId, "floorId");
        EditableFloor floor = document.floor(floorId);
        int width = tileDimension(document.canvas().width(), document.tileEdge(), tileX);
        int height = tileDimension(document.canvas().height(), document.tileEdge(), tileY);
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Tile is outside the canvas");
        }

        int[] composited = new int[width * height];
        for (EditableLayer layer : floor.layers()) {
            if (!layer.visible() || layer.opacity() <= 0.0) {
                continue;
            }
            document.tilePixelsOptional(floorId, layer.id(), tileX, tileY)
                    .ifPresent(pixels -> composite(composited, pixels, layer.opacity()));
        }

        byte[] rgba = new byte[composited.length * 4];
        boolean transparent = true;
        for (int index = 0; index < composited.length; index++) {
            int pixel = composited[index];
            int offset = index * 4;
            rgba[offset] = (byte) ((pixel >>> 16) & 0xff);
            rgba[offset + 1] = (byte) ((pixel >>> 8) & 0xff);
            rgba[offset + 2] = (byte) (pixel & 0xff);
            rgba[offset + 3] = (byte) ((pixel >>> 24) & 0xff);
            transparent &= (pixel >>> 24) == 0;
        }
        return new CompositedTile(width, height, rgba, transparent);
    }

    private static int tileDimension(int canvasSize, int tileEdge, int tileCoordinate) {
        if (tileCoordinate < 0) {
            return 0;
        }
        long origin = (long) tileCoordinate * tileEdge;
        if (origin >= canvasSize) {
            return 0;
        }
        return (int) Math.min(tileEdge, canvasSize - origin);
    }

    private static void composite(int[] destination, int[] source, double layerOpacity) {
        if (source.length != destination.length) {
            throw new IllegalArgumentException("Layer tile dimensions do not match the canvas tile");
        }
        for (int index = 0; index < destination.length; index++) {
            int sourcePixel = source[index];
            if (RasterSurface.isInheritedPixel(sourcePixel)) {
                continue;
            }
            int sourceAlpha = (int) Math.round(((sourcePixel >>> 24) & 0xff) * layerOpacity);
            if (sourceAlpha <= 0) {
                continue;
            }
            int destinationPixel = destination[index];
            int destinationAlpha = (destinationPixel >>> 24) & 0xff;
            int outputAlpha = sourceAlpha + destinationAlpha * (255 - sourceAlpha) / 255;
            if (outputAlpha == 0) {
                destination[index] = 0;
                continue;
            }
            int red = blendChannel(sourcePixel >>> 16, destinationPixel >>> 16,
                    sourceAlpha, destinationAlpha, outputAlpha);
            int green = blendChannel(sourcePixel >>> 8, destinationPixel >>> 8,
                    sourceAlpha, destinationAlpha, outputAlpha);
            int blue = blendChannel(sourcePixel, destinationPixel,
                    sourceAlpha, destinationAlpha, outputAlpha);
            destination[index] = outputAlpha << 24 | red << 16 | green << 8 | blue;
        }
    }

    private static int blendChannel(
            int source,
            int destination,
            int sourceAlpha,
            int destinationAlpha,
            int outputAlpha
    ) {
        int sourceValue = source & 0xff;
        int destinationValue = destination & 0xff;
        int numerator = sourceValue * sourceAlpha
                + destinationValue * destinationAlpha * (255 - sourceAlpha) / 255;
        return Math.min(255, numerator / outputAlpha);
    }

    public record CompositedTile(int width, int height, byte[] rgba, boolean transparent) {
        public CompositedTile {
            if (width <= 0 || height <= 0) {
                throw new IllegalArgumentException("Composited tile dimensions must be positive");
            }
            Objects.requireNonNull(rgba, "rgba");
            if (rgba.length != width * height * 4) {
                throw new IllegalArgumentException("Composited tile RGBA length is invalid");
            }
            rgba = rgba.clone();
        }

        @Override
        public byte[] rgba() {
            return rgba.clone();
        }
    }
}
