package com.ptcrys.fpsmatch.core.minimap.editor.document;

import com.ptcrys.fpsmatch.core.minimap.format.PreservedExtensions;
import com.ptcrys.fpsmatch.core.minimap.model.ContainerPath;
import com.ptcrys.fpsmatch.core.minimap.model.MinimapDefinition;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class EditorSourceSnapshot {
    private final byte[] originalSourceBytes;
    private final MinimapDefinition definition;
    private final Map<ContainerPath, PreservedExtensions> authorityExtensions;
    private final Map<ContainerPath, byte[]> entries;
    private final EditorDocument document;

    EditorSourceSnapshot(
            byte[] originalSourceBytes,
            MinimapDefinition definition,
            Map<ContainerPath, PreservedExtensions> authorityExtensions,
            Map<ContainerPath, byte[]> entries,
            EditorDocument document
    ) {
        this.originalSourceBytes = Objects.requireNonNull(
                originalSourceBytes, "originalSourceBytes").clone();
        this.definition = Objects.requireNonNull(definition, "definition");
        this.authorityExtensions = Map.copyOf(new LinkedHashMap<>(
                Objects.requireNonNull(authorityExtensions, "authorityExtensions")));
        this.entries = copyEntries(entries);
        this.document = Objects.requireNonNull(document, "document");
    }

    public byte[] originalSourceBytes() {
        return originalSourceBytes.clone();
    }

    public MinimapDefinition definition() {
        return definition;
    }

    public Map<ContainerPath, PreservedExtensions> authorityExtensions() {
        return authorityExtensions;
    }

    public Map<ContainerPath, byte[]> entries() {
        return copyEntries(entries);
    }

    public EditorDocument document() {
        return document;
    }

    private static Map<ContainerPath, byte[]> copyEntries(Map<ContainerPath, byte[]> source) {
        Objects.requireNonNull(source, "entries");
        Map<ContainerPath, byte[]> copy = new LinkedHashMap<>();
        source.forEach((path, bytes) -> copy.put(
                Objects.requireNonNull(path, "entry path"),
                Objects.requireNonNull(bytes, "entry bytes").clone()));
        return Collections.unmodifiableMap(copy);
    }
}
