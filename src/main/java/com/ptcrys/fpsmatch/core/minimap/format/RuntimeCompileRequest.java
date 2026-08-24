package com.ptcrys.fpsmatch.core.minimap.format;

import com.ptcrys.fpsmatch.core.minimap.contract.MinimapHardLimits;
import com.ptcrys.fpsmatch.core.minimap.model.CompilerProfile;
import com.ptcrys.fpsmatch.core.minimap.model.ContainerPath;
import com.ptcrys.fpsmatch.core.minimap.model.MapKey;
import com.ptcrys.fpsmatch.core.minimap.model.NamespacedId;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record RuntimeCompileRequest(
        NamespacedId documentId,
        MapKey binding,
        long publishRevision,
        CompilerProfile compilerProfile,
        int tileEdge,
        int zoomLevels,
        List<CanonicalZipWriter.EntrySource> tileEntries,
        Optional<CanonicalZipWriter.EntrySource> thumbnail
) {
    public RuntimeCompileRequest {
        if (publishRevision < 0) {
            throw new IllegalArgumentException("Publish revision must be non-negative");
        }
        Objects.requireNonNull(compilerProfile, "compilerProfile");
        if (tileEdge < 0 || tileEdge > MinimapHardLimits.MAX_TILE_EDGE) {
            throw new IllegalArgumentException("Tile edge must be zero or within the hard limit");
        }
        if (zoomLevels <= 0 || zoomLevels > 32) {
            throw new IllegalArgumentException("Zoom levels must be in [1, 32]");
        }
        tileEntries = List.copyOf(tileEntries);
        thumbnail = Objects.requireNonNull(thumbnail, "thumbnail");
    }

    public RuntimeCompileRequest(
            long publishRevision,
            CompilerProfile compilerProfile,
            List<? extends CanonicalZipWriter.EntrySource> tileEntries
    ) {
        this(null, null, publishRevision, compilerProfile, 0, 1,
                copyEntries(tileEntries), Optional.empty());
    }

    public RuntimeCompileRequest(
            long publishRevision,
            CompilerProfile compilerProfile,
            int tileEdge,
            int zoomLevels,
            List<? extends CanonicalZipWriter.EntrySource> tileEntries
    ) {
        this(null, null, publishRevision, compilerProfile, tileEdge, zoomLevels,
                copyEntries(tileEntries), Optional.empty());
    }

    public static RuntimeCompileRequest forSource(
            com.ptcrys.fpsmatch.core.minimap.model.SourceManifest source,
            long publishRevision,
            CompilerProfile compilerProfile,
            List<? extends CanonicalZipWriter.EntrySource> tileEntries
    ) {
        return new RuntimeCompileRequest(
                source.documentId(), source.binding(), publishRevision, compilerProfile,
                source.tileEdge(), 1, copyEntries(tileEntries), Optional.empty()
        );
    }

    private static List<CanonicalZipWriter.EntrySource> copyEntries(
            List<? extends CanonicalZipWriter.EntrySource> entries
    ) {
        Objects.requireNonNull(entries, "tileEntries");
        return List.copyOf(entries);
    }
}
