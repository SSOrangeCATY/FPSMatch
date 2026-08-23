package com.ptcrys.fpsmatch.core.minimap.model;

import com.ptcrys.fpsmatch.core.minimap.contract.MinimapFormatContract;

import java.util.Objects;

public record ImportedImageLayer(LayerCommon common, String assetId) implements MinimapLayer {
    public ImportedImageLayer {
        Objects.requireNonNull(common, "common");
        if (!MinimapFormatContract.isInternalSlug(assetId)) {
            throw new IllegalArgumentException("Imported image asset ID must be a valid internal slug");
        }
    }

    @Override
    public LayerType type() {
        return LayerType.IMPORTED_IMAGE;
    }
}
