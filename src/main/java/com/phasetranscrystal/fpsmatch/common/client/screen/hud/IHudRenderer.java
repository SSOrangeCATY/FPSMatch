package com.phasetranscrystal.fpsmatch.common.client.screen.hud;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;


public interface IHudRenderer {
    void onRenderGuiLayerPre(RenderGuiLayerEvent.Pre event);
    void onSpectatorRender(GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker, int screenWidth, int screenHeight);
    void onPlayerRender(GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker, int screenWidth, int screenHeight);

    default void render(GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker, int screenWidth, int screenHeight, boolean isSpectator) {
        if (isSpectator) {
            onSpectatorRender(guiGraphics, deltaTracker, screenWidth, screenHeight);
        } else {
            onPlayerRender(guiGraphics, deltaTracker, screenWidth, screenHeight);
        }
    }
}
