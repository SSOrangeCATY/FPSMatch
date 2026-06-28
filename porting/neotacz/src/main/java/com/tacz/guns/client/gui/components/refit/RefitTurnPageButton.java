package com.tacz.guns.client.gui.components.refit;

import com.tacz.guns.client.gui.GunRefitScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public class RefitTurnPageButton extends Button implements IComponentTooltip {
    private final boolean isUpPage;

    public RefitTurnPageButton(int pX, int pY, boolean isUpPage, OnPress pOnPress) {
        super(pX, pY, 18, 8, Component.empty(), pOnPress, DEFAULT_NARRATION);
        this.isUpPage = isUpPage;
    }

    @Override
    protected void extractContents(@Nonnull GuiGraphicsExtractor graphics, int pMouseX, int pMouseY, float pPartialTick) {
        int x = getX(), y = getY();
        int yOffset = isUpPage ? 0 : 80;
        if (isHoveredOrFocused()) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, GunRefitScreen.TURN_PAGE_TEXTURE, x, y, 0, yOffset, width, height, 180, 80, 180, 160);
        } else {
            graphics.blit(RenderPipelines.GUI_TEXTURED, GunRefitScreen.TURN_PAGE_TEXTURE, x + 1, y + 1, 10, yOffset + 10, width - 2, height - 2, 180 - 20, 80 - 20, 180, 160);
        }
    }

    @Override
    public void renderTooltip(Consumer<List<Component>> consumer) {
        if (this.isHoveredOrFocused()) {
            String key = isUpPage ? "tooltip.tacz.page.previous" : "tooltip.tacz.page.next";
            consumer.accept(Collections.singletonList(Component.translatable(key)));
        }
    }
}
