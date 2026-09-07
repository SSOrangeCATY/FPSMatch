package com.ptcrys.fpsmatch.common.client.screen.ldlib2;

import com.ptcrys.fpsmatch.common.client.screen.mapselect.ldlib2.FPSMMapSelectTheme;
import net.minecraft.client.gui.GuiGraphics;

/** Shared low-contrast survey backdrop for every FPSM map and tactical-map surface. */
public final class FPSMLdlib2Backdrop {
    private static final int GRID_STEP = 48;

    private FPSMLdlib2Backdrop() {
    }

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

    /**
     * Map-selection backdrop: a neutral deployment field with local measurement marks. The grid
     * is deliberately confined to the edges so it does not compete with room names or previews.
     */
    public static void drawMapIndex(GuiGraphics graphics, int width, int height) {
        graphics.fill(0, 0, width, height, FPSMMapSelectTheme.BG);
        int edge = Math.min(96, Math.max(24, width / 7));
        for (int y = 24; y < height; y += 32) {
            graphics.fill(0, y, edge, y + 1, FPSMMapSelectTheme.GRID_LINE);
            graphics.fill(Math.max(0, width - edge), y, width, y + 1,
                    FPSMMapSelectTheme.GRID_LINE);
        }
        for (int x = 24; x < width; x += 64) {
            graphics.fill(x, 0, x + 1, Math.min(18, height), FPSMMapSelectTheme.GRID_LINE);
            graphics.fill(x, Math.max(0, height - 18), x + 1, height,
                    FPSMMapSelectTheme.GRID_LINE);
        }
        graphics.fill(12, 8, Math.min(width - 12, 104), 10, FPSMMapSelectTheme.ACCENT);
        graphics.fill(Math.max(12, width - 70), Math.max(0, height - 10),
                Math.max(12, width - 12), Math.max(0, height - 8), FPSMMapSelectTheme.WARNING);
    }
}
