package com.phasetranscrystal.fpsmatch.common.minimap.server;

record WorldSectionRevisionLimits(
        long maximumSnapshotBytes,
        int maximumDimensions,
        int maximumSections,
        int maximumDirtySections
) {
    private static final long HARD_MAXIMUM_SNAPSHOT_BYTES = 64L * 1024 * 1024;
    private static final int HARD_MAXIMUM_DIMENSIONS = 1_024;
    private static final int HARD_MAXIMUM_SECTIONS = 262_144;
    private static final int HARD_MAXIMUM_DIRTY_SECTIONS = 262_144;

    WorldSectionRevisionLimits {
        if (maximumSnapshotBytes <= 0
                || maximumSnapshotBytes > HARD_MAXIMUM_SNAPSHOT_BYTES
                || maximumDimensions <= 0
                || maximumDimensions > HARD_MAXIMUM_DIMENSIONS
                || maximumSections <= 0
                || maximumSections > HARD_MAXIMUM_SECTIONS
                || maximumDirtySections <= 0
                || maximumDirtySections > HARD_MAXIMUM_DIRTY_SECTIONS) {
            throw new IllegalArgumentException(
                    "World section revision limits are invalid"
            );
        }
    }

    static WorldSectionRevisionLimits hardDefaults() {
        return new WorldSectionRevisionLimits(
                HARD_MAXIMUM_SNAPSHOT_BYTES,
                HARD_MAXIMUM_DIMENSIONS,
                HARD_MAXIMUM_SECTIONS,
                HARD_MAXIMUM_DIRTY_SECTIONS
        );
    }
}
