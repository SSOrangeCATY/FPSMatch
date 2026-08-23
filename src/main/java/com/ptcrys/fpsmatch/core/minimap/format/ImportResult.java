package com.ptcrys.fpsmatch.core.minimap.format;

import com.ptcrys.fpsmatch.core.minimap.model.Provenance;

import java.util.Objects;

public record ImportResult(
        SourceMapDraft draft,
        Provenance provenance,
        long baseRevision
) {
    public ImportResult {
        Objects.requireNonNull(draft, "draft");
        Objects.requireNonNull(provenance, "provenance");
        if (baseRevision < 0) {
            throw new IllegalArgumentException("Base revision must be non-negative");
        }
    }
}
