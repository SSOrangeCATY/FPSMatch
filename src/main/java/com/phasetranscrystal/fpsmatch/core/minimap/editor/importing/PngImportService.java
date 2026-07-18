package com.phasetranscrystal.fpsmatch.core.minimap.editor.importing;

import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapFormatContract;
import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapHardLimits;
import com.phasetranscrystal.fpsmatch.core.minimap.editor.document.EditorDocument;
import com.phasetranscrystal.fpsmatch.core.minimap.editor.raster.Rgba8;
import com.phasetranscrystal.fpsmatch.core.minimap.format.BoundedPngReader;
import com.phasetranscrystal.fpsmatch.core.minimap.format.CanonicalPngCodecV1;
import com.phasetranscrystal.fpsmatch.core.minimap.model.DisplayLabel;
import com.phasetranscrystal.fpsmatch.core.minimap.model.LayerType;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public final class PngImportService {
    private final Set<String> reservedAssetIds;

    public PngImportService() {
        this(Set.of());
    }

    public PngImportService(Set<String> reservedAssetIds) {
        this.reservedAssetIds = new HashSet<>(Objects.requireNonNull(reservedAssetIds, "reservedAssetIds"));
    }

    public ImportedImageAsset importCanonicalPng(
            EditorDocument document,
            String floorId,
            String assetId,
            byte[] pngBytes,
            ImagePlacementMode mode
    ) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(pngBytes, "pngBytes");
        Objects.requireNonNull(mode, "mode");
        BoundedPngReader.DecodedPng decoded = BoundedPngReader.decode(pngBytes);
        return importRgba(document, floorId, assetId, decoded.width(), decoded.height(), decoded.rgba(), mode);
    }

    public ImportedImageAsset importRgba(
            EditorDocument document,
            String floorId,
            String assetId,
            int width,
            int height,
            byte[] rgba,
            ImagePlacementMode mode
    ) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(rgba, "rgba");
        Objects.requireNonNull(mode, "mode");
        if (!MinimapFormatContract.isInternalSlug(assetId)) {
            throw new IllegalArgumentException("Asset ID must be a valid internal slug");
        }
        if (reservedAssetIds.contains(assetId)) {
            throw new IllegalArgumentException("Asset slug already exists: " + assetId);
        }
        long decodedBytes = 4L * width * height;
        if (width <= 0 || height <= 0 || decodedBytes > MinimapHardLimits.MAX_DECODED_TILE_BYTES) {
            throw new IllegalArgumentException("Imported image exceeds decoded size hard limit");
        }
        if (rgba.length != width * height * 4) {
            throw new IllegalArgumentException("RGBA buffer length does not match dimensions");
        }

        byte[] canonical = CanonicalPngCodecV1.encode(width, height, rgba);
        ImagePlacement drawPlacement = ImagePlacement.computeInternal(document.canvas(), width, height, mode);
        ImagePlacement placement = ImagePlacement.compute(document.canvas(), width, height, mode);
        String layerId = document.createLayer(floorId, LayerType.IMPORTED_IMAGE, DisplayLabel.literal(assetId));
        document.bindImportedAsset(floorId, layerId, assetId);

        writePlacedPixels(document, floorId, layerId, width, height, rgba, drawPlacement);
        reservedAssetIds.add(assetId);
        return new ImportedImageAsset(
                assetId,
                layerId,
                width,
                height,
                placement.placedWidth(),
                placement.placedHeight(),
                placement.offsetX(),
                placement.offsetY(),
                canonical
        );
    }

    private static void writePlacedPixels(
            EditorDocument document,
            String floorId,
            String layerId,
            int sourceWidth,
            int sourceHeight,
            byte[] rgba,
            ImagePlacement placement
    ) {
        int canvasWidth = document.canvas().width();
        int canvasHeight = document.canvas().height();
        int tileEdge = document.tileEdge();
        int tilesX = (canvasWidth + tileEdge - 1) / tileEdge;
        int tilesY = (canvasHeight + tileEdge - 1) / tileEdge;
        for (int tileY = 0; tileY < tilesY; tileY++) {
            for (int tileX = 0; tileX < tilesX; tileX++) {
                int originX = tileX * tileEdge;
                int originY = tileY * tileEdge;
                int tileWidth = Math.min(tileEdge, canvasWidth - originX);
                int tileHeight = Math.min(tileEdge, canvasHeight - originY);
                int[] pixels = new int[tileWidth * tileHeight];
                boolean any = false;
                for (int localY = 0; localY < tileHeight; localY++) {
                    for (int localX = 0; localX < tileWidth; localX++) {
                        int canvasX = originX + localX;
                        int canvasY = originY + localY;
                        int sourceX = mapCoordinate(
                                canvasX, placement.offsetX(), placement.placedWidth(), sourceWidth);
                        int sourceY = mapCoordinate(
                                canvasY, placement.offsetY(), placement.placedHeight(), sourceHeight);
                        if (sourceX < 0 || sourceY < 0 || sourceX >= sourceWidth || sourceY >= sourceHeight) {
                            continue;
                        }
                        int src = (sourceY * sourceWidth + sourceX) * 4;
                        int color = Rgba8.of(
                                rgba[src] & 0xFF,
                                rgba[src + 1] & 0xFF,
                                rgba[src + 2] & 0xFF,
                                rgba[src + 3] & 0xFF
                        );
                        if (color != 0) {
                            pixels[localY * tileWidth + localX] = color;
                            any = true;
                        }
                    }
                }
                if (any) {
                    document.putTilePixels(floorId, layerId, tileX, tileY, pixels);
                }
            }
        }
    }

    private static int mapCoordinate(int canvas, int offset, int placed, int source) {
        int relative = canvas - offset;
        if (relative < 0 || relative >= placed) {
            return -1;
        }
        return Math.min(source - 1, (int) ((relative + 0.5) * source / (double) placed));
    }
}
