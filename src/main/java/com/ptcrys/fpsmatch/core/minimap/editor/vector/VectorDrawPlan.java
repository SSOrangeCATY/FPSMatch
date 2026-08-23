package com.ptcrys.fpsmatch.core.minimap.editor.vector;

import java.util.List;

public record VectorDrawPlan(List<VectorDrawPrimitive> primitives) {
    public VectorDrawPlan {
        primitives = List.copyOf(primitives);
    }
}
