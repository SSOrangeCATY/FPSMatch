package com.ptcrys.fpsmatch.core.minimap.editor.raster;

public final class FillBudget {
    private final int maxCells;

    public FillBudget(int maxCells) {
        if (maxCells <= 0) {
            throw new IllegalArgumentException("Fill budget must be positive");
        }
        this.maxCells = maxCells;
    }

    public int maxCells() {
        return maxCells;
    }
}
