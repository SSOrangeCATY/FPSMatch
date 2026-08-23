package com.ptcrys.fpsmatch.core.minimap.model;

import java.util.List;

public record ConnectionsFile(List<MinimapFloorConnection> connections) {
    public ConnectionsFile {
        connections = List.copyOf(connections);
    }
}
