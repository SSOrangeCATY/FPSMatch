package com.ptcrys.fpsmatch.core.minimap.model;

import com.ptcrys.fpsmatch.core.minimap.contract.MinimapFormatContract;

import java.util.Objects;

public record ConnectionEndpoint(String floorId, CanvasPoint point) {
    public ConnectionEndpoint {
        if (!MinimapFormatContract.isInternalSlug(floorId)) {
            throw new IllegalArgumentException("Connection floor ID must be a valid internal slug");
        }
        Objects.requireNonNull(point, "point");
    }
}
