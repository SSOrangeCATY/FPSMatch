package com.ptcrys.fpsmatch.core.minimap.model;

import com.ptcrys.fpsmatch.core.minimap.contract.MinimapFormatVersion;
import com.ptcrys.fpsmatch.core.minimap.contract.MinimapHardLimits;

import java.util.List;
import java.util.Objects;

public record RuntimeManifest(
        MinimapFormatVersion formatVersion,
        NamespacedId documentId,
        MapKey binding,
        long publishRevision,
        Sha256 sourceHash,
        CompilerProfile compilerProfile,
        CanvasBounds canvas,
        DefaultViewMode defaultViewMode,
        List<RuntimeFloor> floors,
        int tileEdge,
        List<RuntimeEntryDescriptor> entries
) {
    public RuntimeManifest {
        Objects.requireNonNull(formatVersion, "formatVersion");
        Objects.requireNonNull(documentId, "documentId");
        Objects.requireNonNull(binding, "binding");
        Objects.requireNonNull(sourceHash, "sourceHash");
        Objects.requireNonNull(compilerProfile, "compilerProfile");
        Objects.requireNonNull(canvas, "canvas");
        Objects.requireNonNull(defaultViewMode, "defaultViewMode");
        floors = List.copyOf(floors);
        entries = List.copyOf(entries);
        if (publishRevision < 0) {
            throw new IllegalArgumentException("Publish revision must be non-negative");
        }
        if (tileEdge <= 0 || tileEdge > MinimapHardLimits.MAX_TILE_EDGE) {
            throw new IllegalArgumentException("Tile edge exceeds the hard limit");
        }
        if (floors.size() > MinimapHardLimits.MAX_FLOORS
                || entries.size() > MinimapHardLimits.MAX_ZIP_ENTRIES - 1) {
            throw new IllegalArgumentException("Runtime manifest exceeds a hard count limit");
        }
    }
}
