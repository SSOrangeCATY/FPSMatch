package com.phasetranscrystal.fpsmatch.core.minimap.editor.vector;

import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapFormatContract;
import com.phasetranscrystal.fpsmatch.core.minimap.editor.document.EditorDocument;
import com.phasetranscrystal.fpsmatch.core.minimap.model.CanvasPoint;
import com.phasetranscrystal.fpsmatch.core.minimap.model.MinimapRegion;
import com.phasetranscrystal.fpsmatch.core.minimap.model.VectorObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

public final class ObjectEditor {
    private final EditorDocument document;
    private final Map<String, VectorObject> vectorsById = new LinkedHashMap<>();
    private final Map<String, MinimapRegion> regionsById = new LinkedHashMap<>();
    private final AtomicLong vectorSequence = new AtomicLong();

    private ObjectEditor(EditorDocument document) {
        this.document = Objects.requireNonNull(document, "document");
    }

    public static ObjectEditor bind(EditorDocument document) {
        return new ObjectEditor(document);
    }

    public EditorDocument document() {
        return document;
    }

    public List<String> vectorIds(String floorId) {
        requireFloor(floorId);
        List<String> ids = new ArrayList<>();
        for (VectorObject vector : vectorsById.values()) {
            if (vector.floorId().equals(floorId)) {
                ids.add(vector.id());
            }
        }
        return List.copyOf(ids);
    }

    public List<String> regionIds(String floorId) {
        requireFloor(floorId);
        List<String> ids = new ArrayList<>();
        for (MinimapRegion region : regionsById.values()) {
            if (region.floorId().equals(floorId)) {
                ids.add(region.id());
            }
        }
        return List.copyOf(ids);
    }

    public VectorObject vector(String id) {
        VectorObject vector = vectorsById.get(id);
        if (vector == null) {
            throw new IllegalArgumentException("Unknown vector: " + id);
        }
        return vector;
    }

    public MinimapRegion region(String id) {
        MinimapRegion region = regionsById.get(id);
        if (region == null) {
            throw new IllegalArgumentException("Unknown region: " + id);
        }
        return region;
    }

    public VectorObject createVector(VectorObject vector) {
        Objects.requireNonNull(vector, "vector");
        requireFloor(vector.floorId());
        if (vectorsById.containsKey(vector.id())) {
            throw new IllegalArgumentException("Duplicate vector id: " + vector.id());
        }
        vectorsById.put(vector.id(), vector);
        return vector;
    }

    public MinimapRegion createRegion(MinimapRegion region) {
        Objects.requireNonNull(region, "region");
        requireFloor(region.floorId());
        if (regionsById.containsKey(region.id())) {
            throw new IllegalArgumentException("Duplicate region id: " + region.id());
        }
        regionsById.put(region.id(), region);
        return region;
    }

    public VectorObject updateVector(VectorObject vector) {
        Objects.requireNonNull(vector, "vector");
        requireKnownVector(vector.id());
        requireFloor(vector.floorId());
        vectorsById.put(vector.id(), vector);
        return vector;
    }

    public MinimapRegion updateRegion(MinimapRegion region) {
        Objects.requireNonNull(region, "region");
        requireKnownRegion(region.id());
        requireFloor(region.floorId());
        regionsById.put(region.id(), region);
        return region;
    }

    public MinimapRegion updateRegionVisibility(
            String id,
            double minVisibleScale,
            double maxVisibleScale,
            int priority,
            CanvasPoint labelAnchor
    ) {
        MinimapRegion current = region(id);
        MinimapRegion updated = RegionMutations.withVisibility(
                current, minVisibleScale, maxVisibleScale, priority, labelAnchor);
        regionsById.put(id, updated);
        return updated;
    }

    public VectorObject moveVector(String id, double du, double dv) {
        VectorObject moved = vector(id).translated(du, dv);
        vectorsById.put(id, moved);
        return moved;
    }

    public VectorObject copyVector(String id, String targetFloorId) {
        requireFloor(targetFloorId);
        VectorObject source = vector(id);
        String copyId = nextVectorId(source.id());
        VectorObject copy = source.withId(copyId).withFloorId(targetFloorId);
        vectorsById.put(copyId, copy);
        return copy;
    }

    public VectorObject cutVector(String id, String targetFloorId) {
        requireFloor(targetFloorId);
        VectorObject source = vector(id);
        VectorObject cut = source.withFloorId(targetFloorId);
        vectorsById.put(id, cut);
        return cut;
    }

    public void deleteVector(String id) {
        requireKnownVector(id);
        vectorsById.remove(id);
    }

    public void deleteRegion(String id) {
        requireKnownRegion(id);
        regionsById.remove(id);
    }

    private String nextVectorId(String seed) {
        String base = seed;
        if (!MinimapFormatContract.isInternalSlug(base)) {
            base = "vector";
        }
        while (true) {
            String candidate = base + "_copy_" + Long.toString(vectorSequence.incrementAndGet(), 36);
            if (MinimapFormatContract.isInternalSlug(candidate) && !vectorsById.containsKey(candidate)) {
                return candidate;
            }
        }
    }

    private void requireFloor(String floorId) {
        document.floor(floorId);
    }

    private void requireKnownVector(String id) {
        if (!vectorsById.containsKey(id)) {
            throw new IllegalArgumentException("Unknown vector: " + id);
        }
    }

    private void requireKnownRegion(String id) {
        if (!regionsById.containsKey(id)) {
            throw new IllegalArgumentException("Unknown region: " + id);
        }
    }
}
