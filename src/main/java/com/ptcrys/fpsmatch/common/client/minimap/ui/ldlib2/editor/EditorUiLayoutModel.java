package com.ptcrys.fpsmatch.common.client.minimap.ui.ldlib2.editor;

public record EditorUiLayoutModel(
        Rect canvas,
        Rect toolbar,
        Rect layerPanel,
        Rect properties,
        Rect floorStrip,
        Rect statusBar
) {
    public record Rect(int x, int y, int width, int height) {
        public Rect {
            if (width < 0 || height < 0) {
                throw new IllegalArgumentException("Rect size must be non-negative");
            }
        }
    }
}