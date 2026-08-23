package com.ptcrys.fpsmatch.core.minimap.format;

import com.ptcrys.fpsmatch.core.minimap.model.ContainerPath;
import com.ptcrys.fpsmatch.core.minimap.model.MinimapDefinition;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record SourceMapDraft(
        MinimapDefinition definition,
        List<? extends CanonicalZipWriter.EntrySource> entries,
        Map<ContainerPath, PreservedExtensions> authorityExtensions
) {
    public SourceMapDraft {
        Objects.requireNonNull(definition, "definition");
        entries = List.copyOf(entries);
        Objects.requireNonNull(authorityExtensions, "authorityExtensions");
        authorityExtensions = Map.copyOf(new LinkedHashMap<>(authorityExtensions));
    }

    public SourceMapDraft(
            MinimapDefinition definition,
            List<? extends CanonicalZipWriter.EntrySource> entries
    ) {
        this(definition, entries, Map.of());
    }
}
