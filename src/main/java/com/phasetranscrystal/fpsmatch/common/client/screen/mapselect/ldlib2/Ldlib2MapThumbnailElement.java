package com.phasetranscrystal.fpsmatch.common.client.screen.mapselect.ldlib2;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.phasetranscrystal.fpsmatch.common.client.screen.mapselect.MapThumbnailRenderer;

/** LDLib2 bridge for the existing map preview renderer. */
final class Ldlib2MapThumbnailElement extends UIElement {
    private final String texture;
    private final String mapName;
    private final String gameType;
    private final String displayName;

    Ldlib2MapThumbnailElement(String id, String texture, String mapName, String gameType, String displayName) {
        setId(id);
        setAllowHitTest(false);
        this.texture = texture == null ? "" : texture;
        this.mapName = mapName;
        this.gameType = gameType;
        this.displayName = displayName;
    }

    @Override
    public void drawBackgroundAdditional(GUIContext context) {
        if (getSizeWidth() <= 0 || getSizeHeight() <= 0) return;
        MapThumbnailRenderer.render(
                context.graphics,
                Math.round(getPositionX()),
                Math.round(getPositionY()),
                Math.round(getSizeWidth()),
                Math.round(getSizeHeight()),
                texture,
                mapName,
                gameType,
                displayName,
                false
        );
    }
}
