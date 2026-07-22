package com.phasetranscrystal.fpsmatch.common.client.screen.mapselect.ldlib2;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.phasetranscrystal.fpsmatch.util.RenderUtil;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/** Renders the face and hat layers from a player's Minecraft skin in an LDLib2 row. */
final class Ldlib2PlayerAvatarElement extends UIElement {
    private final ResourceLocation skin;

    Ldlib2PlayerAvatarElement(String id, UUID uuid, String name) {
        setId(id);
        setAllowHitTest(false);
        this.skin = RenderUtil.fetchSkin(uuid, name == null ? "" : name);
    }

    @Override
    public void drawBackgroundAdditional(GUIContext context) {
        int size = Math.min(Math.round(getSizeWidth()), Math.round(getSizeHeight()));
        if (size <= 0) {
            return;
        }
        int x = Math.round(getPositionX());
        int y = Math.round(getPositionY());
        context.graphics.blit(skin, x, y, 8, 8, size, size, 64, 64);
        context.graphics.blit(skin, x, y, 40, 8, size, size, 64, 64);
    }
}
