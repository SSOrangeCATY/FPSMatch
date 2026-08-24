package com.ptcrys.fpsmatch.common.client.minimap.ui.ldlib2.editor;

import java.util.Objects;

public record EditorUiLayoutModel(
        Rect canvas,
        Rect toolbar,
        Rect layerPanel,
        Rect properties,
        Rect floorStrip,
        Rect statusBar
) {
    private static final int MIN_SIDE_WIDTH = 212;
    private static final int TOOLBAR_HEIGHT = 44;
    private static final int FLOOR_HEIGHT = 32;
    private static final int STATUS_HEIGHT = 26;

    public EditorUiLayoutModel {
        Objects.requireNonNull(canvas, "canvas");
        Objects.requireNonNull(toolbar, "toolbar");
        Objects.requireNonNull(layerPanel, "layerPanel");
        Objects.requireNonNull(properties, "properties");
        Objects.requireNonNull(floorStrip, "floorStrip");
        Objects.requireNonNull(statusBar, "statusBar");
    }

    /**
     * Computes one non-overlapping editor geometry. Narrow clients intentionally
     * hide the side panels; the screen exposes them through its drawer trigger.
     */
    public static EditorUiLayoutModel responsive(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Layout dimensions must be positive");
        }
        int statusHeight = Math.min(STATUS_HEIGHT, Math.max(1, height / 8));
        int floorHeight = Math.min(FLOOR_HEIGHT, Math.max(1, height / 8));
        // Keep the footer bands disjoint even while a host reports a transient
        // one- or two-pixel viewport during screen replacement.
        if (statusHeight + floorHeight > height) {
            floorHeight = Math.max(0, height - statusHeight);
            if (floorHeight == 0) {
                statusHeight = height;
            }
        }
        // Compact toolbars wrap to two rows so Save/Publish/Close remain reachable.
        int toolbarRows = width < 760 ? 2 : 1;
        int remainingHeight = Math.max(0, height - statusHeight - floorHeight);
        int toolbarHeight = Math.min(
                TOOLBAR_HEIGHT * toolbarRows,
                remainingHeight
        );
        int bodyTop = toolbarHeight;
        int bodyBottom = Math.max(bodyTop, height - floorHeight - statusHeight);
        int bodyHeight = Math.max(0, bodyBottom - bodyTop);
        boolean compact = width < 760;
        int sideWidth = compact ? 0 : Math.min(260, Math.max(MIN_SIDE_WIDTH, width / 4));
        int canvasX = compact ? 0 : 8;
        int canvasWidth = Math.max(0, width - canvasX - sideWidth - (compact ? 0 : 8));
        int panelX = width - sideWidth;
        int layerHeight = sideWidth == 0 ? 0 : bodyHeight / 2;
        int propertiesHeight = sideWidth == 0 ? 0 : bodyHeight - layerHeight;
        return new EditorUiLayoutModel(
                new Rect(canvasX, bodyTop, canvasWidth, bodyHeight),
                new Rect(0, 0, width, toolbarHeight),
                new Rect(panelX, bodyTop, sideWidth, layerHeight),
                new Rect(panelX, bodyTop + layerHeight, sideWidth, propertiesHeight),
                new Rect(0, bodyBottom, width, floorHeight),
                new Rect(0, height - statusHeight, width, statusHeight)
        );
    }

    public boolean compact() {
        return layerPanel.width() == 0 && properties.width() == 0;
    }

    public record Rect(int x, int y, int width, int height) {
        public Rect {
            if (x < 0 || y < 0 || width < 0 || height < 0) {
                throw new IllegalArgumentException("Rect size must be non-negative");
            }
        }
    }
}
