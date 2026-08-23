package com.ptcrys.fpsmatch.core.minimap.editor.bake;

import com.ptcrys.fpsmatch.core.minimap.model.NamespacedId;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record WorldSnapshotRequest(
        UUID requestId,
        String actorId,
        String mapKey,
        NamespacedId dimension,
        List<SectionCoord> sections,
        List<SnapshotChannelId> channels,
        UnloadedSectionPolicy unloadedPolicy,
        long timeoutMillis
) {
    public WorldSnapshotRequest {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(mapKey, "mapKey");
        Objects.requireNonNull(dimension, "dimension");
        sections = List.copyOf(sections);
        channels = List.copyOf(channels);
        Objects.requireNonNull(unloadedPolicy, "unloadedPolicy");
        if (sections.isEmpty()) {
            throw new IllegalArgumentException("Snapshot request requires at least one section");
        }
        if (channels.isEmpty()) {
            throw new IllegalArgumentException("Snapshot request requires at least one channel");
        }
        if (timeoutMillis < 0) {
            throw new IllegalArgumentException("Timeout must be non-negative");
        }
    }
}
