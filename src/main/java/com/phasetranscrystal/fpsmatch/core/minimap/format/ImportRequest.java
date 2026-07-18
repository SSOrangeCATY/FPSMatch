package com.phasetranscrystal.fpsmatch.core.minimap.format;

import com.phasetranscrystal.fpsmatch.core.minimap.model.MapKey;
import com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId;

import java.util.Objects;
import java.util.Set;

public record ImportRequest(
        SourceMap source,
        MapKey targetBinding,
        NamespacedId targetDimension,
        ImportMode mode,
        NamespacedId currentDocumentId,
        long currentRevision,
        NamespacedId requestedDocumentId,
        Set<NamespacedId> occupiedDocumentIds
) {
    public ImportRequest {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(targetBinding, "targetBinding");
        Objects.requireNonNull(targetDimension, "targetDimension");
        Objects.requireNonNull(mode, "mode");
        if (currentRevision < 0) {
            throw new IllegalArgumentException("Current revision must be non-negative");
        }
        if (mode == ImportMode.REPLACE_CURRENT && currentDocumentId == null) {
            throw new IllegalArgumentException("Replacing requires a current document ID");
        }
        occupiedDocumentIds = Set.copyOf(Objects.requireNonNull(
                occupiedDocumentIds, "occupiedDocumentIds"
        ));
    }

    public static ImportRequest unbound(
            SourceMap source,
            MapKey targetBinding,
            NamespacedId targetDimension
    ) {
        return new ImportRequest(
                source, targetBinding, targetDimension, ImportMode.SAVE_AS,
                null, 0, null, Set.of()
        );
    }

    public static ImportRequest replace(
            SourceMap source,
            MapKey targetBinding,
            NamespacedId targetDimension,
            NamespacedId currentDocumentId,
            long currentRevision
    ) {
        return new ImportRequest(
                source, targetBinding, targetDimension, ImportMode.REPLACE_CURRENT,
                currentDocumentId, currentRevision, null, Set.of()
        );
    }
}
