package com.phasetranscrystal.fpsmatch.core.minimap.model;

import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapFormatContract;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class LayerReferences {
    private LayerReferences() {
    }

    static List<String> copyAndValidate(List<String> ids, String kind) {
        List<String> copy = List.copyOf(ids);
        Set<String> unique = new HashSet<>();
        for (String id : copy) {
            if (!MinimapFormatContract.isInternalSlug(id)) {
                throw new IllegalArgumentException("Invalid " + kind + " reference ID");
            }
            if (!unique.add(id)) {
                throw new IllegalArgumentException("Duplicate " + kind + " reference ID: " + id);
            }
        }
        return copy;
    }
}
