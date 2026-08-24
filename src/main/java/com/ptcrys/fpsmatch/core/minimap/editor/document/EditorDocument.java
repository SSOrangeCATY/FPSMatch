package com.ptcrys.fpsmatch.core.minimap.editor.document;

import com.ptcrys.fpsmatch.core.minimap.contract.MinimapFormatContract;
import com.ptcrys.fpsmatch.core.minimap.contract.MinimapHardLimits;
import com.ptcrys.fpsmatch.core.minimap.model.BlendMode;
import com.ptcrys.fpsmatch.core.minimap.model.CanvasBounds;
import com.ptcrys.fpsmatch.core.minimap.model.DisplayLabel;
import com.ptcrys.fpsmatch.core.minimap.model.LayerType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

public final class EditorDocument {
    private final CanvasBounds canvas;
    private final int tileEdge;
    private final Map<String, EditableFloor> floorsById = new LinkedHashMap<>();
    private final List<String> floorOrder = new ArrayList<>();
    private final DirtyRegionSet dirtyRegions = new DirtyRegionSet();
    private final AtomicLong layerSequence = new AtomicLong();

    private EditorDocument(CanvasBounds canvas, int tileEdge) {
        this.canvas = Objects.requireNonNull(canvas, "canvas");
        if (tileEdge <= 0 || tileEdge > MinimapHardLimits.MAX_TILE_EDGE) {
            throw new IllegalArgumentException("Tile edge exceeds the hard limit");
        }
        this.tileEdge = tileEdge;
    }

    public static EditorDocument createEmpty(
            CanvasBounds canvas,
            int tileEdge,
            String floorId,
            DisplayLabel floorLabel
    ) {
        EditorDocument document = new EditorDocument(canvas, tileEdge);
        document.addFloorInternal(floorId, floorLabel);
        return document;
    }

    static EditorDocument hydrate(CanvasBounds canvas, int tileEdge) {
        return new EditorDocument(canvas, tileEdge);
    }

    void hydrateFloor(String floorId, DisplayLabel floorLabel) {
        addFloorInternal(floorId, floorLabel);
    }

    void hydrateLayer(
            String floorId,
            String layerId,
            LayerType type,
            DisplayLabel label,
            boolean visible,
            boolean locked,
            double opacity,
            BlendMode blendMode,
            boolean maskEnabled,
            Optional<String> importedAssetId,
            Optional<String> generatorId
    ) {
        EditableLayer layer = new EditableLayer(layerId, type, label);
        layer.setVisible(visible);
        layer.setOpacity(opacity);
        layer.setBlendMode(blendMode);
        layer.setMaskEnabled(maskEnabled);
        importedAssetId.ifPresent(layer::bindImportedAsset);
        generatorId.ifPresent(layer::bindGenerator);
        layer.setLocked(locked);
        floor(floorId).addLayer(layer);
    }

    void hydrateTilePixels(
            String floorId,
            String layerId,
            int tileX,
            int tileY,
            int[] pixels,
            boolean mask
    ) {
        validateTilePixels(tileX, tileY, pixels);
        EditableLayer layer = layer(floorId, layerId);
        if (mask) {
            if (!layer.maskEnabled()) {
                throw new IllegalStateException("Mask is not enabled for layer: " + layerId);
            }
            layer.maskTiles().put(tileX, tileY, pixels);
        } else {
            layer.tiles().put(tileX, tileY, pixels);
        }
    }

    public CanvasBounds canvas() {
        return canvas;
    }

    public int tileEdge() {
        return tileEdge;
    }

    public List<String> floorIds() {
        return List.copyOf(floorOrder);
    }

    public EditableFloor floor(String floorId) {
        EditableFloor floor = floorsById.get(floorId);
        if (floor == null) {
            throw new IllegalArgumentException("Unknown floor: " + floorId);
        }
        return floor;
    }

    public EditableLayer layer(String floorId, String layerId) {
        return floor(floorId).layer(layerId);
    }

    public DirtyRegionSet dirtyRegions() {
        return dirtyRegions;
    }

    public boolean isFlattened() {
        return false;
    }

    public void flattenSource() {
        throw new UnsupportedOperationException("EditorDocument never flattens the source document implicitly");
    }

    public void addFloor(String floorId, DisplayLabel label) {
        if (floorOrder.size() >= MinimapHardLimits.MAX_FLOORS) {
            throw new IllegalStateException("Floor count exceeds the hard limit");
        }
        addFloorInternal(floorId, label);
    }

    public void renameFloor(String floorId, String newFloorId, DisplayLabel label) {
        EditableFloor floor = floor(floorId);
        if (!floorId.equals(newFloorId) && floorsById.containsKey(newFloorId)) {
            throw new IllegalArgumentException("Floor id already exists: " + newFloorId);
        }
        if (!MinimapFormatContract.isInternalSlug(newFloorId)) {
            throw new IllegalArgumentException("Floor ID must be a valid internal slug");
        }
        floorsById.remove(floorId);
        floor.rename(newFloorId, label);
        floorsById.put(newFloorId, floor);
        for (int index = 0; index < floorOrder.size(); index++) {
            if (floorOrder.get(index).equals(floorId)) {
                floorOrder.set(index, newFloorId);
                break;
            }
        }
    }

    public String createLayer(String floorId, LayerType type, DisplayLabel label) {
        EditableFloor floor = floor(floorId);
        int totalLayers = 0;
        for (EditableFloor existing : floorsById.values()) {
            totalLayers += existing.layerCount();
        }
        if (totalLayers >= MinimapHardLimits.MAX_SOURCE_LAYERS) {
            throw new IllegalStateException("Layer count exceeds the hard limit");
        }
        String layerId = nextLayerId(type);
        EditableLayer layer = new EditableLayer(layerId, type, label);
        floor.addLayer(layer);
        return layerId;
    }

    public String duplicateLayer(String floorId, String layerId) {
        EditableFloor floor = floor(floorId);
        EditableLayer source = floor.layer(layerId);
        int totalLayers = 0;
        for (EditableFloor existing : floorsById.values()) {
            totalLayers += existing.layerCount();
        }
        if (totalLayers >= MinimapHardLimits.MAX_SOURCE_LAYERS) {
            throw new IllegalStateException("Layer count exceeds the hard limit");
        }
        String copyId = nextLayerId(source.type());
        EditableLayer copy = source.duplicate(copyId);
        floor.addLayer(copy);
        return copyId;
    }

    public void renameLayer(String floorId, String layerId, DisplayLabel label) {
        layer(floorId, layerId).rename(label);
    }

    public void reorderLayer(String floorId, String layerId, int targetIndex) {
        floor(floorId).reorderLayer(layerId, targetIndex);
    }

    public void setLayerVisible(String floorId, String layerId, boolean visible) {
        layer(floorId, layerId).setVisible(visible);
    }

    public void setLayerLocked(String floorId, String layerId, boolean locked) {
        layer(floorId, layerId).setLocked(locked);
    }

    public void setLayerOpacity(String floorId, String layerId, double opacity) {
        layer(floorId, layerId).setOpacity(opacity);
    }

    public void setLayerBlend(String floorId, String layerId, BlendMode blendMode) {
        layer(floorId, layerId).setBlendMode(blendMode);
    }

    public void setLayerMaskEnabled(String floorId, String layerId, boolean maskEnabled) {
        layer(floorId, layerId).setMaskEnabled(maskEnabled);
    }

    public void bindImportedAsset(String floorId, String layerId, String assetId) {
        layer(floorId, layerId).bindImportedAsset(assetId);
    }

    public void bindGenerator(String floorId, String layerId, String generatorId) {
        layer(floorId, layerId).bindGenerator(generatorId);
    }

    public TileStore tileStore(String floorId, String layerId) {
        return layer(floorId, layerId).tiles();
    }

    public Optional<int[]> tilePixelsOptional(String floorId, String layerId, int tileX, int tileY) {
        return layer(floorId, layerId).tiles().pixelsOptional(tileX, tileY);
    }

    public int[] tilePixels(String floorId, String layerId, int tileX, int tileY) {
        return layer(floorId, layerId).tiles().requirePixels(tileX, tileY);
    }

    public Optional<int[]> maskPixelsOptional(String floorId, String layerId, int tileX, int tileY) {
        return layer(floorId, layerId).maskTiles().pixelsOptional(tileX, tileY);
    }

    public int[] maskPixels(String floorId, String layerId, int tileX, int tileY) {
        return layer(floorId, layerId).maskTiles().requirePixels(tileX, tileY);
    }

    public void putTilePixels(String floorId, String layerId, int tileX, int tileY, int[] pixels) {
        EditableLayer layer = layer(floorId, layerId);
        layer.requireUnlocked();
        validateTilePixels(tileX, tileY, pixels);
        layer.tiles().put(tileX, tileY, pixels);
        markTileDirty(tileX, tileY);
    }

    /** Removes one raster tile while preserving the document's dirty-region bookkeeping. */
    public void removeTilePixels(String floorId, String layerId, int tileX, int tileY) {
        EditableLayer layer = layer(floorId, layerId);
        layer.requireUnlocked();
        validateTileCoordinates(tileX, tileY);
        layer.tiles().remove(tileX, tileY);
        markTileDirty(tileX, tileY);
    }

    public void putMaskPixels(String floorId, String layerId, int tileX, int tileY, int[] pixels) {
        EditableLayer layer = layer(floorId, layerId);
        layer.requireUnlocked();
        if (!layer.maskEnabled()) {
            throw new IllegalStateException("Mask is not enabled for layer: " + layerId);
        }
        validateTilePixels(tileX, tileY, pixels);
        layer.maskTiles().put(tileX, tileY, pixels);
        markTileDirty(tileX, tileY);
    }

    public MissingTileSemantics missingTileSemantics(LayerType type) {
        Objects.requireNonNull(type, "type");
        return switch (type) {
            case RASTER_PAINT -> MissingTileSemantics.INHERIT;
            case CUTOUT -> MissingTileSemantics.ZERO_CUTOUT;
            case WORLD_BAKE, IMPORTED_IMAGE, VECTOR, REGION_VISUAL -> MissingTileSemantics.TRANSPARENT;
        };
    }

    public MissingTileSemantics resolveMissingTile(String floorId, String layerId, int tileX, int tileY) {
        EditableLayer layer = layer(floorId, layerId);
        if (layer.tiles().contains(tileX, tileY)) {
            throw new IllegalStateException("Tile is present");
        }
        return missingTileSemantics(layer.type());
    }

    public double resolveMaskAlpha(String floorId, String layerId, int tileX, int tileY, int localX, int localY) {
        EditableLayer layer = layer(floorId, layerId);
        if (!layer.maskEnabled()) {
            return 1.0;
        }
        Optional<int[]> mask = layer.maskTiles().pixelsOptional(tileX, tileY);
        if (mask.isEmpty()) {
            return 1.0;
        }
        int[] pixels = mask.get();
        int width = tileWidth(tileX);
        int height = tileHeight(tileY);
        if (localX < 0 || localY < 0 || localX >= width || localY >= height) {
            throw new IllegalArgumentException("Mask sample is outside the tile");
        }
        int alpha = (pixels[localY * width + localX] >>> 24) & 0xFF;
        return alpha / 255.0;
    }

    private void addFloorInternal(String floorId, DisplayLabel label) {
        if (!MinimapFormatContract.isInternalSlug(floorId)) {
            throw new IllegalArgumentException("Floor ID must be a valid internal slug");
        }
        if (floorsById.containsKey(floorId)) {
            throw new IllegalArgumentException("Floor id already exists: " + floorId);
        }
        EditableFloor floor = new EditableFloor(floorId, label);
        floorsById.put(floorId, floor);
        floorOrder.add(floorId);
    }

    private String nextLayerId(LayerType type) {
        String prefix = switch (type) {
            case IMPORTED_IMAGE -> "image";
            case WORLD_BAKE -> "world";
            case RASTER_PAINT -> "paint";
            case VECTOR -> "vector";
            case REGION_VISUAL -> "region";
            case CUTOUT -> "cutout";
        };
        while (true) {
            String candidate = prefix + "_" + Long.toString(layerSequence.incrementAndGet(), 36);
            if (MinimapFormatContract.isInternalSlug(candidate) && !layerIdExists(candidate)) {
                return candidate;
            }
        }
    }

    private boolean layerIdExists(String layerId) {
        for (EditableFloor floor : floorsById.values()) {
            for (String existing : floor.layerIds()) {
                if (existing.equals(layerId)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void validateTilePixels(int tileX, int tileY, int[] pixels) {
        Objects.requireNonNull(pixels, "pixels");
        validateTileCoordinates(tileX, tileY);
        int expectedWidth = tileWidth(tileX);
        int expectedHeight = tileHeight(tileY);
        if (pixels.length != expectedWidth * expectedHeight) {
            throw new IllegalArgumentException(
                    "Tile pixel length must be " + (expectedWidth * expectedHeight)
                            + " but was " + pixels.length);
        }
    }

    private void validateTileCoordinates(int tileX, int tileY) {
        if (tileX < 0 || tileY < 0) {
            throw new IllegalArgumentException("Tile coordinates must be non-negative");
        }
        int expectedWidth = tileWidth(tileX);
        int expectedHeight = tileHeight(tileY);
        if (expectedWidth <= 0 || expectedHeight <= 0) {
            throw new IllegalArgumentException("Tile is outside the canvas");
        }
    }

    private int tileWidth(int tileX) {
        long origin = (long) tileX * tileEdge;
        if (origin >= canvas.width()) {
            return 0;
        }
        return (int) Math.min(tileEdge, canvas.width() - origin);
    }

    private int tileHeight(int tileY) {
        long origin = (long) tileY * tileEdge;
        if (origin >= canvas.height()) {
            return 0;
        }
        return (int) Math.min(tileEdge, canvas.height() - origin);
    }

    private void markTileDirty(int tileX, int tileY) {
        int minX = tileX * tileEdge;
        int minY = tileY * tileEdge;
        int maxX = minX + tileWidth(tileX);
        int maxY = minY + tileHeight(tileY);
        dirtyRegions.add(new DirtyRegion(minX, minY, maxX, maxY));
    }
}
