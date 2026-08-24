package com.ptcrys.fpsmatch.core.minimap.editor.command;

import com.ptcrys.fpsmatch.core.minimap.contract.MinimapFormatContract;
import com.ptcrys.fpsmatch.core.minimap.model.Sha256;

import java.util.Objects;
import java.util.Optional;

public sealed interface EditorOperation permits
        EditorOperation.SetOpacity,
        EditorOperation.SetVisibility,
        EditorOperation.SetLocked,
        EditorOperation.PutTile,
        EditorOperation.DeleteTile {
    String floorId();

    String layerId();

    String path();

    String kind();

    static SetOpacity setOpacity(String floorId, String layerId, double opacity) {
        return new SetOpacity(floorId, layerId, opacity);
    }

    static SetVisibility setVisibility(String floorId, String layerId, boolean visible) {
        return new SetVisibility(floorId, layerId, visible);
    }

    static SetLocked setLocked(String floorId, String layerId, boolean locked) {
        return new SetLocked(floorId, layerId, locked);
    }

    static PutTile putTile(
            String floorId,
            String layerId,
            int tileX,
            int tileY,
            Optional<Sha256> oldHash,
            Sha256 newHash
    ) {
        return new PutTile(floorId, layerId, tileX, tileY, oldHash, newHash);
    }

    static DeleteTile deleteTile(
            String floorId,
            String layerId,
            int tileX,
            int tileY,
            Sha256 oldHash
    ) {
        return new DeleteTile(floorId, layerId, tileX, tileY, oldHash);
    }

    record SetOpacity(String floorId, String layerId, double opacity)
            implements EditorOperation {
        public SetOpacity {
            requireAddress(floorId, layerId);
            if (!Double.isFinite(opacity) || opacity < 0.0 || opacity > 1.0) {
                throw new IllegalArgumentException("Opacity must be in [0, 1]");
            }
            opacity = opacity == 0.0 ? 0.0 : opacity;
        }

        @Override
        public String path() {
            return layerPath(floorId, layerId);
        }

        @Override
        public String kind() {
            return "set_opacity";
        }
    }

    record SetVisibility(String floorId, String layerId, boolean visible)
            implements EditorOperation {
        public SetVisibility {
            requireAddress(floorId, layerId);
        }

        @Override
        public String path() {
            return layerPath(floorId, layerId);
        }

        @Override
        public String kind() {
            return "set_visibility";
        }
    }

    record SetLocked(String floorId, String layerId, boolean locked)
            implements EditorOperation {
        public SetLocked {
            requireAddress(floorId, layerId);
        }

        @Override
        public String path() {
            return layerPath(floorId, layerId);
        }

        @Override
        public String kind() {
            return "set_locked";
        }
    }

    record PutTile(
            String floorId,
            String layerId,
            int tileX,
            int tileY,
            Optional<Sha256> oldHash,
            Sha256 newHash
    ) implements EditorOperation {
        public PutTile {
            requireAddress(floorId, layerId);
            requireTile(tileX, tileY);
            oldHash = Objects.requireNonNull(oldHash, "oldHash");
            Objects.requireNonNull(newHash, "newHash");
        }

        @Override
        public String path() {
            return tilePath(floorId, layerId, tileX, tileY);
        }

        @Override
        public String kind() {
            return "put_tile";
        }
    }

    record DeleteTile(
            String floorId,
            String layerId,
            int tileX,
            int tileY,
            Sha256 oldHash
    ) implements EditorOperation {
        public DeleteTile {
            requireAddress(floorId, layerId);
            requireTile(tileX, tileY);
            Objects.requireNonNull(oldHash, "oldHash");
        }

        @Override
        public String path() {
            return tilePath(floorId, layerId, tileX, tileY);
        }

        @Override
        public String kind() {
            return "delete_tile";
        }
    }

    private static String layerPath(String floorId, String layerId) {
        return "floors/" + floorId + "/layers/" + layerId;
    }

    private static String tilePath(
            String floorId, String layerId, int tileX, int tileY
    ) {
        return layerPath(floorId, layerId)
                + "/tiles/" + tileX + "_" + tileY + ".png";
    }

    private static void requireAddress(String floorId, String layerId) {
        if (!MinimapFormatContract.isInternalSlug(floorId)) {
            throw new IllegalArgumentException("Floor ID must be a valid internal slug");
        }
        if (!MinimapFormatContract.isInternalSlug(layerId)) {
            throw new IllegalArgumentException("Layer ID must be a valid internal slug");
        }
    }

    private static void requireTile(int tileX, int tileY) {
        if (tileX < 0 || tileY < 0) {
            throw new IllegalArgumentException("Tile coordinates must be non-negative");
        }
    }
}
