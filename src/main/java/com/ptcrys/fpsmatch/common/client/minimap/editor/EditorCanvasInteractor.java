package com.ptcrys.fpsmatch.common.client.minimap.editor;

import com.ptcrys.fpsmatch.core.minimap.editor.command.EditorCommandException;
import com.ptcrys.fpsmatch.core.minimap.editor.command.EditorDocumentMutator;
import com.ptcrys.fpsmatch.core.minimap.editor.command.EditorEdit;
import com.ptcrys.fpsmatch.core.minimap.editor.command.EditorOperation;
import com.ptcrys.fpsmatch.core.minimap.editor.document.EditorDocument;
import com.ptcrys.fpsmatch.core.minimap.editor.document.EditableLayer;
import com.ptcrys.fpsmatch.core.minimap.editor.raster.BrushStamp;
import com.ptcrys.fpsmatch.core.minimap.editor.raster.IntPoint;
import com.ptcrys.fpsmatch.core.minimap.editor.raster.RasterSurface;
import com.ptcrys.fpsmatch.core.minimap.editor.raster.RectangleSelection;
import com.ptcrys.fpsmatch.core.minimap.editor.raster.Rgba8;
import com.ptcrys.fpsmatch.core.minimap.format.CanonicalPngCodecV1;
import com.ptcrys.fpsmatch.core.minimap.format.Sha256Digest;
import com.ptcrys.fpsmatch.core.minimap.model.LayerType;
import com.ptcrys.fpsmatch.core.minimap.model.Sha256;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Turns one canvas gesture into one staged, reversible document edit. */
public final class EditorCanvasInteractor {
    private final EditorDocument document;
    private final EditorDocumentMutator mutator = new EditorDocumentMutator();

    public EditorCanvasInteractor(EditorDocument document) {
        this.document = Objects.requireNonNull(document, "document");
    }

    public EditorEdit brush(
            String floorId,
            String layerId,
            IntPoint from,
            IntPoint to,
            BrushStamp stamp,
            int rgba
    ) {
        return brushPath(floorId, layerId, List.of(from, to), stamp, rgba);
    }

    /** Applies a complete pointer gesture as one reversible edit and outbound command. */
    public EditorEdit brushPath(
            String floorId,
            String layerId,
            List<IntPoint> path,
            BrushStamp stamp,
            int rgba
    ) {
        Objects.requireNonNull(stamp, "stamp");
        requireEditableRaster(floorId, layerId);
        TileAccumulator tiles = new TileAccumulator(document, floorId, layerId);
        samplePath(path, (centerX, centerY) ->
                stamp(tiles, stamp, centerX, centerY, rgba, false));
        return apply(tiles);
    }

    public EditorEdit erase(
            String floorId,
            String layerId,
            IntPoint from,
            IntPoint to,
            BrushStamp stamp
    ) {
        return erasePath(floorId, layerId, List.of(from, to), stamp);
    }

    /** Applies a complete eraser gesture as one reversible edit and outbound command. */
    public EditorEdit erasePath(
            String floorId,
            String layerId,
            List<IntPoint> path,
            BrushStamp stamp
    ) {
        Objects.requireNonNull(stamp, "stamp");
        requireEditableRaster(floorId, layerId);
        TileAccumulator tiles = new TileAccumulator(document, floorId, layerId);
        samplePath(path, (centerX, centerY) ->
                stamp(tiles, stamp, centerX, centerY, 0, true));
        return apply(tiles);
    }

    public EditorEdit moveSelection(
            String floorId,
            String layerId,
            RectangleSelection selection,
            int deltaX,
            int deltaY
    ) {
        Objects.requireNonNull(selection, "selection");
        requireEditableRaster(floorId, layerId);
        validateMoveBounds(selection, deltaX, deltaY);

        int width = selection.maxX() - selection.minX();
        int height = selection.maxY() - selection.minY();
        if (width == 0 || height == 0 || (deltaX == 0 && deltaY == 0)) {
            throw new EditorCommandException("Selection move must change at least one pixel");
        }

        TileAccumulator tiles = new TileAccumulator(document, floorId, layerId);
        int[] snapshot = new int[width * height];
        // Snapshot before clearing so an overlapping destination sees the original pixels.
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                snapshot[y * width + x] = tiles.pixelAt(selection.minX() + x, selection.minY() + y);
            }
        }
        for (int y = selection.minY(); y < selection.maxY(); y++) {
            for (int x = selection.minX(); x < selection.maxX(); x++) {
                tiles.setPixel(x, y, RasterSurface.inheritedPixel());
            }
        }
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                tiles.setPixel(
                        Math.toIntExact((long) selection.minX() + deltaX + x),
                        Math.toIntExact((long) selection.minY() + deltaY + y),
                        snapshot[y * width + x]);
            }
        }
        return apply(tiles);
    }

    private void requireEditableRaster(String floorId, String layerId) {
        try {
            EditableLayer layer = document.layer(floorId, layerId);
            if (layer.type() != LayerType.RASTER_PAINT) {
                throw new EditorCommandException("Only raster-paint layers accept canvas edits");
            }
            if (!layer.visible()) {
                throw new EditorCommandException("Hidden layers cannot be edited");
            }
            if (layer.locked()) {
                throw new EditorCommandException("Locked layers cannot be edited");
            }
        } catch (EditorCommandException exception) {
            throw exception;
        } catch (IllegalArgumentException exception) {
            EditorCommandException failure = new EditorCommandException(
                    "Canvas target does not exist: " + floorId + "/" + layerId);
            failure.initCause(exception);
            throw failure;
        }
    }

    private void validateMoveBounds(RectangleSelection selection, int deltaX, int deltaY) {
        long destinationMinX = (long) selection.minX() + deltaX;
        long destinationMinY = (long) selection.minY() + deltaY;
        long destinationMaxX = (long) selection.maxX() + deltaX;
        long destinationMaxY = (long) selection.maxY() + deltaY;
        if (selection.minX() < 0 || selection.minY() < 0
                || selection.maxX() > document.canvas().width()
                || selection.maxY() > document.canvas().height()
                || destinationMinX < 0 || destinationMinY < 0
                || destinationMaxX > document.canvas().width()
                || destinationMaxY > document.canvas().height()) {
            throw new EditorCommandException("Selection move is outside the document bounds");
        }
    }

    private EditorEdit apply(TileAccumulator tiles) {
        List<TileChange> changes = tiles.changedTiles();
        List<EditorOperation> forward = new ArrayList<>(changes.size());
        List<EditorOperation> inverse = new ArrayList<>(changes.size());
        Map<Sha256, byte[]> payloads = new LinkedHashMap<>();
        for (TileChange change : changes) {
            TilePayload after = allInherited(change.pixels)
                    ? null
                    : encodeTile(change.pixels, change.dimensions);
            if (!change.originalPresent && after == null) {
                continue;
            }
            if (change.originalPresent && after != null && change.originalHash.equals(after.hash)) {
                continue;
            }

            if (after == null) {
                forward.add(EditorOperation.deleteTile(
                        change.address.floorId, change.address.layerId,
                        change.address.tileX, change.address.tileY, change.originalHash));
                inverse.add(EditorOperation.putTile(
                        change.address.floorId, change.address.layerId,
                        change.address.tileX, change.address.tileY,
                        Optional.empty(), change.originalHash));
                payloads.put(change.originalHash, change.originalPayload.clone());
            } else {
                payloads.put(after.hash, after.bytes.clone());
                Optional<Sha256> oldHash = change.originalPresent
                        ? Optional.of(change.originalHash)
                        : Optional.empty();
                forward.add(EditorOperation.putTile(
                        change.address.floorId, change.address.layerId,
                        change.address.tileX, change.address.tileY, oldHash, after.hash));
                if (change.originalPresent) {
                    inverse.add(EditorOperation.putTile(
                            change.address.floorId, change.address.layerId,
                            change.address.tileX, change.address.tileY,
                            Optional.of(after.hash), change.originalHash));
                    payloads.put(change.originalHash, change.originalPayload.clone());
                } else {
                    inverse.add(EditorOperation.deleteTile(
                            change.address.floorId, change.address.layerId,
                            change.address.tileX, change.address.tileY, after.hash));
                }
            }
        }
        if (forward.isEmpty()) {
            throw new EditorCommandException("Canvas gesture did not change any pixels");
        }
        Collections.reverse(inverse);
        EditorEdit edit = new EditorEdit(forward, inverse, payloads);
        mutator.apply(document, edit);
        return edit;
    }

    private void stamp(
            TileAccumulator tiles,
            BrushStamp stamp,
            int centerX,
            int centerY,
            int rgba,
            boolean erasing
    ) {
        int minimum = -stamp.radius();
        int maximum = minimum + stamp.size() - 1;
        for (int dy = minimum; dy <= maximum; dy++) {
            for (int dx = minimum; dx <= maximum; dx++) {
                float coverage = stamp.coverage(dx, dy);
                if (coverage <= 0.0f) {
                    continue;
                }
                long x = (long) centerX + dx;
                long y = (long) centerY + dy;
                if (x < 0 || y < 0 || x >= document.canvas().width() || y >= document.canvas().height()) {
                    continue;
                }
                int documentX = (int) x;
                int documentY = (int) y;
                if (erasing) {
                    tiles.setPixel(documentX, documentY, RasterSurface.inheritedPixel());
                    continue;
                }
                int source = Rgba8.scaleAlpha(rgba, coverage);
                if (source == 0) {
                    continue;
                }
                int destination = tiles.pixelAt(documentX, documentY);
                if (RasterSurface.isInheritedPixel(destination)) {
                    destination = 0;
                }
                int composed = Rgba8.sourceOver(destination, source);
                tiles.setPixel(documentX, documentY,
                        Rgba8.alpha(composed) == 0 ? RasterSurface.inheritedPixel() : composed);
            }
        }
    }

    private static void sampleLine(IntPoint from, IntPoint to, PointConsumer consumer) {
        long dx = Math.abs((long) to.x() - from.x());
        long dy = Math.abs((long) to.y() - from.y());
        long stepX = from.x() < to.x() ? 1L : -1L;
        long stepY = from.y() < to.y() ? 1L : -1L;
        long error = dx - dy;
        long x = from.x();
        long y = from.y();
        while (true) {
            consumer.accept(Math.toIntExact(x), Math.toIntExact(y));
            if (x == to.x() && y == to.y()) {
                return;
            }
            long doubled = error * 2;
            if (doubled > -dy) {
                error -= dy;
                x += stepX;
            }
            if (doubled < dx) {
                error += dx;
                y += stepY;
            }
        }
    }

    private static void samplePath(List<IntPoint> path, PointConsumer consumer) {
        Objects.requireNonNull(path, "path");
        if (path.isEmpty()) {
            throw new EditorCommandException("Canvas gesture path must not be empty");
        }
        List<IntPoint> points = path.stream()
                .map(point -> Objects.requireNonNull(point, "path point"))
                .toList();
        int[] previous = {Integer.MIN_VALUE, Integer.MIN_VALUE};
        PointConsumer distinct = (x, y) -> {
            if (previous[0] != x || previous[1] != y) {
                consumer.accept(x, y);
                previous[0] = x;
                previous[1] = y;
            }
        };
        if (points.size() == 1) {
            distinct.accept(points.get(0).x(), points.get(0).y());
            return;
        }
        for (int index = 1; index < points.size(); index++) {
            sampleLine(points.get(index - 1), points.get(index), distinct);
        }
    }

    private static TilePayload encodeTile(int[] pixels, TileDimensions dimensions) {
        byte[] rgba = new byte[pixels.length * 4];
        for (int index = 0; index < pixels.length; index++) {
            int pixel = pixels[index];
            if (RasterSurface.isInheritedPixel(pixel)) {
                continue;
            }
            int offset = index * 4;
            rgba[offset] = (byte) Rgba8.red(pixel);
            rgba[offset + 1] = (byte) Rgba8.green(pixel);
            rgba[offset + 2] = (byte) Rgba8.blue(pixel);
            rgba[offset + 3] = (byte) Rgba8.alpha(pixel);
        }
        byte[] png = CanonicalPngCodecV1.encode(dimensions.width, dimensions.height, rgba);
        return new TilePayload(Sha256Digest.of(png), png);
    }

    private static boolean allInherited(int[] pixels) {
        for (int pixel : pixels) {
            if (!RasterSurface.isInheritedPixel(pixel)) {
                return false;
            }
        }
        return true;
    }

    @FunctionalInterface
    private interface PointConsumer {
        void accept(int x, int y);
    }

    private static final class TileAccumulator {
        private static final Comparator<TileChange> ORDER = Comparator
                .comparing((TileChange change) -> change.address.floorId)
                .thenComparing(change -> change.address.layerId)
                .thenComparingInt(change -> change.address.tileY)
                .thenComparingInt(change -> change.address.tileX);

        private final EditorDocument document;
        private final String floorId;
        private final String layerId;
        private final Map<TileAddress, TileChange> changes = new LinkedHashMap<>();

        private TileAccumulator(EditorDocument document, String floorId, String layerId) {
            this.document = document;
            this.floorId = floorId;
            this.layerId = layerId;
        }

        private int pixelAt(int x, int y) {
            TileChange change = tileFor(x, y);
            return change.pixels[change.index(x, y)];
        }

        private void setPixel(int x, int y, int pixel) {
            TileChange change = tileFor(x, y);
            change.pixels[change.index(x, y)] = pixel;
        }

        private List<TileChange> changedTiles() {
            List<TileChange> ordered = new ArrayList<>(changes.values());
            ordered.sort(ORDER);
            return ordered;
        }

        private TileChange tileFor(int x, int y) {
            int tileX = x / document.tileEdge();
            int tileY = y / document.tileEdge();
            TileAddress address = new TileAddress(floorId, layerId, tileX, tileY);
            return changes.computeIfAbsent(address, ignored -> create(address));
        }

        private TileChange create(TileAddress address) {
            TileDimensions dimensions = dimensions(document, address);
            Optional<int[]> existing = document.tilePixelsOptional(
                    address.floorId, address.layerId, address.tileX, address.tileY);
            if (existing.isEmpty()) {
                int[] pixels = existing.orElseGet(() -> inheritedPixels(dimensions));
                return new TileChange(address, dimensions, false, null, null, pixels);
            }
            TilePayload original = encodeTile(existing.get(), dimensions);
            return new TileChange(
                    address, dimensions, true, original.hash, original.bytes, existing.get());
        }

        private static int[] inheritedPixels(TileDimensions dimensions) {
            int[] pixels = new int[dimensions.width * dimensions.height];
            Arrays.fill(pixels, RasterSurface.inheritedPixel());
            return pixels;
        }
    }

    private static TileDimensions dimensions(EditorDocument document, TileAddress address) {
        long originX = (long) address.tileX * document.tileEdge();
        long originY = (long) address.tileY * document.tileEdge();
        if (originX < 0 || originY < 0
                || originX >= document.canvas().width() || originY >= document.canvas().height()) {
            throw new EditorCommandException("Tile is outside the document bounds");
        }
        int width = (int) Math.min(document.tileEdge(), document.canvas().width() - originX);
        int height = (int) Math.min(document.tileEdge(), document.canvas().height() - originY);
        return new TileDimensions((int) originX, (int) originY, width, height);
    }

    private record TileAddress(String floorId, String layerId, int tileX, int tileY) {
    }

    private record TileDimensions(int originX, int originY, int width, int height) {
    }

    private record TilePayload(Sha256 hash, byte[] bytes) {
        private TilePayload {
            bytes = bytes.clone();
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }
    }

    private static final class TileChange {
        private final TileAddress address;
        private final TileDimensions dimensions;
        private final boolean originalPresent;
        private final Sha256 originalHash;
        private final byte[] originalPayload;
        private final int[] pixels;

        private TileChange(
                TileAddress address,
                TileDimensions dimensions,
                boolean originalPresent,
                Sha256 originalHash,
                byte[] originalPayload,
                int[] pixels
        ) {
            this.address = address;
            this.dimensions = dimensions;
            this.originalPresent = originalPresent;
            this.originalHash = originalHash;
            this.originalPayload = originalPayload == null ? null : originalPayload.clone();
            this.pixels = pixels;
        }

        private int index(int x, int y) {
            int localX = x - dimensions.originX;
            int localY = y - dimensions.originY;
            return localY * dimensions.width + localX;
        }
    }
}
