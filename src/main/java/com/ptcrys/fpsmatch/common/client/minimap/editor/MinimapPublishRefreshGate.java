package com.ptcrys.fpsmatch.common.client.minimap.editor;

import com.ptcrys.fpsmatch.common.packet.mapselect.MapRoomSummary;
import com.ptcrys.fpsmatch.core.minimap.model.MapKey;
import com.ptcrys.fpsmatch.core.minimap.model.NamespacedId;

import java.util.Objects;

/** Locks editor reopen until map-room detail exposes the committed publication. */
public record MinimapPublishRefreshGate(
        MapKey mapKey,
        NamespacedId documentId,
        long minimumRevision
) {
    public MinimapPublishRefreshGate {
        Objects.requireNonNull(mapKey, "mapKey");
        Objects.requireNonNull(documentId, "documentId");
        if (minimumRevision < 0) {
            throw new IllegalArgumentException("Minimum minimap revision cannot be negative");
        }
    }

    public static MinimapPublishRefreshGate create(
            MapKey mapKey,
            NamespacedId documentId,
            long minimumRevision
    ) {
        return new MinimapPublishRefreshGate(mapKey, documentId, minimumRevision);
    }

    public boolean sameMap(MapRoomSummary summary) {
        Objects.requireNonNull(summary, "summary");
        return mapKey.gameType().equals(summary.gameType())
                && mapKey.mapName().equals(summary.mapName());
    }

    public boolean accepts(MapRoomSummary summary) {
        if (!sameMap(summary)) {
            return false;
        }
        return summary.minimapIdentity()
                .filter(identity -> documentId.equals(identity.documentId()))
                .filter(identity -> identity.revision() >= minimumRevision)
                .isPresent();
    }
}
