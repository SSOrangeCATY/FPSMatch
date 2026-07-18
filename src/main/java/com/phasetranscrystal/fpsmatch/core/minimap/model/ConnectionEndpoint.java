package com.phasetranscrystal.fpsmatch.core.minimap.model;

import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapFormatContract;

import java.util.Objects;

public record ConnectionEndpoint(String floorId, CanvasPoint point) {
    public ConnectionEndpoint {
        if (!MinimapFormatContract.isInternalSlug(floorId)) {
            throw new IllegalArgumentException("Connection floor ID must be a valid internal slug");
        }
        Objects.requireNonNull(point, "point");
    }
}
