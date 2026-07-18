package com.phasetranscrystal.fpsmatch.core.minimap.editor.document;

import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapFormatContract;
import com.phasetranscrystal.fpsmatch.core.minimap.model.BlendMode;
import com.phasetranscrystal.fpsmatch.core.minimap.model.DisplayLabel;
import com.phasetranscrystal.fpsmatch.core.minimap.model.LayerType;

import java.util.Objects;
import java.util.Optional;

public final class EditableLayer {
    private final String id;
    private final LayerType type;
    private DisplayLabel label;
    private boolean visible;
    private boolean locked;
    private double opacity;
    private BlendMode blendMode;
    private boolean maskEnabled;
    private String importedAssetId;
    private String generatorId;
    private final TileStore tiles = new TileStore();
    private final TileStore maskTiles = new TileStore();

    EditableLayer(String id, LayerType type, DisplayLabel label) {
        if (!MinimapFormatContract.isInternalSlug(id)) {
            throw new IllegalArgumentException("Layer ID must be a valid internal slug");
        }
        this.id = id;
        this.type = Objects.requireNonNull(type, "type");
        this.label = Objects.requireNonNull(label, "label");
        this.visible = true;
        this.locked = false;
        this.opacity = 1.0;
        this.blendMode = BlendMode.NORMAL;
        this.maskEnabled = false;
        this.importedAssetId = null;
        this.generatorId = null;
    }

    public String id() {
        return id;
    }

    public LayerType type() {
        return type;
    }

    public DisplayLabel label() {
        return label;
    }

    public boolean visible() {
        return visible;
    }

    public boolean locked() {
        return locked;
    }

    public double opacity() {
        return opacity;
    }

    public BlendMode blendMode() {
        return blendMode;
    }

    public boolean maskEnabled() {
        return maskEnabled;
    }

    public boolean presentInSource() {
        return true;
    }

    public Optional<String> importedAssetId() {
        return Optional.ofNullable(importedAssetId);
    }

    public Optional<String> generatorId() {
        return Optional.ofNullable(generatorId);
    }

    public TileStore tiles() {
        return tiles;
    }

    public TileStore maskTiles() {
        return maskTiles;
    }

    void rename(DisplayLabel label) {
        requireUnlocked();
        this.label = Objects.requireNonNull(label, "label");
    }

    void setVisible(boolean visible) {
        this.visible = visible;
    }

    void setLocked(boolean locked) {
        this.locked = locked;
    }

    void setOpacity(double opacity) {
        requireUnlocked();
        if (!Double.isFinite(opacity) || opacity < 0.0 || opacity > 1.0) {
            throw new IllegalArgumentException("Layer opacity must be in [0, 1]");
        }
        this.opacity = opacity;
    }

    void setBlendMode(BlendMode blendMode) {
        requireUnlocked();
        Objects.requireNonNull(blendMode, "blendMode");
        if (type == LayerType.CUTOUT && blendMode != BlendMode.NORMAL) {
            throw new IllegalArgumentException("Cutout layers use fixed DST_OUT and cannot use a color blend mode");
        }
        this.blendMode = blendMode;
    }

    void setMaskEnabled(boolean maskEnabled) {
        requireUnlocked();
        if (type == LayerType.CUTOUT && maskEnabled) {
            throw new IllegalArgumentException("Cutout layers cannot use a mask");
        }
        this.maskEnabled = maskEnabled;
    }

    void bindGenerator(String generatorId) {
        requireUnlocked();
        if (type != LayerType.WORLD_BAKE) {
            throw new IllegalStateException("Only world-bake layers can bind a generator");
        }
        if (!MinimapFormatContract.isInternalSlug(generatorId)) {
            throw new IllegalArgumentException("World bake generator ID must be a valid internal slug");
        }
        this.generatorId = generatorId;
    }

    void bindImportedAsset(String assetId) {
        requireUnlocked();
        if (type != LayerType.IMPORTED_IMAGE) {
            throw new IllegalStateException("Only imported image layers can bind an asset");
        }
        if (!MinimapFormatContract.isInternalSlug(assetId)) {
            throw new IllegalArgumentException("Imported image asset ID must be a valid internal slug");
        }
        this.importedAssetId = assetId;
    }

    EditableLayer duplicate(String newId) {
        EditableLayer copy = new EditableLayer(newId, type, label);
        copy.visible = visible;
        copy.locked = false;
        copy.opacity = opacity;
        copy.blendMode = blendMode;
        copy.maskEnabled = maskEnabled;
        copy.importedAssetId = importedAssetId;
        copy.generatorId = generatorId;
        tiles.snapshot().forEach((key, value) -> copy.tiles.put(key.tileX(), key.tileY(), value));
        maskTiles.snapshot().forEach((key, value) -> copy.maskTiles.put(key.tileX(), key.tileY(), value));
        return copy;
    }

    public void requireUnlocked() {
        if (locked) {
            throw new IllegalStateException("Layer is locked");
        }
    }
}
