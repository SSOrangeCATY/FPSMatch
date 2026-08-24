package com.ptcrys.fpsmatch.core.minimap.editor.document;

import com.ptcrys.fpsmatch.core.minimap.editor.raster.RasterSurface;
import com.ptcrys.fpsmatch.core.minimap.editor.raster.Rgba8;
import com.ptcrys.fpsmatch.core.minimap.format.BoundedPngReader;
import com.ptcrys.fpsmatch.core.minimap.format.CanonicalPngCodecV1;
import com.ptcrys.fpsmatch.core.minimap.format.CanonicalZipWriter;
import com.ptcrys.fpsmatch.core.minimap.format.MinimapContainerLayout;
import com.ptcrys.fpsmatch.core.minimap.format.PreservedExtensions;
import com.ptcrys.fpsmatch.core.minimap.format.SourceMap;
import com.ptcrys.fpsmatch.core.minimap.format.SourceMapDraft;
import com.ptcrys.fpsmatch.core.minimap.format.SourceMapReader;
import com.ptcrys.fpsmatch.core.minimap.format.SourceMapWriter;
import com.ptcrys.fpsmatch.core.minimap.model.BlendMode;
import com.ptcrys.fpsmatch.core.minimap.model.CanvasBounds;
import com.ptcrys.fpsmatch.core.minimap.model.ConnectionsFile;
import com.ptcrys.fpsmatch.core.minimap.model.ContainerPath;
import com.ptcrys.fpsmatch.core.minimap.model.CutoutLayer;
import com.ptcrys.fpsmatch.core.minimap.model.ImportedImageLayer;
import com.ptcrys.fpsmatch.core.minimap.model.LayerCommon;
import com.ptcrys.fpsmatch.core.minimap.model.LayerType;
import com.ptcrys.fpsmatch.core.minimap.model.MapKey;
import com.ptcrys.fpsmatch.core.minimap.model.MinimapDefinition;
import com.ptcrys.fpsmatch.core.minimap.model.MinimapLayer;
import com.ptcrys.fpsmatch.core.minimap.model.NamespacedId;
import com.ptcrys.fpsmatch.core.minimap.model.RasterPaintLayer;
import com.ptcrys.fpsmatch.core.minimap.model.RegionVisualLayer;
import com.ptcrys.fpsmatch.core.minimap.model.RegionsFile;
import com.ptcrys.fpsmatch.core.minimap.model.SourceDocument;
import com.ptcrys.fpsmatch.core.minimap.model.SourceFloor;
import com.ptcrys.fpsmatch.core.minimap.model.SourceManifest;
import com.ptcrys.fpsmatch.core.minimap.model.StylesFile;
import com.ptcrys.fpsmatch.core.minimap.model.VectorLayer;
import com.ptcrys.fpsmatch.core.minimap.model.WorldBakeLayer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class EditorSourceCodec {
    private static final Set<ContainerPath> AUTHORITY_PATHS = Set.of(
            MinimapContainerLayout.SOURCE_MANIFEST,
            MinimapContainerLayout.SOURCE_DOCUMENT,
            MinimapContainerLayout.SOURCE_REGIONS,
            MinimapContainerLayout.CONNECTIONS,
            MinimapContainerLayout.SOURCE_STYLES
    );

    private EditorSourceCodec() {
    }

    public static EditorSourceSnapshot decode(byte[] sourceBytes) {
        byte[] ownedSource = Objects.requireNonNull(sourceBytes, "sourceBytes").clone();
        try (SourceMap source = SourceMapReader.read(ownedSource)) {
            MinimapDefinition definition = copyDefinition(source.definition());
            Map<ContainerPath, PreservedExtensions> extensions = copyExtensions(
                    source.authorityExtensions());
            Map<ContainerPath, byte[]> entries = new LinkedHashMap<>();
            for (ContainerPath path : source.paths()) {
                if (!AUTHORITY_PATHS.contains(path)) {
                    entries.put(path, source.entryBytes(path));
                }
            }
            EditorDocument document = hydrate(definition, entries);
            return new EditorSourceSnapshot(
                    ownedSource, definition, extensions, entries, document);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to close source map archive", exception);
        }
    }

    public static byte[] encode(EditorSourceSnapshot snapshot, long revision) {
        Objects.requireNonNull(snapshot, "snapshot");
        if (revision < 0) {
            throw new IllegalArgumentException("revision must be non-negative");
        }
        MinimapDefinition definition = mergeDefinition(snapshot, revision);
        List<CanonicalZipWriter.EntrySource> entries = rebuiltEntries(snapshot);
        return SourceMapWriter.write(new SourceMapDraft(
                definition, entries, snapshot.authorityExtensions()));
    }

    public static EditorSourceSnapshot withDocument(
            EditorSourceSnapshot baseline,
            EditorDocument document
    ) {
        Objects.requireNonNull(baseline, "baseline");
        Objects.requireNonNull(document, "document");
        return new EditorSourceSnapshot(
                baseline.originalSourceBytes(),
                copyDefinition(baseline.definition()),
                baseline.authorityExtensions(),
                baseline.entries(),
                document
        );
    }

    public static EditorSourceSnapshot createEmpty(
            MapKey mapKey,
            NamespacedId dimension,
            NamespacedId documentId,
            long revision,
            CanvasBounds canvas,
            int tileEdge,
            String floorId
    ) {
        RasterPaintLayer paint = new RasterPaintLayer(new LayerCommon(
                "paint",
                com.ptcrys.fpsmatch.core.minimap.model.DisplayLabel.literal("Paint"),
                true,
                false,
                1.0,
                BlendMode.NORMAL,
                Optional.empty(),
                false
        ));
        MinimapDefinition definition = EditorSourceDefaults.createDefinition(
                mapKey, dimension, documentId, revision, canvas, tileEdge, floorId,
                List.of(paint));
        return decode(SourceMapWriter.write(definition));
    }

    public static boolean hasVisiblePixels(EditorSourceSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        EditorDocument document = snapshot.document();
        Map<ContainerPath, byte[]> entries = snapshot.entries();
        for (String floorId : document.floorIds()) {
            for (EditableLayer layer : document.floor(floorId).layers()) {
                if (!layer.visible() || layer.opacity() <= 0.0) {
                    continue;
                }
                if (layer.type() == LayerType.IMPORTED_IMAGE) {
                    // Existing imported-image bindings are source-authoritative like encode().
                    String assetId = baselineImportedAssetId(
                            snapshot.definition(), floorId, layer.id()).orElse(null);
                    if (assetId != null && assetHasVisiblePixels(entries, assetId)) {
                        return true;
                    }
                } else if (layer.type() == LayerType.RASTER_PAINT
                        || layer.type() == LayerType.WORLD_BAKE) {
                    for (int[] pixels : layer.tiles().snapshot().values()) {
                        if (hasAlpha(pixels)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private static Optional<String> baselineImportedAssetId(
            MinimapDefinition definition,
            String floorId,
            String layerId
    ) {
        for (SourceFloor floor : definition.document().floors()) {
            if (!floor.selection().id().equals(floorId)) {
                continue;
            }
            for (MinimapLayer layer : floor.layers()) {
                if (layer.common().id().equals(layerId) && layer instanceof ImportedImageLayer image) {
                    return Optional.of(image.assetId());
                }
            }
            return Optional.empty();
        }
        return Optional.empty();
    }

    private static EditorDocument hydrate(
            MinimapDefinition definition,
            Map<ContainerPath, byte[]> entries
    ) {
        SourceDocument sourceDocument = definition.document();
        EditorDocument document = EditorDocument.hydrate(
                sourceDocument.canvas(), definition.manifest().tileEdge());
        for (SourceFloor floor : sourceDocument.floors()) {
            String floorId = floor.selection().id();
            document.hydrateFloor(floorId, floor.label());
            Map<String, MinimapLayer> layers = layersById(floor.layers());
            for (String layerId : sourceDocument.layerOrder().get(floorId)) {
                MinimapLayer layer = layers.get(layerId);
                LayerCommon common = layer.common();
                document.hydrateLayer(
                        floorId,
                        layerId,
                        layer.type(),
                        common.label(),
                        common.visible(),
                        common.locked(),
                        common.opacity(),
                        common.blendMode(),
                        common.maskEnabled(),
                        layer instanceof ImportedImageLayer image
                                ? Optional.of(image.assetId()) : Optional.empty(),
                        layer instanceof WorldBakeLayer world
                                ? Optional.of(world.generatorId()) : Optional.empty()
                );
            }
        }
        for (Map.Entry<ContainerPath, byte[]> entry : entries.entrySet()) {
            MinimapContainerLayout.SourceTileAddress address =
                    MinimapContainerLayout.parseSourceTile(entry.getKey()).orElse(null);
            if (address == null
                    || address.kind() == MinimapContainerLayout.SourceEntryKind.ASSET_TILE) {
                continue;
            }
            EditableLayer layer = document.layer(address.floorId(), address.ownerId());
            boolean mask = address.kind() == MinimapContainerLayout.SourceEntryKind.LAYER_MASK;
            document.hydrateTilePixels(
                    address.floorId(),
                    address.ownerId(),
                    address.x(),
                    address.y(),
                    decodePixels(entry.getValue(), layer.type(), mask),
                    mask
            );
        }
        return document;
    }

    private static MinimapDefinition mergeDefinition(
            EditorSourceSnapshot snapshot,
            long revision
    ) {
        MinimapDefinition baseline = snapshot.definition();
        EditorDocument document = snapshot.document();
        if (!document.canvas().equals(baseline.document().canvas())
                || document.tileEdge() != baseline.manifest().tileEdge()) {
            throw new IllegalStateException("Editor canvas and tile edge cannot diverge from the source");
        }

        List<String> baselineFloorIds = baseline.document().floors().stream()
                .map(floor -> floor.selection().id()).toList();
        if (!document.floorIds().equals(baselineFloorIds)) {
            throw new IllegalStateException("Source floor identity and order cannot be changed by this codec");
        }

        List<SourceFloor> floors = new ArrayList<>(baseline.document().floors().size());
        Map<String, List<String>> layerOrder = new LinkedHashMap<>();
        for (SourceFloor baselineFloor : baseline.document().floors()) {
            String floorId = baselineFloor.selection().id();
            EditableFloor editableFloor = document.floor(floorId);
            Map<String, MinimapLayer> baselineLayers = layersById(baselineFloor.layers());
            Map<String, MinimapLayer> merged = new LinkedHashMap<>();
            for (String layerId : editableFloor.layerIds()) {
                EditableLayer editable = editableFloor.layer(layerId);
                MinimapLayer original = baselineLayers.get(layerId);
                merged.put(layerId, original == null
                        ? newLayer(editable)
                        : mergeLayer(original, editable));
            }
            if (!merged.keySet().containsAll(baselineLayers.keySet())) {
                throw new IllegalStateException("Existing source layers cannot be removed by this codec");
            }

            List<MinimapLayer> floorLayers = new ArrayList<>(merged.size());
            for (MinimapLayer original : baselineFloor.layers()) {
                floorLayers.add(merged.remove(original.common().id()));
            }
            for (String layerId : editableFloor.layerIds()) {
                MinimapLayer added = merged.remove(layerId);
                if (added != null) {
                    floorLayers.add(added);
                }
            }
            floors.add(new SourceFloor(
                    baselineFloor.selection(),
                    editableFloor.label(),
                    baselineFloor.contentBounds(),
                    baselineFloor.background(),
                    baselineFloor.calibration(),
                    floorLayers
            ));
            layerOrder.put(floorId, editableFloor.layerIds());
        }

        SourceManifest oldManifest = baseline.manifest();
        SourceManifest manifest = new SourceManifest(
                oldManifest.formatVersion(),
                oldManifest.documentId(),
                oldManifest.binding(),
                revision,
                oldManifest.dimension(),
                oldManifest.provenance(),
                oldManifest.tileEdge(),
                oldManifest.entries()
        );
        SourceDocument oldDocument = baseline.document();
        SourceDocument mergedDocument = new SourceDocument(
                oldDocument.worldBounds(),
                oldDocument.canvas(),
                oldDocument.defaultViewMode(),
                floors,
                layerOrder
        );
        return new MinimapDefinition(
                manifest,
                mergedDocument,
                baseline.regions(),
                baseline.connections(),
                baseline.styles()
        );
    }

    private static MinimapLayer mergeLayer(MinimapLayer baseline, EditableLayer editable) {
        if (baseline.type() != editable.type()) {
            throw new IllegalStateException("Existing source layer type cannot change: " + editable.id());
        }
        LayerCommon common = common(editable, baseline.common().clip());
        // Existing type-specific bindings remain source-authoritative in this snapshot slice.
        if (baseline instanceof ImportedImageLayer image) {
            return new ImportedImageLayer(common, image.assetId());
        }
        if (baseline instanceof WorldBakeLayer world) {
            return new WorldBakeLayer(common, world.generatorId());
        }
        if (baseline instanceof RasterPaintLayer) {
            return new RasterPaintLayer(common);
        }
        if (baseline instanceof VectorLayer vector) {
            return new VectorLayer(common, vector.vectorIds());
        }
        if (baseline instanceof RegionVisualLayer region) {
            return new RegionVisualLayer(common, region.regionIds());
        }
        if (baseline instanceof CutoutLayer) {
            return new CutoutLayer(common);
        }
        throw new IllegalStateException("Unsupported source layer type: " + baseline.type());
    }

    private static MinimapLayer newLayer(EditableLayer editable) {
        if (editable.type() != LayerType.RASTER_PAINT) {
            throw new IllegalStateException(
                    "New editor source layers must be RASTER_PAINT: " + editable.id());
        }
        return new RasterPaintLayer(common(editable, Optional.empty()));
    }

    private static LayerCommon common(
            EditableLayer editable,
            Optional<com.ptcrys.fpsmatch.core.minimap.model.CanvasRect> clip
    ) {
        return new LayerCommon(
                editable.id(),
                editable.label(),
                editable.visible(),
                editable.locked(),
                editable.opacity(),
                editable.blendMode(),
                clip,
                editable.maskEnabled()
        );
    }

    private static List<CanonicalZipWriter.EntrySource> rebuiltEntries(
            EditorSourceSnapshot snapshot
    ) {
        Map<ContainerPath, byte[]> entries = new LinkedHashMap<>();
        snapshot.entries().forEach((path, bytes) -> {
            MinimapContainerLayout.SourceEntryKind kind =
                    MinimapContainerLayout.classifySource(path).orElseThrow();
            if (kind != MinimapContainerLayout.SourceEntryKind.LAYER_TILE
                    && kind != MinimapContainerLayout.SourceEntryKind.LAYER_MASK) {
                entries.put(path, bytes);
            }
        });

        EditorDocument document = snapshot.document();
        for (String floorId : document.floorIds()) {
            for (EditableLayer layer : document.floor(floorId).layers()) {
                for (Map.Entry<com.ptcrys.fpsmatch.core.minimap.editor.document.TileKey, int[]>
                        tile : layer.tiles().snapshot().entrySet()) {
                    entries.put(layerPath(floorId, layer.id(), "tiles", tile.getKey()),
                            encodePixels(document, tile.getKey(), tile.getValue()));
                }
                if (layer.maskEnabled()) {
                    for (Map.Entry<com.ptcrys.fpsmatch.core.minimap.editor.document.TileKey, int[]>
                            tile : layer.maskTiles().snapshot().entrySet()) {
                        entries.put(layerPath(floorId, layer.id(), "mask", tile.getKey()),
                                encodePixels(document, tile.getKey(), tile.getValue()));
                    }
                }
            }
        }
        List<CanonicalZipWriter.EntrySource> sources = new ArrayList<>(entries.size());
        entries.forEach((path, bytes) -> sources.add(new CanonicalZipWriter.Entry(path, bytes)));
        return List.copyOf(sources);
    }

    private static ContainerPath layerPath(
            String floorId,
            String layerId,
            String kind,
            com.ptcrys.fpsmatch.core.minimap.editor.document.TileKey tile
    ) {
        return ContainerPath.parse("floors/" + floorId + "/layers/" + layerId + "/"
                + kind + "/" + tile.tileX() + "_" + tile.tileY() + ".png");
    }

    private static byte[] encodePixels(EditorDocument document, TileKey tile, int[] pixels) {
        int width = Math.min(document.tileEdge(),
                document.canvas().width() - tile.tileX() * document.tileEdge());
        int height = Math.min(document.tileEdge(),
                document.canvas().height() - tile.tileY() * document.tileEdge());
        byte[] rgba = new byte[pixels.length * 4];
        for (int index = 0; index < pixels.length; index++) {
            int pixel = RasterSurface.isInheritedPixel(pixels[index]) ? 0 : pixels[index];
            int offset = index * 4;
            rgba[offset] = (byte) Rgba8.red(pixel);
            rgba[offset + 1] = (byte) Rgba8.green(pixel);
            rgba[offset + 2] = (byte) Rgba8.blue(pixel);
            rgba[offset + 3] = (byte) Rgba8.alpha(pixel);
        }
        return CanonicalPngCodecV1.encode(width, height, rgba);
    }

    private static int[] decodePixels(byte[] png, LayerType type, boolean mask) {
        BoundedPngReader.DecodedPng decoded = BoundedPngReader.decode(png);
        byte[] rgba = decoded.rgba();
        int[] pixels = new int[decoded.width() * decoded.height()];
        for (int index = 0; index < pixels.length; index++) {
            int offset = index * 4;
            int alpha = rgba[offset + 3] & 0xff;
            if (!mask && type == LayerType.RASTER_PAINT && alpha == 0) {
                pixels[index] = RasterSurface.inheritedPixel();
            } else {
                pixels[index] = Rgba8.of(
                        rgba[offset] & 0xff,
                        rgba[offset + 1] & 0xff,
                        rgba[offset + 2] & 0xff,
                        alpha
                );
            }
        }
        return pixels;
    }

    private static boolean assetHasVisiblePixels(
            Map<ContainerPath, byte[]> entries,
            String assetId
    ) {
        for (Map.Entry<ContainerPath, byte[]> entry : entries.entrySet()) {
            MinimapContainerLayout.SourceTileAddress address =
                    MinimapContainerLayout.parseSourceTile(entry.getKey()).orElse(null);
            if (address != null
                    && address.kind() == MinimapContainerLayout.SourceEntryKind.ASSET_TILE
                    && assetId.equals(address.ownerId())
                    && pngHasAlpha(entry.getValue())) {
                return true;
            }
        }
        return false;
    }

    private static boolean pngHasAlpha(byte[] png) {
        byte[] rgba = BoundedPngReader.decode(png).rgba();
        for (int index = 3; index < rgba.length; index += 4) {
            if ((rgba[index] & 0xff) != 0) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasAlpha(int[] pixels) {
        for (int pixel : pixels) {
            if (!RasterSurface.isInheritedPixel(pixel) && Rgba8.alpha(pixel) != 0) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, MinimapLayer> layersById(List<MinimapLayer> layers) {
        Map<String, MinimapLayer> result = new LinkedHashMap<>();
        for (MinimapLayer layer : layers) {
            result.put(layer.common().id(), layer);
        }
        return result;
    }

    private static Map<ContainerPath, PreservedExtensions> copyExtensions(
            Map<ContainerPath, PreservedExtensions> source
    ) {
        Map<ContainerPath, PreservedExtensions> copy = new LinkedHashMap<>();
        source.forEach((path, extensions) -> copy.put(path,
                extensions.isPresent()
                        ? PreservedExtensions.fromCanonicalBytes(extensions.canonicalBytes())
                        : PreservedExtensions.missing()));
        return Map.copyOf(copy);
    }

    private static MinimapDefinition copyDefinition(MinimapDefinition source) {
        SourceManifest manifest = source.manifest();
        SourceManifest manifestCopy = new SourceManifest(
                manifest.formatVersion(), manifest.documentId(), manifest.binding(),
                manifest.revision(), manifest.dimension(), manifest.provenance(),
                manifest.tileEdge(), manifest.entries());
        SourceDocument document = source.document();
        List<SourceFloor> floors = document.floors().stream()
                .map(EditorSourceCodec::copyFloor)
                .toList();
        SourceDocument documentCopy = new SourceDocument(
                document.worldBounds(), document.canvas(), document.defaultViewMode(),
                floors, document.layerOrder());
        return new MinimapDefinition(
                manifestCopy,
                documentCopy,
                new RegionsFile(source.regions().regions()),
                new ConnectionsFile(source.connections().connections()),
                new StylesFile(source.styles().styles())
        );
    }

    private static SourceFloor copyFloor(SourceFloor floor) {
        return new SourceFloor(
                floor.selection(),
                floor.label(),
                floor.contentBounds(),
                floor.background(),
                floor.calibration(),
                floor.layers().stream().map(EditorSourceCodec::copyLayer).toList()
        );
    }

    private static MinimapLayer copyLayer(MinimapLayer source) {
        LayerCommon old = source.common();
        LayerCommon common = new LayerCommon(
                old.id(), old.label(), old.visible(), old.locked(), old.opacity(),
                old.blendMode(), old.clip(), old.maskEnabled());
        if (source instanceof ImportedImageLayer image) {
            return new ImportedImageLayer(common, image.assetId());
        }
        if (source instanceof WorldBakeLayer world) {
            return new WorldBakeLayer(common, world.generatorId());
        }
        if (source instanceof RasterPaintLayer) {
            return new RasterPaintLayer(common);
        }
        if (source instanceof VectorLayer vector) {
            return new VectorLayer(common, vector.vectorIds());
        }
        if (source instanceof RegionVisualLayer region) {
            return new RegionVisualLayer(common, region.regionIds());
        }
        if (source instanceof CutoutLayer) {
            return new CutoutLayer(common);
        }
        throw new IllegalStateException("Unsupported source layer type: " + source.type());
    }
}
