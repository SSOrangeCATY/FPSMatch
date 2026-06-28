package com.tacz.guns.client.gui.components.smith;

import com.tacz.guns.GunMod;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

public class TypeButton extends Button {
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "textures/gui/gun_smith_table.png");
    private final ItemStack stack;
    private boolean isSelected = false;

    public TypeButton(int pX, int pY, ItemStack stack, Button.OnPress onPress) {
        super(pX, pY, 24, 25, Component.empty(), onPress, DEFAULT_NARRATION);
        this.stack = stack;
    }

    @Override
    protected void extractContents(@NotNull GuiGraphicsExtractor gui, int pMouseX, int pMouseY, float pPartialTick) {
        int vOffset = isHoveredOrFocused() ? 204 + this.height : 204;
        if (isSelected) {
            gui.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, this.getX(), this.getY(), 0, vOffset, this.width, this.height, 256, 256);
        } else {
            gui.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, this.getX(), this.getY(), 26, vOffset, this.width, this.height, 256, 256);
        }

        gui.item(this.stack, this.getX() + 4, this.getY() + 5);
    }

    @Override
    public void onPress(InputWithModifiers input) {
        this.isSelected = true;
        this.onPress.onPress(this);
    }

    public void setSelected(boolean selected) {
        isSelected = selected;
    }
}
