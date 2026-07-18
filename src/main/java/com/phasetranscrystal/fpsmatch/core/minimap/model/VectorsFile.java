package com.phasetranscrystal.fpsmatch.core.minimap.model;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record VectorsFile(List<VectorObject> vectors) {
    public VectorsFile {
        vectors = List.copyOf(vectors);
        Set<String> ids = new HashSet<>();
        for (VectorObject vector : vectors) {
            if (!ids.add(vector.id())) {
                throw new IllegalArgumentException("Duplicate vector id: " + vector.id());
            }
        }
    }
}
