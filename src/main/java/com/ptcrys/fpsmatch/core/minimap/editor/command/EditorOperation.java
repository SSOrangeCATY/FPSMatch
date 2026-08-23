package com.ptcrys.fpsmatch.core.minimap.editor.command;

import com.ptcrys.fpsmatch.core.minimap.contract.MinimapFormatContract;
import com.ptcrys.fpsmatch.core.minimap.model.Sha256;

import java.util.Objects;

public sealed interface EditorOperation
        permits EditorOperation.SetOpacity, EditorOperation.SetVisibility, EditorOperation.PaintTile {
    String path();

    String kind();

    static SetOpacity setOpacity(String layerId, double opacity) {
        return new SetOpacity(layerId, opacity);
    }

    static SetVisibility setVisibility(String layerId, boolean visible) {
        return new SetVisibility(layerId, visible);
    }

    static PaintTile paintTile(String layerId, int tileX, int tileY, Sha256 payloadHash, int pixelCount) {
        return new PaintTile(layerId, tileX, tileY, payloadHash, pixelCount);
    }

    record SetOpacity(String layerId, double opacity) implements EditorOperation {
        public SetOpacity {
            requireLayerId(layerId);
            if (!Double.isFinite(opacity) || opacity < 0.0 || opacity > 1.0) {
                throw new IllegalArgumentException("Opacity must be in [0, 1]");
            }
        }

        @Override
        public String path() {
            return layerId;
        }

        @Override
        public String kind() {
            return "set_opacity";
        }
    }

    record SetVisibility(String layerId, boolean visible) implements EditorOperation {
        public SetVisibility {
            requireLayerId(layerId);
        }

        @Override
        public String path() {
            return layerId;
        }

        @Override
        public String kind() {
            return "set_visibility";
        }
    }

    record PaintTile(String layerId, int tileX, int tileY, Sha256 payloadHash, int pixelCount)
            implements EditorOperation {
        public PaintTile {
            requireLayerId(layerId);
            Objects.requireNonNull(payloadHash, "payloadHash");
            if (tileX < 0 || tileY < 0) {
                throw new IllegalArgumentException("Tile coordinates must be non-negative");
            }
            if (pixelCount <= 0) {
                throw new IllegalArgumentException("Pixel count must be positive");
            }
        }

        @Override
        public String path() {
            return layerId + "/tiles/" + tileX + "_" + tileY;
        }

        @Override
        public String kind() {
            return "paint_tile";
        }
    }

    private static void requireLayerId(String layerId) {
        if (!MinimapFormatContract.isInternalSlug(layerId)) {
            throw new IllegalArgumentException("Layer ID must be a valid internal slug");
        }
    }
}
