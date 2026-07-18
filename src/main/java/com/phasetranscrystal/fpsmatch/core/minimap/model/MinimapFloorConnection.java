package com.phasetranscrystal.fpsmatch.core.minimap.model;

import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapFormatContract;

import java.util.Objects;
import java.util.Optional;

public record MinimapFloorConnection(
        String id,
        ConnectionEndpoint from,
        ConnectionEndpoint to,
        ConnectionType type,
        ConnectionDisplayDirection displayDirection,
        Optional<DisplayLabel> label
) {
    public MinimapFloorConnection {
        if (!MinimapFormatContract.isInternalSlug(id)) {
            throw new IllegalArgumentException("Connection ID must be a valid internal slug");
        }
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(displayDirection, "displayDirection");
        label = Objects.requireNonNull(label, "label");
        if (from.floorId().equals(to.floorId())) {
            throw new IllegalArgumentException("A floor connection must connect different floors");
        }
    }
}
