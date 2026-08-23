package com.ptcrys.fpsmatch.core.minimap.editor.importing;

import com.ptcrys.fpsmatch.core.minimap.contract.MinimapFormatContract;

import java.util.Objects;

public record ImportedImageAsset(
        String assetId,
        String layerId,
        int width,
        int height,
        int placedWidth,
        int placedHeight,
        int offsetX,
        int offsetY,
        byte[] canonicalPngBytes
) {
    public ImportedImageAsset {
        if (!MinimapFormatContract.isInternalSlug(assetId)) {
            throw new IllegalArgumentException("Asset ID must be a valid internal slug");
        }
        Objects.requireNonNull(layerId, "layerId");
        Objects.requireNonNull(canonicalPngBytes, "canonicalPngBytes");
        if (width <= 0 || height <= 0 || placedWidth <= 0 || placedHeight <= 0) {
            throw new IllegalArgumentException("Imported image dimensions must be positive");
        }
        canonicalPngBytes = canonicalPngBytes.clone();
    }

    @Override
    public byte[] canonicalPngBytes() {
        return canonicalPngBytes.clone();
    }
}
