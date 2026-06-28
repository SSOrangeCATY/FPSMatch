package com.tacz.guns.client.gui.components;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.Collections;
import java.util.List;

public class FlatColorButton extends Button {
    private boolean isSelect = false;
    private List<Component> tooltips;

    public FlatColorButton(int pX, int pY, int pWidth, int pHeight, Component pMessage, OnPress pOnPress) {
        super(pX, pY, pWidth, pHeight, pMessage, pOnPress, DEFAULT_NARRATION);
    }

    public FlatColorButton setTooltips(String key) {
        tooltips = Collections.singletonList(Component.translatable(key));
        return this;
    }

    public FlatColorButton setTooltips(List<Component> tooltips) {
        this.tooltips = tooltips;
        return this;
    }

    public FlatColorButton setTooltips(Component... tooltips) {
        this.tooltips = List.of(tooltips);
        return this;
    }

    public void extractToolTip(GuiGraphicsExtractor graphics, int pMouseX, int pMouseY) {
        if (this.isHovered && tooltips != null) {
            graphics.setComponentTooltipForNextFrame(Minecraft.getInstance().font, tooltips, pMouseX, pMouseY);
        }
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float pPartialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        if (isSelect) {
            graphics.fillGradient(this.getX(), this.getY(), this.getX() + this.getWidth(), this.getY() + this.getHeight(), 0xAF222222, 0xAF222222);
        } else {
            graphics.fillGradient(this.getX(), this.getY(), this.getX() + this.getWidth(), this.getY() + this.getHeight(), 0xAF222222, 0xAF222222);
        }
        if (this.isHoveredOrFocused()) {
            graphics.fillGradient(this.getX(), this.getY() + 1, this.getX() + 1, this.getY() + this.getHeight() - 1, 0xff_F3EFE0, 0xff_F3EFE0);
            graphics.fillGradient(this.getX(), this.getY(), this.getX() + this.getWidth(), this.getY() + 1, 0xff_F3EFE0, 0xff_F3EFE0);
            graphics.fillGradient(this.getX() + this.getWidth() - 1, this.getY() + 1, this.getX() + this.getWidth(), this.getY() + this.getHeight() - 1, 0xff_F3EFE0, 0xff_F3EFE0);
            graphics.fillGradient(this.getX(), this.getY() + this.getHeight() - 1, this.getX() + this.getWidth(), this.getY() + this.getHeight(), 0xff_F3EFE0, 0xff_F3EFE0);
        }
        var text = graphics.textRendererForWidget(this, GuiGraphicsExtractor.HoveredTextEffects.NONE);
        text.defaultParameters(text.defaultParameters().withOpacity(this.getAlpha()));
        this.extractScrollingStringOverContents(text, this.getMessage().copy().withColor(0xF3EFE0), 2);
        this.extractToolTip(graphics, mouseX, mouseY);
    }

    public void setSelect(boolean select) {
        isSelect = select;
    }
}
