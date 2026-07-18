package com.phasetranscrystal.fpsmatch.common.minimap.server;

import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapHardLimits;

public record DraftStoreLimits(
        int maximumActiveDrafts,
        int maximumOperationsPerDraft,
        long maximumContentBytesPerDraft
) {
    private static final int HARD_MAXIMUM_ACTIVE_DRAFTS = 1_024;
    private static final int HARD_MAXIMUM_OPERATIONS = 4_096;

    public DraftStoreLimits {
        if (maximumActiveDrafts <= 0
                || maximumActiveDrafts > HARD_MAXIMUM_ACTIVE_DRAFTS
                || maximumOperationsPerDraft <= 0
                || maximumOperationsPerDraft > HARD_MAXIMUM_OPERATIONS
                || maximumContentBytesPerDraft <= 0
                || maximumContentBytesPerDraft > MinimapHardLimits.MAX_SOURCE_EXPANDED_BYTES) {
            throw new IllegalArgumentException("Draft store limits are invalid");
        }
    }

    public static DraftStoreLimits hardDefaults() {
        return new DraftStoreLimits(
                HARD_MAXIMUM_ACTIVE_DRAFTS,
                HARD_MAXIMUM_OPERATIONS,
                MinimapHardLimits.MAX_SOURCE_EXPANDED_BYTES
        );
    }
}
