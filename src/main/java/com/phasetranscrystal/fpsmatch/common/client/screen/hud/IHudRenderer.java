package com.phasetranscrystal.fpsmatch.common.client.screen.hud;

import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

public interface IHudRenderer extends IGuiOverlay {
    void onRenderGuiOverlayPre(RenderGuiOverlayEvent.Pre event);
    boolean isSpectatorRenderable();
}
