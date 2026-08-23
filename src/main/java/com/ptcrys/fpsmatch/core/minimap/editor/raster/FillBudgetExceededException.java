package com.ptcrys.fpsmatch.core.minimap.editor.raster;

public final class FillBudgetExceededException extends RuntimeException {
    public FillBudgetExceededException(int budget) {
        super("Flood fill exceeded budget of " + budget + " cells");
    }
}
