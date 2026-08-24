package com.ptcrys.fpsmatch.core.minimap.format;

import com.ptcrys.fpsmatch.core.minimap.contract.MinimapHardLimits;
import com.ptcrys.fpsmatch.core.minimap.model.CanvasBounds;
import com.ptcrys.fpsmatch.core.minimap.model.ContainerPath;
import com.ptcrys.fpsmatch.core.minimap.model.ImportedImageLayer;
import com.ptcrys.fpsmatch.core.minimap.model.MinimapDefinition;
import com.ptcrys.fpsmatch.core.minimap.model.MinimapFloor;
import com.ptcrys.fpsmatch.core.minimap.model.MinimapLayer;
import com.ptcrys.fpsmatch.core.minimap.model.RuntimeFloor;
import com.ptcrys.fpsmatch.core.minimap.model.RuntimeManifest;
import com.ptcrys.fpsmatch.core.minimap.model.SourceFloor;
import com.ptcrys.fpsmatch.core.minimap.model.SourceManifest;
import com.ptcrys.fpsmatch.core.minimap.model.CutoutLayer;
import com.ptcrys.fpsmatch.core.minimap.model.RasterPaintLayer;
import com.ptcrys.fpsmatch.core.minimap.model.WorldBakeLayer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class MinimapTileValidator {
    private MinimapTileValidator() {
    }

    public static void validateSourceArchive(
            CanonicalZipReader.Archive archive,
            MinimapDefinition definition
    ) {
        Objects.requireNonNull(archive, "archive");
        Objects.requireNonNull(definition, "definition");
        SourceTileContext context = sourceContext(definition);
        requireSourceCoverageBudget(context);
        Map<String, Set<TileCoordinate>> assetCoordinates = new HashMap<>();
        for (ContainerPath path : archive.paths()) {
            MinimapContainerLayout.SourceEntryKind kind = MinimapContainerLayout.classifySource(path)
                    .orElseThrow(() -> new ContainerValidationException("Invalid source path: " + path));
            if (kind != MinimapContainerLayout.SourceEntryKind.LAYER_TILE
                    && kind != MinimapContainerLayout.SourceEntryKind.LAYER_MASK
                    && kind != MinimapContainerLayout.SourceEntryKind.ASSET_TILE) {
                continue;
            }
            MinimapContainerLayout.SourceTileAddress address = MinimapContainerLayout.parseSourceTile(path)
                    .orElseThrow(() -> new ContainerValidationException("Source tile coordinate is too large: " + path));
            validateSourceAddress(context, address, path);
            byte[] bytes = archive.entryBytes(path);
            validatePngDimensions(
                    bytes, context.canvas(), context.tileEdge(), address.x(), address.y(), path,
                    address.kind() == MinimapContainerLayout.SourceEntryKind.LAYER_MASK
            );
            if (address.kind() == MinimapContainerLayout.SourceEntryKind.ASSET_TILE) {
                assetCoordinates.computeIfAbsent(address.ownerId(), ignored -> new HashSet<>())
                        .add(new TileCoordinate(address.x(), address.y()));
            }
        }
        requireAssetCoverage(context, assetCoordinates);
    }

    public static void validateSourceEntries(
            MinimapDefinition definition,
            List<? extends CanonicalZipWriter.EntrySource> entries
    ) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(entries, "entries");
        SourceTileContext context = sourceContext(definition);
        requireSourceCoverageBudget(context);
        Map<String, Set<TileCoordinate>> assetCoordinates = new HashMap<>();
        for (CanonicalZipWriter.EntrySource source : entries) {
            ContainerPath path = Objects.requireNonNull(source.path(), "source path");
            MinimapContainerLayout.SourceEntryKind kind = MinimapContainerLayout.classifySource(path)
                    .orElseThrow(() -> new ContainerValidationException("Invalid source path: " + path));
            if (kind != MinimapContainerLayout.SourceEntryKind.LAYER_TILE
                    && kind != MinimapContainerLayout.SourceEntryKind.LAYER_MASK
                    && kind != MinimapContainerLayout.SourceEntryKind.ASSET_TILE) {
                continue;
            }
            MinimapContainerLayout.SourceTileAddress address = MinimapContainerLayout.parseSourceTile(path)
                    .orElseThrow(() -> new ContainerValidationException("Source tile coordinate is too large: " + path));
            validateSourceAddress(context, address, path);
            validatePngDimensions(
                    readEntry(source), context.canvas(), context.tileEdge(),
                    address.x(), address.y(), path,
                    address.kind() == MinimapContainerLayout.SourceEntryKind.LAYER_MASK
            );
            if (address.kind() == MinimapContainerLayout.SourceEntryKind.ASSET_TILE) {
                assetCoordinates.computeIfAbsent(address.ownerId(), ignored -> new HashSet<>())
                        .add(new TileCoordinate(address.x(), address.y()));
            }
        }
        requireAssetCoverage(context, assetCoordinates);
    }

    public static void validateRuntimeArchive(
            CanonicalZipReader.Archive archive,
            RuntimeManifest manifest
    ) {
        Objects.requireNonNull(archive, "archive");
        Objects.requireNonNull(manifest, "manifest");
        validateRuntimeCoverageBudget(manifest);
        Map<String, RuntimeFloor> floors = new HashMap<>();
        for (RuntimeFloor floor : manifest.floors()) {
            floors.put(floor.selection().id(), floor);
        }
        Set<RuntimeTileCoordinate> present = new HashSet<>();
        for (ContainerPath path : archive.paths()) {
            if (MinimapContainerLayout.classifyRuntime(path).orElse(null)
                    != MinimapContainerLayout.RuntimeEntryKind.FLOOR_TILE) {
                continue;
            }
            MinimapContainerLayout.RuntimeTileAddress address = MinimapContainerLayout.parseRuntimeTile(path)
                    .orElseThrow(() -> new ContainerValidationException("Runtime tile coordinate is too large: " + path));
            RuntimeFloor floor = floors.get(address.floorId());
            if (floor == null || address.zoom() < 0 || address.zoom() >= floor.zoomLevels()) {
                throw new ContainerValidationException("Runtime tile references an invalid floor or zoom: " + path);
            }
            Dimensions dimensions = dimensionsAtZoom(
                    manifest.canvas(), manifest.tileEdge(), address.zoom(), address.x(), address.y()
            );
            validatePngDimensions(archive.entryBytes(path), dimensions.width(), dimensions.height(), path);
            present.add(new RuntimeTileCoordinate(
                    address.floorId(), address.zoom(), address.x(), address.y()
            ));
        }
        for (RuntimeFloor floor : manifest.floors()) {
            for (int zoom = 0; zoom < floor.zoomLevels(); zoom++) {
                Dimensions scaled = scaledCanvas(manifest.canvas(), zoom);
                int tileCountX = ceilDiv(scaled.width(), manifest.tileEdge());
                int tileCountY = ceilDiv(scaled.height(), manifest.tileEdge());
                for (int x = 0; x < tileCountX; x++) {
                    for (int y = 0; y < tileCountY; y++) {
                        if (!present.contains(new RuntimeTileCoordinate(
                                floor.selection().id(), zoom, x, y
                        ))) {
                            throw new ContainerValidationException(
                                    "Runtime tile coverage is incomplete for floor "
                                            + floor.selection().id() + " at zoom " + zoom
                            );
                        }
                    }
                }
            }
        }
    }

    public static void validateRuntimeTile(
            RuntimeManifest manifest,
            ContainerPath path,
            byte[] png
    ) {
        Objects.requireNonNull(manifest, "manifest");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(png, "png");
        MinimapContainerLayout.RuntimeTileAddress address =
                MinimapContainerLayout.parseRuntimeTile(path)
                        .orElseThrow(() -> new ContainerValidationException(
                                "Runtime tile coordinate is invalid: " + path
                        ));
        RuntimeFloor floor = manifest.floors().stream()
                .filter(candidate -> candidate.selection().id().equals(address.floorId()))
                .findFirst()
                .orElseThrow(() -> new ContainerValidationException(
                        "Runtime tile references an unknown floor: " + path
                ));
        if (address.zoom() < 0 || address.zoom() >= floor.zoomLevels()) {
            throw new ContainerValidationException(
                    "Runtime tile references an invalid zoom: " + path
            );
        }
        Dimensions dimensions = dimensionsAtZoom(
                manifest.canvas(), manifest.tileEdge(), address.zoom(), address.x(), address.y()
        );
        validatePngDimensions(png, dimensions.width(), dimensions.height(), path);
    }

    static void validateSourceCoverageBudget(MinimapDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        requireSourceCoverageBudget(sourceContext(definition));
    }

    static void validateRuntimeCoverageBudget(RuntimeManifest manifest) {
        Objects.requireNonNull(manifest, "manifest");
        long maximumTiles = MinimapHardLimits.MAX_ZIP_ENTRIES - 4L;
        boolean hasThumbnail = manifest.entries().stream()
                .anyMatch(entry -> entry.path().equals(MinimapContainerLayout.THUMBNAIL));
        if (hasThumbnail) {
            maximumTiles--;
        }
        long expectedTiles = 0;
        for (RuntimeFloor floor : manifest.floors()) {
            for (int zoom = 0; zoom < floor.zoomLevels(); zoom++) {
                Dimensions scaled = scaledCanvas(manifest.canvas(), zoom);
                expectedTiles = addCoverage(
                        expectedTiles,
                        ceilDiv(scaled.width(), manifest.tileEdge()),
                        ceilDiv(scaled.height(), manifest.tileEdge()),
                        maximumTiles,
                        "Runtime tile coverage cannot fit in the ZIP entry budget"
                );
            }
        }
    }

    static void validateRuntimeManifestCoverage(RuntimeManifest manifest) {
        Objects.requireNonNull(manifest, "manifest");
        validateRuntimeCoverageBudget(manifest);
        Set<RuntimeTileCoordinate> declared = new HashSet<>();
        for (com.ptcrys.fpsmatch.core.minimap.model.RuntimeEntryDescriptor entry
                : manifest.entries()) {
            if (MinimapContainerLayout.classifyRuntime(entry.path()).orElse(null)
                    != MinimapContainerLayout.RuntimeEntryKind.FLOOR_TILE) {
                continue;
            }
            MinimapContainerLayout.RuntimeTileAddress address =
                    MinimapContainerLayout.parseRuntimeTile(entry.path())
                            .orElseThrow(() -> new ContainerValidationException(
                                    "Runtime tile coordinate is invalid: " + entry.path()
                            ));
            declared.add(new RuntimeTileCoordinate(
                    address.floorId(), address.zoom(), address.x(), address.y()
            ));
        }
        for (RuntimeFloor floor : manifest.floors()) {
            for (int zoom = 0; zoom < floor.zoomLevels(); zoom++) {
                Dimensions scaled = scaledCanvas(manifest.canvas(), zoom);
                int tileCountX = ceilDiv(scaled.width(), manifest.tileEdge());
                int tileCountY = ceilDiv(scaled.height(), manifest.tileEdge());
                for (int x = 0; x < tileCountX; x++) {
                    for (int y = 0; y < tileCountY; y++) {
                        if (!declared.contains(new RuntimeTileCoordinate(
                                floor.selection().id(), zoom, x, y
                        ))) {
                            throw new ContainerValidationException(
                                    "Runtime manifest tile coverage is incomplete for floor "
                                            + floor.selection().id() + " at zoom " + zoom
                            );
                        }
                    }
                }
            }
        }
    }

    private static SourceTileContext sourceContext(MinimapDefinition definition) {
        Map<String, SourceFloor> floors = new HashMap<>();
        Set<String> assets = new HashSet<>();
        for (SourceFloor floor : definition.document().floors()) {
            floors.put(floor.selection().id(), floor);
            for (MinimapLayer layer : floor.layers()) {
                if (layer instanceof ImportedImageLayer image) {
                    assets.add(image.assetId());
                }
            }
        }
        return new SourceTileContext(
                definition.document().canvas(), definition.manifest().tileEdge(), floors, assets
        );
    }

    private static void validateSourceAddress(
            SourceTileContext context,
            MinimapContainerLayout.SourceTileAddress address,
            ContainerPath path
    ) {
        if (!withinCanvas(context.canvas(), context.tileEdge(), address.x(), address.y())) {
            throw new ContainerValidationException("Source tile is outside the declared canvas: " + path);
        }
        if (address.kind() == MinimapContainerLayout.SourceEntryKind.ASSET_TILE) {
            if (!context.assets().contains(address.ownerId())) {
                throw new ContainerValidationException("Source asset tile is not referenced by a layer: " + path);
            }
            return;
        }
        SourceFloor floor = context.floors().get(address.floorId());
        if (floor == null) {
            throw new ContainerValidationException("Source tile references an unknown floor: " + path);
        }
        MinimapLayer layer = floor.layers().stream()
                .filter(candidate -> candidate.common().id().equals(address.ownerId()))
                .findFirst()
                .orElseThrow(() -> new ContainerValidationException(
                        "Source tile references an unknown layer: " + path
                ));
        if (address.kind() == MinimapContainerLayout.SourceEntryKind.LAYER_MASK
                && !layer.common().maskEnabled()) {
            throw new ContainerValidationException("Source mask is disabled for the layer: " + path);
        }
        if (address.kind() == MinimapContainerLayout.SourceEntryKind.LAYER_TILE
                && layer instanceof ImportedImageLayer) {
            throw new ContainerValidationException("Imported image content must use an asset tile path: " + path);
        }
        if (address.kind() == MinimapContainerLayout.SourceEntryKind.LAYER_TILE
                && !(layer instanceof WorldBakeLayer
                || layer instanceof RasterPaintLayer
                || layer instanceof CutoutLayer)) {
            throw new ContainerValidationException("Layer type cannot own raster tile content: " + path);
        }
    }

    private static void requireAssetCoverage(
            SourceTileContext context,
            Map<String, Set<TileCoordinate>> present
    ) {
        if (context.assets().isEmpty()) {
            return;
        }
        int countX = ceilDiv(context.canvas().width(), context.tileEdge());
        int countY = ceilDiv(context.canvas().height(), context.tileEdge());
        for (String asset : context.assets()) {
            Set<TileCoordinate> assetTiles = present.getOrDefault(asset, Set.of());
            for (int x = 0; x < countX; x++) {
                for (int y = 0; y < countY; y++) {
                    if (!assetTiles.contains(new TileCoordinate(x, y))) {
                        throw new ContainerValidationException(
                                "Imported image asset tile coverage is incomplete: " + asset
                        );
                    }
                }
            }
        }
    }

    private static void requireSourceCoverageBudget(SourceTileContext context) {
        if (context.assets().isEmpty()) {
            return;
        }
        long maximumTiles = MinimapHardLimits.MAX_ZIP_ENTRIES - 5L;
        long tilesPerAsset = Math.multiplyExact(
                (long) ceilDiv(context.canvas().width(), context.tileEdge()),
                ceilDiv(context.canvas().height(), context.tileEdge())
        );
        long requiredTiles;
        try {
            requiredTiles = Math.multiplyExact(tilesPerAsset, context.assets().size());
        } catch (ArithmeticException exception) {
            throw new ContainerValidationException(
                    "Imported asset tile coverage overflows its entry budget", exception
            );
        }
        if (requiredTiles > maximumTiles) {
            throw new ContainerValidationException(
                    "Imported asset tile coverage cannot fit in the ZIP entry budget"
            );
        }
    }

    private static long addCoverage(
            long current,
            int countX,
            int countY,
            long maximum,
            String message
    ) {
        try {
            long next = Math.addExact(current, Math.multiplyExact((long) countX, countY));
            if (next > maximum) {
                throw new ContainerValidationException(message);
            }
            return next;
        } catch (ArithmeticException exception) {
            throw new ContainerValidationException(message, exception);
        }
    }

    private static boolean withinCanvas(CanvasBounds canvas, int tileEdge, int x, int y) {
        return x >= 0 && y >= 0
                && x < ceilDiv(canvas.width(), tileEdge)
                && y < ceilDiv(canvas.height(), tileEdge);
    }

    private static Dimensions dimensionsAtZoom(
            CanvasBounds canvas, int tileEdge, int zoom, int x, int y
    ) {
        Dimensions scaled = scaledCanvas(canvas, zoom);
        int countX = ceilDiv(scaled.width(), tileEdge);
        int countY = ceilDiv(scaled.height(), tileEdge);
        if (x < 0 || y < 0 || x >= countX || y >= countY) {
            throw new ContainerValidationException("Runtime tile is outside the scaled canvas");
        }
        int width = Math.min(tileEdge, scaled.width() - x * tileEdge);
        int height = Math.min(tileEdge, scaled.height() - y * tileEdge);
        return new Dimensions(width, height);
    }

    private static Dimensions scaledCanvas(CanvasBounds canvas, int zoom) {
        int width = canvas.width();
        int height = canvas.height();
        for (int index = 0; index < zoom; index++) {
            width = (width + 1) / 2;
            height = (height + 1) / 2;
        }
        return new Dimensions(width, height);
    }

    private static void validatePngDimensions(
            byte[] png, CanvasBounds canvas, int tileEdge, int x, int y, ContainerPath path
    ) {
        validatePngDimensions(png, canvas, tileEdge, x, y, path, false);
    }

    private static void validatePngDimensions(
            byte[] png,
            CanvasBounds canvas,
            int tileEdge,
            int x,
            int y,
            ContainerPath path,
            boolean mask
    ) {
        Dimensions expected = new Dimensions(
                Math.min(tileEdge, canvas.width() - x * tileEdge),
                Math.min(tileEdge, canvas.height() - y * tileEdge)
        );
        validatePngDimensions(png, expected.width(), expected.height(), path, mask);
    }

    private static void validatePngDimensions(byte[] png, int width, int height, ContainerPath path) {
        validatePngDimensions(png, width, height, path, false);
    }

    private static void validatePngDimensions(
            byte[] png, int width, int height, ContainerPath path, boolean mask
    ) {
        BoundedPngReader.DecodedPng decoded;
        try {
            decoded = BoundedPngReader.decode(png);
        } catch (PngValidationException exception) {
            throw new ContainerValidationException("PNG tile is not canonical: " + path, exception);
        }
        if (decoded.width() != width || decoded.height() != height) {
            throw new ContainerValidationException(
                    "PNG dimensions do not match tile edge/canvas at " + path
            );
        }
        if (mask) {
            byte[] rgba = decoded.rgba();
            for (int index = 0; index < rgba.length; index += 4) {
                int alpha = rgba[index + 3] & 0xff;
                if (alpha != 0 && ((rgba[index] & 0xff) != 0xff
                        || (rgba[index + 1] & 0xff) != 0xff
                        || (rgba[index + 2] & 0xff) != 0xff)) {
                    throw new ContainerValidationException(
                            "Mask PNG RGB must be white at " + path
                    );
                }
            }
        }
    }

    private static byte[] readEntry(CanonicalZipWriter.EntrySource source) {
        long size = source.size();
        if (size < 0 || size > MinimapHardLimits.MAX_ZIP_ENTRY_BYTES || size > Integer.MAX_VALUE) {
            throw new ContainerValidationException("Source tile size exceeds the hard limit");
        }
        try (InputStream input = source.openStream()) {
            ByteArrayOutputStream output = new ByteArrayOutputStream((int) size);
            byte[] buffer = new byte[8192];
            long remaining = size;
            while (remaining > 0) {
                int count = input.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                if (count < 0 || count == 0) {
                    throw new ContainerValidationException("Source tile stream ended or made no progress");
                }
                output.write(buffer, 0, count);
                remaining -= count;
            }
            if (input.read() != -1) {
                throw new ContainerValidationException("Source tile stream exceeds its declared size");
            }
            return output.toByteArray();
        } catch (IOException exception) {
            throw new ContainerValidationException("Failed to read source tile", exception);
        }
    }

    private static int ceilDiv(int value, int divisor) {
        return Math.addExact(value, divisor - 1) / divisor;
    }

    private record SourceTileContext(
            CanvasBounds canvas,
            int tileEdge,
            Map<String, SourceFloor> floors,
            Set<String> assets
    ) {
    }

    private record TileCoordinate(int x, int y) {
    }

    private record RuntimeTileCoordinate(String floorId, int zoom, int x, int y) {
    }

    private record Dimensions(int width, int height) {
    }
}
