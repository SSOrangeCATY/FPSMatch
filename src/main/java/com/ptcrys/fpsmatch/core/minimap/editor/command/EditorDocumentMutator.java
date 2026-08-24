package com.ptcrys.fpsmatch.core.minimap.editor.command;

import com.ptcrys.fpsmatch.core.minimap.editor.document.EditorDocument;
import com.ptcrys.fpsmatch.core.minimap.editor.document.EditableLayer;
import com.ptcrys.fpsmatch.core.minimap.editor.raster.RasterSurface;
import com.ptcrys.fpsmatch.core.minimap.editor.raster.Rgba8;
import com.ptcrys.fpsmatch.core.minimap.format.BoundedPngReader;
import com.ptcrys.fpsmatch.core.minimap.format.CanonicalPngCodecV1;
import com.ptcrys.fpsmatch.core.minimap.format.Sha256Digest;
import com.ptcrys.fpsmatch.core.minimap.model.LayerType;
import com.ptcrys.fpsmatch.core.minimap.model.Sha256;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Applies a local edit only after every operation can be validated against one staged view. */
public final class EditorDocumentMutator {
    public void apply(EditorDocument document, EditorEdit edit) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(edit, "edit");
        try {
            validatePayloads(edit);
            List<PlannedOperation> planned = stage(document, edit);
            commit(document, planned);
        } catch (EditorCommandException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            EditorCommandException failure = new EditorCommandException(
                    "Editor edit cannot be applied: " + exception.getMessage());
            failure.initCause(exception);
            throw failure;
        }
    }

    private static void validatePayloads(EditorEdit edit) {
        for (Map.Entry<Sha256, byte[]> entry : edit.payloads().entrySet()) {
            if (!Sha256Digest.of(entry.getValue()).equals(entry.getKey())) {
                throw new EditorCommandException("Editor payload hash does not match its bytes");
            }
        }
    }

    private static List<PlannedOperation> stage(EditorDocument document, EditorEdit edit) {
        StagedDocument staged = new StagedDocument(document);
        List<PlannedOperation> planned = stageOperations(
                document, staged, edit, edit.forward(), true);
        stageOperations(document, staged, edit, edit.inverse(), false);
        if (!staged.isRestored()) {
            throw new EditorCommandException(
                    "Editor inverse operations do not restore the original document state");
        }
        return List.copyOf(planned);
    }

    private static List<PlannedOperation> stageOperations(
            EditorDocument document,
            StagedDocument staged,
            EditorEdit edit,
            List<EditorOperation> operations,
            boolean collect
    ) {
        List<PlannedOperation> planned = new ArrayList<>(collect ? operations.size() : 0);
        for (EditorOperation operation : operations) {
            if (operation == null) {
                throw new EditorCommandException("Editor edits cannot contain null operations");
            }
            if (operation instanceof EditorOperation.SetOpacity setOpacity) {
                LayerStage layer = staged.layer(setOpacity.floorId(), setOpacity.layerId());
                if (layer.locked) {
                    throw new EditorCommandException("Locked layers cannot change opacity");
                }
                layer.opacity = setOpacity.opacity();
                planned.add(new PlannedOperation(operation, null));
            } else if (operation instanceof EditorOperation.SetVisibility setVisibility) {
                staged.layer(setVisibility.floorId(), setVisibility.layerId()).visible = setVisibility.visible();
                planned.add(new PlannedOperation(operation, null));
            } else if (operation instanceof EditorOperation.SetLocked setLocked) {
                staged.layer(setLocked.floorId(), setLocked.layerId()).locked = setLocked.locked();
                planned.add(new PlannedOperation(operation, null));
            } else if (operation instanceof EditorOperation.PutTile putTile) {
                LayerStage layer = staged.layer(putTile.floorId(), putTile.layerId());
                requireEditableRaster(layer);
                TileAddress address = new TileAddress(
                        putTile.floorId(), putTile.layerId(), putTile.tileX(), putTile.tileY());
                TileDimensions dimensions = dimensions(document, address);
                Optional<int[]> current = staged.tile(address);
                requireExpectedHash(current, putTile.oldHash(), address, dimensions);
                int[] pixels = decodeTile(edit, putTile.newHash(), dimensions);
                staged.put(address, pixels);
                if (collect) {
                    planned.add(new PlannedOperation(operation, pixels));
                }
            } else if (operation instanceof EditorOperation.DeleteTile deleteTile) {
                LayerStage layer = staged.layer(deleteTile.floorId(), deleteTile.layerId());
                requireEditableRaster(layer);
                TileAddress address = new TileAddress(
                        deleteTile.floorId(), deleteTile.layerId(), deleteTile.tileX(), deleteTile.tileY());
                dimensions(document, address);
                requireExpectedHash(
                        staged.tile(address), Optional.of(deleteTile.oldHash()), address,
                        dimensions(document, address));
                staged.delete(address);
                if (collect) {
                    planned.add(new PlannedOperation(operation, null));
                }
            } else {
                throw new EditorCommandException("Unsupported editor operation: " + operation);
            }
        }
        return planned;
    }

    private static void commit(EditorDocument document, List<PlannedOperation> planned) {
        for (PlannedOperation plannedOperation : planned) {
            EditorOperation operation = plannedOperation.operation();
            if (operation instanceof EditorOperation.SetOpacity setOpacity) {
                document.setLayerOpacity(setOpacity.floorId(), setOpacity.layerId(), setOpacity.opacity());
            } else if (operation instanceof EditorOperation.SetVisibility setVisibility) {
                document.setLayerVisible(
                        setVisibility.floorId(), setVisibility.layerId(), setVisibility.visible());
            } else if (operation instanceof EditorOperation.SetLocked setLocked) {
                document.setLayerLocked(setLocked.floorId(), setLocked.layerId(), setLocked.locked());
            } else if (operation instanceof EditorOperation.PutTile putTile) {
                document.putTilePixels(
                        putTile.floorId(), putTile.layerId(), putTile.tileX(), putTile.tileY(),
                        plannedOperation.pixels());
            } else if (operation instanceof EditorOperation.DeleteTile deleteTile) {
                document.removeTilePixels(
                        deleteTile.floorId(), deleteTile.layerId(),
                        deleteTile.tileX(), deleteTile.tileY());
            } else {
                throw new EditorCommandException("Unsupported editor operation: " + operation);
            }
        }
    }

    private static void requireEditableRaster(LayerStage layer) {
        if (layer.type != LayerType.RASTER_PAINT) {
            throw new EditorCommandException("Only raster-paint layers accept tile edits");
        }
        if (!layer.visible) {
            throw new EditorCommandException("Hidden layers cannot be edited");
        }
        if (layer.locked) {
            throw new EditorCommandException("Locked layers cannot be edited");
        }
    }

    private static void requireExpectedHash(
            Optional<int[]> current,
            Optional<Sha256> expected,
            TileAddress address,
            TileDimensions dimensions
    ) {
        if (expected.isEmpty()) {
            if (current.isPresent()) {
                throw new EditorCommandException("Tile already exists: " + address);
            }
            return;
        }
        if (current.isEmpty() || !tileHash(current.get(), dimensions).equals(expected.get())) {
            throw new EditorCommandException("Tile hash does not match the staged document: " + address);
        }
    }

    private static int[] decodeTile(EditorEdit edit, Sha256 hash, TileDimensions dimensions) {
        byte[] bytes = edit.payload(hash).orElseThrow(
                () -> new EditorCommandException("Missing PNG payload for tile hash " + hash));
        if (!Sha256Digest.of(bytes).equals(hash)) {
            throw new EditorCommandException("Tile payload hash does not match its bytes");
        }
        BoundedPngReader.DecodedPng png = BoundedPngReader.decode(bytes);
        if (png.width() != dimensions.width || png.height() != dimensions.height) {
            throw new EditorCommandException("Tile PNG dimensions do not match the canvas tile");
        }
        byte[] rgba = png.rgba();
        int[] pixels = new int[dimensions.width * dimensions.height];
        for (int index = 0; index < pixels.length; index++) {
            int offset = index * 4;
            int red = rgba[offset] & 0xff;
            int green = rgba[offset + 1] & 0xff;
            int blue = rgba[offset + 2] & 0xff;
            int alpha = rgba[offset + 3] & 0xff;
            pixels[index] = alpha == 0
                    ? RasterSurface.inheritedPixel()
                    : Rgba8.of(red, green, blue, alpha);
        }
        return pixels;
    }

    private static TileDimensions dimensions(EditorDocument document, TileAddress address) {
        long originX = (long) address.tileX * document.tileEdge();
        long originY = (long) address.tileY * document.tileEdge();
        if (originX >= document.canvas().width() || originY >= document.canvas().height()) {
            throw new EditorCommandException("Tile is outside the canvas: " + address);
        }
        int width = (int) Math.min(document.tileEdge(), document.canvas().width() - originX);
        int height = (int) Math.min(document.tileEdge(), document.canvas().height() - originY);
        return new TileDimensions(width, height);
    }

    private static Sha256 tileHash(int[] pixels, TileDimensions dimensions) {
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
        return Sha256Digest.of(CanonicalPngCodecV1.encode(dimensions.width, dimensions.height, rgba));
    }

    private static boolean allInherited(int[] pixels) {
        for (int pixel : pixels) {
            if (!RasterSurface.isInheritedPixel(pixel)) {
                return false;
            }
        }
        return true;
    }

    private record PlannedOperation(EditorOperation operation, int[] pixels) {
        private PlannedOperation {
            pixels = pixels == null ? null : pixels.clone();
        }

        @Override
        public int[] pixels() {
            return pixels == null ? null : pixels.clone();
        }
    }

    private record TileAddress(String floorId, String layerId, int tileX, int tileY) {
    }

    private record TileDimensions(int width, int height) {
    }

    private static final class LayerStage {
        private final LayerType type;
        private final boolean originalVisible;
        private final boolean originalLocked;
        private final double originalOpacity;
        private boolean visible;
        private boolean locked;
        private double opacity;

        private LayerStage(EditableLayer layer) {
            this.type = layer.type();
            this.originalVisible = layer.visible();
            this.originalLocked = layer.locked();
            this.originalOpacity = layer.opacity();
            this.visible = layer.visible();
            this.locked = layer.locked();
            this.opacity = layer.opacity();
        }

        private boolean isRestored() {
            return visible == originalVisible
                    && locked == originalLocked
                    && Double.compare(opacity, originalOpacity) == 0;
        }
    }

    private static final class StagedDocument {
        private final EditorDocument document;
        private final Map<LayerAddress, LayerStage> layers = new LinkedHashMap<>();
        private final Map<TileAddress, Optional<int[]>> tiles = new LinkedHashMap<>();

        private StagedDocument(EditorDocument document) {
            this.document = document;
        }

        private LayerStage layer(String floorId, String layerId) {
            LayerAddress address = new LayerAddress(floorId, layerId);
            return layers.computeIfAbsent(address,
                    ignored -> new LayerStage(document.layer(floorId, layerId)));
        }

        private Optional<int[]> tile(TileAddress address) {
            Optional<int[]> staged = tiles.get(address);
            if (staged != null) {
                return staged.map(int[]::clone);
            }
            Optional<int[]> existing = document.tilePixelsOptional(
                    address.floorId, address.layerId, address.tileX, address.tileY);
            return existing;
        }

        private void put(TileAddress address, int[] pixels) {
            tiles.put(address, Optional.of(pixels.clone()));
        }

        private void delete(TileAddress address) {
            tiles.put(address, Optional.empty());
        }

        private boolean isRestored() {
            for (LayerStage layer : layers.values()) {
                if (!layer.isRestored()) {
                    return false;
                }
            }
            for (Map.Entry<TileAddress, Optional<int[]>> entry : tiles.entrySet()) {
                TileAddress address = entry.getKey();
                Optional<int[]> original = document.tilePixelsOptional(
                        address.floorId, address.layerId, address.tileX, address.tileY);
                Optional<int[]> current = entry.getValue();
                if (original.isPresent() != current.isPresent()) {
                    return false;
                }
                if (original.isPresent()
                        && !java.util.Arrays.equals(original.get(), current.orElseThrow())) {
                    return false;
                }
            }
            return true;
        }
    }

    private record LayerAddress(String floorId, String layerId) {
    }
}
