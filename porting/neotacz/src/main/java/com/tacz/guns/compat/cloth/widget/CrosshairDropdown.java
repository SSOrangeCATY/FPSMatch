package com.tacz.guns.compat.cloth.widget;

import com.tacz.guns.client.renderer.crosshair.CrosshairType;
import me.shedaniel.clothconfig2.gui.entries.DropdownBoxEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

public class CrosshairDropdown {
    public static DropdownBoxEntry.SelectionTopCellElement<CrosshairType> of(CrosshairType type) {
        return new DropdownBoxEntry.DefaultSelectionTopCellElement<>(type, name -> {
            for (CrosshairType crosshairType : CrosshairType.values()) {
                if (crosshairType.name().equals(name)) {
                    return crosshairType;
                }
            }
            return null;
        }, id -> Component.literal(id.toString())) {
            @Override
            public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int x, int y, int width, int height, float delta) {
                this.textFieldWidget.setX(x + 4);
                this.textFieldWidget.setY(y + 6);
                this.textFieldWidget.setWidth(width - 4 - 20);
                this.textFieldWidget.setEditable(this.getParent().isEditable());
                this.textFieldWidget.setTextColor(this.getPreferredTextColor());
                this.textFieldWidget.extractRenderState(graphics, mouseX, mouseY, delta);

                Identifier location = CrosshairType.getTextureLocation(this.value);
                graphics.blit(RenderPipelines.GUI_TEXTURED, location, x + width - 18, y + 2, 0, 0, 16, 16, 16, 16);
            }
        };
    }

    public static DropdownBoxEntry.SelectionCellCreator<CrosshairType> of() {
        return new DropdownBoxEntry.DefaultSelectionCellCreator<>(i -> Component.literal(i.name())) {
            @Override
            public DropdownBoxEntry.SelectionCellElement<CrosshairType> create(CrosshairType selection) {
                return new DropdownBoxEntry.DefaultSelectionCellElement<>(selection, this.toTextFunction) {
                    @Override
                    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int x, int y, int width, int height, float delta) {
                        this.rendering = true;
                        this.x = x;
                        this.y = y;
                        this.width = width;
                        this.height = height;
                        boolean isHover = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
                        if (isHover) {
                            graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0xff191919);
                        }
                        FormattedCharSequence text = this.toTextFunction.apply(this.r).getVisualOrderText();
                        int color = isHover ? 0xffffff : 0x888888;
                        graphics.text(Minecraft.getInstance().font, text, x + 6 + 18, y + 6, color);

                        Identifier location = CrosshairType.getTextureLocation(this.r);
                        graphics.blit(RenderPipelines.GUI_TEXTURED, location, x + 4, y + 2, 0, 0, 16, 16, 16, 16);
                    }
                };
            }

            @Override
            public int getCellHeight() {
                return 20;
            }

            @Override
            public int getCellWidth() {
                return 146;
            }

            @Override
            public int getDropBoxMaxHeight() {
                return this.getCellHeight() * CrosshairType.values().length;
            }
        };
    }
}
