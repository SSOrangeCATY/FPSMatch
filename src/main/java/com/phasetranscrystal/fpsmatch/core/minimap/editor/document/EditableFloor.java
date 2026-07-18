package com.phasetranscrystal.fpsmatch.core.minimap.editor.document;

import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapFormatContract;
import com.phasetranscrystal.fpsmatch.core.minimap.model.DisplayLabel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class EditableFloor {
    private String id;
    private DisplayLabel label;
    private final Map<String, EditableLayer> layersById = new LinkedHashMap<>();
    private final List<String> layerOrder = new ArrayList<>();

    EditableFloor(String id, DisplayLabel label) {
        if (!MinimapFormatContract.isInternalSlug(id)) {
            throw new IllegalArgumentException("Floor ID must be a valid internal slug");
        }
        this.id = id;
        this.label = Objects.requireNonNull(label, "label");
    }

    public String id() {
        return id;
    }

    public DisplayLabel label() {
        return label;
    }

    public List<String> layerIds() {
        return List.copyOf(layerOrder);
    }

    public List<EditableLayer> layers() {
        List<EditableLayer> layers = new ArrayList<>(layerOrder.size());
        for (String layerId : layerOrder) {
            layers.add(layersById.get(layerId));
        }
        return Collections.unmodifiableList(layers);
    }

    public EditableLayer layer(String layerId) {
        EditableLayer layer = layersById.get(layerId);
        if (layer == null) {
            throw new IllegalArgumentException("Unknown layer: " + layerId);
        }
        return layer;
    }

    void rename(String newId, DisplayLabel label) {
        if (!MinimapFormatContract.isInternalSlug(newId)) {
            throw new IllegalArgumentException("Floor ID must be a valid internal slug");
        }
        this.id = newId;
        this.label = Objects.requireNonNull(label, "label");
    }

    void addLayer(EditableLayer layer) {
        Objects.requireNonNull(layer, "layer");
        if (layersById.containsKey(layer.id())) {
            throw new IllegalArgumentException("Duplicate layer id: " + layer.id());
        }
        layersById.put(layer.id(), layer);
        layerOrder.add(layer.id());
    }

    void reorderLayer(String layerId, int targetIndex) {
        if (!layersById.containsKey(layerId)) {
            throw new IllegalArgumentException("Unknown layer: " + layerId);
        }
        if (targetIndex < 0 || targetIndex >= layerOrder.size()) {
            throw new IllegalArgumentException("Layer reorder index out of bounds");
        }
        layerOrder.remove(layerId);
        layerOrder.add(targetIndex, layerId);
    }

    int layerCount() {
        return layerOrder.size();
    }
}
