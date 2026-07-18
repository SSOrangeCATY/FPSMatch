package com.phasetranscrystal.fpsmatch.core.minimap.model;

import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapFormatContract;

public record MinimapFloor(
        String id,
        double minY,
        double maxY,
        int autoPriority,
        double enterMargin,
        double exitMargin
) {
    private static final double MAX_MARGIN = 16.0;

    public MinimapFloor {
        if (!MinimapFormatContract.isInternalSlug(id)) {
            throw new IllegalArgumentException("Floor ID must be a valid internal slug");
        }
        if (!Double.isFinite(minY) || !Double.isFinite(maxY) || minY >= maxY) {
            throw new IllegalArgumentException("Floor height range must be finite and non-empty");
        }
        requireMargin(enterMargin, "enterMargin");
        requireMargin(exitMargin, "exitMargin");
        if (minY + enterMargin >= maxY - enterMargin) {
            throw new IllegalArgumentException("Floor enter range must be non-empty");
        }
    }

    public boolean containsBase(double y) {
        return y >= minY && y < maxY;
    }

    public boolean containsEnter(double y) {
        return y >= minY + enterMargin && y < maxY - enterMargin;
    }

    public boolean containsExit(double y) {
        return y >= minY - exitMargin && y < maxY + exitMargin;
    }

    private static void requireMargin(double value, String name) {
        if (!Double.isFinite(value) || value < 0 || value > MAX_MARGIN) {
            throw new IllegalArgumentException(name + " must be in [0, 16]");
        }
    }
}
