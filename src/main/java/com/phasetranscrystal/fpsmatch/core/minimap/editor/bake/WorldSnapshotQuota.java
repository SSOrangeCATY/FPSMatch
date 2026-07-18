package com.phasetranscrystal.fpsmatch.core.minimap.editor.bake;

public record WorldSnapshotQuota(int maxSections, long maxBytes, long maxDurationMillis) {
    public WorldSnapshotQuota {
        if (maxSections <= 0 || maxBytes <= 0 || maxDurationMillis <= 0) {
            throw new IllegalArgumentException("Snapshot quotas must be positive");
        }
    }
}
