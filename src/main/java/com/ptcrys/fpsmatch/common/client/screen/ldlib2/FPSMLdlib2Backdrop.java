package com.ptcrys.fpsmatch.common.client.screen.ldlib2;

import net.minecraft.client.gui.GuiGraphics;

/** Shared low-contrast survey backdrop for every FPSM map and tactical-map surface. */
public final class FPSMLdlib2Backdrop {

    private static final int GRID_STEP = 48;

    private FPSMLdlib2Backdrop() {}

    public static void draw(GuiGraphics graphics, int width, int height) {
        graphics.fill(0, 0, width, height, FPSMLdlib2Theme.BG);
        for (int x = 16; x < width; x += GRID_STEP) {
            graphics.fill(x, 8, x + 1, height - 8, FPSMLdlib2Theme.GRID_LINE);
        }
        for (int y = 16; y < height; y += GRID_STEP) {
            graphics.fill(8, y, width - 8, y + 1, FPSMLdlib2Theme.GRID_LINE);
        }
        graphics.fill(16, 7, Math.min(width - 16, 92), 9, FPSMLdlib2Theme.ACCENT);
        graphics.fill(Math.max(16, width - 76), height - 9, width - 16, height - 7,
                FPSMLdlib2Theme.WARNING);
        graphics.fill(8, 8, 10, Math.min(height - 8, 42), FPSMLdlib2Theme.BORDER);
        graphics.fill(width - 10, Math.max(8, height - 42), width - 8, height - 8,
                FPSMLdlib2Theme.BORDER);
    }
}
