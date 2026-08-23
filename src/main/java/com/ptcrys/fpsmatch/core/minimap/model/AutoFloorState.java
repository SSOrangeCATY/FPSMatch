package com.ptcrys.fpsmatch.core.minimap.model;

import com.ptcrys.fpsmatch.core.minimap.contract.MinimapFormatContract;

public sealed interface AutoFloorState permits AutoFloorState.None, AutoFloorState.Floor {
    static AutoFloorState none() {
        return None.INSTANCE;
    }

    static AutoFloorState floor(String floorId) {
        return new Floor(floorId);
    }

    enum None implements AutoFloorState {
        INSTANCE
    }

    record Floor(String floorId) implements AutoFloorState {
        public Floor {
            if (!MinimapFormatContract.isInternalSlug(floorId)) {
                throw new IllegalArgumentException("Floor state ID must be a valid internal slug");
            }
        }
    }
}
