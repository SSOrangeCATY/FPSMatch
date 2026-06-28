package com.tacz.guns.client.gui.components;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Keeps TACZ's atlas-backed buttons on the 26.1 GUI extraction path.
 */
public class TextureImageButton extends Button {
    private final Identifier texture;
    private final int u;
    private final int v;
    private final int hoverVOffset;
    private final int textureWidth;
    private final int textureHeight;

    public TextureImageButton(int x, int y, int width, int height, int u, int v, int hoverVOffset, Identifier texture, OnPress onPress) {
        this(x, y, width, height, u, v, hoverVOffset, texture, 256, 256, onPress);
    }

    public TextureImageButton(
            int x, int y, int width, int height, int u, int v, int hoverVOffset,
            Identifier texture, int textureWidth, int textureHeight, OnPress onPress) {
        super(x, y, width, height, Component.empty(), onPress, DEFAULT_NARRATION);
        this.texture = texture;
        this.u = u;
        this.v = v;
        this.hoverVOffset = hoverVOffset;
        this.textureWidth = textureWidth;
        this.textureHeight = textureHeight;
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int vOffset = this.v + (this.isHoveredOrFocused() ? this.hoverVOffset : 0);
        graphics.blit(RenderPipelines.GUI_TEXTURED, this.texture, this.getX(), this.getY(), this.u, vOffset,
                this.getWidth(), this.getHeight(), this.textureWidth, this.textureHeight);
    }
}
