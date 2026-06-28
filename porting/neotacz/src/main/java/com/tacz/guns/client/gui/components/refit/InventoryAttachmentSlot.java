package com.tacz.guns.client.gui.components.refit;

import com.tacz.guns.client.gui.GunRefitScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nonnull;
import java.util.function.Consumer;

public class InventoryAttachmentSlot extends Button implements IStackTooltip {
    private final int slotIndex;
    private final Inventory inventory;

    public InventoryAttachmentSlot(int pX, int pY, int slotIndex, Inventory inventory, Button.OnPress onPress) {
        super(pX, pY, 18, 18, Component.empty(), onPress, DEFAULT_NARRATION);
        this.slotIndex = slotIndex;
        this.inventory = inventory;
    }

    @Override
    public void renderTooltip(Consumer<ItemStack> consumer) {
        if (this.isHoveredOrFocused() && 0 <= this.slotIndex && this.slotIndex < this.inventory.getContainerSize()) {
            ItemStack item = this.inventory.getItem(slotIndex);
            consumer.accept(item);
        }
    }

    @Override
    protected void extractContents(@Nonnull GuiGraphicsExtractor graphics, int pMouseX, int pMouseY, float pPartialTick) {
        int x = getX(), y = getY();
        if (isHoveredOrFocused()) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, GunRefitScreen.SLOT_TEXTURE, x, y, 0, 0, width, height, 18, 18);
        } else {
            graphics.blit(RenderPipelines.GUI_TEXTURED, GunRefitScreen.SLOT_TEXTURE, x + 1, y + 1, 1, 1, width - 2, height - 2, 18, 18);
        }
        graphics.item(inventory.getItem(slotIndex), x + 1, y + 1);
    }

    public int getSlotIndex() {
        return slotIndex;
    }
}
