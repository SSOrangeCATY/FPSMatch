package com.phasetranscrystal.fpsmatch.common.minimap.server.sync;

public record MinimapSyncQuotas(
        int maxSubscriptionsPerPlayer,
        int maxFragmentsPerTick,
        int maxQueuedFragmentsPerPlayer
) {
    public MinimapSyncQuotas {
        if (maxSubscriptionsPerPlayer <= 0 || maxFragmentsPerTick <= 0 || maxQueuedFragmentsPerPlayer <= 0) {
            throw new IllegalArgumentException("Sync quotas must be positive");
        }
    }
}