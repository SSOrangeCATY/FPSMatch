package com.ptcrys.fpsmatch.core.minimap.model;

import com.ptcrys.fpsmatch.core.minimap.contract.MinimapFormatVersion;
import com.ptcrys.fpsmatch.core.minimap.contract.MinimapHardLimits;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record SourceManifest(
        MinimapFormatVersion formatVersion,
        NamespacedId documentId,
        MapKey binding,
        long revision,
        NamespacedId dimension,
        Optional<Provenance> provenance,
        int tileEdge,
        List<SourceEntryDescriptor> entries
) {
    public SourceManifest {
        Objects.requireNonNull(formatVersion, "formatVersion");
        Objects.requireNonNull(documentId, "documentId");
        Objects.requireNonNull(binding, "binding");
        Objects.requireNonNull(dimension, "dimension");
        provenance = Objects.requireNonNull(provenance, "provenance");
        entries = List.copyOf(entries);
        if (revision < 0) {
            throw new IllegalArgumentException("Source revision must be non-negative");
        }
        if (tileEdge <= 0 || tileEdge > MinimapHardLimits.MAX_TILE_EDGE) {
            throw new IllegalArgumentException("Tile edge exceeds the hard limit");
        }
        if (entries.size() > MinimapHardLimits.MAX_ZIP_ENTRIES - 1) {
            throw new IllegalArgumentException("Source entry count exceeds the hard limit");
        }
    }
}
