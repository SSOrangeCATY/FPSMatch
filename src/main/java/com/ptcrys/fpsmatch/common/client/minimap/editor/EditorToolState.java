package com.ptcrys.fpsmatch.common.client.minimap.editor;

public final class EditorToolState {
    private EditorTool tool = EditorTool.BRUSH;
    private SelectionMode selectionMode = SelectionMode.NONE;
    private int brushSize = 4;
    private int colorArgb = 0xFFFFFFFF;

    public EditorTool tool() {
        return tool;
    }

    public SelectionMode selectionMode() {
        return selectionMode;
    }

    public int brushSize() {
        return brushSize;
    }

    public int colorArgb() {
        return colorArgb;
    }

    void selectTool(EditorTool tool) {
        this.tool = java.util.Objects.requireNonNull(tool, "tool");
    }

    void setSelectionMode(SelectionMode mode) {
        this.selectionMode = java.util.Objects.requireNonNull(mode, "mode");
    }

    void setBrushSize(int size) {
        if (size < 1 || size > 64) {
            throw new IllegalArgumentException("Brush size must be in 1..64");
        }
        this.brushSize = size;
    }

    void setColorArgb(int colorArgb) {
        this.colorArgb = colorArgb;
    }
}