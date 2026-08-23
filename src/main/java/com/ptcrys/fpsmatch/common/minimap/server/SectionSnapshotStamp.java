package com.ptcrys.fpsmatch.common.minimap.server;

import java.util.Objects;

public record SectionSnapshotStamp(
        long snapshotId,
        WorldSectionKey section,
        long sectionRevision,
        boolean stale
) {
    public SectionSnapshotStamp {
        Objects.requireNonNull(section, "section");
        if (snapshotId < 0 || sectionRevision < 0) {
            throw new IllegalArgumentException("Snapshot stamp counters must be non-negative");
        }
    }

    SectionSnapshotStamp withStale(boolean nextStale) {
        return new SectionSnapshotStamp(
                snapshotId, section, sectionRevision, nextStale
        );
    }
}
