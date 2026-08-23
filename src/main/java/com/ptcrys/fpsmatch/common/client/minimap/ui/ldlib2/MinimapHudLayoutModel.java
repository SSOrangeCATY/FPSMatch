package com.ptcrys.fpsmatch.common.client.minimap.ui.ldlib2;

public record MinimapHudLayoutModel(int x, int y, int width, int height) {
    public MinimapHudLayoutModel {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("layout size must be positive");
        }
    }
}