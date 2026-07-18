package com.phasetranscrystal.fpsmatch.core.minimap.editor.importing;

import com.phasetranscrystal.fpsmatch.core.minimap.editor.document.EditorDocument;
import com.phasetranscrystal.fpsmatch.core.minimap.model.CanvasBounds;
import com.phasetranscrystal.fpsmatch.core.minimap.model.DisplayLabel;
import com.phasetranscrystal.fpsmatch.core.minimap.model.LayerType;

import java.util.Objects;

public final class FlattenedRuntimeImportService {
    public EditorDocument importFlattenedRuntime(
            CanvasBounds canvas,
            int tileEdge,
            String floorId,
            int[] flatArgbPixels,
            String layerLabel
    ) {
        Objects.requireNonNull(canvas, "canvas");
        Objects.requireNonNull(flatArgbPixels, "flatArgbPixels");
        Objects.requireNonNull(layerLabel, "layerLabel");
        if (flatArgbPixels.length != canvas.width() * canvas.height()) {
            throw new IllegalArgumentException("Flattened pixel buffer must match canvas dimensions");
        }
        EditorDocument document = EditorDocument.createEmpty(
                canvas, tileEdge, floorId, DisplayLabel.literal(floorId));
        String layerId = document.createLayer(
                floorId, LayerType.RASTER_PAINT, DisplayLabel.literal(layerLabel));
        int tilesX = (canvas.width() + tileEdge - 1) / tileEdge;
        int tilesY = (canvas.height() + tileEdge - 1) / tileEdge;
        for (int tileY = 0; tileY < tilesY; tileY++) {
            for (int tileX = 0; tileX < tilesX; tileX++) {
                int originX = tileX * tileEdge;
                int originY = tileY * tileEdge;
                int tileWidth = Math.min(tileEdge, canvas.width() - originX);
                int tileHeight = Math.min(tileEdge, canvas.height() - originY);
                int[] tile = new int[tileWidth * tileHeight];
                for (int localY = 0; localY < tileHeight; localY++) {
                    for (int localX = 0; localX < tileWidth; localX++) {
                        int canvasX = originX + localX;
                        int canvasY = originY + localY;
                        tile[localY * tileWidth + localX] =
                                flatArgbPixels[canvasY * canvas.width() + canvasX];
                    }
                }
                document.putTilePixels(floorId, layerId, tileX, tileY, tile);
            }
        }
        return document;
    }
}
