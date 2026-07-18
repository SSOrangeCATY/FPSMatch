package com.phasetranscrystal.fpsmatch.core.minimap.editor.raster;

import com.phasetranscrystal.fpsmatch.core.minimap.editor.document.EditorDocument;
import com.phasetranscrystal.fpsmatch.core.minimap.editor.document.EditableLayer;
import com.phasetranscrystal.fpsmatch.core.minimap.model.LayerType;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

public final class RasterSurface {
    private final EditorDocument document;
    private final String floorId;
    private final String layerId;
    private final EditableLayer layer;
    private final int width;
    private final int height;
    private final int tileEdge;
    private SelectionMask selection;

    private RasterSurface(EditorDocument document, String floorId, String layerId) {
        this.document = Objects.requireNonNull(document, "document");
        this.floorId = Objects.requireNonNull(floorId, "floorId");
        this.layerId = Objects.requireNonNull(layerId, "layerId");
        this.layer = document.layer(floorId, layerId);
        this.width = document.canvas().width();
        this.height = document.canvas().height();
        this.tileEdge = document.tileEdge();
        this.selection = null;
    }

    public static RasterSurface bind(EditorDocument document, String floorId, String layerId) {
        return new RasterSurface(document, floorId, layerId);
    }

    public LayerType layerType() {
        return layer.type();
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public boolean isSelected(int x, int y) {
        return selection == null || selection.contains(x, y);
    }

    public com.phasetranscrystal.fpsmatch.core.minimap.model.CompositionOperator compositionOperator() {
        return layer.type().operator();
    }

    public void setSelection(SelectionMask selection) {
        this.selection = selection;
    }

    public boolean isInherited(int x, int y) {
        requireInside(x, y);
        int tileX = x / tileEdge;
        int tileY = y / tileEdge;
        Optional<int[]> tile = document.tilePixelsOptional(floorId, layerId, tileX, tileY);
        if (tile.isEmpty()) {
            return true;
        }
        int localX = x - tileX * tileEdge;
        int localY = y - tileY * tileEdge;
        int tileWidth = Math.min(tileEdge, width - tileX * tileEdge);
        return isInheritedSentinel(tile.get()[localY * tileWidth + localX]);
    }

    public int getPixel(int x, int y) {
        requireInside(x, y);
        int tileX = x / tileEdge;
        int tileY = y / tileEdge;
        Optional<int[]> tile = document.tilePixelsOptional(floorId, layerId, tileX, tileY);
        if (tile.isEmpty()) {
            return 0;
        }
        int localX = x - tileX * tileEdge;
        int localY = y - tileY * tileEdge;
        int tileWidth = Math.min(tileEdge, width - tileX * tileEdge);
        int value = tile.get()[localY * tileWidth + localX];
        return isInheritedSentinel(value) ? 0 : value;
    }

    public void setPixel(int x, int y, int rgba) {
        requireInside(x, y);
        if (selection != null && !selection.contains(x, y)) {
            return;
        }
        layer.requireUnlocked();
        int tileX = x / tileEdge;
        int tileY = y / tileEdge;
        int tileWidth = Math.min(tileEdge, width - tileX * tileEdge);
        int tileHeight = Math.min(tileEdge, height - tileY * tileEdge);
        int localX = x - tileX * tileEdge;
        int localY = y - tileY * tileEdge;
        int[] pixels = ensureTile(tileX, tileY, tileWidth, tileHeight);
        pixels[localY * tileWidth + localX] = rgba;
        document.putTilePixels(floorId, layerId, tileX, tileY, pixels);
    }

    public void erasePixel(int x, int y) {
        requireInside(x, y);
        if (selection != null && !selection.contains(x, y)) {
            return;
        }
        layer.requireUnlocked();
        int tileX = x / tileEdge;
        int tileY = y / tileEdge;
        Optional<int[]> existing = document.tilePixelsOptional(floorId, layerId, tileX, tileY);
        if (existing.isEmpty()) {
            return;
        }
        int tileWidth = Math.min(tileEdge, width - tileX * tileEdge);
        int tileHeight = Math.min(tileEdge, height - tileY * tileEdge);
        int localX = x - tileX * tileEdge;
        int localY = y - tileY * tileEdge;
        int[] pixels = existing.get();
        pixels[localY * tileWidth + localX] = inheritedSentinel();
        if (allInherited(pixels)) {
            // Leave an all-inherited tile rather than deleting; missing-tile semantics remain inherit.
            Arrays.fill(pixels, inheritedSentinel());
        }
        document.putTilePixels(floorId, layerId, tileX, tileY, pixels);
    }

    public void paintCoverage(int x, int y, int rgba, float coverage) {
        if (coverage <= 0.0f) {
            return;
        }
        if (selection != null && !selection.contains(x, y)) {
            return;
        }
        int scaled = Rgba8.scaleAlpha(rgba, coverage);
        if (scaled == 0) {
            return;
        }
        int dst = isInherited(x, y) ? 0 : getPixel(x, y);
        setPixel(x, y, Rgba8.sourceOver(dst, scaled));
    }

    private int[] ensureTile(int tileX, int tileY, int tileWidth, int tileHeight) {
        Optional<int[]> existing = document.tilePixelsOptional(floorId, layerId, tileX, tileY);
        if (existing.isPresent()) {
            return existing.get();
        }
        int[] created = new int[tileWidth * tileHeight];
        Arrays.fill(created, inheritedSentinel());
        return created;
    }

    private void requireInside(int x, int y) {
        if (x < 0 || y < 0 || x >= width || y >= height) {
            throw new IllegalArgumentException("Pixel is outside the canvas");
        }
    }

    private static int inheritedSentinel() {
        // Use a non-zero alpha with marker in high green/blue unused pattern? Spec uses inherit as absence.
        // Represent inherit inside an existing tile as 0x00000001 (alpha 0, rgb marker) is ambiguous with transparent.
        // Use alpha=0 and RGB=1,0,0 as inherit marker; transparent paint stores exact 0 via Rgba8.
        return 0x00000001;
    }

    private static boolean isInheritedSentinel(int value) {
        return value == 0x00000001;
    }

    private static boolean allInherited(int[] pixels) {
        for (int pixel : pixels) {
            if (!isInheritedSentinel(pixel)) {
                return false;
            }
        }
        return true;
    }
}
